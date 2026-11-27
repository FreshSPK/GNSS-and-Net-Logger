package com.example.bts

import com.example.data.db.BtsDao
import com.example.data.db.BtsTowerEntity
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.FilterInputStream
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
import java.util.zip.ZipInputStream

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
        NETMONSTER_NTM,
        CELLMAPPER_TOWER,
        CELLMAPPER_LOG,
        GENERIC_CSV
    }

    private class NonClosingInputStream(inner: InputStream) : FilterInputStream(inner) {
        override fun close() {
            // Keep underlying ZipInputStream open when advancing between ZIP entries
        }
    }

    /**
     * Streams arbitrarily large CSV / CLF / NTM / KML / GZ / ZIP files into Room with constant memory.
     * Auto-detects UTF-8 vs Windows-1250 (CP1250) and GSMweb / CLF / NetMonster NTM / CellMapper / OpenCelliD / KML formats.
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
        rawBuffered.mark(8)
        val b0 = rawBuffered.read()
        val b1 = rawBuffered.read()
        val b2 = rawBuffered.read()
        val b3 = rawBuffered.read()
        rawBuffered.reset()

        val lowerHint = fileNameHint?.lowercase(Locale.ROOT).orEmpty()
        val isZip = (b0 == 0x50 && b1 == 0x4B && b2 == 0x03 && b3 == 0x04) || lowerHint.endsWith(".zip")
        if (isZip) {
            return importZipStream(
                rawBuffered = rawBuffered,
                btsDao = btsDao,
                defaultOperatorPlmn = defaultOperatorPlmn,
                replaceOperator = replaceOperator,
                startMs = startMs,
                onProgress = onProgress
            )
        }

        val isGzip = (b0 == 0x1F && b1 == 0x8B) || lowerHint.endsWith(".gz")
        val buffered = if (isGzip) {
            BufferedInputStream(GZIPInputStream(rawBuffered, 64 * 1024), 64 * 1024)
        } else {
            rawBuffered
        }

        return importSingleUncompressedStream(
            buffered = buffered,
            btsDao = btsDao,
            defaultOperatorPlmn = defaultOperatorPlmn,
            fileNameHint = fileNameHint,
            isGzip = isGzip,
            replaceOperator = replaceOperator,
            clearedOperators = mutableSetOf(),
            startMs = startMs,
            onProgress = onProgress
        )
    }

    private suspend fun importZipStream(
        rawBuffered: InputStream,
        btsDao: BtsDao,
        defaultOperatorPlmn: String?,
        replaceOperator: Boolean,
        startMs: Long,
        onProgress: (processedLines: Int, importedCount: Int) -> Unit
    ): BtsImportResult {
        var totalLines = 0
        var imported = 0
        var withCoords = 0
        var skipped = 0
        val formats = linkedSetOf<String>()
        val operators = mutableSetOf<String>()
        val clearedOperators = mutableSetOf<String>()

        ZipInputStream(rawBuffered).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                val low = name.lowercase(Locale.ROOT)
                val isSupported = low.endsWith(".csv") || low.endsWith(".clf") ||
                    low.endsWith(".ntm") || low.endsWith(".txt") || low.endsWith(".kml")
                if (!isSupported && !low.contains(".")) continue

                val entryBuffered = BufferedInputStream(NonClosingInputStream(zis), 64 * 1024)
                val res = importSingleUncompressedStream(
                    buffered = entryBuffered,
                    btsDao = btsDao,
                    defaultOperatorPlmn = defaultOperatorPlmn,
                    fileNameHint = name,
                    isGzip = false,
                    replaceOperator = replaceOperator,
                    clearedOperators = clearedOperators,
                    startMs = System.currentTimeMillis()
                ) { proc, imp ->
                    onProgress(totalLines + proc, imported + imp)
                }
                totalLines += res.totalLines
                imported += res.importedCount
                withCoords += res.withCoordsCount
                skipped += res.skippedCount
                if (res.detectedFormat != "Empty") formats.add(res.detectedFormat)
                operators.addAll(res.operatorsFound)
                zis.closeEntry()
            }
        }

        val formatLabel = if (formats.isEmpty()) "Empty ZIP" else "ZIP (${formats.joinToString(" + ")})"
        return BtsImportResult(
            totalLines = totalLines,
            importedCount = imported,
            withCoordsCount = withCoords,
            skippedCount = skipped,
            detectedFormat = formatLabel,
            operatorsFound = operators,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    private suspend fun importSingleUncompressedStream(
        buffered: BufferedInputStream,
        btsDao: BtsDao,
        defaultOperatorPlmn: String?,
        fileNameHint: String?,
        isGzip: Boolean,
        replaceOperator: Boolean,
        clearedOperators: MutableSet<String>,
        startMs: Long,
        onProgress: (processedLines: Int, importedCount: Int) -> Unit
    ): BtsImportResult {
        buffered.mark(16 * 1024)
        val probeBytes = ByteArray(16 * 1024)
        val probeLen = buffered.read(probeBytes).coerceAtLeast(0)
        buffered.reset()

        val charset = detectCharset(probeBytes, probeLen)
        val probeHead = String(probeBytes, 0, probeLen.coerceAtMost(1024), charset).trimStart()
        val lowerHint = fileNameHint?.lowercase(Locale.ROOT).orEmpty()
        val hintedPlmn = defaultOperatorPlmn ?: BtsGeoUtils.inferOperatorFromFilename(fileNameHint)

        if (lowerHint.endsWith(".kml") || probeHead.startsWith("<kml", ignoreCase = true) ||
            (probeHead.startsWith("<?xml", ignoreCase = true) && probeHead.contains("<kml", ignoreCase = true))
        ) {
            return importKmlStream(
                buffered = buffered,
                charset = charset,
                btsDao = btsDao,
                hintedPlmn = hintedPlmn,
                replaceOperator = replaceOperator,
                clearedOperators = clearedOperators,
                startMs = startMs,
                onProgress = onProgress
            )
        }

        var totalLines = 0
        var importedCount = 0
        var withCoordsCount = 0
        var skippedCount = 0
        var detectedFormat = "GSMweb CSV"
        val operatorsFound = mutableSetOf<String>()

        BufferedReader(InputStreamReader(buffered, charset), 64 * 1024).use { reader ->
            var firstLine: String? = null
            while (true) {
                val raw = reader.readLine() ?: break
                val clean = raw.removePrefix("\uFEFF").trim()
                if (clean.isNotBlank() && !isCommentLine(clean) && clean.any { it != ';' && it != ',' }) {
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
                    col.trim().lowercase(Locale.ROOT)
                        .replace(" ", "")
                        .replace("_", "")
                        .replace("-", "") to idx
                }.toMap()
                format = detectFormatFromHeader(headerMap, firstDelim)
            } else {
                headerMap = emptyMap()
                format = detectHeaderlessFormat(firstCols, firstDelim, lowerHint)
            }

            detectedFormat = when (format) {
                Format.GSMWEB_SEMICOLON -> "GSMweb CSV (${charset.name()})"
                Format.OPENCELLID_CSV -> if (isGzip) "OpenCelliD GZ CSV (MCC 230)" else "OpenCelliD / MLS CSV (MCC 230)"
                Format.CLF -> "CLF v3/v4"
                Format.NETMONSTER_NTM -> "NetMonster NTM (${charset.name()})"
                Format.CELLMAPPER_TOWER -> "CellMapper Tower CSV (eNodeB)"
                Format.CELLMAPPER_LOG -> "CellMapper Signal Log"
                Format.GENERIC_CSV -> "CSV (${charset.name()})"
            }

            val batchesByPlmn = mutableMapOf<String, MutableList<BtsTowerEntity>>()
            val enodebAnchorsByPlmn = mutableMapOf<String, MutableList<BtsTowerEntity>>()

            suspend fun flushPlmn(plmn: String) {
                val list = batchesByPlmn[plmn]
                if (!list.isNullOrEmpty()) {
                    if (replaceOperator && clearedOperators.add(plmn)) {
                        btsDao.deleteByOperator(plmn)
                    }
                    btsDao.upsertPreservingCoords(plmn, list)
                    list.clear()
                }
                val anchors = enodebAnchorsByPlmn[plmn]
                if (!anchors.isNullOrEmpty()) {
                    if (replaceOperator && clearedOperators.add(plmn)) {
                        btsDao.deleteByOperator(plmn)
                    }
                    btsDao.applyEnodebTowersPreservingPriority(plmn, anchors)
                    anchors.clear()
                }
            }

            suspend fun processLine(rawLine: String) {
                val clean = rawLine.removePrefix("\uFEFF").trim()
                if (clean.isEmpty() || isCommentLine(clean) || clean.all { it == ';' || it == ',' || it == ' ' }) return
                totalLines++

                val delim = if (format == Format.OPENCELLID_CSV || format == Format.CELLMAPPER_LOG) ',' else firstDelim
                val cols = splitRow(clean, delim)

                if (format == Format.CELLMAPPER_TOWER) {
                    val hasCi = headerMap.containsKey("cellid") || headerMap.containsKey("ci") ||
                        headerMap.containsKey("eci") || headerMap.containsKey("cell")
                    val row = parseCellMapperTowerRow(cols, headerMap, hintedPlmn, hasExplicitCellId = hasCi)
                    if (row == null) {
                        skippedCount++
                        return
                    }
                    operatorsFound.add(row.operatorPlmn)
                    importedCount++
                    if (row.latitude != null && row.longitude != null) withCoordsCount++

                    val targetMap = if (hasCi && row.sectorId >= 0 && row.cellId != (row.enodebId shl 8)) {
                        batchesByPlmn
                    } else {
                        enodebAnchorsByPlmn
                    }
                    val list = targetMap.getOrPut(row.operatorPlmn) { ArrayList(BATCH_SIZE) }
                    list.add(row)
                    if (list.size >= BATCH_SIZE) {
                        flushPlmn(row.operatorPlmn)
                        onProgress(totalLines, importedCount)
                    }
                    return
                }

                val parsed = when (format) {
                    Format.GSMWEB_SEMICOLON -> parseGsmwebRow(cols, headerMap, hintedPlmn)
                    Format.OPENCELLID_CSV -> parseOpenCellIdRow(cols, headerMap, hintedPlmn)
                    Format.CLF -> parseClfRow(cols, hintedPlmn)
                    Format.NETMONSTER_NTM -> parseNetMonsterNtmRow(cols, headerMap, hintedPlmn)
                    Format.CELLMAPPER_LOG -> parseCellMapperLogRow(cols, hintedPlmn)
                    Format.GENERIC_CSV -> parseGenericRow(cols, headerMap, hintedPlmn)
                    Format.CELLMAPPER_TOWER -> null
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

            val allPlmns = (batchesByPlmn.keys + enodebAnchorsByPlmn.keys).distinct()
            for (plmn in allPlmns) {
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

    private suspend fun importKmlStream(
        buffered: BufferedInputStream,
        charset: Charset,
        btsDao: BtsDao,
        hintedPlmn: String?,
        replaceOperator: Boolean,
        clearedOperators: MutableSet<String>,
        startMs: Long,
        onProgress: (processedLines: Int, importedCount: Int) -> Unit
    ): BtsImportResult {
        var totalPlacemarks = 0
        var importedCount = 0
        var withCoordsCount = 0
        var skippedCount = 0
        val operatorsFound = mutableSetOf<String>()
        val batchesByPlmn = mutableMapOf<String, MutableList<BtsTowerEntity>>()
        val enodebAnchorsByPlmn = mutableMapOf<String, MutableList<BtsTowerEntity>>()

        val nameRegex = Regex("<name[^>]*>(.*?)</name>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val descRegex = Regex("<description[^>]*>(.*?)</description>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val coordsRegex = Regex("<coordinates[^>]*>(.*?)</coordinates>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val ciRegex = Regex("(?:cellid|cid|eci|ci)\\s*[:=]?\\s*(\\d{4,11})", RegexOption.IGNORE_CASE)
        val enbRegex = Regex("(?:enodeb|gnodeb|enb|gnb)\\s*[:=]?\\s*(\\d{3,8})", RegexOption.IGNORE_CASE)
        val tacRegex = Regex("(?:tac|lac)\\s*[:=]?\\s*(\\d{2,7})", RegexOption.IGNORE_CASE)
        val pciRegex = Regex("(?:pci|psc)\\s*[:=]?\\s*(\\d{1,4})", RegexOption.IGNORE_CASE)
        val plmnRegex = Regex("\\b(2300[123])\\b")

        suspend fun flushAll() {
            for ((plmn, list) in batchesByPlmn) {
                if (list.isNotEmpty()) {
                    if (replaceOperator && clearedOperators.add(plmn)) btsDao.deleteByOperator(plmn)
                    btsDao.upsertPreservingCoords(plmn, list)
                    list.clear()
                }
            }
            for ((plmn, list) in enodebAnchorsByPlmn) {
                if (list.isNotEmpty()) {
                    if (replaceOperator && clearedOperators.add(plmn)) btsDao.deleteByOperator(plmn)
                    btsDao.applyEnodebTowersPreservingPriority(plmn, list)
                    list.clear()
                }
            }
        }

        fun stripXml(raw: String): String = raw
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        BufferedReader(InputStreamReader(buffered, charset), 64 * 1024).use { reader ->
            val sb = StringBuilder()
            var insidePlacemark = false
            while (true) {
                val line = reader.readLine() ?: break
                if (!insidePlacemark) {
                    val startIdx = line.indexOf("<Placemark", ignoreCase = true)
                    if (startIdx >= 0) {
                        insidePlacemark = true
                        sb.setLength(0)
                        sb.append(line.substring(startIdx)).append('\n')
                    } else {
                        continue
                    }
                } else {
                    sb.append(line).append('\n')
                }

                if (insidePlacemark && sb.contains("</Placemark>", ignoreCase = true)) {
                    insidePlacemark = false
                    totalPlacemarks++
                    val block = sb.toString()
                    sb.setLength(0)

                    val name = nameRegex.find(block)?.groupValues?.get(1)?.let(::stripXml).orEmpty()
                    val desc = descRegex.find(block)?.groupValues?.get(1)?.let(::stripXml).orEmpty()
                    val coordStr = coordsRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                    val firstCoordToken = coordStr.split(Regex("\\s+")).firstOrNull { it.contains(',') }.orEmpty()
                    val xyz = firstCoordToken.split(',')
                    val lon = xyz.getOrNull(0)?.trim()?.toDoubleOrNull()
                    val lat = xyz.getOrNull(1)?.trim()?.toDoubleOrNull()

                    if (!BtsGeoUtils.isValidCzechCoord(lat, lon)) {
                        skippedCount++
                        continue
                    }

                    val combinedText = "$name $desc"
                    val plmn = plmnRegex.find(combinedText)?.groupValues?.get(1)
                        ?: BtsGeoUtils.normalizeCzechOperatorPlmn(combinedText)
                        ?: hintedPlmn
                        ?: "23001"

                    val ci = ciRegex.find(combinedText)?.groupValues?.get(1)?.toLongOrNull()
                    val enb = enbRegex.find(combinedText)?.groupValues?.get(1)?.toLongOrNull()
                    val tac = tacRegex.find(combinedText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val pci = pciRegex.find(combinedText)?.groupValues?.get(1)?.toIntOrNull() ?: -1

                    if (ci == null && enb == null) {
                        skippedCount++
                        continue
                    }

                    val resolvedEnb = enb ?: if (ci!! > 256L) ci ushr 8 else 0L
                    val resolvedCi = ci ?: (resolvedEnb shl 8)
                    val sector = if (ci != null) (ci and 0xFF).toInt() else 0

                    val entity = BtsTowerEntity(
                        operatorPlmn = plmn,
                        cellId = resolvedCi,
                        enodebId = resolvedEnb,
                        sectorId = sector,
                        pci = pci,
                        tac = tac,
                        locationName = name.ifBlank { desc },
                        latitude = lat,
                        longitude = lon,
                        coordSource = "KML"
                    )
                    operatorsFound.add(plmn)
                    importedCount++
                    withCoordsCount++

                    if (ci != null) {
                        batchesByPlmn.getOrPut(plmn) { ArrayList(BATCH_SIZE) }.add(entity)
                    } else {
                        enodebAnchorsByPlmn.getOrPut(plmn) { ArrayList(BATCH_SIZE) }.add(entity)
                    }

                    if (importedCount % BATCH_SIZE == 0) {
                        flushAll()
                        onProgress(totalPlacemarks, importedCount)
                    }
                }
            }
            flushAll()
            onProgress(totalPlacemarks, importedCount)
        }

        return BtsImportResult(
            totalLines = totalPlacemarks,
            importedCount = importedCount,
            withCoordsCount = withCoordsCount,
            skippedCount = skippedCount,
            detectedFormat = "KML Placemarks",
            operatorsFound = operatorsFound,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    private fun isCommentLine(line: String): Boolean {
        return line.startsWith("#") || line.startsWith("//")
    }

    private fun detectCharset(bytes: ByteArray, len: Int): Charset {
        if (len <= 0) return StandardCharsets.UTF_8
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
        val joined = cols.joinToString("|").lowercase(Locale.ROOT)
        return joined.contains("cellid") || joined.contains("physcid") ||
            joined.contains("umisteni") || joined.contains("mcc") ||
            joined.contains("latitude") || joined.contains("eci") ||
            joined.contains("enodeb") || joined.contains("enb") ||
            joined.contains("gnb") || (joined.contains("lat") && joined.contains("lon"))
    }

    private fun detectFormatFromHeader(header: Map<String, Int>, delim: Char): Format {
        if (header.containsKey("radio") && header.containsKey("mcc") && header.containsKey("net")) {
            return Format.OPENCELLID_CSV
        }
        if ((header.containsKey("tech") || header.containsKey("rat") || header.containsKey("nettype")) &&
            header.containsKey("mcc") && header.containsKey("mnc") &&
            (header.containsKey("cid") || header.containsKey("cellid"))
        ) {
            return Format.NETMONSTER_NTM
        }
        val hasLatLon = (header.containsKey("lat") || header.containsKey("latitude")) &&
            (header.containsKey("lon") || header.containsKey("lng") || header.containsKey("longitude"))
        val hasEnb = header.containsKey("enb") || header.containsKey("enodeb") ||
            header.containsKey("enodebid") || header.containsKey("gnb") ||
            header.containsKey("gnodeb") || header.containsKey("node")
        val hasExplicitCi = header.containsKey("cellid") || header.containsKey("ci") ||
            header.containsKey("eci") || header.containsKey("cell")
        if (hasLatLon && hasEnb && !hasExplicitCi) {
            return Format.CELLMAPPER_TOWER
        }
        if (delim == ';' && (header.containsKey("physcid") || header.containsKey("umisteni") || header.containsKey("gsmcid"))) {
            return Format.GSMWEB_SEMICOLON
        }
        return Format.GENERIC_CSV
    }

    private fun detectHeaderlessFormat(firstCols: List<String>, firstDelim: Char, lowerHint: String): Format {
        val col0 = firstCols.getOrNull(0)?.trim().orEmpty()
        val col0Up = col0.uppercase(Locale.ROOT)
        val col1 = firstCols.getOrNull(1)?.trim().orEmpty()

        // 1. Headerless CellMapper Android App CSV log: lat,lon,alt,mcc,mnc,lac,cid,...
        if (firstDelim == ',' && firstCols.size >= 8 && firstCols.getOrNull(3)?.trim() == "230") {
            val maybeLat = parseDoubleCoord(col0)
            val maybeLon = parseDoubleCoord(col1)
            if (BtsGeoUtils.isValidCzechCoord(maybeLat, maybeLon)) {
                return Format.CELLMAPPER_LOG
            }
        }

        // 2. CLF v3/v4: MCCMNC;CID;LAC;RNC;LAT;LON;...
        if (firstCols.size >= 6 && col0.length == 5 && col0.startsWith("230")) {
            return Format.CLF
        }

        // 3. Standard OpenCelliD CSV (headerless): radio,mcc,net,area,cell,unit,lon,lat,range,samples,...
        // Note: OpenCelliD also starts with "LTE,230,1,...", so check comma delimiter, 11+ cols, or (lon@6, lat@7).
        if (firstDelim == ',' && firstCols.size >= 8) {
            val col6 = parseDoubleCoord(firstCols.getOrNull(6))
            val col7 = parseDoubleCoord(firstCols.getOrNull(7))
            val isOpenCellLonLatOrder = BtsGeoUtils.isValidCzechCoord(col7, col6)
            val looksOpenCellHint = lowerHint.contains("opencell") || Regex("""(^|[/\\])230\.csv""").containsMatchIn(lowerHint)
            if (looksOpenCellHint || isOpenCellLonLatOrder || firstCols.size >= 11 ||
                (col1.toIntOrNull() != null && col1 != "230")
            ) {
                return Format.OPENCELLID_CSV
            }
        }

        // 4. NetMonster .ntm / nm2300x.csv: 4G;230;01;CID;TAC;[eNB;]PCI;Lat;Lon;Name
        if (firstCols.size >= 8 &&
            col0Up in setOf("2G", "3G", "4G", "5G", "GSM", "UMTS", "WCDMA", "LTE", "NR", "FDD", "TDD") &&
            col1 == "230"
        ) {
            return Format.NETMONSTER_NTM
        }

        // Fallback OpenCelliD check
        if (firstDelim == ',' && firstCols.size >= 8 &&
            (col1.toIntOrNull() != null || col0Up in setOf("LTE", "NR", "UMTS", "GSM", "CDMA"))
        ) {
            return Format.OPENCELLID_CSV
        }

        if (lowerHint.endsWith(".ntm") && firstCols.size >= 7) {
            return Format.NETMONSTER_NTM
        }
        if (lowerHint.endsWith(".clf") && firstCols.size >= 6) {
            return Format.CLF
        }

        return Format.GSMWEB_SEMICOLON
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
        if (mcc != "230") return null

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
        if (cellId <= 0L) return null
        val tac = parseHexOrDecLong(cols.getOrNull(2)?.trim())?.toInt() ?: 0
        val lat = parseDoubleCoord(cols.getOrNull(4))
        val lon = parseDoubleCoord(cols.getOrNull(5))
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)
        val desc = cols.getOrNull(7)?.trim().orEmpty().ifBlank { cols.getOrNull(6)?.trim().orEmpty() }

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
            coordSource = if (validCoords) "CLF" else ""
        )
    }

    private fun parseNetMonsterNtmRow(
        cols: List<String>,
        header: Map<String, Int>,
        hintedPlmn: String?
    ): BtsTowerEntity? {
        if (cols.size < 7) return null

        val mccIdx = header["mcc"] ?: 1
        val mncIdx = header["mnc"] ?: header["net"] ?: 2
        val mcc = cols.getOrNull(mccIdx)?.trim()
        if (mcc != null && mcc != "230") return null

        val mncRaw = cols.getOrNull(mncIdx)?.trim()?.padStart(2, '0')
        val plmn = (if (mncRaw != null) BtsGeoUtils.normalizeCzechOperatorPlmn("230$mncRaw") else null)
            ?: hintedPlmn
            ?: return null

        val tech = cols.getOrNull(header["tech"] ?: header["rat"] ?: header["nettype"] ?: 0)?.trim().orEmpty()
        val cidIdx = header["cid"] ?: header["cellid"] ?: header["eci"] ?: 3
        val cellId = parseHexOrDecLong(cols.getOrNull(cidIdx)) ?: return null
        if (cellId <= 0L) return null

        val tacIdx = header["tac"] ?: header["lac"] ?: header["area"] ?: 4
        val tac = cols.getOrNull(tacIdx)?.trim()?.toIntOrNull() ?: 0

        // Support both 9-column NTM (Tech;MCC;MNC;CID;TAC;PCI;Lat;Lon;Name) and
        // 10-column NTM (Tech;MCC;MNC;CID;TAC;eNB;PCI;Lat;Lon;Name)
        var latIdx = header["lat"] ?: header["latitude"]
        var lonIdx = header["lon"] ?: header["lng"] ?: header["longitude"]
        var pciIdx = header["pci"] ?: header["psc"]
        var nameIdx = header["name"] ?: header["desc"] ?: header["location"] ?: header["umisteni"]

        if (latIdx == null || lonIdx == null) {
            val latAt6 = parseDoubleCoord(cols.getOrNull(6))
            val lonAt7 = parseDoubleCoord(cols.getOrNull(7))
            val latAt7 = parseDoubleCoord(cols.getOrNull(7))
            val lonAt8 = parseDoubleCoord(cols.getOrNull(8))
            if (BtsGeoUtils.isValidCzechCoord(latAt6, lonAt7)) {
                pciIdx = pciIdx ?: 5
                latIdx = 6
                lonIdx = 7
                nameIdx = nameIdx ?: 8
            } else if (BtsGeoUtils.isValidCzechCoord(latAt7, lonAt8)) {
                pciIdx = pciIdx ?: 6
                latIdx = 7
                lonIdx = 8
                nameIdx = nameIdx ?: 9
            } else {
                pciIdx = pciIdx ?: 5
                latIdx = 6
                lonIdx = 7
                nameIdx = nameIdx ?: 8
            }
        }

        val pci = pciIdx?.let { cols.getOrNull(it)?.trim()?.toIntOrNull() } ?: -1
        val lat = parseDoubleCoord(cols.getOrNull(latIdx))
        val lon = parseDoubleCoord(cols.getOrNull(lonIdx))
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)
        val name = nameIdx?.let { cols.getOrNull(it)?.trim() }.orEmpty()
        val band = cols.getOrNull(header["band"] ?: -1)?.trim().orEmpty().ifBlank { tech.ifBlank { "LTE" } }

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
            locationName = name,
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "NTM" else ""
        )
    }

    private fun parseCellMapperTowerRow(
        cols: List<String>,
        header: Map<String, Int>,
        hintedPlmn: String?,
        hasExplicitCellId: Boolean
    ): BtsTowerEntity? {
        val latIdx = header["lat"] ?: header["latitude"] ?: return null
        val lonIdx = header["lon"] ?: header["lng"] ?: header["longitude"] ?: return null
        val lat = parseDoubleCoord(cols.getOrNull(latIdx))
        val lon = parseDoubleCoord(cols.getOrNull(lonIdx))
        if (!BtsGeoUtils.isValidCzechCoord(lat, lon)) return null

        val mccIdx = header["mcc"]
        val mncIdx = header["mnc"] ?: header["net"]
        val mcc = mccIdx?.let { cols.getOrNull(it)?.trim() }
        if (mcc != null && mcc != "230") return null

        val opIdx = header["plmn"] ?: header["operator"] ?: header["mccmnc"]
        val plmn = when {
            mncIdx != null -> {
                val mnc = cols.getOrNull(mncIdx)?.trim()?.padStart(2, '0')
                mnc?.let { BtsGeoUtils.normalizeCzechOperatorPlmn("230$it") }
            }
            opIdx != null -> BtsGeoUtils.normalizeCzechOperatorPlmn(cols.getOrNull(opIdx))
            else -> null
        } ?: hintedPlmn ?: return null

        val enbIdx = header["enb"] ?: header["enodeb"] ?: header["enodebid"] ?:
            header["gnb"] ?: header["gnodeb"] ?: header["node"]
        val ciIdx = header["cellid"] ?: header["ci"] ?: header["eci"] ?: header["cell"]

        val enb = enbIdx?.let { cols.getOrNull(it)?.trim()?.toLongOrNull() }
        val ci = ciIdx?.let { cols.getOrNull(it)?.trim()?.toLongOrNull() }
        if ((enb == null || enb <= 0L) && (ci == null || ci <= 0L)) return null

        val resolvedEnb = enb ?: (ci!! ushr 8)
        val resolvedCi = if (hasExplicitCellId && ci != null && ci > 0L) ci else (resolvedEnb shl 8)
        val sector = if (hasExplicitCellId && ci != null) (ci and 0xFF).toInt() else 0

        val tac = (header["tac"] ?: header["lac"] ?: header["area"])?.let { cols.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0
        val band = (header["band"] ?: header["system"] ?: header["type"])?.let { cols.getOrNull(it)?.trim() }.orEmpty()
        val name = (header["name"] ?: header["location"] ?: header["desc"] ?: header["notes"] ?: header["umisteni"])
            ?.let { cols.getOrNull(it)?.trim() }
            .orEmpty()
            .ifBlank { "CellMapper eNB $resolvedEnb" }

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = resolvedCi,
            enodebId = resolvedEnb,
            sectorId = sector,
            pci = -1,
            tac = tac,
            band = band,
            locationName = name,
            latitude = lat,
            longitude = lon,
            coordSource = "CELLMAPPER"
        )
    }

    private fun parseCellMapperLogRow(cols: List<String>, hintedPlmn: String?): BtsTowerEntity? {
        // CellMapper App CSV log: lat,lon,alt,mcc,mnc,lac,cid,signal,type,subtype,arfcn,pci
        if (cols.size < 8) return null
        val mcc = cols.getOrNull(3)?.trim() ?: return null
        if (mcc != "230") return null
        val mnc = cols.getOrNull(4)?.trim()?.padStart(2, '0') ?: return null
        val plmn = BtsGeoUtils.normalizeCzechOperatorPlmn("230$mnc") ?: hintedPlmn ?: return null
        val cellId = cols.getOrNull(6)?.trim()?.toLongOrNull() ?: return null
        if (cellId <= 0L) return null

        val lat = parseDoubleCoord(cols.getOrNull(0))
        val lon = parseDoubleCoord(cols.getOrNull(1))
        val validCoords = BtsGeoUtils.isValidCzechCoord(lat, lon)
        val tac = cols.getOrNull(5)?.trim()?.toIntOrNull() ?: 0
        val type = cols.getOrNull(8)?.trim().orEmpty()
        val pci = cols.getOrNull(11)?.trim()?.toIntOrNull() ?: -1

        val enb = if (cellId > 256L) cellId ushr 8 else 0L
        val sector = (cellId and 0xFF).toInt()

        return BtsTowerEntity(
            operatorPlmn = plmn,
            cellId = cellId,
            enodebId = enb,
            sectorId = sector,
            pci = pci,
            tac = tac,
            band = type.ifBlank { "LTE" },
            locationName = "CellMapper (${type.ifBlank { "LTE" }})",
            latitude = if (validCoords) lat else null,
            longitude = if (validCoords) lon else null,
            coordSource = if (validCoords) "CELLMAPPER_LOG" else ""
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
        val mccIdx = header["mcc"]
        val mncIdx = header["mnc"]
        val plmn = when {
            mccIdx != null && mncIdx != null -> {
                val mcc = cols.getOrNull(mccIdx)?.trim()
                val mnc = cols.getOrNull(mncIdx)?.trim()?.padStart(2, '0')
                if (mcc == "230" && mnc != null) BtsGeoUtils.normalizeCzechOperatorPlmn("230$mnc") else null
            }
            opIdx != null -> BtsGeoUtils.normalizeCzechOperatorPlmn(cols.getOrNull(opIdx))
            else -> null
        } ?: hintedPlmn ?: "23001"

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
