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
    val usedInFix: Boolean,
    val pseudorangeRateMps: Float? = null, // Doppler velocity
    val adrState: String? = null // Carrier Phase / ADR status
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
    val uniqueSatellitesInView: Int = 0,
    val uniqueSatellitesUsedInFix: Int = 0,
    val pdop: Float = 0f,
    val hdop: Float = 0f,
    val vdop: Float = 0f,
    val dualFrequencyRatioPct: Float = 0f,
    val satellites: List<SatelliteInfo> = emptyList(),
    val agcInterferenceScore: Float = 0f, // 0 = normal/clear, >50 = elevated interference risk
    val jammingAlert: Boolean = false,
    val constellationCountMap: Map<String, Pair<Int, Int>> = emptyMap(), // Constellation -> (used, total)
    val l1Count: Int = 0,
    val l5Count: Int = 0,
    val l5InViewCount: Int = 0,
    val latestNmeaSentence: String? = null,
    val rawDopplerHz: Float? = null,
    val adrValidCount: Int = 0,
    val speedAccuracyKmh: Float? = null,
    val geoidSeparationM: Double? = null,
    val ttffSeconds: Float? = null
)

data class CellNeighbor(
    val tech: String,
    val pci: Int,
    val earfcn: Int,
    val band: String,
    val rsrp: Int,
    val deltaFromServingDb: Int,
    val dlFreqMhz: Double? = null,
    val ulFreqMhz: Double? = null
)

enum class AnomalySeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class AnomalyType {
    ROGUE_BTS_ISOLATED,      // Isolated strong cell with no neighbors
    DOWNGRADE_ATTACK_2G,     // Insecure 2G GSM downgrade without mutual auth
    TAC_INCONSISTENCY,       // Sudden TAC change or invalid area code
    RF_INTERFERENCE_JAMMING, // Strong RSRP but collapsed SINR
    INVALID_CELL_ID          // Invalid eNodeB or zero sector ID
}

data class CellularAnomaly(
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val title: String,
    val description: String
)

data class SimCellInfo(
    val slotIndex: Int = 0,
    val subscriptionId: Int = -1,
    val displayName: String = "SIM 1",
    val operatorAlpha: String = "No Service",
    val networkGen: String = "Searching", // 2G, 3G, 4G, 5G NSA, 5G SA
    val isServing: Boolean = false,
    val isDataActive: Boolean = false,
    val plmn: String = "N/A", // e.g. "230-01" or "23001"
    val tac: String = "N/A",
    val ci: String = "N/A",
    val enbOrGnbId: String = "N/A",
    val sectorId: String = "N/A",
    val pci: Int = 0,
    val earfcnOrArfcn: Int = 0,
    val band: String = "N/A",
    val channelBandwidthKhz: Int? = null, // e.g. 10000, 15000, 20000 kHz
    val dlFreqMhz: Double? = null,
    val ulFreqMhz: Double? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rssi: Int? = null,
    val sinr: Float? = null,
    val cqi: Int? = null,
    val ssRsrp: Int? = null,
    val ssRsrq: Int? = null,
    val ssSinr: Float? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Float? = null,
    val timingAdvance: Int? = null,
    val estimatedDistanceM: Double? = null,
    val neighbors: List<CellNeighbor> = emptyList(),
    val anomalies: List<CellularAnomaly> = emptyList(),
    val carrierAggregationSummary: String? = null,
    val totalAggregatedBandwidthKhz: Int? = null,
    val aggregatedCarriers: List<String> = emptyList(),
    val nrStateLabel: String? = null,
    val nrBand: String? = null,
    val nrArfcn: Int? = null,
    val nrPci: Int? = null,
    val nrDlFreqMhz: Double? = null
)

data class CellularState(
    val sim1: SimCellInfo = SimCellInfo(slotIndex = 0, displayName = "SIM 1"),
    val sim2: SimCellInfo = SimCellInfo(slotIndex = 1, displayName = "SIM 2"),
    val activeSimSlot: Int = 0,
    val pingRttMs: Long? = null,
    val pingPacketLoss: Boolean = false,
    val lastUpdateEpochMs: Long = System.currentTimeMillis()
)

data class MotionSensorState(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 9.81f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val compassAzimuthDeg: Float = 0f,
    val hasSensors: Boolean = true
)

data class BatteryState(
    val temperatureC: Float? = null,
    val levelPct: Int? = null,
    val voltageMv: Int? = null,
    val isCharging: Boolean = false
)

data class EnvironmentState(
    val pressureHpa: Float? = null,
    val baseQnhHpa: Float = 1013.25f,
    val relativeAltitudeM: Float? = null,
    val seaLevelPressureHpa: Float? = null,
    val motion: MotionSensorState = MotionSensorState(),
    val battery: BatteryState = BatteryState()
)

data class ChartPoint(
    val timestampMs: Long,
    val rsrp: Float?,
    val rsrpSim2: Float? = null,
    val speedKmh: Float,
    val satCount: Int,
    val satInViewCount: Int = 0,
    val pingMs: Float?,
    val accelMagnitude: Float = 9.81f,
    val gyroZ: Float = 0f,
    val compassAzimuth: Float = 0f,
    val batteryTempC: Float? = null,
    val sim1Operator: String = "SIM 1",
    val sim2Operator: String = "SIM 2",
    val sinrSim1: Float? = null,
    val sinrSim2: Float? = null,
    val pdop: Float? = null,
    val agcScore: Float = 0f
)

data class MapTrailPoint(
    val latitude: Double,
    val longitude: Double,
    val sim1Rsrp: Int?,
    val sim2Rsrp: Int?,
    val speedKmh: Float,
    val satellitesUsed: Int,
    val satellitesInView: Int = 0,
    val pdop: Float,
    val timestampMs: Long
)

enum class LogTriggerMode {
    TIME,
    DISTANCE,
    HYBRID
}

data class LoggedFieldsSelection(
    val logGnss: Boolean = true,
    val logCellularRf: Boolean = true,
    val logSensors: Boolean = true,
    val logPing: Boolean = true,
    val logNeighbors: Boolean = true,
    val logBattery: Boolean = true,
    val logNmea: Boolean = true
)

data class LoggingSettings(
    val triggerMode: LogTriggerMode = LogTriggerMode.TIME,
    val timeIntervalSeconds: Int = 1,
    val distanceThresholdMeters: Float = 5f,
    val isDemoMode: Boolean = false,
    val language: String = "SYSTEM", // "SYSTEM", "EN", "CS"
    val isDarkMode: Boolean? = null, // null = system default
    val qnhHpa: Float = 1013.25f,
    val fieldsSelection: LoggedFieldsSelection = LoggedFieldsSelection(),
    val taMultiplierMode: Int = 2, // 1 = standard 78m, 2 = 156m (calibrated for user mobile)
    val darkMapMode: Boolean = true,
    val audioAnomalyAlerts: Boolean = false
)
