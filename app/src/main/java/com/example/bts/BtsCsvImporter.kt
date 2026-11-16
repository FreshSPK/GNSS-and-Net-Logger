package com.example.bts

import com.example.data.db.BtsDao
import com.example.data.db.BtsTowerEntity
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

data class BtsImportResult(
    val totalLines: Int,
    val importedCount: Int,
    val withCoordsCount: Int,
    val skippedCount: Int,
    val detectedFormat: String,
    val operatorsFound: Set<String>,
    val durationMs: Long
)

object BtsCsvImporter {
    private const val BATCH_SIZE = 2000
    private val CP1250: Charset = Charset.forName("windows-1250")

    private enum class Format {
        GSMWEB_SEMICOLON,
        OPENCELLID_CSV,
        CLF,
        GENERIC_CSV
    }

    /**
     * Streams arbitrarily large CSV / CLF files (2-6 MB+) into Room with constant memory.
     * Auto-detects UTF-8 vs Windows-1250 (CP1250) and GSMweb / CLF / OpenCelliD / Generic CSV formats.
     */
    suspend fun importStream(
        inputStream: InputStream,
        btsDao: BtsDao,
        defaultOperatorPlmn: String? = null,
        fileNameHint: String? = null,
        replaceOperator: Boolean = false,
        onProgress: (processedLines: Int, importedCount: Int) -> Unit = { _, _ -> }
    ): BtsImportResult {
        val startMs = System.currentTimeMillis()
        val rawBuffered = BufferedInputStream(inputStream, 64 * 1024)
        rawBuffered.mark(4)
        val b0 = rawBuffered.read()
        val b1 = rawBuffered.read()
        rawBuffered.reset()
        val isGzip = (b0 == 0x1F && b1 == 0x8B) || fileNameHint?.lowercase(Locale.ROOT)?.endsWith(".gz") == true
        val buffered = if (isGzip) {
            BufferedInputStream(GZIPInputStream(rawBuffered, 64 * 1024), 64 * 1024)
        } else {
            rawBuffered
        }

        buffered.mark(16 * 1024)
        val probeBytes = ByteArray(16 * 1024)
        val probeLen = buffered.read(probeBytes).coerceAtLeast(0)
        buffered.reset()

        val charset = detectCharset(probeBytes, probeLen)
        val hintedPlmn = defaultOperatorPlmn ?: BtsGeoUtils.inferOperatorFromFilename(fileNameHint)

        var totalLines = 0
        var importedCount = 0
        var withCoordsCount = 0
        var skippedCount = 0
        var detectedFormat = "GSMweb CSV"
        val operatorsFound = mutableSetOf<String>()
        val clearedOperators = mutableSetOf<String>()

        BufferedReader(InputStreamReader(buffered, charset), 64 * 1024).use { reader ->
            var firstLine: String? = null
            while (true) {
                val raw = reader.readLine() ?: break
                val clean = raw.removePrefix("\uFEFF").trim()
                if (clean.isNotBlank() && clean.any { it != ';' && it != ',' }) {
                    firstLine = clean
                    break
                }
            }

            if (firstLine == null) {
                return BtsImportResult(0, 0, 0, 0, "Empty", emptySet(), System.currentTimeMillis() - startMs)
            }

            val firstDelim = detectDelimiter(firstLine)
            val firstCols = splitRow(firstLine, firstDelim)
            val hasHeader = looksLikeHeader(firstCols)

            val format: Format
            val headerMap: Map<String, Int>
            if (hasHeader) {
                headerMap = firstCols.mapIndexed { idx, col ->
                    col.trim().lowercase().replace(" ", "").replace("_", "") to idx
                }.toMap()
                format = detectFormatFromHeader(headerMap, firstDelim)
            } else {
                headerMap = emptyMap()
                val firstColUpper = firstCols.getOrNull(0)?.trim()?.uppercase(Locale.ROOT).orEmpty()
                format = when {
                    firstDelim == ',' && firstCols.size >= 8 &&
                        (firstCols.getOrNull(1)?.trim()?.toIntOrNull() != null ||
                            firstColUpper in setOf("LTE", "NR", "UMTS", "GSM", "CDMA")) -> Format.OPENCELLID_CSV
                    firstCols.size >= 8 && firstCols[0].trim().length == 5 && firstCols[0].trim().startsWith("230") -> Format.CLF
                    else -> Format.GSMWEB_SEMICOLON
                }
            }

            detectedFormat = when (format) {
                Format.GSMWEB_SEMICOLON -> "GSMweb CSV (${charset.name()})"
                Format.OPENCELLID_CSV -> if (isGzip) "OpenCelliD GZ CSV (MCC 230)" else "OpenCelliD / MLS CSV (MCC 230)"
                Format.CLF -> "CLF v3/v4"
                Format.GENERIC_CSV -> "CSV (${charset.name()})"
            }

            val batchesByPlmn = mutableMapOf<String, MutableList<BtsTowerEntity>>()

            suspend fun flushPlmn(plmn: String) {
                val list = batchesByPlmn[plmn] ?: return
                if (list.isEmpty()) return
                if (replaceOperator && clearedOperators.add(plmn)) {
                    btsDao.deleteByOperator(plmn)
                }
                btsDao.upsertPreservingCoords(plmn, list)
                list.clear()
            }

            suspend fun processLine(rawLine: String) {
                val clean = rawLine.removePrefix("\uFEFF").trim()
                if (clean.isEmpty() || clean.all { it == ';' || it == ',' || it == ' ' }) return
                totalLines++

                val delim = if (format == Format.OPENCELLID_CSV) ',' else firstDelim
                val cols = splitRow(clean, delim)
                val parsed = when (format) {
                    Format.GSMWEB_SEMICOLON -> parseGsmwebRow(cols, headerMap, hintedPlmn)
                    Format.OPENCELLID_CSV -> parseOpenCellIdRow(cols, headerMap, hintedPlmn)
                    Format.CLF -> parseClfRow(cols, hintedPlmn)
                    Format.GENERIC_CSV -> parseGenericRow(cols, headerMap, hintedPlmn)
                }

                if (parsed == null) {
                    skippedCount++
                    return
                }

                operatorsFound.add(parsed.operatorPlmn)
                importedCount++
                if (parsed.latitude != null && parsed.longitude != null) {
                    withCoordsCount++
                }

                val list = batchesByPlmn.getOrPut(parsed.operatorPlmn) { ArrayList(BATCH_SIZE) }
                list.add(parsed)
                if (list.size >= BATCH_SIZE) {
                    flushPlmn(parsed.operatorPlmn)
                    onProgress(totalLines, importedCount)
                }
            }

            if (!hasHeader) {
                processLine(firstLine)
            }

            while (true) {
                val line = reader.readLine() ?: break
                processLine(line)
            }

            for (plmn in batchesByPlmn.keys.toList()) {
                flushPlmn(plmn)
            }
            onProgress(totalLines, importedCount)
        }

        return BtsImportResult(
            totalLines = totalLines,
            importedCount = importedCount,
            withCoordsCount = withCoordsCount,
            skippedCount = skippedCount,
            detectedFormat = detectedFormat,
            operatorsFound = operatorsFound,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    private fun detectCharset(bytes: ByteArray, len: Int): Charset {
        if (len <= 0) return StandardCharsets.UTF_8
        // Check UTF-8 BOM
        if (len >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, len))
            StandardCharsets.UTF_8
        } catch (_: Exception) {
            CP1250
        }
    }

