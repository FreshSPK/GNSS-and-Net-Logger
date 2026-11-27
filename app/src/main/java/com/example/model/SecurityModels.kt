package com.example.model

enum class ThreatSeverity {
    OK,
    INFO,
    WARNING,
    CRITICAL
}

enum class ThreatCategory {
    GNSS_SPOOFING,
    GNSS_JAMMING,
    TIME_ANOMALY,
    CELLULAR_SPOOFING,
    CELLULAR_JAMMING,
    INTEGRITY
}

enum class SimulatedAttackType {
    NONE,
    GNSS_SPOOFING,
    GNSS_JAMMING,
    IMSI_CATCHER_2G,
    TA_POWER_SPOOF,
    TIME_DRIFT_ATTACK
}

data class SecurityCheckResult(
    val id: String,
    val category: ThreatCategory,
    val severity: ThreatSeverity,
    val titleCs: String,
    val titleEn: String,
    val summaryCs: String,
    val summaryEn: String,
    val measuredValue: String,
    val expectedRange: String,
    val scoreImpact: Int = 0
)

data class TimeSyncDiagnostics(
    val ntpTimeMs: Long? = null,
    val ntpServer: String = "—",
    val ntpRttMs: Long? = null,
    val ntpStratum: Int? = null,
    val ntpError: String? = null,
    val lastNtpSyncEpochMs: Long? = null,
    val isNtpSyncing: Boolean = false,
    val systemTimeMs: Long = System.currentTimeMillis(),
    val gnssFixTimeMs: Long? = null,
    val gnssHardwareUtcMs: Long? = null,
    val gnssMinusNtpMs: Long? = null,
    val systemMinusNtpMs: Long? = null,
    val gnssMinusSystemMs: Long? = null,
    val clockDiscontinuityCount: Int = 0,
    val clockDriftNanosPerSec: Double? = null,
    val clockBiasUncertaintyNanos: Double? = null
)

data class GnssSecurityMetrics(
    val isMockLocation: Boolean = false,
    val devOptionsEnabled: Boolean = false,
    val adbEnabled: Boolean = false,
    val avgCn0DbHz: Float? = null,
    val cn0StdDev: Float? = null,
    val highElevAvgCn0: Float? = null,
    val lowElevAvgCn0: Float? = null,
    val elevationCn0Inversion: Boolean = false,
    val dopplerSpreadMps: Float? = null,
    val multipathCount: Int = 0,
    val avgAgcDb: Double? = null,
    val agcBaselineDb: Double? = null,
    val agcDropDb: Double? = null,
    val agcInterferenceScore: Float = 0f,
    val l1OnlySuspicion: Boolean = false,
    val gnssVsImuMismatch: Boolean = false,
    val imuStationarySeconds: Int = 0,
    val gnssVsBaroAltJumpM: Float? = null,
    val gnssVsBtsDistanceM: Double? = null,
    val gnssVsBtsMismatch: Boolean = false,
    val jumpEventsCount: Int = 0,
    // v1.7 extended metrics
    val nonGpsSatCount: Int = 0,
    val constellationDrop: Boolean = false,
    val nmeaVsLocationDistM: Double? = null,
    val adrValidRatioPct: Float? = null,
    val frozenConstellation: Boolean = false,
    val dopplerElevMismatchCount: Int = 0,
    val cn0TakeoverJumpDb: Float? = null,
    val gnssBearingDeltaDeg: Float? = null,
    val gyroYawDeltaDeg: Float? = null,
    val gyroTurnMismatch: Boolean = false,
    val magneticFieldUt: Float? = null,
    val stepsLast6s: Int = 0,
    val walkingVsGnssMismatch: Boolean = false
)

data class SimRfSecurityMetrics(
    val slotIndex: Int = 0,
    val operatorName: String = "SIM 1",
    val isServing: Boolean = false,
    val networkGen: String = "Searching",
    val timingAdvance: Int? = null,
    val taDistanceM: Double? = null,
    val btsDistanceM: Double? = null,
    val taVsBtsDeltaM: Double? = null,
    val taAnomaly: Boolean = false,
    val measuredRsrpDbm: Int? = null,
    val expectedRsrpFromTaDbm: Int? = null,
    val rsrpVsTaResidualDb: Int? = null,
    val powerVsTaAnomalyType: String? = null, // "OVERPOWER_NEAR_BTS", "IMPOSSIBLE_STRONG_FAR_TA", "SEVERE_PATH_ATTENUATION"
    val rssiDbm: Int? = null,
    val rssiMinusRsrpGapDb: Int? = null,
    val widebandJammingDetected: Boolean = false,
    val sinrDb: Float? = null,
    val tacChangesCount: Int = 0,
    val is2gDowngrade: Boolean = false,
    val isIsolatedNoNeighbors: Boolean = false,
    val dbMismatch: Boolean = false,
    val pingLossWithStrongRf: Boolean = false,
    // v1.7 extended RF metrics
    val fsplMaxExpectedDbm: Int? = null,
    val fsplExcessDb: Int? = null,
    val suspiciousCellOrBwIssue: String? = null,
    val cqiPoorWithStrongRf: Boolean = false,
    val stationaryTaDrop: Boolean = false
)

data class SecurityIncident(
    val id: Long,
    val timestampMs: Long,
    val category: ThreatCategory,
    val severity: ThreatSeverity,
    val titleCs: String,
    val titleEn: String,
    val detailCs: String,
    val detailEn: String
)

data class SecurityAnalysisState(
    val overallRiskScore: Int = 0,
    val overallStatus: ThreatSeverity = ThreatSeverity.OK,
    val gnssRiskScore: Int = 0,
    val rfRiskScore: Int = 0,
    val timeRiskScore: Int = 0,
    val checks: List<SecurityCheckResult> = emptyList(),
    val timeDiagnostics: TimeSyncDiagnostics = TimeSyncDiagnostics(),
    val gnssMetrics: GnssSecurityMetrics = GnssSecurityMetrics(),
    val sim1RfMetrics: SimRfSecurityMetrics = SimRfSecurityMetrics(slotIndex = 0, operatorName = "SIM 1"),
    val sim2RfMetrics: SimRfSecurityMetrics = SimRfSecurityMetrics(slotIndex = 1, operatorName = "SIM 2"),
    val hasVpnActive: Boolean = false,
    val hasValidatedInternet: Boolean = true,
    val recentIncidents: List<SecurityIncident> = emptyList(),
    val activeSimulatedAttack: SimulatedAttackType = SimulatedAttackType.NONE,
    val lastEvaluatedMs: Long = System.currentTimeMillis(),
    val isDeepScanning: Boolean = false,
    val lastDeepScanTimeMs: Long? = null
)
