package com.example.model

data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int, // GnssStatus.CONSTELLATION_*
    val constellationName: String,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val hasCarrierFrequency: Boolean,
    val carrierFrequencyHz: Float,
    val bandLabel: String, // "L1/E1", "L5/E5a", "L2", etc.
    val usedInFix: Boolean
)

data class GnssState(
    val hasFix: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMsl: Double = 0.0,
    val altitudeWgs84: Double = 0.0,
    val speedKmh: Float = 0f,
    val speedMs: Float = 0f,
    val bearingDeg: Float = 0f,
    val horizontalAccuracyM: Float = 0f,
    val verticalAccuracyM: Float = 0f,
    val satellitesInView: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val pdop: Float = 0f,
    val hdop: Float = 0f,
    val vdop: Float = 0f,
    val dualFrequencyRatioPct: Float = 0f,
    val satellites: List<SatelliteInfo> = emptyList(),
    val agcInterferenceScore: Float = 0f, // 0 = normal/clear, >50 = elevated interference risk
    val jammingAlert: Boolean = false,
    val constellationCountMap: Map<String, Pair<Int, Int>> = emptyMap() // Constellation -> (used, total)
)

data class CellNeighbor(
    val tech: String,
    val pci: Int,
    val earfcn: Int,
    val band: String,
    val rsrp: Int,
    val deltaFromServingDb: Int
)

data class SimCellInfo(
    val slotIndex: Int = 0,
    val subscriptionId: Int = -1,
    val displayName: String = "SIM 1",
    val operatorAlpha: String = "No Service",
    val networkGen: String = "Searching", // 2G, 3G, 4G, 5G NSA, 5G SA
    val isServing: Boolean = false,
    val tac: String = "N/A",
    val ci: String = "N/A",
    val enbOrGnbId: String = "N/A",
    val sectorId: String = "N/A",
    val pci: Int = 0,
    val earfcnOrArfcn: Int = 0,
    val band: String = "N/A",
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssi: Int? = null,
    val sinr: Float? = null,
    val ssRsrp: Int? = null,
    val ssRsrq: Int? = null,
    val ssSinr: Float? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Float? = null,
    val timingAdvance: Int? = null,
    val estimatedDistanceM: Double? = null,
    val neighbors: List<CellNeighbor> = emptyList()
)

data class CellularState(
    val sim1: SimCellInfo = SimCellInfo(slotIndex = 0, displayName = "SIM 1"),
    val sim2: SimCellInfo = SimCellInfo(slotIndex = 1, displayName = "SIM 2"),
    val activeSimSlot: Int = 0,
    val pingRttMs: Long? = null,
    val pingPacketLoss: Boolean = false
)

data class EnvironmentState(
    val pressureHpa: Float? = null,
    val baseQnhHpa: Float = 1013.25f,
    val relativeAltitudeM: Float? = null
)

data class ChartPoint(
    val timestampMs: Long,
    val rsrp: Float?,
    val speedKmh: Float,
    val satCount: Int,
    val pingMs: Float?
)

enum class LogTriggerMode {
    TIME,
    DISTANCE,
    HYBRID
}

data class LoggingSettings(
    val triggerMode: LogTriggerMode = LogTriggerMode.TIME,
    val timeIntervalSeconds: Int = 1,
    val distanceThresholdMeters: Float = 5f,
    val isDemoMode: Boolean = false,
    val language: String = "SYSTEM", // "SYSTEM", "EN", "CS"
    val isDarkMode: Boolean? = null, // null = system default
    val qnhHpa: Float = 1013.25f
)
