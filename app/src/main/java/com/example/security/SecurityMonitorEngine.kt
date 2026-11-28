package com.example.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address
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
    @Volatile
    private var isDeepScanning: Boolean = false
    @Volatile
    private var lastDeepScanTimeMs: Long? = null

    // Rolling kinematics & TAC windows
    private var imuStationaryStreakSec: Int = 0
    private val altWindow = ArrayDeque<Triple<Long, Double, Float?>>() // elapsedMs, gnssAlt, baroAlt
    private val sim1TacHistory = ArrayDeque<Pair<Long, String>>() // elapsedMs, tac
    private val sim2TacHistory = ArrayDeque<Pair<Long, String>>()
    private var lastSim1Tac: String = ""
    private var lastSim2Tac: String = ""
    private var sustainedPingLossCount: Int = 0
    private var initialClockDiscontinuity: Int? = null
    private var prevClockDiscontinuity: Int? = null
    private var inFixClockJumps: Int = 0

    // v1.7 rolling windows for gyro-bearing, stationary TA drop, sky freeze, Doppler-elev, C/N0 step & accuracy freeze
    private data class HeadingSample(val elapsedMs: Long, val bearingDeg: Float, val yawRateRadPerSec: Float)
    private data class SatTrackSample(val elapsedMs: Long, val azDeg: Float, val elDeg: Float)
    private data class TaBaseline(val ta: Int, val ci: String, val pci: Int)

    private val headingWindow = ArrayDeque<HeadingSample>()
    private var gyroMismatchStreak: Int = 0
    private val satBaselineMap = HashMap<Pair<Int, Int>, SatTrackSample>()
    private val cn0History = ArrayDeque<Triple<Long, Float, Int>>() // elapsedMs, avgCn0, satCount
    private val accFreezeHistory = ArrayDeque<Float>()
    private var sim1TaBaseline: TaBaseline? = null
    private var sim2TaBaseline: TaBaseline? = null
    private var sim1TaDropUntilElapsedMs: Long = 0L
    private var sim2TaDropUntilElapsedMs: Long = 0L
    private var sim1TaDropDetail: String? = null
    private var sim2TaDropDetail: String? = null

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

    fun resetSessionBaselines(preserveNtp: Boolean = false) {
        imuStationaryStreakSec = 0
        altWindow.clear()
        sim1TacHistory.clear()
        sim2TacHistory.clear()
        lastSim1Tac = ""
        lastSim2Tac = ""
        sustainedPingLossCount = 0
        initialClockDiscontinuity = null
        prevClockDiscontinuity = null
        inFixClockJumps = 0
        headingWindow.clear()
        gyroMismatchStreak = 0
        satBaselineMap.clear()
        cn0History.clear()
        accFreezeHistory.clear()
        sim1TaBaseline = null
        sim2TaBaseline = null
        sim1TaDropUntilElapsedMs = 0L
        sim2TaDropUntilElapsedMs = 0L
        sim1TaDropDetail = null
        sim2TaDropDetail = null
        simulatedAttack = SimulatedAttackType.NONE
        if (!preserveNtp) {
            ntpOffsetFromElapsedMs = null
            ntpServerName = "—"
            ntpRttMs = null
            ntpStratum = null
            ntpError = null
            lastNtpSyncEpochMs = null
            lastNtpAttemptElapsedMs = 0L
        }
    }

    fun setDeepScanActive(active: Boolean, completedEpochMs: Long? = null) {
        isDeepScanning = active
        if (completedEpochMs != null) {
            lastDeepScanTimeMs = completedEpochMs
        }
        _securityState.value = _securityState.value.copy(
            isDeepScanning = isDeepScanning,
            lastDeepScanTimeMs = lastDeepScanTimeMs
        )
    }

    fun syncNtpNow(force: Boolean = true) {
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!force && isNtpSyncing) return
        if (!force && ntpOffsetFromElapsedMs != null && nowElapsed - lastNtpAttemptElapsedMs < 60_000L) return

        isNtpSyncing = true
        lastNtpAttemptElapsedMs = nowElapsed
        _securityState.value = _securityState.value.copy(
            timeDiagnostics = _securityState.value.timeDiagnostics.copy(isNtpSyncing = true)
        )
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
            val nowE = SystemClock.elapsedRealtime()
            val cur = _securityState.value
            _securityState.value = cur.copy(
                timeDiagnostics = cur.timeDiagnostics.copy(
                    ntpTimeMs = ntpOffsetFromElapsedMs?.let { nowE + it },
                    ntpServer = ntpServerName,
                    ntpRttMs = ntpRttMs,
                    ntpStratum = ntpStratum,
                    ntpError = ntpError,
                    lastNtpSyncEpochMs = lastNtpSyncEpochMs,
                    isNtpSyncing = false
                )
            )
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

        if (gnss.hasGnssClockData && gnss.clockDiscontinuityCount >= 0) {
            if (initialClockDiscontinuity == null) {
                initialClockDiscontinuity = gnss.clockDiscontinuityCount
                prevClockDiscontinuity = gnss.clockDiscontinuityCount
            } else {
                val prev = prevClockDiscontinuity ?: gnss.clockDiscontinuityCount
                val step = (gnss.clockDiscontinuityCount - prev).coerceAtLeast(0)
                if (step > 0 && gnss.hasFix && gnss.satellitesUsedInFix >= 4) {
                    inFixClockJumps += step
                }
                prevClockDiscontinuity = gnss.clockDiscontinuityCount
            }
        }

        // 5. v1.7 Heading Fusion (Gyroscope vs GNSS Bearing)
        var gnssBearingDelta4s: Float? = null
        var gyroYawDelta4s: Float? = null
        var gyroMismatchNow = false
        if (!isDemoMode && gnss.hasFix && gnss.speedKmh > 14f) {
            headingWindow.addLast(HeadingSample(nowElapsed, gnss.bearingDeg, motion.verticalYawRadPerSec))
            while (headingWindow.isNotEmpty() && nowElapsed - headingWindow.first().elapsedMs > 4500L) {
                headingWindow.removeFirst()
            }
            if (headingWindow.size >= 3 && nowElapsed - headingWindow.first().elapsedMs >= 2500L) {
                val oldest = headingWindow.first()
                val newest = headingWindow.last()
                var bDiff = newest.bearingDeg - oldest.bearingDeg
                if (bDiff > 180f) bDiff -= 360f
                if (bDiff < -180f) bDiff += 360f
                gnssBearingDelta4s = bDiff

                var yawDegSum = 0.0
                for (i in 1 until headingWindow.size) {
                    val prev = headingWindow.elementAt(i - 1)
                    val cur = headingWindow.elementAt(i)
                    val dtSec = ((cur.elapsedMs - prev.elapsedMs) / 1000.0).coerceIn(0.0, 2.0)
                    val avgRad = (prev.yawRateRadPerSec + cur.yawRateRadPerSec) * 0.5
                    yawDegSum += Math.toDegrees(avgRad * dtSec)
                }
                gyroYawDelta4s = yawDegSum.toFloat()

                val turnDiff = abs(abs(bDiff) - abs(gyroYawDelta4s))
                val maxTurn = maxOf(abs(bDiff), abs(gyroYawDelta4s))
                if (turnDiff > 28f && maxTurn > 25f) {
                    gyroMismatchStreak++
                    if (gyroMismatchStreak >= 2) gyroMismatchNow = true
                } else {
                    gyroMismatchStreak = 0
                }
            }
        } else {
            headingWindow.clear()
            gyroMismatchStreak = 0
        }

        // 6. v1.7 Stationary IMU vs Timing Advance Drop
        val (taDrop1, taDropDetail1) = trackStationaryTaDrop(
            sim = cellular.sim1,
            isStationary = isStationaryImu && !isDemoMode,
            stationarySec = imuStationaryStreakSec,
            nowElapsed = nowElapsed,
            baseline = sim1TaBaseline,
            updateBaseline = { sim1TaBaseline = it },
            heldUntil = sim1TaDropUntilElapsedMs,
            updateHeldUntil = { sim1TaDropUntilElapsedMs = it },
            heldDetail = sim1TaDropDetail,
            updateHeldDetail = { sim1TaDropDetail = it }
        )
        val (taDrop2, taDropDetail2) = trackStationaryTaDrop(
            sim = cellular.sim2,
            isStationary = isStationaryImu && !isDemoMode,
            stationarySec = imuStationaryStreakSec,
            nowElapsed = nowElapsed,
            baseline = sim2TaBaseline,
            updateBaseline = { sim2TaBaseline = it },
            heldUntil = sim2TaDropUntilElapsedMs,
            updateHeldUntil = { sim2TaDropUntilElapsedMs = it },
            heldDetail = sim2TaDropDetail,
            updateHeldDetail = { sim2TaDropDetail = it }
        )

        // 7. v1.7 GNSS Sky Freeze, Doppler Sign vs Elevation Rate, C/N0 Takeover Step, Frozen Accuracy
        var frozenConstellation = false
        var dopplerElevMismatches = 0
        if (!isDemoMode && gnss.satellites.isNotEmpty()) {
            var matchedFreezeSats = 0
            var maxDeltaAzEl = 0f
            var longestSpanMs = 0L
            val activeKeys = HashSet<Pair<Int, Int>>()
            for (s in gnss.satellites) {
                val key = s.constellationType to s.svid
                activeKeys.add(key)
                val base = satBaselineMap[key]
                if (base == null) {
                    satBaselineMap[key] = SatTrackSample(nowElapsed, s.azimuthDegrees, s.elevationDegrees)
                } else {
                    val span = nowElapsed - base.elapsedMs
                    if (span > longestSpanMs) longestSpanMs = span
                    val dEl = s.elevationDegrees - base.elDeg
                    var dAz = abs(s.azimuthDegrees - base.azDeg)
                    if (dAz > 180f) dAz = 360f - dAz
                    val combined = dAz + abs(dEl)
                    if (span >= 55_000L) {
                        matchedFreezeSats++
                        if (combined > maxDeltaAzEl) maxDeltaAzEl = combined
                    }
                    val prRate = s.pseudorangeRateMps
                    if (span >= 12_000L && prRate != null && abs(prRate) > 25f && abs(dEl) >= 0.35f) {
                        if ((dEl > 0.35f && prRate > 25f) || (dEl < -0.35f && prRate < -25f)) {
                            dopplerElevMismatches++
                        }
                        if (span > 45_000L && matchedFreezeSats == 0) {
                            satBaselineMap[key] = SatTrackSample(nowElapsed, s.azimuthDegrees, s.elevationDegrees)
                        }
                    }
                }
            }
            satBaselineMap.keys.retainAll(activeKeys)
            if (matchedFreezeSats >= 6 && longestSpanMs >= 60_000L && maxDeltaAzEl < 0.05f) {
                frozenConstellation = true
            }
        } else if (isDemoMode) {
            satBaselineMap.clear()
        }

        val liveAvgCn0 = gnss.satellites.map { it.cn0DbHz }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        var takeoverJumpDb: Float? = null
        if (!isDemoMode && liveAvgCn0 != null) {
            cn0History.addLast(Triple(nowElapsed, liveAvgCn0, gnss.satellitesInView))
            while (cn0History.isNotEmpty() && nowElapsed - cn0History.first().first > 4500L) {
                cn0History.removeFirst()
            }
            if (isStationaryImu && imuStationaryStreakSec >= 5 && gnss.satellitesInView >= 6 && cn0History.size >= 2) {
                val old = cn0History.first()
                if (nowElapsed - old.first >= 1500L && old.third >= 6) {
                    val jump = liveAvgCn0 - old.second
                    if (jump >= 9.0f) takeoverJumpDb = jump
                }
            }
        } else {
            cn0History.clear()
        }

        var frozenAccWithHighHdop = false
        if (!isDemoMode && gnss.hasFix && gnss.horizontalAccuracyM > 0f) {
            accFreezeHistory.addLast(gnss.horizontalAccuracyM)
            while (accFreezeHistory.size > 15) accFreezeHistory.removeFirst()
            if (accFreezeHistory.size == 15 && gnss.hdop >= 3.0f && gnss.horizontalAccuracyM <= 1.5f) {
                val minAcc = accFreezeHistory.minOrNull() ?: 0f
                val maxAcc = accFreezeHistory.maxOrNull() ?: 0f
                if (maxAcc - minAcc < 0.01f) {
                    frozenAccWithHighHdop = true
                }
            }
        } else {
            accFreezeHistory.clear()
        }

        // 8. Network / Device integrity flags & LinkProperties DNS inspection
        val netFlags = readNetworkFlags()
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

        val alignedFixTimeMs = if (isDemoMode) {
            nowEpoch
        } else {
            val raw = gnss.fixTimestampMs
            val capElapsed = gnss.fixElapsedMs
            if (raw != null && capElapsed != null) {
                val age = nowElapsed - capElapsed
                if (age in 0L..8000L) raw + age else raw
            } else raw
        }
        val alignedHwUtcMs = if (isDemoMode) {
            nowEpoch
        } else {
            val raw = gnss.gnssHardwareUtcMs
            val capElapsed = gnss.gnssHardwareUtcElapsedMs
            if (raw != null && capElapsed != null) {
                val age = nowElapsed - capElapsed
                if (age in 0L..8000L) raw + age else raw
            } else raw
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
            gnssFixTimeMs = alignedFixTimeMs,
            gnssHardwareUtcMs = alignedHwUtcMs,
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
            hasVpnActive = netFlags.hasVpn,
            hasValidatedInternet = netFlags.hasValidatedInternet,
            initialClockDiscontinuity = initialClockDiscontinuity,
            inFixClockDiscontinuityJumps = inFixClockJumps,
            gnssBearingDelta4sDeg = gnssBearingDelta4s,
            gyroYawDelta4sDeg = gyroYawDelta4s,
            gyroTurnMismatchDetected = gyroMismatchNow,
            magneticFieldUt = motion.magneticFieldUt,
            stepsLast6s = motion.stepsLast6s,
            isWalkingDetected = motion.isWalkingDetected && !isDemoMode,
            stationaryTaDropDetectedSim1 = taDrop1,
            stationaryTaDropDetectedSim2 = taDrop2,
            stationaryTaDropDetailSim1 = taDropDetail1,
            stationaryTaDropDetailSim2 = taDropDetail2,
            frozenConstellationDetected = frozenConstellation,
            dopplerElevationMismatchCount = dopplerElevMismatches,
            cn0TakeoverJumpDb = takeoverJumpDb,
            frozenAccuracyWithHighHdop = frozenAccWithHighHdop,
            dnsServers = netFlags.dnsServers,
            hasPrivateCellularDns = netFlags.hasPrivateCellularDns,
            hasCaptivePortal = netFlags.hasCaptivePortal,
            isCellularDataSuspended = netFlags.isCellularDataSuspended
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
        _securityState.value = evaluated.copy(
            recentIncidents = currentIncidents,
            isDeepScanning = isDeepScanning,
            lastDeepScanTimeMs = lastDeepScanTimeMs
        )
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

    private fun trackStationaryTaDrop(
        sim: SimCellInfo,
        isStationary: Boolean,
        stationarySec: Int,
        nowElapsed: Long,
        baseline: TaBaseline?,
        updateBaseline: (TaBaseline?) -> Unit,
        heldUntil: Long,
        updateHeldUntil: (Long) -> Unit,
        heldDetail: String?,
        updateHeldDetail: (String?) -> Unit
    ): Pair<Boolean, String?> {
        val currentTa = sim.timingAdvance
        if (!sim.isServing || currentTa == null || !isStationary) {
            if (!isStationary) updateBaseline(null)
            return (nowElapsed < heldUntil) to (if (nowElapsed < heldUntil) heldDetail else null)
        }
        if (baseline == null) {
            if (stationarySec >= 10) {
                updateBaseline(TaBaseline(currentTa, sim.ci, sim.pci))
            }
        } else {
            val cellSwitched = sim.ci != baseline.ci || sim.pci != baseline.pci
            if (baseline.ta - currentTa >= 4 && currentTa <= 2 && (cellSwitched || sim.neighbors.isEmpty())) {
                val detail = "TA ${baseline.ta} → $currentTa (PCI ${baseline.pci}→${sim.pci})"
                updateHeldDetail(detail)
                updateHeldUntil(nowElapsed + 90_000L)
                updateBaseline(TaBaseline(currentTa, sim.ci, sim.pci))
                return true to detail
            } else if (!cellSwitched && currentTa > baseline.ta) {
                updateBaseline(TaBaseline(currentTa, sim.ci, sim.pci))
            }
        }
        return (nowElapsed < heldUntil) to (if (nowElapsed < heldUntil) heldDetail else null)
    }

    private data class NetworkFlags(
        val hasVpn: Boolean = false,
        val hasValidatedInternet: Boolean = true,
        val dnsServers: List<String> = emptyList(),
        val hasPrivateCellularDns: Boolean = false,
        val hasCaptivePortal: Boolean = false,
        val isCellularDataSuspended: Boolean = false
    )

    private fun readNetworkFlags(): NetworkFlags {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkFlags()
            val net = cm.activeNetwork ?: return NetworkFlags(hasValidatedInternet = false)
            val caps = cm.getNetworkCapabilities(net) ?: return NetworkFlags(hasValidatedInternet = false)
            val vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val captive = cellular && !wifi && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

            val dnsList = try {
                cm.getLinkProperties(net)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
            } catch (_: Exception) {
                emptyList()
            }

            val privateCellDns = if (cellular && !wifi && !vpn) {
                cm.getLinkProperties(net)?.dnsServers?.any { addr ->
                    when (addr) {
                        is Inet4Address -> addr.isSiteLocalAddress || addr.isLoopbackAddress
                        is Inet6Address -> addr.isSiteLocalAddress || (addr.address.isNotEmpty() && (addr.address[0].toInt() and 0xFE) == 0xFC)
                        else -> false
                    }
                } == true
            } else false

            val dataSuspended = if (cellular && !wifi &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    @Suppress("DEPRECATION")
                    tm?.dataState == TelephonyManager.DATA_SUSPENDED
                } catch (_: Exception) {
                    false
                }
            } else false

            NetworkFlags(
                hasVpn = vpn,
                hasValidatedInternet = validated,
                dnsServers = dnsList,
                hasPrivateCellularDns = privateCellDns,
                hasCaptivePortal = captive,
                isCellularDataSuspended = dataSuspended
            )
        } catch (_: Exception) {
            NetworkFlags()
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