    private fun detectDelimiter(line: String): Char {
        val semiCount = line.count { it == ';' }
        val commaCount = line.count { it == ',' }
        val tabCount = line.count { it == '\t' }
        return when {
            semiCount >= commaCount && semiCount >= tabCount && semiCount > 0 -> ';'
            tabCount > commaCount && tabCount > 0 -> '\t'
            else -> ','
        }
    }

    private fun splitRow(line: String, delim: Char): List<String> {
        if (!line.contains('"')) return line.split(delim)
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delim && !inQuotes -> {
                    out.add(sb.toString())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString())
        return out
    }

    private fun looksLikeHeader(cols: List<String>): Boolean {
        val joined = cols.joinToString("|").lowercase()
        return joined.contains("cellid") || joined.contains("physcid") ||
            joined.contains("umisteni") || joined.contains("mcc") ||
            joined.contains("latitude") || joined.contains("eci")
    }

    private fun detectFormatFromHeader(header: Map<String, Int>, delim: Char): Format {
        if (header.containsKey("radio") && header.containsKey("mcc") && header.containsKey("net")) {
            return Format.OPENCELLID_CSV
        }
        if (delim == ';' && (header.containsKey("physcid") || header.containsKey("umisteni") || header.containsKey("gsmcid"))) {
            return Format.GSMWEB_SEMICOLON
        }
        return Format.GENERIC_CSV
    }

