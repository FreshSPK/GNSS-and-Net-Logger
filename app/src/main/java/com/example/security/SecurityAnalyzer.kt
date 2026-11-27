package com.example.security

import com.example.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class SecurityContextSnapshot(
    val imuStationarySeconds: Int = 0,
    val isImuStationaryNow: Boolean = false,
    val baroAltDelta5sM: Float? = null,
    val gnssAltDelta5sM: Float? = null,
    val sim1TacChanges2Min: Int = 0,
    val sim2TacChanges2Min: Int = 0,
    val sustainedPingLossCount: Int = 0,
    val devOptionsEnabled: Boolean = false,
    val adbEnabled: Boolean = false,
    val hasVpnActive: Boolean = false,
    val hasValidatedInternet: Boolean = true,
    val initialClockDiscontinuity: Int? = null
)

object SecurityAnalyzer {

    /**
     * Urban/suburban macrocell empirical reference RSRP (dBm) for a given TA distance and carrier frequency.
     * Calibrated for Czech LTE/5G networks (~ -72 dBm @ 150m on 1800 MHz, ~34 dB/decade path-loss exponent).
     */
    fun estimateExpectedRsrpFromTa(distanceMeters: Double, freqMhz: Double?): Int {
        val d = distanceMeters.coerceAtLeast(50.0)
        val f = (freqMhz ?: 1800.0).coerceIn(450.0, 6000.0)
        val freqPenaltyDb = 20.0 * log10(f / 1800.0)
        val pathLossOffset = 34.0 * log10(d / 150.0)
        return (-72.0 - pathLossOffset - freqPenaltyDb).toInt().coerceIn(-140, -45)
    }

    fun evaluate(
        gnssInput: GnssState,
        cellularInput: CellularState,
        timeDiagInput: TimeSyncDiagnostics,
        ctx: SecurityContextSnapshot,
        simulatedAttack: SimulatedAttackType = SimulatedAttackType.NONE
    ): SecurityAnalysisState {
        // Apply optional Demo Attack Simulator transformations on copies
        val (gnss, cellular, timeDiag) = applySimulatedAttackIfActive(
            gnssInput, cellularInput, timeDiagInput, simulatedAttack
        )

        val checks = mutableListOf<SecurityCheckResult>()

        // =====================================================================
        // 1. GNSS ANTI-SPOOFING & JAMMING ANALYSIS
        // =====================================================================
        val satCn0s = gnss.satellites.map { it.cn0DbHz }.filter { it > 0f }
        val avgCn0 = if (satCn0s.isNotEmpty()) satCn0s.average().toFloat() else null
        val cn0StdDev = if (satCn0s.size >= 4) {
            val mean = satCn0s.average()
            sqrt(satCn0s.sumOf { (it - mean) * (it - mean) } / satCn0s.size).toFloat()
        } else null

        val highElevSats = gnss.satellites.filter { it.elevationDegrees >= 45f && it.cn0DbHz > 0f }
        val lowElevSats = gnss.satellites.filter { it.elevationDegrees in 5f..20f && it.cn0DbHz > 0f }
        val highElevAvg = if (highElevSats.size >= 2) highElevSats.map { it.cn0DbHz }.average().toFloat() else null
        val lowElevAvg = if (lowElevSats.size >= 2) lowElevSats.map { it.cn0DbHz }.average().toFloat() else null
        val elevInversion = highElevAvg != null && lowElevAvg != null && lowElevAvg >= highElevAvg + 2.5f && (avgCn0 ?: 0f) > 30f

        val agcDropDb = if (gnss.agcBaselineDb != null && gnss.avgAgcDb != null) {
            gnss.agcBaselineDb - gnss.avgAgcDb
        } else null

        // 1A. Software Mock Location check
        val mockSeverity = if (gnss.isMockLocation) ThreatSeverity.CRITICAL else ThreatSeverity.OK
        checks.add(
            SecurityCheckResult(
                id = "gnss_mock",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = mockSeverity,
                titleCs = "Softwarový Mock Location / Fake GPS",
                titleEn = "Software Mock Location / Fake GPS",
                summaryCs = if (gnss.isMockLocation) {
                    "Android hlásí příznak isMock=true! Externí aplikace podvrhává GPS souřadnice systému."
                } else {
                    "Fix pochází z hardwarového GNSS přijímače (bez příznaku Mock Location)."
                },
                summaryEn = if (gnss.isMockLocation) {
                    "Android reports isMock=true! A mock location app is injecting synthetic coordinates."
                } else {
                    "Fix originates from genuine GNSS hardware (no Mock Location flag)."
                },
                measuredValue = if (gnss.isMockLocation) "MOCK = TRUE" else "Hardware GPS",
                expectedRange = "Hardware GPS",
                scoreImpact = if (gnss.isMockLocation) 85 else 0
            )
        )

        // 1B. AGC Jamming & Interference
        val jammingSeverity = when {
            gnss.jammingAlert || gnss.agcInterferenceScore >= 65f -> ThreatSeverity.CRITICAL
            gnss.agcInterferenceScore >= 35f -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "gnss_agc_jam",
                category = ThreatCategory.GNSS_JAMMING,
                severity = jammingSeverity,
                titleCs = "GNSS AGC Zarušení pásma (Jamming)",
                titleEn = "GNSS AGC Band Jamming",
                summaryCs = when (jammingSeverity) {
                    ThreatSeverity.CRITICAL -> "Kritický pokles zisku AGC (${String.format(Locale.US, "%.0f", gnss.agcInterferenceScore)}%) – silná rušička v pásmu L1/L5!"
                    ThreatSeverity.WARNING -> "Zvýšený šum na vstupu přijímače (AGC index ${String.format(Locale.US, "%.0f", gnss.agcInterferenceScore)}%)."
                    else -> "Úroveň zisku AGC stabilní a bez známek širokopásmového rušení."
                },
                summaryEn = when (jammingSeverity) {
                    ThreatSeverity.CRITICAL -> "Critical AGC compression (${String.format(Locale.US, "%.0f", gnss.agcInterferenceScore)}%) – strong L1/L5 RF jammer nearby!"
                    ThreatSeverity.WARNING -> "Elevated RF noise floor (AGC index ${String.format(Locale.US, "%.0f", gnss.agcInterferenceScore)}%)."
                    else -> "AGC baseline nominal with clean GNSS RF spectrum."
                },
                measuredValue = agcDropDb?.let {
                    "Index ${String.format(Locale.US, "%.0f%%", gnss.agcInterferenceScore)} (Δ${String.format(Locale.US, "%+.1f", -it)} dB)"
                } ?: "Index ${String.format(Locale.US, "%.0f%%", gnss.agcInterferenceScore)}",
                expectedRange = "< 35% (ΔAGC < 6 dB)",
                scoreImpact = when (jammingSeverity) {
                    ThreatSeverity.CRITICAL -> 80
                    ThreatSeverity.WARNING -> 35
                    else -> 0
                }
            )
        )

