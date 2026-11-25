package com.example.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class SecurityMonitorEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _securityState = MutableStateFlow(SecurityAnalysisState())
    val securityState: StateFlow<SecurityAnalysisState> = _securityState.asStateFlow()

    @Volatile
    private var simulatedAttack: SimulatedAttackType = SimulatedAttackType.NONE

    // Rolling NTP state anchored to SystemClock.elapsedRealtime()
    @Volatile
    private var ntpOffsetFromElapsedMs: Long? = null
    @Volatile
    private var ntpServerName: String = "—"
    @Volatile
    private var ntpRttMs: Long? = null
    @Volatile
    private var ntpStratum: Int? = null
    @Volatile
    private var ntpError: String? = null
    @Volatile
    private var lastNtpSyncEpochMs: Long? = null
    @Volatile
    private var lastNtpAttemptElapsedMs: Long = 0L
    @Volatile
    private var isNtpSyncing: Boolean = false

    // Rolling kinematics & TAC windows
    private var imuStationaryStreakSec: Int = 0
    private val altWindow = ArrayDeque<Triple<Long, Double, Float?>>() // elapsedMs, gnssAlt, baroAlt
    private val sim1TacHistory = ArrayDeque<Pair<Long, String>>() // elapsedMs, tac
    private val sim2TacHistory = ArrayDeque<Pair<Long, String>>()
    private var lastSim1Tac: String = ""
    private var lastSim2Tac: String = ""
    private var sustainedPingLossCount: Int = 0
    private var initialClockDiscontinuity: Int? = null

    // Forensic incident log (deduplicated with 30s cooldown per check ID)
    private val incidents = mutableListOf<SecurityIncident>()
    private val lastIncidentByCheckId = mutableMapOf<String, Long>()
    private val incidentIdCounter = AtomicLong(1L)

    fun setSimulatedAttack(attack: SimulatedAttackType) {
        simulatedAttack = attack
    }

    fun clearIncidents() {
        synchronized(incidents) {
            incidents.clear()
            lastIncidentByCheckId.clear()
        }
        _securityState.value = _securityState.value.copy(recentIncidents = emptyList())
    }

    fun resetSessionBaselines() {
        imuStationaryStreakSec = 0
        altWindow.clear()
        sim1TacHistory.clear()
        sim2TacHistory.clear()
        lastSim1Tac = ""
        lastSim2Tac = ""
        sustainedPingLossCount = 0
        initialClockDiscontinuity = null
        simulatedAttack = SimulatedAttackType.NONE
    }

    fun syncNtpNow(force: Boolean = true) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && isNtpSyncing) return
        if (!force && ntpOffsetFromElapsedMs != null && nowElapsed - lastNtpAttemptElapsedMs < 60_000L) return

        isNtpSyncing = true
        lastNtpAttemptElapsedMs = nowElapsed
        scope.launch(Dispatchers.IO) {
            val result = SntpTimeClient.syncTime()
            isNtpSyncing = false
            result.onSuccess { res ->
                ntpOffsetFromElapsedMs = res.offsetFromElapsedMs
                ntpServerName = res.server
                ntpRttMs = res.rttMs
                ntpStratum = res.stratum
                ntpError = null
                lastNtpSyncEpochMs = res.syncedAtEpochMs
            }.onFailure { err ->
                ntpError = err.message ?: "NTP sync failed"
            }
        }
    }

    fun evaluateNow(
        gnss: GnssState,
        cellular: CellularState,
        env: EnvironmentState,
        isDemoMode: Boolean
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()

        // Auto-sync NTP periodically (every 60s) when live
        if (!isDemoMode && (ntpOffsetFromElapsedMs == null || nowElapsed - lastNtpAttemptElapsedMs >= 60_000L)) {
            if (nowElapsed - lastNtpAttemptElapsedMs >= 15_000L) {
                syncNtpNow(force = false)
            }
        }

        // 1. Update IMU stationary streak (when |accelMag - 9.81| < 0.45 m/s² and |gyroZ| < 0.08 rad/s)
        val motion = env.motion
        val isStationaryImu = abs(motion.accelMagnitude - 9.81f) < 0.45f &&
            abs(motion.gyroX) < 0.08f &&
            abs(motion.gyroY) < 0.08f &&
            abs(motion.gyroZ) < 0.08f
        if (isStationaryImu) {
            imuStationaryStreakSec = (imuStationaryStreakSec + 1).coerceAtMost(3600)
        } else {
            imuStationaryStreakSec = 0
        }

        // 2. Update 5-second altitude delta window (GNSS vs Baro)
        if (gnss.hasFix) {
            altWindow.addLast(Triple(nowElapsed, gnss.altitudeMsl, env.relativeAltitudeM))
            while (altWindow.isNotEmpty() && nowElapsed - altWindow.first().first > 6500L) {
                altWindow.removeFirst()
            }
        } else {
            altWindow.clear()
        }
        val oldestAlt = altWindow.firstOrNull()
        val gnssAltDelta = if (gnss.hasFix && oldestAlt != null && nowElapsed - oldestAlt.first >= 2500L) {
            (gnss.altitudeMsl - oldestAlt.second).toFloat()
        } else null
        val baroAltDelta = if (env.relativeAltitudeM != null && oldestAlt?.third != null && nowElapsed - oldestAlt.first >= 2500L) {
            env.relativeAltitudeM - oldestAlt.third!!
        } else null

        // 3. Track TAC changes over 2-minute window per SIM
        trackTacChange(cellular.sim1.tac, lastSim1Tac, sim1TacHistory, nowElapsed) { lastSim1Tac = it }
        trackTacChange(cellular.sim2.tac, lastSim2Tac, sim2TacHistory, nowElapsed) { lastSim2Tac = it }

        // 4. Track ping loss streak
        if (cellular.pingPacketLoss) {
            sustainedPingLossCount++
        } else if (cellular.pingRttMs != null) {
            sustainedPingLossCount = 0
        }

        if (initialClockDiscontinuity == null && gnss.clockDiscontinuityCount >= 0) {
            initialClockDiscontinuity = gnss.clockDiscontinuityCount
        }

        // 5. Network / Device integrity flags
        val (hasVpn, hasInternet) = readNetworkFlags()
        val devOpts = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        val adbOn = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) { false }

        val effectiveNtpMs = if (isDemoMode) {
            nowEpoch
        } else {
            ntpOffsetFromElapsedMs?.let { nowElapsed + it }
        }

        val timeDiag = TimeSyncDiagnostics(
            ntpTimeMs = effectiveNtpMs,
            ntpServer = if (isDemoMode) "demo.ntp.vut.cz" else ntpServerName,
            ntpRttMs = if (isDemoMode) 12L else ntpRttMs,
            ntpStratum = if (isDemoMode) 2 else ntpStratum,
            ntpError = if (isDemoMode) null else ntpError,
            lastNtpSyncEpochMs = if (isDemoMode) nowEpoch else lastNtpSyncEpochMs,
            isNtpSyncing = !isDemoMode && isNtpSyncing,
            systemTimeMs = nowEpoch,
            gnssFixTimeMs = if (isDemoMode) nowEpoch else gnss.fixTimestampMs,
            gnssHardwareUtcMs = if (isDemoMode) nowEpoch else gnss.gnssHardwareUtcMs,
            clockDiscontinuityCount = gnss.clockDiscontinuityCount,
            clockDriftNanosPerSec = gnss.clockDriftNanosPerSec,
            clockBiasUncertaintyNanos = gnss.clockBiasUncertaintyNanos
        )

        val ctxSnap = SecurityContextSnapshot(
            imuStationarySeconds = imuStationaryStreakSec,
            isImuStationaryNow = isStationaryImu && !isDemoMode,
            baroAltDelta5sM = baroAltDelta,
            gnssAltDelta5sM = gnssAltDelta,
            sim1TacChanges2Min = sim1TacHistory.size,
            sim2TacChanges2Min = sim2TacHistory.size,
            sustainedPingLossCount = sustainedPingLossCount,
            devOptionsEnabled = devOpts,
            adbEnabled = adbOn,
            hasVpnActive = hasVpn,
            hasValidatedInternet = hasInternet,
            initialClockDiscontinuity = initialClockDiscontinuity
        )

        val evaluated = SecurityAnalyzer.evaluate(
            gnssInput = gnss,
            cellularInput = cellular,
            timeDiagInput = timeDiag,
            ctx = ctxSnap,
            simulatedAttack = if (isDemoMode) simulatedAttack else SimulatedAttackType.NONE
        )

        // Record new WARNING / CRITICAL incidents into timeline
        recordIncidents(evaluated.checks, nowEpoch)

        val currentIncidents = synchronized(incidents) { incidents.toList() }
        _securityState.value = evaluated.copy(recentIncidents = currentIncidents)
    }

    private fun trackTacChange(
        currentTac: String,
        previousTac: String,
        history: ArrayDeque<Pair<Long, String>>,
        nowElapsed: Long,
        updatePrev: (String) -> Unit
    ) {
        if (currentTac.isNotBlank() && currentTac != "N/A" &&
            !currentTac.contains("RRC", ignoreCase = true) &&
            !currentTac.contains("Standby", ignoreCase = true)
        ) {
            if (previousTac.isNotBlank() && previousTac != currentTac) {
                history.addLast(nowElapsed to currentTac)
            }
            updatePrev(currentTac)
        }
        while (history.isNotEmpty() && nowElapsed - history.first().first > 120_000L) {
            history.removeFirst()
        }
    }

    private fun readNetworkFlags(): Pair<Boolean, Boolean> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false to true
            val net = cm.activeNetwork ?: return false to false
            val caps = cm.getNetworkCapabilities(net) ?: return false to false
            val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            vpn to validated
        } catch (_: Exception) {
            false to true
        }
    }

    private fun recordIncidents(checks: List<SecurityCheckResult>, nowEpoch: Long) {
        synchronized(incidents) {
            for (chk in checks) {
                if (chk.severity == ThreatSeverity.WARNING || chk.severity == ThreatSeverity.CRITICAL) {
                    val lastMs = lastIncidentByCheckId[chk.id] ?: 0L
                    if (nowEpoch - lastMs >= 30_000L) {
                        lastIncidentByCheckId[chk.id] = nowEpoch
                        incidents.add(
                            0,
                            SecurityIncident(
                                id = incidentIdCounter.getAndIncrement(),
                                timestampMs = nowEpoch,
                                category = chk.category,
                                severity = chk.severity,
                                titleCs = chk.titleCs,
                                titleEn = chk.titleEn,
                                detailCs = "${chk.summaryCs} [${chk.measuredValue}]",
                                detailEn = "${chk.summaryEn} [${chk.measuredValue}]"
                            )
                        )
                        if (incidents.size > 100) {
                            incidents.removeAt(incidents.lastIndex)
                        }
                    }
                }
            }
        }
    }
}