    private fun parseGsmwebRow(
        cols: List<String>,
        header: Map<String, Int>,
        hintedPlmn: String?
    ): BtsTowerEntity? {
        val ciIdx = header["cellid"] ?: 0
        val pciIdx = header["physcid"] ?: header["pci"] ?: 1
        val tacIdx = header["tac"] ?: header["lac"] ?: 2
        val bandIdx = header["band"] ?: 3
        val gsmIdx = header["gsmcid"] ?: 4
        val dateIdx = header["datum"] ?: header["date"] ?: 5
        val okrIdx = header["okr"] ?: header["okres"] ?: 6
        val locIdx = header["umisteni"] ?: header["location"] ?: header["popis"] ?: 7
        val latIdx = header["lat"] ?: header["latitude"] ?: header["gpsn"] ?: header["sirka"]
        val lonIdx = header["lon"] ?: header["lng"] ?: header["longitude"] ?: header["gpse"] ?: header["delka"]
        val opIdx = header["plmn"] ?: header["operator"] ?: header["mccmnc"]

        val cellId = cols.getOrNull(ciIdx)?.trim()?.toLongOrNull() ?: return null
        if (cellId <= 0L) return null

        val plmn = opIdx?.let { BtsGeoUtils.normalizeCzechOperatorPlmn(cols.getOrNull(it)) }
            ?: hintedPlmn
            ?: "23001"

        val pci = cols.getOrNull(pciIdx)?.trim()?.toIntOrNull() ?: -1
        val tac = cols.getOrNull(tacIdx)?.trim()?.toIntOrNull() ?: 0
        val band = cols.getOrNull(bandIdx)?.trim().orEmpty()
        val gsmCid = cols.getOrNull(gsmIdx)?.trim().orEmpty()
        val datum = cols.getOrNull(dateIdx)?.trim().orEmpty()
        val okr = cols.getOrNull(okrIdx)?.trim().orEmpty()
        val loc = cols.getOrNull(locIdx)?.trim().orEmpty()

        val lat = latIdx?.let { parseDoubleCoord(cols.getOrNull(it)) }
        val lon = lonIdx?.let { parseDoubleCoord(cols.getOrNull(it)) }
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)