        // 1C. Overpower / Uniform C/N0 + Elevation Inversion (RF Spoofer signature)
        val uniformOverpower = avgCn0 != null && cn0StdDev != null && satCn0s.size >= 6 && avgCn0 > 43.5f && cn0StdDev < 2.3f
        val cn0AnomalySeverity = when {
            uniformOverpower -> ThreatSeverity.CRITICAL
            elevInversion -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "gnss_cn0_profile",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = cn0AnomalySeverity,
                titleCs = "Profil C/N0 & Inverze elevace satelitů",
                titleEn = "C/N0 Distribution & Elevation Inversion",
                summaryCs = when {
                    uniformOverpower -> "Všechny satelity mají podezřele vysoký a jednotný výkon (${String.format(Locale.US, "%.1f", avgCn0)} dB-Hz, σ=${String.format(Locale.US, "%.2f", cn0StdDev)}). Typický podpis pozemního RF spooferu!"
                    elevInversion -> "Satelity nízko nad obzorem (${String.format(Locale.US, "%.1f", lowElevAvg)} dB-Hz) jsou silnější než v zenitu (${String.format(Locale.US, "%.1f", highElevAvg)} dB-Hz) – podezření na pozemní vysílač."
                    avgCn0 != null -> "Přirozený rozptyl výkonu C/N0 podle elevace družic."
                    else -> "Čekání na dostatečný počet družic pro analýzu profilu C/N0."
                },
                summaryEn = when {
                    uniformOverpower -> "All satellites exhibit abnormally strong & uniform C/N0 (${String.format(Locale.US, "%.1f", avgCn0)} dB-Hz, σ=${String.format(Locale.US, "%.2f", cn0StdDev)}). Typical RF spoofer signature!"
                    elevInversion -> "Low-elevation satellites (${String.format(Locale.US, "%.1f", lowElevAvg)} dB-Hz) exceed zenith satellites (${String.format(Locale.US, "%.1f", highElevAvg)} dB-Hz)."
                    avgCn0 != null -> "Natural satellite C/N0 dispersion across elevation angles."
                    else -> "Waiting for sufficient satellites to analyze C/N0 profile."
                },
                measuredValue = if (avgCn0 != null && cn0StdDev != null) {
                    "Ø ${String.format(Locale.US, "%.1f", avgCn0)} dB-Hz • σ=${String.format(Locale.US, "%.2f", cn0StdDev)}"
                } else "N/A",
                expectedRange = "σ ≥ 2.5 dB-Hz, HighEl > LowEl",
                scoreImpact = when (cn0AnomalySeverity) {
                    ThreatSeverity.CRITICAL -> 70
                    ThreatSeverity.WARNING -> 35
                    else -> 0
                }
            )
        )

        // 1D. Orbital Doppler Spread Check
        val dopplerSpread = gnss.dopplerSpreadMps
        val dopplerSeverity = when {
            dopplerSpread != null && gnss.satellitesInView >= 5 && dopplerSpread < 45f -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "gnss_doppler",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = dopplerSeverity,
                titleCs = "Orbitální Dopplerův rozptyl pseudovzdáleností",
                titleEn = "Orbital Doppler Pseudorange Spread",
                summaryCs = when {
                    dopplerSeverity == ThreatSeverity.WARNING -> "Rozptyl Dopplerova posuvu je podezřele nízký (${String.format(Locale.US, "%.1f", dopplerSpread)} m/s) – statický či naivní SDR spoofer!"
                    dopplerSpread != null -> "Družice vykazují realistickou orbitální radiální dynamiku (±Doppler)."
                    else -> "Surový Doppler z GnssMeasurements není na tomto čipu aktivní nebo čeká na data."
                },
                summaryEn = when {
                    dopplerSeverity == ThreatSeverity.WARNING -> "Doppler velocity spread collapsed (${String.format(Locale.US, "%.1f", dopplerSpread)} m/s) – naive static SDR spoofer indicator!"
                    dopplerSpread != null -> "Satellites exhibit realistic orbital radial velocity spread."
                    else -> "Raw GnssMeasurements Doppler unavailable or awaiting samples."
                },
                measuredValue = dopplerSpread?.let { "σ = ${String.format(Locale.US, "%.1f", it)} m/s" } ?: "N/A",
                expectedRange = "σ = 150–650 m/s",
                scoreImpact = if (dopplerSeverity == ThreatSeverity.WARNING) 45 else 0
            )
        )

        // 1E. Dual-Frequency L1 vs L5/E5a Consistency
        val l1OnlySuspicion = gnss.hasEverSeenDualFreq &&
            gnss.satellitesInView >= 8 &&
            (avgCn0 ?: 0f) > 32f &&
            gnss.l5InViewCount == 0
        val dualBandSeverity = if (l1OnlySuspicion) ThreatSeverity.WARNING else ThreatSeverity.OK
        checks.add(
            SecurityCheckResult(
                id = "gnss_dual_band",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = dualBandSeverity,
                titleCs = "Konzistence pásem L1/E1 vs. L5/E5a",
                titleEn = "Dual-Band L1/E1 vs. L5/E5a Consistency",
                summaryCs = when {
                    l1OnlySuspicion -> "Telefon podporuje L5/E5a, ale při silném příjmu L1 (${gnss.satellitesInView} sat.) zcela zmizely signály L5/E5a – typický znak jednokanálového L1 spooferu!"
                    gnss.l5InViewCount > 0 -> "Současný příjem pásem L1/E1 (${gnss.l1Count}) i L5/E5a/B2a (${gnss.l5InViewCount}) zvyšuje odolnost proti spoofingu."
                    else -> "Zařízení zatím v této relaci nezachytilo L5/E5a pásmo (nebo HW podporuje jen L1)."
                },
                summaryEn = when {
                    l1OnlySuspicion -> "Device supports L5/E5a, yet all L5/E5a signals vanished despite strong L1 (${gnss.satellitesInView} sats) – classic single-band L1 spoofer!"
                    gnss.l5InViewCount > 0 -> "Simultaneous L1/E1 and L5/E5a/B2a tracking verifies multi-frequency integrity."
                    else -> "No L5/E5a capability observed yet in this session."
                },
                measuredValue = "L1: ${gnss.l1Count} • L5/E5a: ${gnss.l5InViewCount}",
                expectedRange = if (gnss.hasEverSeenDualFreq) "L5/E5a ≥ 1" else "HW závislé",
                scoreImpact = if (l1OnlySuspicion) 40 else 0
            )
        )

        // 1F. IMU Kinematics & Barometer vs GNSS
        val gnssVsImuMismatch = ctx.isImuStationaryNow && ctx.imuStationarySeconds >= 4 && gnss.hasFix && gnss.speedKmh > 12f
        val baroJump = if (ctx.baroAltDelta5sM != null && ctx.gnssAltDelta5sM != null) {
            abs(ctx.gnssAltDelta5sM - ctx.baroAltDelta5sM)
        } else null
        val kinematicSeverity = when {
            gnssVsImuMismatch || gnss.jumpEventsCount > 0 -> ThreatSeverity.CRITICAL
            baroJump != null && baroJump > 40f -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "gnss_imu_kinematics",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = kinematicSeverity,
                titleCs = "Fúze senzorů: GNSS vs. IMU & Barometr",
                titleEn = "Sensor Fusion: GNSS vs. IMU & Barometer",
                summaryCs = when {
                    gnssVsImuMismatch -> "GNSS hlásí rychlost ${String.format(Locale.US, "%.1f", gnss.speedKmh)} km/h, ale akcelerometr a gyroskop potvrzují klid zařízení (${ctx.imuStationarySeconds} s)!"
                    gnss.jumpEventsCount > 0 -> "Detekováno ${gnss.jumpEventsCount} skokových teleportací polohy (> 65 m/s) – silný multipath nebo spoofer."
                    baroJump != null && baroJump > 40f -> "Skoková změna výšky GNSS o ${String.format(Locale.US, "%.0f", baroJump)} m bez odpovídající změny barometrického tlaku."
                    else -> "Kinematika GNSS odpovídá datům z akcelerometru, gyroskopu i barometru."
                },
                summaryEn = when {
                    gnssVsImuMismatch -> "GNSS reports ${String.format(Locale.US, "%.1f", gnss.speedKmh)} km/h while IMU sensors confirm stationary device (${ctx.imuStationarySeconds}s)!"
                    gnss.jumpEventsCount > 0 -> "Detected ${gnss.jumpEventsCount} abrupt coordinate teleport jumps (> 65 m/s)."
                    baroJump != null && baroJump > 40f -> "GNSS altitude shifted by ${String.format(Locale.US, "%.0f", baroJump)} m with no corresponding barometric pressure change."
                    else -> "GNSS motion aligns consistently with IMU and barometer sensors."
                },
                measuredValue = if (gnss.hasFix) {
                    "GNSS ${String.format(Locale.US, "%.1f", gnss.speedKmh)} km/h • Skoky: ${gnss.jumpEventsCount}"
                } else "Bez fixu",
                expectedRange = "Shoda s IMU (Δh < 40 m)",
                scoreImpact = when (kinematicSeverity) {
                    ThreatSeverity.CRITICAL -> 65
                    ThreatSeverity.WARNING -> 35
                    else -> 0
                }
            )
        )

        // 1G. GNSS Position vs Known Serving BTS Distance Sanity
        val btsDist1 = cellular.sim1.btsMatch?.distanceMeters
        val btsDist2 = cellular.sim2.btsMatch?.distanceMeters
        val maxBtsDist = listOfNotNull(btsDist1, btsDist2).maxOrNull()
        val gnssVsBtsMismatch = gnss.hasFix && maxBtsDist != null && maxBtsDist > 45_000.0
        checks.add(
            SecurityCheckResult(
                id = "gnss_vs_bts_geo",
                category = ThreatCategory.GNSS_SPOOFING,
                severity = if (gnssVsBtsMismatch) ThreatSeverity.CRITICAL else ThreatSeverity.OK,
                titleCs = "Prostorová křížová kontrola: GNSS vs. BTS",
                titleEn = "Spatial Cross-Check: GNSS vs. Known BTS",
                summaryCs = when {
                    gnssVsBtsMismatch -> "Nahlášená poloha GNSS je ${String.format(Locale.US, "%.1f", (maxBtsDist ?: 0.0) / 1000.0)} km od obslužného vysílače v ČR! Pravděpodobný GNSS spoofing polohy."
                    maxBtsDist != null -> "GNSS souřadnice leží v reálném dosahu obsluhující základnové stanice (${String.format(Locale.US, "%.2f", maxBtsDist / 1000.0)} km)."
                    else -> "Čekání na spárování BTS s GPS souřadnicemi v lokální databázi."
                },
                summaryEn = when {
                    gnssVsBtsMismatch -> "Reported GNSS position is ${String.format(Locale.US, "%.1f", (maxBtsDist ?: 0.0) / 1000.0)} km from the serving cellular tower! Likely GNSS position spoofing."
                    maxBtsDist != null -> "GNSS position falls within realistic serving BTS coverage (${String.format(Locale.US, "%.2f", maxBtsDist / 1000.0)} km)."
                    else -> "Awaiting serving BTS GPS match from local registry."
                },
                measuredValue = maxBtsDist?.let {
                    if (it >= 1000.0) String.format(Locale.US, "%.2f km", it / 1000.0) else String.format(Locale.US, "%.0f m", it)
                } ?: "N/A",
                expectedRange = "< 35 km (dosah buňky)",
                scoreImpact = if (gnssVsBtsMismatch) 75 else 0
            )
        )

        // =====================================================================
        // 2. MULTI-CLOCK TIME FORENSICS (GNSS vs MOBILE NITZ vs INTERNET NTP)
        // =====================================================================
        val bestGnssTimeMs = timeDiag.gnssHardwareUtcMs ?: timeDiag.gnssFixTimeMs
        val gnssMinusNtp = if (bestGnssTimeMs != null && timeDiag.ntpTimeMs != null) {
            bestGnssTimeMs - timeDiag.ntpTimeMs
        } else null
        val sysMinusNtp = if (timeDiag.ntpTimeMs != null) {
            timeDiag.systemTimeMs - timeDiag.ntpTimeMs
        } else null
        val gnssMinusSys = if (bestGnssTimeMs != null) {
            bestGnssTimeMs - timeDiag.systemTimeMs
        } else null

        val updatedTimeDiag = timeDiag.copy(
            gnssMinusNtpMs = gnssMinusNtp,
            systemMinusNtpMs = sysMinusNtp,
            gnssMinusSystemMs = gnssMinusSys,
            clockDiscontinuityCount = gnss.clockDiscontinuityCount,
            clockDriftNanosPerSec = gnss.clockDriftNanosPerSec,
            clockBiasUncertaintyNanos = gnss.clockBiasUncertaintyNanos
        )

        val absGnssNtp = gnssMinusNtp?.let { abs(it) }
        val absSysNtp = sysMinusNtp?.let { abs(it) }
        val absGnssSys = gnssMinusSys?.let { abs(it) }

        val timeDriftSeverity = when {
            absGnssNtp != null && absGnssNtp > 2000L -> ThreatSeverity.CRITICAL
            absGnssSys != null && absGnssSys > 2500L -> ThreatSeverity.CRITICAL
            absGnssNtp != null && absGnssNtp > 800L -> ThreatSeverity.WARNING
            absSysNtp != null && absSysNtp > 2500L -> ThreatSeverity.WARNING
            absGnssSys != null && absGnssSys > 800L -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "time_triple_sync",
                category = ThreatCategory.TIME_ANOMALY,
                severity = timeDriftSeverity,
                titleCs = "Rozdíl času: GNSS vs. Mobilní síť vs. NTP",
                titleEn = "Clock Skew: GNSS vs. Mobile Network vs. NTP",
                summaryCs = when (timeDriftSeverity) {
                    ThreatSeverity.CRITICAL -> "Kritická odchylka hodin! Přehrávání záznamu GNSS (meaconing) nebo manipulace se síťovým časem NITZ."
                    ThreatSeverity.WARNING -> "Měřitelný časový posun mezi GNSS, mobilní sítí a internetovým NTP serverem."
                    else -> if (timeDiag.ntpTimeMs != null || bestGnssTimeMs != null) {
                        "Všechny dostupné časové zdroje (GNSS / Mobilní síť / NTP) jsou vzájemně synchronizované."
                    } else {
                        "Čekání na NTP synchronizaci nebo platný GNSS čas."
                    }
                },
                summaryEn = when (timeDriftSeverity) {
                    ThreatSeverity.CRITICAL -> "Critical clock divergence! Indicates GNSS replay/meaconing attack or NITZ network time spoofing."
                    ThreatSeverity.WARNING -> "Noticeable time drift detected between GNSS, cellular NITZ, and NTP UTC."
                    else -> "Available time references (GNSS / Cellular NITZ / NTP) are tightly synchronized."
                },
                measuredValue = buildString {
                    append("G-N: ${gnssMinusNtp?.let { "${it}ms" } ?: "—"}")
                    append(" | S-N: ${sysMinusNtp?.let { "${it}ms" } ?: "—"}")
                    append(" | G-S: ${gnssMinusSys?.let { "${it}ms" } ?: "—"}")
                },
                expectedRange = "|Δt| ≤ 800 ms",
                scoreImpact = when (timeDriftSeverity) {
                    ThreatSeverity.CRITICAL -> 75
                    ThreatSeverity.WARNING -> 35
                    else -> 0
                }
            )
        )

        val discDelta = if (ctx.initialClockDiscontinuity != null) {
            (gnss.clockDiscontinuityCount - ctx.initialClockDiscontinuity).coerceAtLeast(0)
        } else 0
        val clockDriftAnomaly = gnss.clockDriftNanosPerSec != null && abs(gnss.clockDriftNanosPerSec) > 50_000.0
        val hwClockSeverity = when {
            discDelta > 0 || clockDriftAnomaly -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "gnss_hw_clock",
                category = ThreatCategory.TIME_ANOMALY,
                severity = hwClockSeverity,
                titleCs = "Stabilita interních hodin GNSS (GnssClock)",
                titleEn = "GNSS Hardware Clock Stability",
                summaryCs = when {
                    discDelta > 0 -> "Zaznamenány $discDelta skokové diskontinuity hardwarových hodin GNSS během relace."
                    clockDriftAnomaly -> "Neobvykle vysoký drift oscilátoru (${String.format(Locale.US, "%.0f", gnss.clockDriftNanosPerSec)} ns/s)."
                    else -> "Lokální oscilátor TCXO a časová základna GNSS běží plynule bez skoků."
                },
                summaryEn = when {
                    discDelta > 0 -> "Observed $discDelta GNSS hardware clock discontinuities during this session."
                    clockDriftAnomaly -> "Abnormally high GNSS oscillator drift (${String.format(Locale.US, "%.0f", gnss.clockDriftNanosPerSec)} ns/s)."
                    else -> "GNSS TCXO oscillator drift and clock bias remain stable."
                },
                measuredValue = "Skoky: +$discDelta • Drift: ${gnss.clockDriftNanosPerSec?.let { String.format(Locale.US, "%.1f ns/s", it) } ?: "—"}",
                expectedRange = "0 skoků, |drift| < 50k ns/s",
                scoreImpact = if (hwClockSeverity == ThreatSeverity.WARNING) 30 else 0
            )
        )

        // =====================================================================
        // 3. CELLULAR RF ANTI-SPOOFING, TA-POWER PHYSICS & JAMMING (SIM 1 & SIM 2)
        // =====================================================================
        val sim1Metrics = analyzeSimRfSecurity(cellular.sim1, ctx.sim1TacChanges2Min, cellular.pingPacketLoss, checks)
        val sim2Metrics = analyzeSimRfSecurity(cellular.sim2, ctx.sim2TacChanges2Min, false, checks)

        // =====================================================================
        // 4. NETWORK & DEVICE INTEGRITY CHECKS
        // =====================================================================
        val netSeverity = when {
            !ctx.hasValidatedInternet && (cellular.sim1.isDataActive || cellular.sim2.isDataActive) &&
                ((cellular.sim1.rsrp ?: -140) > -95 || (cellular.sim2.rsrp ?: -140) > -95) -> ThreatSeverity.WARNING
            ctx.hasVpnActive -> ThreatSeverity.INFO
            else -> ThreatSeverity.OK
        }
        checks.add(
            SecurityCheckResult(
                id = "net_integrity",
                category = ThreatCategory.INTEGRITY,
                severity = netSeverity,
                titleCs = "Integrita datového tunelu, VPN & Mock nastavení",
                titleEn = "Network Tunnel, VPN & Mock Settings",
                summaryCs = when (netSeverity) {
                    ThreatSeverity.WARNING -> "Silný mobilní signál, ale síť nemá ověřený přístup k internetu (možný pasivní IMSI Catcher nebo blokace dat)."
                    ThreatSeverity.INFO -> "Aktivní VPN tunel – internetový Ping a NTP procházejí přes šifrovaný tunel (vyšší RTT)."
                    else -> "Konektivita ověřena, bez podezřelé odchylky síťového stacku."
                },
                summaryEn = when (netSeverity) {
                    ThreatSeverity.WARNING -> "Strong cellular signal but unvalidated internet access (possible passive IMSI Catcher or data blackhole)."
                    ThreatSeverity.INFO -> "Active VPN tunnel – Ping and NTP traffic are routed through VPN (elevated RTT)."
                    else -> "Network connectivity validated with clean routing."
                },
                measuredValue = buildString {
                    append(if (ctx.hasValidatedInternet) "Internet OK" else "Bez konektivity")
                    if (ctx.hasVpnActive) append(" • VPN")
                    if (ctx.devOptionsEnabled) append(" • DevOpts")
                },
                expectedRange = "Internet OK",
                scoreImpact = if (netSeverity == ThreatSeverity.WARNING) 30 else 0
            )
        )

        // Compute Category Risk Scores (capped 0..100)
        val gnssRisk = checks
            .filter { it.category == ThreatCategory.GNSS_SPOOFING || it.category == ThreatCategory.GNSS_JAMMING }
            .sumOf { it.scoreImpact }
            .coerceIn(0, 100)
        val timeRisk = checks
            .filter { it.category == ThreatCategory.TIME_ANOMALY }
            .sumOf { it.scoreImpact }
            .coerceIn(0, 100)
        val rfRisk = checks
            .filter { it.category == ThreatCategory.CELLULAR_SPOOFING || it.category == ThreatCategory.CELLULAR_JAMMING }
            .sumOf { it.scoreImpact }
            .coerceIn(0, 100)

        val maxSingle = checks.maxOfOrNull { it.scoreImpact } ?: 0
        val overallRisk = max(maxSingle, ((gnssRisk * 0.4) + (rfRisk * 0.4) + (timeRisk * 0.25)).toInt()).coerceIn(0, 100)

        val overallStatus = when {
            checks.any { it.severity == ThreatSeverity.CRITICAL } || overallRisk >= 65 -> ThreatSeverity.CRITICAL
            checks.any { it.severity == ThreatSeverity.WARNING } || overallRisk >= 25 -> ThreatSeverity.WARNING
            checks.any { it.severity == ThreatSeverity.INFO } -> ThreatSeverity.INFO
            else -> ThreatSeverity.OK
        }

        val gnssMetrics = GnssSecurityMetrics(
            isMockLocation = gnss.isMockLocation,
            devOptionsEnabled = ctx.devOptionsEnabled,
            adbEnabled = ctx.adbEnabled,
            avgCn0DbHz = avgCn0,
            cn0StdDev = cn0StdDev,
            highElevAvgCn0 = highElevAvg,
            lowElevAvgCn0 = lowElevAvg,
            elevationCn0Inversion = elevInversion,
            dopplerSpreadMps = dopplerSpread,
            multipathCount = gnss.multipathCount,
            avgAgcDb = gnss.avgAgcDb,
            agcBaselineDb = gnss.agcBaselineDb,
            agcDropDb = agcDropDb,
            agcInterferenceScore = gnss.agcInterferenceScore,
            l1OnlySuspicion = l1OnlySuspicion,
            gnssVsImuMismatch = gnssVsImuMismatch,
            imuStationarySeconds = ctx.imuStationarySeconds,
            gnssVsBaroAltJumpM = baroJump,
            gnssVsBtsDistanceM = maxBtsDist,
            gnssVsBtsMismatch = gnssVsBtsMismatch,
            jumpEventsCount = gnss.jumpEventsCount
        )

        return SecurityAnalysisState(
            overallRiskScore = overallRisk,
            overallStatus = overallStatus,
            gnssRiskScore = gnssRisk,
            rfRiskScore = rfRisk,
            timeRiskScore = timeRisk,
            checks = checks,
            timeDiagnostics = updatedTimeDiag,
            gnssMetrics = gnssMetrics,
            sim1RfMetrics = sim1Metrics,
            sim2RfMetrics = sim2Metrics,
            hasVpnActive = ctx.hasVpnActive,
            hasValidatedInternet = ctx.hasValidatedInternet,
            activeSimulatedAttack = simulatedAttack,
            lastEvaluatedMs = System.currentTimeMillis()
        )
    }

    private fun analyzeSimRfSecurity(
        sim: SimCellInfo,
        tacChanges2Min: Int,
        pingLossOnData: Boolean,
        outChecks: MutableList<SecurityCheckResult>
    ): SimRfSecurityMetrics {
        val simTag = "sim${sim.slotIndex + 1}"
        val simTitlePrefix = "SIM ${sim.slotIndex + 1} (${sim.operatorAlpha.ifBlank { "Cellular" }})"

        if (!sim.isServing || sim.networkGen == "Searching") {
            return SimRfSecurityMetrics(slotIndex = sim.slotIndex, operatorName = sim.operatorAlpha)
        }

        val taDist = sim.estimatedDistanceM
        val btsDist = sim.btsMatch?.distanceMeters
        val taVsBtsDelta = if (taDist != null && btsDist != null) taDist - btsDist else null
        val taAnomaly = taVsBtsDelta != null && btsDist != null && btsDist < 30_000.0 && abs(taVsBtsDelta) > 1500.0

        val freqMhz = sim.dlFreqMhz ?: sim.btsMatch?.let { com.example.bts.BtsGeoUtils.bandToApproxFreqMhz(it.dbBand) }
        val expectedRsrp = taDist?.let { estimateExpectedRsrpFromTa(it, freqMhz) }
        val residualDb = if (sim.rsrp != null && expectedRsrp != null) sim.rsrp - expectedRsrp else null

        val powerVsTaAnomalyType: String? = when {
            sim.timingAdvance != null && sim.timingAdvance >= 12 && (sim.rsrp ?: -140) > -68 -> "IMPOSSIBLE_STRONG_FAR_TA"
            sim.timingAdvance != null && sim.timingAdvance <= 1 && (sim.rsrp ?: -140) > -55 &&
                (sim.neighbors.isEmpty() || (sim.sinr ?: 10f) < 4f) -> "OVERPOWER_NEAR_BTS"
            sim.timingAdvance != null && sim.timingAdvance <= 2 && (sim.rsrp ?: 0) < -114 -> "SEVERE_PATH_ATTENUATION"
            residualDb != null && residualDb > 28 -> "IMPOSSIBLE_STRONG_FAR_TA"
            else -> null
        }

        val rssiGapDb = if (sim.rssi != null && sim.rsrp != null) sim.rssi - sim.rsrp else null
        val widebandJamming = rssiGapDb != null && rssiGapDb > 38 && ((sim.sinr ?: 10f) < 5f || (sim.rsrq ?: -10) < -15)

        val is2g = sim.networkGen.contains("2G") || sim.networkGen.contains("GSM") ||
            sim.networkGen.contains("GPRS") || sim.networkGen.contains("EDGE")
        val isIsolated = (sim.rsrp ?: -140) > -85 && sim.neighbors.isEmpty()
        val dbMismatch = sim.btsMatch?.let { it.pciMismatch || it.tacMismatch } == true
        val pingLossWithStrongRf = sim.isDataActive && pingLossOnData && (sim.rsrp ?: -140) > -85

        // Check A: TA vs BTS Distance (ΔTA)
        if (sim.timingAdvance != null || btsDist != null) {
            val taSeverity = when {
                taVsBtsDelta != null && abs(taVsBtsDelta) > 3500.0 && (btsDist ?: 0.0) < 30_000.0 -> ThreatSeverity.CRITICAL
                taAnomaly -> ThreatSeverity.WARNING
                else -> ThreatSeverity.OK
            }
            outChecks.add(
                SecurityCheckResult(
                    id = "${simTag}_ta_vs_bts",
                    category = ThreatCategory.CELLULAR_SPOOFING,
                    severity = taSeverity,
                    titleCs = "$simTitlePrefix: Timing Advance vs. poloha BTS",
                    titleEn = "$simTitlePrefix: Timing Advance vs. BTS Location",
                    summaryCs = when (taSeverity) {
                        ThreatSeverity.CRITICAL, ThreatSeverity.WARNING ->
                            "Rozdíl mezi odhadem vzdálenosti z TA (${taDist?.toInt()} m) a skutečnou polohou vysílače (${btsDist?.toInt()} m) činí ${taVsBtsDelta?.toInt()} m! Podezření na podvrženou BTS blíže k uživateli nebo chybu GNSS."
                        else -> if (taDist != null && btsDist != null) {
                            "Časové předstižení TA souhlasí se skutečnou vzdáleností ke známé BTS."
                        } else {
                            "Pro plné srovnání TA vs. BTS je nutné znát jak TA modemu, tak GPS polohu BTS."
                        }
                    },
                    summaryEn = when (taSeverity) {
                        ThreatSeverity.CRITICAL, ThreatSeverity.WARNING ->
                            "TA range (${taDist?.toInt()} m) differs from known BTS distance (${btsDist?.toInt()} m) by ${taVsBtsDelta?.toInt()} m! Possible nearby fake BTS or GNSS offset."
                        else -> "Timing Advance distance aligns with known serving BTS location."
                    },
                    measuredValue = "TA: ${taDist?.let { "${it.toInt()} m" } ?: "—"} • BTS: ${btsDist?.let { "${it.toInt()} m" } ?: "—"}",
                    expectedRange = "|ΔTA| ≤ 1500 m",
                    scoreImpact = when (taSeverity) {
                        ThreatSeverity.CRITICAL -> 65
                        ThreatSeverity.WARNING -> 35
                        else -> 0
                    }
                )
            )
        }

        // Check B: RSRP vs TA Power-Distance Physics
        if (sim.rsrp != null && taDist != null) {
            val pwrSeverity = when (powerVsTaAnomalyType) {
                "IMPOSSIBLE_STRONG_FAR_TA", "OVERPOWER_NEAR_BTS" -> ThreatSeverity.CRITICAL
                "SEVERE_PATH_ATTENUATION" -> ThreatSeverity.WARNING
                else -> ThreatSeverity.OK
            }
            outChecks.add(
                SecurityCheckResult(
                    id = "${simTag}_power_vs_ta",
                    category = ThreatCategory.CELLULAR_SPOOFING,
                    severity = pwrSeverity,
                    titleCs = "$simTitlePrefix: Poměr výkonu RSRP a vzdálenosti TA",
                    titleEn = "$simTitlePrefix: RSRP Power vs. TA Distance Physics",
                    summaryCs = when (powerVsTaAnomalyType) {
                        "IMPOSSIBLE_STRONG_FAR_TA" -> "Fyzikálně nepravděpodobný vysoký výkon (${sim.rsrp} dBm) při velkém TA=${sim.timingAdvance} (~${taDist.toInt()} m) – znak nelegálního opakovače nebo manipulace s TA!"
                        "OVERPOWER_NEAR_BTS" -> "Extrémně silný blízký signál (${sim.rsrp} dBm při TA=${sim.timingAdvance}) bez sousedů / se špatným SINR – profil blízkého IMSI Catcheru!"
                        "SEVERE_PATH_ATTENUATION" -> "Navzdory malému TA=${sim.timingAdvance} (~${taDist.toInt()} m) je výkon velmi slabý (${sim.rsrp} dBm) – silné stínění nebo rušení."
                        else -> "Naměřený RSRP (${sim.rsrp} dBm) odpovídá teoretickému útlumu šíření pro TA=${sim.timingAdvance} (oček. ~${expectedRsrp} dBm)."
                    },
                    summaryEn = when (powerVsTaAnomalyType) {
                        "IMPOSSIBLE_STRONG_FAR_TA" -> "Implausibly strong RSRP (${sim.rsrp} dBm) at large TA=${sim.timingAdvance} (~${taDist.toInt()} m) – illegal repeater or TA spoofing!"
                        "OVERPOWER_NEAR_BTS" -> "Overpowering near-field signal (${sim.rsrp} dBm at TA=${sim.timingAdvance}) with missing neighbors / low SINR – Stingray profile!"
                        "SEVERE_PATH_ATTENUATION" -> "Severely attenuated RSRP (${sim.rsrp} dBm) despite small TA=${sim.timingAdvance} (~${taDist.toInt()} m)."
                        else -> "Measured RSRP (${sim.rsrp} dBm) matches propagation path-loss model for TA=${sim.timingAdvance} (exp. ~${expectedRsrp} dBm)."
                    },
                    measuredValue = "${sim.rsrp} dBm (oček. ${expectedRsrp ?: "—"} dBm, Δ=${residualDb?.let { "${if (it >= 0) "+" else ""}$it" } ?: "—"} dB)",
                    expectedRange = "Δ = -22 až +25 dB",
                    scoreImpact = when (pwrSeverity) {
                        ThreatSeverity.CRITICAL -> 65
                        ThreatSeverity.WARNING -> 30
                        else -> 0
                    }
                )
            )
        }

        // Check C: RF Wideband Jamming (RSSI - RSRP Gap & SINR)
        val rfJamSeverity = when {
            widebandJamming || ((sim.rsrp ?: -140) > -90 && (sim.sinr ?: 10f) < -3f) -> ThreatSeverity.CRITICAL
            rssiGapDb != null && rssiGapDb > 35 -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        outChecks.add(
            SecurityCheckResult(
                id = "${simTag}_rf_jamming",
                category = ThreatCategory.CELLULAR_JAMMING,
                severity = rfJamSeverity,
                titleCs = "$simTitlePrefix: Rušení RF pásma (RSSI−RSRP & SINR)",
                titleEn = "$simTitlePrefix: RF Band Jamming (RSSI−RSRP & SINR)",
                summaryCs = when (rfJamSeverity) {
                    ThreatSeverity.CRITICAL -> "Detekováno silné rušení kanálu! Širokopásmový rozdíl RSSI−RSRP=${rssiGapDb ?: "—"} dB, SINR=${sim.sinr?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} dB."
                    ThreatSeverity.WARNING -> "Zvýšený širokopásmový šum v kanálu (RSSI−RSRP = ${rssiGapDb} dB)."
                    else -> "Odstup šumu SINR i širokopásmový výkon RSSI jsou v normě."
                },
                summaryEn = when (rfJamSeverity) {
                    ThreatSeverity.CRITICAL -> "Severe co-channel/wideband RF jamming! RSSI−RSRP gap=${rssiGapDb ?: "—"} dB, SINR=${sim.sinr?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} dB."
                    ThreatSeverity.WARNING -> "Elevated wideband channel noise floor (RSSI−RSRP = ${rssiGapDb} dB)."
                    else -> "Channel SINR and wideband RSSI noise floor are nominal."
                },
                measuredValue = buildString {
                    append("SINR: ${sim.sinr?.let { String.format(Locale.US, "%.1f dB", it) } ?: "—"}")
                    if (rssiGapDb != null) append(" • Δ(RSSI-RSRP): ${rssiGapDb} dB")
                },
                expectedRange = "SINR ≥ -2 dB, Δ < 36 dB",
                scoreImpact = when (rfJamSeverity) {
                    ThreatSeverity.CRITICAL -> 70
                    ThreatSeverity.WARNING -> 30
                    else -> 0
                }
            )
        )

        // Check D: IMSI Catcher / 2G Downgrade / Isolated Cell / TAC Oscillation / DB Mismatch
        val catcherSeverity = when {
            is2g -> ThreatSeverity.CRITICAL
            dbMismatch || isIsolated || tacChanges2Min >= 3 || pingLossWithStrongRf -> ThreatSeverity.WARNING
            else -> ThreatSeverity.OK
        }
        outChecks.add(
            SecurityCheckResult(
                id = "${simTag}_imsi_catcher",
                category = ThreatCategory.CELLULAR_SPOOFING,
                severity = catcherSeverity,
                titleCs = "$simTitlePrefix: IMSI Catcher, 2G Downgrade & DB shoda",
                titleEn = "$simTitlePrefix: IMSI Catcher, 2G Downgrade & DB Match",
                summaryCs = when {
                    is2g -> "KRITICKÉ: Telefon byl sražen do 2G GSM bez vzájemné autentizace – vysoké riziko aktivního IMSI Catcheru!"
                    dbMismatch -> "Nesoulad živé buňky (PCI ${sim.pci}, TAC ${sim.tac}) s lokální databází BTS!"
                    isIsolated -> "Silná buňka (${sim.rsrp} dBm) nevysílá žádné sousední buňky v SIB tabulce."
                    tacChanges2Min >= 3 -> "Rychlé přepínání směrovací oblasti TAC (${tacChanges2Min}× za 2 min) – typické vynucování IMSI registrace."
                    pingLossWithStrongRf -> "Silný RF signál (${sim.rsrp} dBm), ale 100% ztráta paketů – možný pasivní IMSI Catcher bez routingu!"
                    else -> "Autentická LTE/5G síť se sousedními buňkami a konzistentním TAC/PCI."
                },
                summaryEn = when {
                    is2g -> "CRITICAL: Device forced onto legacy 2G GSM without mutual authentication – active IMSI Catcher risk!"
                    dbMismatch -> "Live cell PCI (${sim.pci}) or TAC (${sim.tac}) mismatches local BTS database!"
                    isIsolated -> "Strong cell (${sim.rsrp} dBm) broadcasts zero SIB neighbor cells."
                    tacChanges2Min >= 3 -> "Rapid TAC oscillation (${tacChanges2Min}× in 2 min) – typical IMSI probing signature."
                    pingLossWithStrongRf -> "Strong RF (${sim.rsrp} dBm) with 100% packet loss – possible passive Stingray blackholing traffic!"
                    else -> "Authentic LTE/5G cell with valid neighbors and consistent PCI/TAC."
                },
                measuredValue = "${sim.networkGen} • Sousedé: ${sim.neighbors.size} • TAC Δ: $tacChanges2Min",
                expectedRange = "4G/5G, Sousedé ≥ 1, Shoda DB",
                scoreImpact = when {
                    is2g -> 85
                    catcherSeverity == ThreatSeverity.WARNING -> 45
                    else -> 0
                }
            )
        )

        return SimRfSecurityMetrics(
            slotIndex = sim.slotIndex,
            operatorName = sim.operatorAlpha,
            isServing = sim.isServing,
            networkGen = sim.networkGen,
            timingAdvance = sim.timingAdvance,
            taDistanceM = taDist,
            btsDistanceM = btsDist,
            taVsBtsDeltaM = taVsBtsDelta,
            taAnomaly = taAnomaly,
            measuredRsrpDbm = sim.rsrp,
            expectedRsrpFromTaDbm = expectedRsrp,
            rsrpVsTaResidualDb = residualDb,
            powerVsTaAnomalyType = powerVsTaAnomalyType,
            rssiDbm = sim.rssi,
            rssiMinusRsrpGapDb = rssiGapDb,
            widebandJammingDetected = widebandJamming,
            sinrDb = sim.sinr,
            tacChangesCount = tacChanges2Min,
            is2gDowngrade = is2g,
            isIsolatedNoNeighbors = isIsolated,
            dbMismatch = dbMismatch,
            pingLossWithStrongRf = pingLossWithStrongRf
        )
    }

    private fun applySimulatedAttackIfActive(
        gnss: GnssState,
        cellular: CellularState,
        timeDiag: TimeSyncDiagnostics,
        attack: SimulatedAttackType
    ): Triple<GnssState, CellularState, TimeSyncDiagnostics> {
        return when (attack) {
            SimulatedAttackType.NONE -> Triple(gnss, cellular, timeDiag)
            SimulatedAttackType.GNSS_SPOOFING -> {
                val spoofedSats = gnss.satellites.map { s ->
                    s.copy(
                        cn0DbHz = 47.2f,
                        bandLabel = if (s.constellationType == 1) "L1 (1575.42 MHz)" else s.bandLabel
                    )
                }
                val spoofGnss = gnss.copy(
                    satellites = spoofedSats,
                    l5InViewCount = 0,
                    l5Count = 0,
                    hasEverSeenDualFreq = true,
                    dopplerSpreadMps = 12.4f,
                    speedKmh = 64.0f,
                    jumpEventsCount = 2
                )
                Triple(spoofGnss, cellular, timeDiag)
            }
            SimulatedAttackType.GNSS_JAMMING -> {
                val jammedSats = gnss.satellites.map { it.copy(cn0DbHz = 16.5f) }
                val jamGnss = gnss.copy(
                    satellites = jammedSats,
                    agcInterferenceScore = 88f,
                    jammingAlert = true,
                    avgAgcDb = (gnss.agcBaselineDb ?: -10.0) - 18.0,
                    agcBaselineDb = gnss.agcBaselineDb ?: -10.0
                )
                Triple(jamGnss, cellular, timeDiag)
            }
            SimulatedAttackType.IMSI_CATCHER_2G -> {
                val rogueSim1 = cellular.sim1.copy(
                    networkGen = "2G GSM",
                    band = "GSM 900",
                    rsrp = -51,
                    sinr = 2.0f,
                    neighbors = emptyList(),
                    timingAdvance = 0,
                    estimatedDistanceM = 0.0
                )
                Triple(gnss, cellular.copy(sim1 = rogueSim1, pingPacketLoss = true), timeDiag)
            }
            SimulatedAttackType.TA_POWER_SPOOF -> {
                val spoofSim1 = cellular.sim1.copy(
                    timingAdvance = 18,
                    estimatedDistanceM = 2812.0,
                    rsrp = -61,
                    rssi = -18,
                    sinr = -5.4f
                )
                Triple(gnss, cellular.copy(sim1 = spoofSim1), timeDiag)
            }
            SimulatedAttackType.TIME_DRIFT_ATTACK -> {
                val now = System.currentTimeMillis()
                val driftedTime = timeDiag.copy(
                    ntpTimeMs = now,
                    ntpServer = "demo.ntp.local",
                    ntpRttMs = 14L,
                    ntpStratum = 2,
                    gnssFixTimeMs = now - 4820L,
                    gnssHardwareUtcMs = now - 4820L,
                    systemTimeMs = now + 3500L
                )
                Triple(gnss, cellular, driftedTime)
            }
        }
    }
}
