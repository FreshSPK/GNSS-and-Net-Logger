package com.example.export

import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import java.text.SimpleDateFormat
import java.util.*

object Exporter {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Generate full raw matrix CSV export.
     */
    fun exportToCsv(session: LogSession, records: List<TelemetryRecord>): String {
        val sb = StringBuilder()
        sb.append("# Signal & GNSS Field Logger - Session Export\n")
        sb.append("# Author: Ing. Jan Kufa, Ph.D. (VUT FEKT UREL)\n")
        sb.append("# Session: ${session.title} (ID: ${session.id})\n")
        sb.append("# Start: ${isoFormat.format(Date(session.startTimeMs))}\n")
        sb.append("TimestampIso,TimestampMs,Latitude,Longitude,AltMsl_m,AltWgs84_m,Speed_kmh,Speed_ms,Bearing_deg,HAcc_m,VAcc_m,SatsInView,SatsUsed,PDOP,HDOP,VDOP,DualFreqPct,AgcScore,Constellations,Sim1Operator,Sim1Tech,Sim1TAC,Sim1CellId,Sim1eNodeB_gNodeB,Sim1Sector,Sim1PCI,Sim1ARFCN,Sim1Band,Sim1RSRP_dBm,Sim1RSRQ_dB,Sim1RSSI_dBm,Sim1SINR_dB,Sim1SS_RSRP,Sim1SS_SINR,Sim1TA,Sim1EstDist_m,Sim2Operator,Sim2Tech,Sim2Band,Sim2RSRP_dBm,Sim2SINR_dB,BestNeighborRSRP,NeighborDelta_dB,Pressure_hPa,BaroAlt_m,Ping_ms\n")

        for (r in records) {
            sb.append("${isoFormat.format(Date(r.timestampMs))},")
            sb.append("${r.timestampMs},")
            sb.append("${r.latitude},")
            sb.append("${r.longitude},")
            sb.append("${r.altitudeMsl},")
            sb.append("${r.altitudeWgs84},")
            sb.append("${r.speedKmh},")
            sb.append("${r.speedMs},")
            sb.append("${r.bearingDeg},")
            sb.append("${r.horizontalAccuracyM},")
            sb.append("${r.verticalAccuracyM},")
            sb.append("${r.satellitesInView},")
            sb.append("${r.satellitesUsedInFix},")
            sb.append("${r.pdop},")
            sb.append("${r.hdop},")
            sb.append("${r.vdop},")
            sb.append("${r.dualFrequencyRatioPct},")
            sb.append("${r.agcInterferenceScore},")
            sb.append("\"${r.constellationBreakdown}\",")
            sb.append("\"${r.sim1Operator}\",")
            sb.append("\"${r.sim1NetworkGen}\",")
            sb.append("\"${r.sim1TacLac}\",")
            sb.append("\"${r.sim1CellId}\",")
            sb.append("\"${r.sim1EnbOrGnb}\",")
            sb.append("\"${r.sim1SectorId}\",")
            sb.append("${r.sim1Pci},")
            sb.append("${r.sim1Arfcn},")
            sb.append("\"${r.sim1Band}\",")
            sb.append("${r.sim1Rsrp ?: ""},")
            sb.append("${r.sim1Rsrq ?: ""},")
            sb.append("${r.sim1Rssi ?: ""},")
            sb.append("${r.sim1Sinr ?: ""},")
            sb.append("${r.sim1SsRsrp ?: ""},")
            sb.append("${r.sim1SsSinr ?: ""},")
            sb.append("${r.sim1TimingAdvance ?: ""},")
            sb.append("${r.sim1EstimatedDistanceM ?: ""},")
            sb.append("\"${r.sim2Operator}\",")
            sb.append("\"${r.sim2NetworkGen}\",")
            sb.append("\"${r.sim2Band}\",")
            sb.append("${r.sim2Rsrp ?: ""},")
            sb.append("${r.sim2Sinr ?: ""},")
            sb.append("${r.bestNeighborRsrp ?: ""},")
            sb.append("${r.neighborServingDeltaDb ?: ""},")
            sb.append("${r.pressureHpa ?: ""},")
            sb.append("${r.baroRelativeAltM ?: ""},")
            sb.append("${r.pingRttMs ?: ""}\n")
        }
        return sb.toString()
    }

    /**
     * Generate 3D KML with satellite-count color altitude paths for Google Earth.
     */
    fun exportToKml(session: LogSession, records: List<TelemetryRecord>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>${escapeXml(session.title)}</name>
    <description>Recorded by Signal &amp; GNSS Field Logger (Ing. Jan Kufa, Ph.D. - VUT Brno FEKT UREL)</description>
    <Style id="pathStyle">
      <LineStyle>
        <color>ff00ffff</color>
        <width>5</width>
      </LineStyle>
    </Style>
    <Placemark>
      <name>3D Flight/Drive Track</name>
      <styleUrl>#pathStyle</styleUrl>
      <LineString>
        <extrude>1</extrude>
        <tessellate>1</tessellate>
        <altitudeMode>absolute</altitudeMode>
        <coordinates>
""")
        for (r in records) {
            sb.append("          ${r.longitude},${r.latitude},${r.altitudeMsl}\n")
        }
        sb.append("""        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>
""")
        return sb.toString()
    }

    /**
     * Generate GeoJSON FeatureCollection for QGIS / ArcGIS.
     */
    fun exportToGeoJson(session: LogSession, records: List<TelemetryRecord>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"type\": \"FeatureCollection\",\n")
        sb.append("  \"properties\": {\n")
        sb.append("    \"sessionId\": ${session.id},\n")
        sb.append("    \"title\": \"${escapeJson(session.title)}\",\n")
        sb.append("    \"author\": \"Ing. Jan Kufa, Ph.D. (VUT FEKT UREL)\"\n")
        sb.append("  },\n")
        sb.append("  \"features\": [\n")

        records.forEachIndexed { index, r ->
            sb.append("    {\n")
            sb.append("      \"type\": \"Feature\",\n")
            sb.append("      \"geometry\": {\n")
            sb.append("        \"type\": \"Point\",\n")
            sb.append("        \"coordinates\": [${r.longitude}, ${r.latitude}, ${r.altitudeMsl}]\n")
            sb.append("      },\n")
            sb.append("      \"properties\": {\n")
            sb.append("        \"timestampMs\": ${r.timestampMs},\n")
            sb.append("        \"speedKmh\": ${r.speedKmh},\n")
            sb.append("        \"bearingDeg\": ${r.bearingDeg},\n")
            sb.append("        \"satellitesUsed\": ${r.satellitesUsedInFix},\n")
            sb.append("        \"dualFrequencyRatioPct\": ${r.dualFrequencyRatioPct},\n")
            sb.append("        \"sim1Operator\": \"${escapeJson(r.sim1Operator)}\",\n")
            sb.append("        \"sim1NetworkGen\": \"${escapeJson(r.sim1NetworkGen)}\",\n")
            sb.append("        \"sim1Band\": \"${escapeJson(r.sim1Band)}\",\n")
            sb.append("        \"sim1Rsrp\": ${r.sim1Rsrp ?: "null"},\n")
            sb.append("        \"sim1Sinr\": ${r.sim1Sinr ?: "null"},\n")
            sb.append("        \"pingRttMs\": ${r.pingRttMs ?: "null"}\n")
            sb.append("      }\n")
            sb.append("    }${if (index < records.size - 1) "," else ""}\n")
        }

        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun escapeJson(input: String): String {
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
    }
}