        val enb = if (cellId > 256L) cellId ushr 8 else 0L
        val sector = (cellId and 0xFF).toInt()

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = cellId,
            enodebId = enb,
            sectorId = sector,
            pci = pci,
            tac = tac,
            band = band,
            gsmCid = gsmCid,
            updatedDate = datum,
            districtCode = okr,
            locationName = loc,
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "IMPORT" else ""
        )
    }

    private fun parseOpenCellIdRow(
        cols: List<String>,
        header: Map<String, Int>,
        hintedPlmn: String?
    ): BtsTowerEntity? {
        // OpenCelliD standard: radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal
        val mccIdx = header["mcc"] ?: 1
        val netIdx = header["net"] ?: header["mnc"] ?: 2
        val areaIdx = header["area"] ?: header["tac"] ?: header["lac"] ?: 3
        val cellIdx = header["cell"] ?: header["cellid"] ?: 4
        val unitIdx = header["unit"] ?: header["pci"] ?: 5
        val lonIdx = header["lon"] ?: header["longitude"] ?: 6
        val latIdx = header["lat"] ?: header["latitude"] ?: 7
        val radioIdx = header["radio"] ?: 0
        val updatedIdx = header["updated"] ?: 12

        val mcc = cols.getOrNull(mccIdx)?.trim() ?: return null
        if (mcc != "230") return null // Only Czech Republic operators

        val mnc = cols.getOrNull(netIdx)?.trim()?.padStart(2, '0') ?: return null
        val plmn = BtsGeoUtils.normalizeCzechOperatorPlmn("230$mnc") ?: hintedPlmn ?: return null
        val cellId = cols.getOrNull(cellIdx)?.trim()?.toLongOrNull() ?: return null
        if (cellId <= 0L) return null
        val tac = cols.getOrNull(areaIdx)?.trim()?.toIntOrNull() ?: 0
        val pci = cols.getOrNull(unitIdx)?.trim()?.toIntOrNull() ?: -1
        val lat = parseDoubleCoord(cols.getOrNull(latIdx))
        val lon = parseDoubleCoord(cols.getOrNull(lonIdx))
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)
        val radio = cols.getOrNull(radioIdx)?.trim().orEmpty()
        val updatedEpochSec = cols.getOrNull(updatedIdx)?.trim()?.toLongOrNull()
        val updatedStr = if (updatedEpochSec != null && updatedEpochSec > 1_000_000_000L) {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date(updatedEpochSec * 1000L))
        } else ""

        val enb = if (cellId > 256L) cellId ushr 8 else 0L
        val sector = (cellId and 0xFF).toInt()
        val tacPart = if (tac > 0) " • TAC $tac" else ""

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = cellId,
            enodebId = enb,
            sectorId = sector,
            pci = pci,
            tac = tac,
            band = radio.ifBlank { "LTE" },
            updatedDate = updatedStr,
            locationName = "OpenCelliD (${radio.ifBlank { "LTE" }}$tacPart)",
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "OPENCELLID" else ""
        )
    }

    private fun parseClfRow(cols: List<String>, hintedPlmn: String?): BtsTowerEntity? {
        // CLF v3.0 / v4.0: MCCMNC;CID;LAC;RNC;LAT;LON;POS_RAT;DESC;RFU
        if (cols.size < 6) return null
        val plmn = BtsGeoUtils.normalizeCzechOperatorPlmn(cols.getOrNull(0)) ?: hintedPlmn ?: return null
        val cidStr = cols.getOrNull(1)?.trim() ?: return null
        val cellId = parseHexOrDecLong(cidStr) ?: return null
        val tac = parseHexOrDecLong(cols.getOrNull(2)?.trim())?.toInt() ?: 0
        val lat = parseDoubleCoord(cols.getOrNull(4))
        val lon = parseDoubleCoord(cols.getOrNull(5))
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)
        val desc = cols.getOrNull(7)?.trim().orEmpty()

        val enb = if (cellId > 256L) cellId ushr 8 else 0L
        val sector = (cellId and 0xFF).toInt()

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = cellId,
            enodebId = enb,
            sectorId = sector,
            pci = -1,
            tac = tac,
            band = "LTE/CLF",
            locationName = desc,
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "IMPORT" else ""
        )
    }

    private fun parseGenericRow(
        cols: List<String>,
        header: Map<String, Int>,
        hintedPlmn: String?
    ): BtsTowerEntity? {
        val ciIdx = header["cellid"] ?: header["ci"] ?: header["eci"] ?: header["cell"] ?: return null
        val cellId = cols.getOrNull(ciIdx)?.trim()?.toLongOrNull() ?: return null
        if (cellId <= 0L) return null

        val opIdx = header["plmn"] ?: header["operator"] ?: header["mccmnc"] ?: header["net"]
        val plmn = opIdx?.let { BtsGeoUtils.normalizeCzechOperatorPlmn(cols.getOrNull(it)) }
            ?: hintedPlmn
            ?: "23001"

        val pciIdx = header["physcid"] ?: header["pci"]
        val tacIdx = header["tac"] ?: header["lac"] ?: header["area"]
        val bandIdx = header["band"] ?: header["freq"]
        val okrIdx = header["okr"] ?: header["district"]
        val locIdx = header["umisteni"] ?: header["location"] ?: header["address"] ?: header["desc"] ?: header["name"]
        val latIdx = header["lat"] ?: header["latitude"] ?: header["sirka"] ?: header["gpsn"]
        val lonIdx = header["lon"] ?: header["lng"] ?: header["longitude"] ?: header["delka"] ?: header["gpse"]

        val pci = pciIdx?.let { cols.getOrNull(it)?.trim()?.toIntOrNull() } ?: -1
        val tac = tacIdx?.let { cols.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val band = bandIdx?.let { cols.getOrNull(it)?.trim() }.orEmpty()
        val okr = okrIdx?.let { cols.getOrNull(it)?.trim() }.orEmpty()
        val loc = locIdx?.let { cols.getOrNull(it)?.trim() }.orEmpty()
        val lat = latIdx?.let { parseDoubleCoord(cols.getOrNull(it)) }
        val lon = lonIdx?.let { parseDoubleCoord(cols.getOrNull(it)) }
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)

        val enb = if (cellId > 256L) cellId ushr 8 else 0L
        val sector = (cellId and 0xFF).toInt()

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = cellId,
            enodebId = enb,
            sectorId = sector,
            pci = pci,
            tac = tac,
            band = band,
            districtCode = okr,
            locationName = loc,
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "IMPORT" else ""
        )
    }

    private fun parseDoubleCoord(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        return raw.trim().replace(',', '.').toDoubleOrNull()
    }

    private fun parseHexOrDecLong(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        return if (s.startsWith("0x", ignoreCase = true)) {
            s.substring(2).toLongOrNull(16)
        } else {
            s.toLongOrNull()
        }
    }
}
