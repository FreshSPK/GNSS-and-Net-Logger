package com.example.export

import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object Exporter {
    private fun createIsoFormat(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Streaming CSV writer (prevents OOM on 50k+ point sessions).
     */
    fun writeCsvToStream(session: LogSession, records: List<TelemetryRecord>, outputStream: OutputStream) {
        val isoFormat = createIsoFormat()
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 32 * 1024)
        writer.apply {
            append("# Signal & GNSS Field Logger - Session Export (v1.3)\n")
            append("# Author: Ing. Jan Kufa, Ph.D. (VUT FEKT UREL)\n")
            append("# Session: ${session.title} (ID: ${session.id})\n")
            append("# Start: ${isoFormat.format(Date(session.startTimeMs))}\n")
            append(
                "TimestampIso,TimestampMs,WaypointNote,Latitude,Longitude,AltMsl_m,AltWgs84_m,Speed_kmh,Speed_ms,Bearing_deg,HAcc_m,VAcc_m," +
                    "SatsInView,SatsUsed,PDOP,HDOP,VDOP,DualFreqPct,AgcScore,Constellations,RawDoppler_Hz," +
                    "Sim1Operator,Sim1PLMN,Sim1Tech,Sim1TAC,Sim1CellId,Sim1eNodeB_gNodeB,Sim1Sector,Sim1PCI,Sim1ARFCN,Sim1Band," +
                    "Sim1RSRP_dBm,Sim1RSRQ_dB,Sim1RSSI_dBm,Sim1SINR_dB,Sim1SS_RSRP,Sim1SS_SINR,Sim1TA,Sim1EstDist_m," +
                    "Sim2Operator,Sim2PLMN,Sim2Tech,Sim2TAC,Sim2CellId,Sim2eNodeB_gNodeB,Sim2Sector,Sim2PCI,Sim2ARFCN,Sim2Band," +
                    "Sim2RSRP_dBm,Sim2RSRQ_dB,Sim2RSSI_dBm,Sim2SINR_dB,Sim2TA,Sim2EstDist_m," +
                    "BestNeighborRSRP,NeighborDelta_dB,Pressure_hPa,SeaLevelPressure_hPa,BaroAlt_m,BatteryTemp_C,BatteryLevel_pct,Ping_ms,NmeaSentence\n"
            )

            for (r in records) {
                val hasDop = r.satellitesUsedInFix >= 4 && r.pdop > 0f && r.pdop < 9.0f
                append("${isoFormat.format(Date(r.timestampMs))},")
                append("${r.timestampMs},")
                append("\"${escapeCsv(r.waypointNote ?: "")}\",")
                append("${r.latitude},")
                append("${r.longitude},")
                append("${r.altitudeMsl},")
                append("${r.altitudeWgs84},")
                append("${r.speedKmh},")
                append("${r.speedMs},")
                append("${r.bearingDeg},")
                append("${r.horizontalAccuracyM},")
                append("${r.verticalAccuracyM},")
                append("${r.satellitesInView},")
                append("${r.satellitesUsedInFix},")
                append("${if (hasDop) r.pdop else "-"},")
                append("${if (hasDop && r.hdop > 0f) r.hdop else "-"},")
                append("${if (hasDop && r.vdop > 0f) r.vdop else "-"},")
                append("${r.dualFrequencyRatioPct},")
                append("${r.agcInterferenceScore},")
                append("\"${escapeCsv(r.constellationBreakdown)}\",")
                append("${r.rawDopplerHz ?: ""},")
                append("\"${escapeCsv(r.sim1Operator)}\",")
                append("\"${escapeCsv(r.sim1Plmn)}\",")
                append("\"${escapeCsv(r.sim1NetworkGen)}\",")
                append("\"${escapeCsv(r.sim1TacLac)}\",")
                append("\"${escapeCsv(r.sim1CellId)}\",")
                append("\"${escapeCsv(r.sim1EnbOrGnb)}\",")
                append("\"${escapeCsv(r.sim1SectorId)}\",")
                append("${r.sim1Pci},")
                append("${r.sim1Arfcn},")
                append("\"${escapeCsv(r.sim1Band)}\",")
                append("${r.sim1Rsrp ?: ""},")
                append("${r.sim1Rsrq ?: ""},")
                append("${r.sim1Rssi ?: ""},")
                append("${r.sim1Sinr ?: ""},")
                append("${r.sim1SsRsrp ?: ""},")
                append("${r.sim1SsSinr ?: ""},")
                append("${r.sim1TimingAdvance ?: ""},")
                append("${r.sim1EstimatedDistanceM ?: ""},")
                append("\"${escapeCsv(r.sim2Operator)}\",")
                append("\"${escapeCsv(r.sim2Plmn)}\",")
                append("\"${escapeCsv(r.sim2NetworkGen)}\",")
                append("\"${escapeCsv(r.sim2TacLac)}\",")
                append("\"${escapeCsv(r.sim2CellId)}\",")
                append("\"${escapeCsv(r.sim2EnbOrGnb)}\",")
                append("\"${escapeCsv(r.sim2SectorId)}\",")
                append("${r.sim2Pci},")
                append("${r.sim2Arfcn},")
                append("\"${escapeCsv(r.sim2Band)}\",")
                append("${r.sim2Rsrp ?: ""},")
                append("${r.sim2Rsrq ?: ""},")
                append("${r.sim2Rssi ?: ""},")
                append("${r.sim2Sinr ?: ""},")
                append("${r.sim2TimingAdvance ?: ""},")
                append("${r.sim2EstimatedDistanceM ?: ""},")
                append("${r.bestNeighborRsrp ?: ""},")
                append("${r.neighborServingDeltaDb ?: ""},")
                append("${r.pressureHpa ?: ""},")
                append("${r.seaLevelPressureHpa ?: ""},")
                append("${r.baroRelativeAltM ?: ""},")
                append("${r.batteryTempC ?: ""},")
                append("${r.batteryLevelPct ?: ""},")
                append("${r.pingRttMs ?: ""},")
                append("\"${escapeCsv(r.nmeaSentence ?: "")}\"\n")
            }
            flush()
        }
    }

    /**
     * Streaming Multi-Color RSRP KML writer + Waypoint pins for Google Earth.
     */
    fun writeKmlToStream(session: LogSession, records: List<TelemetryRecord>, outputStream: OutputStream) {
        val isoFormat = createIsoFormat()
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 32 * 1024)
        writer.apply {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>${escapeXml(session.title)}</name>
    <description>Recorded by GNSS &amp; Net Logger v1.3 (Ing. Jan Kufa, Ph.D. - VUT Brno FEKT UREL)</description>
    <Style id="rsrpStrong"><LineStyle><color>ff81b910</color><width>5</width></LineStyle></Style>
    <Style id="rsrpGood"><LineStyle><color>ff16cc84</color><width>5</width></LineStyle></Style>
    <Style id="rsrpFair"><LineStyle><color>ff0b9ef5</color><width>5</width></LineStyle></Style>
    <Style id="rsrpWeak"><LineStyle><color>ff5e3ff4</color><width>5</width></LineStyle></Style>
    <Style id="rsrpUnknown"><LineStyle><color>ffffe506</color><width>4</width></LineStyle></Style>
"""
            )
            val validRecords = records.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            if (validRecords.size >= 2) {
                var currentStyle = rsrpStyleId(validRecords[0].sim1Rsrp)
                var segPoints = mutableListOf(validRecords[0])
                var segIdx = 1

                for (i in 1 until validRecords.size) {
                    val rec = validRecords[i]
                    val nextStyle = rsrpStyleId(rec.sim1Rsrp)
                    segPoints.add(rec)
                    if (nextStyle != currentStyle || i == validRecords.lastIndex) {
                        append("    <Placemark>\n")
                        append("      <name>RF Segment #$segIdx</name>\n")
                        append("      <styleUrl>#$currentStyle</styleUrl>\n")
                        append("      <LineString><tessellate>1</tessellate><altitudeMode>clampToGround</altitudeMode><coordinates>\n")
                        for (pt in segPoints) {
                            append("        ${pt.longitude},${pt.latitude},${pt.altitudeMsl}\n")
                        }
                        append("      </coordinates></LineString>\n")
                        append("    </Placemark>\n")
                        segIdx++
                        currentStyle = nextStyle
                        segPoints = mutableListOf(rec)
                    }
                }
            } else if (validRecords.size == 1) {
                val r = validRecords[0]
                append("    <Placemark><name>Start</name><Point><coordinates>${r.longitude},${r.latitude},${r.altitudeMsl}</coordinates></Point></Placemark>\n")
            }

            // Waypoint placemarks
            for (r in validRecords) {
                if (!r.waypointNote.isNullOrBlank()) {
                    append("    <Placemark>\n")
                    append("      <name>${escapeXml(r.waypointNote)}</name>\n")
                    append(
                        "      <description>${escapeXml("Time: ${isoFormat.format(Date(r.timestampMs))} | SIM1 RSRP: ${r.sim1Rsrp ?: "N/A"} dBm | SIM2 RSRP: ${r.sim2Rsrp ?: "N/A"} dBm")}</description>\n"
                    )
                    append("      <Point><coordinates>${r.longitude},${r.latitude},${r.altitudeMsl}</coordinates></Point>\n")
                    append("    </Placemark>\n")
                }
            }

            append("  </Document>\n</kml>\n")
            flush()
        }
    }

    /**
     * Streaming GPX 1.1 writer with RF telemetry extensions & Waypoints.
     */
    fun writeGpxToStream(session: LogSession, records: List<TelemetryRecord>, outputStream: OutputStream) {
        val isoFormat = createIsoFormat()
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 32 * 1024)
        writer.apply {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GNSS &amp; Net Logger v1.3 - VUT FEKT UREL" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>${escapeXml(session.title)}</name>
    <time>${isoFormat.format(Date(session.startTimeMs))}</time>
  </metadata>
"""
            )
            val validRecords = records.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            for (r in validRecords) {
                if (!r.waypointNote.isNullOrBlank()) {
                    append("  <wpt lat=\"${r.latitude}\" lon=\"${r.longitude}\">\n")
                    append("    <ele>${r.altitudeMsl}</ele>\n")
                    append("    <time>${isoFormat.format(Date(r.timestampMs))}</time>\n")
                    append("    <name>${escapeXml(r.waypointNote)}</name>\n")
                    append("    <desc>${escapeXml("SIM1: ${r.sim1Rsrp ?: "N/A"} dBm (${r.sim1Band}) | SIM2: ${r.sim2Rsrp ?: "N/A"} dBm")}</desc>\n")
                    append("  </wpt>\n")
                }
            }

            append("  <trk>\n    <name>${escapeXml(session.title)}</name>\n    <trkseg>\n")
            for (r in validRecords) {
                append("      <trkpt lat=\"${r.latitude}\" lon=\"${r.longitude}\">\n")
                append("        <ele>${r.altitudeMsl}</ele>\n")
                append("        <time>${isoFormat.format(Date(r.timestampMs))}</time>\n")
                append("        <sat>${r.satellitesUsedInFix}</sat>\n")
                if (r.satellitesUsedInFix >= 4 && r.hdop > 0f && r.hdop < 9f) append("        <hdop>${r.hdop}</hdop>\n")
                if (r.satellitesUsedInFix >= 4 && r.vdop > 0f && r.vdop < 9f) append("        <vdop>${r.vdop}</vdop>\n")
                if (r.satellitesUsedInFix >= 4 && r.pdop > 0f && r.pdop < 9f) append("        <pdop>${r.pdop}</pdop>\n")
                append("        <extensions>\n")
                append("          <speed_kmh>${r.speedKmh}</speed_kmh>\n")
                r.sim1Rsrp?.let { append("          <sim1_rsrp>$it</sim1_rsrp>\n") }
                if (r.sim1Band.isNotBlank()) append("          <sim1_band>${escapeXml(r.sim1Band)}</sim1_band>\n")
                r.sim2Rsrp?.let { append("          <sim2_rsrp>$it</sim2_rsrp>\n") }
                append("        </extensions>\n")
                append("      </trkpt>\n")
            }
            append("    </trkseg>\n  </trk>\n</gpx>\n")
            flush()
        }
    }

    /**
     * Streaming GeoJSON FeatureCollection writer for QGIS / ArcGIS.
     */
    fun writeGeoJsonToStream(session: LogSession, records: List<TelemetryRecord>, outputStream: OutputStream) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 32 * 1024)
        writer.apply {
            append("{\n")
            append("  \"type\": \"FeatureCollection\",\n")
            append("  \"properties\": {\n")
            append("    \"sessionId\": ${session.id},\n")
            append("    \"title\": \"${escapeJson(session.title)}\",\n")
            append("    \"author\": \"Ing. Jan Kufa, Ph.D. (VUT FEKT UREL)\"\n")
            append("  },\n")
            append("  \"features\": [\n")

            records.forEachIndexed { index, r ->
                append("    {\n")
                append("      \"type\": \"Feature\",\n")
                append("      \"geometry\": {\n")
                append("        \"type\": \"Point\",\n")
                append("        \"coordinates\": [${r.longitude}, ${r.latitude}, ${r.altitudeMsl}]\n")
                append("      },\n")
                append("      \"properties\": {\n")
                append("        \"timestampMs\": ${r.timestampMs},\n")
                append("        \"waypointNote\": ${if (r.waypointNote != null) "\"${escapeJson(r.waypointNote)}\"" else "null"},\n")
                append("        \"speedKmh\": ${r.speedKmh},\n")
                append("        \"bearingDeg\": ${r.bearingDeg},\n")
                append("        \"satellitesUsed\": ${r.satellitesUsedInFix},\n")
                append("        \"pdop\": ${if (r.satellitesUsedInFix >= 4 && r.pdop > 0f && r.pdop < 9f) r.pdop else "null"},\n")
                append("        \"dualFrequencyRatioPct\": ${r.dualFrequencyRatioPct},\n")
                append("        \"agcScore\": ${r.agcInterferenceScore},\n")
                append("        \"sim1Operator\": \"${escapeJson(r.sim1Operator)}\",\n")
                append("        \"sim1NetworkGen\": \"${escapeJson(r.sim1NetworkGen)}\",\n")
                append("        \"sim1Band\": \"${escapeJson(r.sim1Band)}\",\n")
                append("        \"sim1Pci\": ${r.sim1Pci},\n")
                append("        \"sim1Rsrp\": ${r.sim1Rsrp ?: "null"},\n")
                append("        \"sim1Sinr\": ${r.sim1Sinr ?: "null"},\n")
                append("        \"sim2Operator\": \"${escapeJson(r.sim2Operator)}\",\n")
                append("        \"sim2NetworkGen\": \"${escapeJson(r.sim2NetworkGen)}\",\n")
                append("        \"sim2Band\": \"${escapeJson(r.sim2Band)}\",\n")
                append("        \"sim2Pci\": ${r.sim2Pci},\n")
                append("        \"sim2Rsrp\": ${r.sim2Rsrp ?: "null"},\n")
                append("        \"sim2Sinr\": ${r.sim2Sinr ?: "null"},\n")
                append("        \"pingRttMs\": ${r.pingRttMs ?: "null"}\n")
                append("      }\n")
                append("    }${if (index < records.size - 1) "," else ""}\n")
            }

            append("  ]\n")
            append("}\n")
            flush()
        }
    }

    private fun rsrpStyleId(rsrp: Int?): String {
        return when {
            rsrp == null -> "rsrpUnknown"
            rsrp >= -85 -> "rsrpStrong"
            rsrp >= -100 -> "rsrpGood"
            rsrp >= -110 -> "rsrpFair"
            else -> "rsrpWeak"
        }
    }

    // Convenience wrappers (for small exports / unit tests)
    fun exportToCsv(session: LogSession, records: List<TelemetryRecord>): String =
        ByteArrayOutputStream().also { writeCsvToStream(session, records, it) }.toString("UTF-8")

    fun exportToKml(session: LogSession, records: List<TelemetryRecord>): String =
        ByteArrayOutputStream().also { writeKmlToStream(session, records, it) }.toString("UTF-8")

    fun exportToGpx(session: LogSession, records: List<TelemetryRecord>): String =
        ByteArrayOutputStream().also { writeGpxToStream(session, records, it) }.toString("UTF-8")

    fun exportToGeoJson(session: LogSession, records: List<TelemetryRecord>): String =
        ByteArrayOutputStream().also { writeGeoJsonToStream(session, records, it) }.toString("UTF-8")

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }

    private fun escapeCsv(input: String): String {
        return input.replace("\"", "\"\"").replace("\n", " ").replace("\r", "")
    }
}
