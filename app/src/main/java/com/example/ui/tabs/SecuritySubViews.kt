package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bts.BtsGeoUtils
import com.example.model.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun TimeForensicsSubView(
    timeDiag: TimeSyncDiagnostics,
    isCs: Boolean,
    onSyncNtpNow: () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formatMs: (Long?) -> String = { ms ->
        if (ms != null && ms > 0L) timeFmt.format(Date(ms)) else "—"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800),
        border = BorderStroke(1.dp, CyanSecondary.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyanTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isCs) "SROVNÁNÍ PŘESNÉHO ČASU (UTC)" else "MULTI-SOURCE UTC CLOCK AUDIT",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        Text(
                            text = if (isCs) "GNSS satelity vs. Mobilní síť (NITZ) vs. Internet NTP" else "GNSS satellites vs. Cellular NITZ vs. Internet NTP",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextSecondaryDark
                        )
                    }
                }
                Button(
                    onClick = onSyncNtpNow,
                    enabled = !timeDiag.isNtpSyncing,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sync_ntp_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (timeDiag.isNtpSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = DarkSlate900
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = DarkSlate900,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCs) "Sync NTP" else "Sync NTP",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkSlate900
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val ntpAgeSec = timeDiag.lastNtpSyncEpochMs?.let { syncMs ->
                ((timeDiag.systemTimeMs - syncMs) / 1000L).coerceAtLeast(0L)
            }
            ClockSourceRow(
                label = if (isCs) "1. Internet NTP (${timeDiag.ntpServer})" else "1. Internet NTP (${timeDiag.ntpServer})",
                timeText = formatMs(timeDiag.ntpTimeMs),
                subText = when {
                    timeDiag.ntpRttMs != null -> buildString {
                        append("Stratum ${timeDiag.ntpStratum ?: "?"} • RTT ${timeDiag.ntpRttMs} ms")
                        if (ntpAgeSec != null) {
                            append(if (isCs) " • dotaz před ${ntpAgeSec}s (auto 60s)" else " • polled ${ntpAgeSec}s ago (auto 60s)")
                        }
                    }
                    timeDiag.ntpError != null -> timeDiag.ntpError
                    else -> if (isCs) "Čekání na odpověď UDP:123..." else "Awaiting UDP:123 response..."
                },
                accentColor = CyanTertiary
            )

            Spacer(modifier = Modifier.height(8.dp))

            ClockSourceRow(
                label = if (isCs) "2. Mobilní síť / Systém (NITZ)" else "2. Mobile Network / System (NITZ)",
                timeText = formatMs(timeDiag.systemTimeMs),
                subText = if (isCs) {
                    "OS / NITZ ze sítě (operátor posílá při Attach / změně TA)"
                } else {
                    "OS / carrier NITZ clock (pushed on Attach / TA change)"
                },
                accentColor = AmberWarning
            )

            Spacer(modifier = Modifier.height(8.dp))

            val gnssMainTime = timeDiag.gnssHardwareUtcMs ?: timeDiag.gnssFixTimeMs
            ClockSourceRow(
                label = if (isCs) "3. GNSS Satelitní čas (GPS/Galileo)" else "3. GNSS Satellite Time (GPS/Galileo)",
                timeText = formatMs(gnssMainTime),
                subText = buildString {
                    append(if (timeDiag.gnssHardwareUtcMs != null) "HW GnssClock UTC" else "Fix UTC")
                    if (timeDiag.gnssFixTimeMs != null && timeDiag.gnssHardwareUtcMs != null) {
                        append(" • Fix: ${formatMs(timeDiag.gnssFixTimeMs)}")
                    }
                },
                accentColor = EmeraldSignal
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkSlate700)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isCs) "ODCHYLKY ČASOVÝCH ZDROJŮ (Δt)" else "CLOCK SKEW DELTAS (Δt)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeltaTile(
                    modifier = Modifier.weight(1f),
                    title = "GNSS − NTP",
                    deltaMs = timeDiag.gnssMinusNtpMs,
                    warnThresholdMs = 800L,
                    critThresholdMs = 2000L
                )
                DeltaTile(
                    modifier = Modifier.weight(1f),
                    title = "NITZ − NTP",
                    deltaMs = timeDiag.systemMinusNtpMs,
                    warnThresholdMs = 1500L,
                    critThresholdMs = 3000L
                )
                DeltaTile(
                    modifier = Modifier.weight(1f),
                    title = "GNSS − NITZ",
                    deltaMs = timeDiag.gnssMinusSystemMs,
                    warnThresholdMs = 800L,
                    critThresholdMs = 2500L
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isCs) {
                    "Limity (zelená): GNSS−NTP ≤ 800 ms • NITZ−NTP ≤ 1500 ms (krok NITZ) • GNSS−NITZ ≤ 800 ms"
                } else {
                    "Green limits: GNSS−NTP ≤ 800 ms • NITZ−NTP ≤ 1500 ms (NITZ step) • GNSS−NITZ ≤ 800 ms"
                },
                fontSize = 9.sp,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isCs) {
                    "NTP se dotazuje přes UDP:123 (auto každých 60 s) a mezi dotazy běží nad elapsedRealtime() bez změny hodin OS. NITZ síť vysílá jen při událostech (Attach/TA/změna 4G↔5G) s krokem 1 s."
                } else {
                    "NTP queries UDP:123 every 60s and propagates via monotonic elapsedRealtime() without altering OS time. Carrier NITZ updates on Attach/TA/RAT events at 1s steps."
                },
                fontSize = 9.sp,
                lineHeight = 12.sp,
                color = TextSecondaryDark.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DarkSlate900,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = if (isCs) "HARDWAROVÝ OSCILÁTOR GNSS (GnssClock)" else "GNSS HARDWARE CLOCK (GnssClock)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Purple5G
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isCs) "Diskontinuity: ${timeDiag.clockDiscontinuityCount}" else "Discontinuities: ${timeDiag.clockDiscontinuityCount}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Drift: ${timeDiag.clockDriftNanosPerSec?.let { String.format(Locale.US, "%.1f ns/s", it) } ?: "—"}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyanTertiary
                        )
                    }
                    timeDiag.clockBiasUncertaintyNanos?.let { unc ->
                        Text(
                            text = if (isCs) "Nejistota biasu: ${String.format(Locale.US, "%.0f", unc)} ns" else "Bias uncertainty: ${String.format(Locale.US, "%.0f", unc)} ns",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockSourceRow(
    label: String,
    timeText: String,
    subText: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSlate900,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subText,
                    fontSize = 10.sp,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimaryDark
            )
        }
    }
}

@Composable
private fun DeltaTile(
    modifier: Modifier = Modifier,
    title: String,
    deltaMs: Long?,
    warnThresholdMs: Long,
    critThresholdMs: Long
) {
    val absDelta = deltaMs?.let { abs(it) }
    val color = when {
        absDelta == null -> TextSecondaryDark
        absDelta > critThresholdMs -> RoseCritical
        absDelta > warnThresholdMs -> AmberWarning
        else -> EmeraldSignal
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = deltaMs?.let { "${if (it >= 0) "+" else ""}${it} ms" } ?: "—",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PhysicsWorkbenchSubView(
    gnssMetrics: GnssSecurityMetrics,
    sim1: SimRfSecurityMetrics,
    sim2: SimRfSecurityMetrics,
    isCs: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SimPhysicsCard(sim = sim1, accent = CyanTertiary, isCs = isCs)
        if (sim2.isServing) {
            SimPhysicsCard(sim = sim2, accent = AmberWarning, isCs = isCs)
        }
        GnssPhysicsCard(m = gnssMetrics, isCs = isCs)
    }
}

@Composable
private fun SimPhysicsCard(
    sim: SimRfSecurityMetrics,
    accent: Color,
    isCs: Boolean
) {
    val hasIssue = sim.taAnomaly || sim.powerVsTaAnomalyType != null || sim.widebandJammingDetected ||
        sim.is2gDowngrade || sim.isIsolatedNoNeighbors || sim.dbMismatch || sim.pingLossWithStrongRf ||
        sim.suspiciousCellOrBwIssue != null || sim.cqiPoorWithStrongRf || sim.stationaryTaDrop ||
        ((sim.fsplExcessDb ?: -99) > 12)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800),
        border = BorderStroke(1.dp, if (hasIssue) RoseCritical.copy(alpha = 0.6f) else accent.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SIM ${sim.slotIndex + 1}: ${sim.operatorName} (${sim.networkGen})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .background(
                            (if (hasIssue) RoseCritical else EmeraldSignal).copy(alpha = 0.2f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (hasIssue) (if (isCs) "ANOMÁLIE" else "ANOMALY") else "OK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasIssue) RoseCritical else EmeraldSignal
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1. TA vs BTS Distance
            PhysicsMetricRow(
                label = if (isCs) "Timing Advance (TA) vzdálenost" else "Timing Advance (TA) range",
                value = sim.taDistanceM?.let { "TA=${sim.timingAdvance} (${BtsGeoUtils.formatDistance(it)})" } ?: "N/A",
                valueColor = TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "Reálná GPS vzdálenost k BTS" else "Real GPS-to-BTS distance",
                value = sim.btsDistanceM?.let { BtsGeoUtils.formatDistance(it) } ?: "N/A",
                valueColor = TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "Rozdíl ΔTA = TA − BTS" else "Delta ΔTA = TA − BTS",
                value = sim.taVsBtsDeltaM?.let { "${if (it >= 0) "+" else ""}${it.toInt()} m" } ?: "—",
                valueColor = if (sim.taAnomaly) RoseCritical else EmeraldSignal
            )

            HorizontalDivider(color = DarkSlate700, modifier = Modifier.padding(vertical = 8.dp))

            // 2. Power vs TA Path-Loss
            PhysicsMetricRow(
                label = if (isCs) "Změřený RSRP vs. model z TA" else "Measured RSRP vs. TA model",
                value = buildString {
                    append(sim.measuredRsrpDbm?.let { "$it dBm" } ?: "—")
                    append(" / ")
                    append(sim.expectedRsrpFromTaDbm?.let { "$it dBm" } ?: "—")
                },
                valueColor = if (sim.powerVsTaAnomalyType != null) RoseCritical else CyanTertiary
            )
            PhysicsMetricRow(
                label = if (isCs) "Odchylka výkonu (Reziduum)" else "Power Residual vs. Path Loss",
                value = sim.rsrpVsTaResidualDb?.let { "${if (it >= 0) "+" else ""}$it dB" } ?: "—",
                valueColor = if (sim.powerVsTaAnomalyType != null) AmberWarning else EmeraldSignal
            )

            HorizontalDivider(color = DarkSlate700, modifier = Modifier.padding(vertical = 8.dp))

            // 3. Wideband Jamming (RSSI - RSRP)
            PhysicsMetricRow(
                label = if (isCs) "Širokopásmový rozdíl (RSSI − RSRP)" else "Wideband Noise Gap (RSSI − RSRP)",
                value = sim.rssiMinusRsrpGapDb?.let { "$it dB (práh 38 dB)" } ?: "—",
                valueColor = if (sim.widebandJammingDetected) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = "SINR / TAC změny (2 min)",
                value = "${sim.sinrDb?.let { String.format(Locale.US, "%.1f dB", it) } ?: "—"} / ${sim.tacChangesCount}×",
                valueColor = if ((sim.sinrDb ?: 10f) < -3f || sim.tacChangesCount >= 3) AmberWarning else TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "Strop FSPL (Δ proti max)" else "FSPL Ceiling (Δ vs. max)",
                value = sim.fsplMaxExpectedDbm?.let {
                    "$it dBm (${sim.fsplExcessDb?.let { d -> "${if (d >= 0) "+" else ""}$d dB" } ?: "—"})"
                } ?: "—",
                valueColor = if ((sim.fsplExcessDb ?: -99) > 12) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Test ID / SDR BW / CQI" else "Test ID / SDR BW / CQI",
                value = sim.suspiciousCellOrBwIssue ?: (if (sim.cqiPoorWithStrongRf) "Low CQI @ Strong RSRP" else "OK"),
                valueColor = if (sim.suspiciousCellOrBwIssue != null || sim.cqiPoorWithStrongRf) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Skok TA při klidu IMU" else "Stationary IMU TA Drop",
                value = if (sim.stationaryTaDrop) (if (isCs) "DETEKOVÁN" else "DETECTED") else "OK",
                valueColor = if (sim.stationaryTaDrop) RoseCritical else EmeraldSignal
            )
        }
    }
}

@Composable
private fun GnssPhysicsCard(m: GnssSecurityMetrics, isCs: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800),
        border = BorderStroke(1.dp, Purple5G.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = if (isCs) "FYZIKA SIGNÁLU GNSS & FÚZE SENZORŮ" else "GNSS SIGNAL PHYSICS & SENSOR FUSION",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Purple5G
            )
            Spacer(modifier = Modifier.height(10.dp))

            PhysicsMetricRow(
                label = if (isCs) "Průměr C/N0 & rozptyl (σ)" else "Mean C/N0 & std-dev (σ)",
                value = if (m.avgCn0DbHz != null && m.cn0StdDev != null) {
                    "${String.format(Locale.US, "%.1f", m.avgCn0DbHz)} dB-Hz (σ=${String.format(Locale.US, "%.2f", m.cn0StdDev)})"
                } else "N/A",
                valueColor = if (m.elevationCn0Inversion) AmberWarning else TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "Elevace: Zenit (≥45°) vs. Obzor (<20°)" else "Elevation: Zenith (≥45°) vs. Low (<20°)",
                value = "${m.highElevAvgCn0?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} / ${m.lowElevAvgCn0?.let { String.format(Locale.US, "%.1f", it) } ?: "—"} dB-Hz",
                valueColor = if (m.elevationCn0Inversion) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Dopplerův orbitální rozptyl (σ_v)" else "Orbital Doppler Spread (σ_v)",
                value = m.dopplerSpreadMps?.let { "${String.format(Locale.US, "%.1f", it)} m/s" } ?: "N/A",
                valueColor = if ((m.dopplerSpreadMps ?: 200f) < 45f) AmberWarning else CyanTertiary
            )
            PhysicsMetricRow(
                label = if (isCs) "AGC zisk (Aktuální / Baseline)" else "AGC Level (Live / Baseline)",
                value = if (m.avgAgcDb != null) {
                    "${String.format(Locale.US, "%.1f", m.avgAgcDb)} / ${String.format(Locale.US, "%.1f", m.agcBaselineDb ?: m.avgAgcDb)} dB"
                } else "N/A",
                valueColor = if (m.agcInterferenceScore >= 65f) RoseCritical else TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "Multipath indikátor / Skoky" else "Multipath count / Teleport jumps",
                value = "${m.multipathCount} sat. / ${m.jumpEventsCount}×",
                valueColor = if (m.jumpEventsCount > 0) RoseCritical else TextPrimaryDark
            )
            PhysicsMetricRow(
                label = if (isCs) "IMU klidový test / Δh Baro" else "IMU stationary / Δh Baro",
                value = "${m.imuStationarySeconds}s / ${m.gnssVsBaroAltJumpM?.let { String.format(Locale.US, "%.1f m", it) } ?: "—"}",
                valueColor = if (m.gnssVsImuMismatch) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Ostatní konstelace / ΔNMEA" else "Non-GPS sats / ΔNMEA",
                value = "${m.nonGpsSatCount} sat. / ${m.nmeaVsLocationDistM?.let { String.format(Locale.US, "%.1f m", it) } ?: "—"}",
                valueColor = if (m.constellationDrop || (m.nmeaVsLocationDistM ?: 0.0) > 35.0) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Nosná ADR / Opačný Doppler" else "Carrier ADR / Opp. Doppler",
                value = "${m.adrValidRatioPct?.let { "${it.toInt()}%" } ?: "—"} / ${m.dopplerElevMismatchCount}×${if (m.frozenConstellation) " FROZEN" else ""}",
                valueColor = if (m.frozenConstellation || m.dopplerElevMismatchCount >= 3 || (m.cn0TakeoverJumpDb ?: 0f) >= 9f) RoseCritical else CyanTertiary
            )
            PhysicsMetricRow(
                label = if (isCs) "Gyroskop ΔYaw vs. GNSS" else "Gyro ΔYaw vs. GNSS Bearing",
                value = "${m.gyroYawDeltaDeg?.let { "${it.toInt()}°" } ?: "—"} / ${m.gnssBearingDeltaDeg?.let { "${it.toInt()}°" } ?: "—"}",
                valueColor = if (m.gyroTurnMismatch) RoseCritical else EmeraldSignal
            )
            PhysicsMetricRow(
                label = if (isCs) "Magnetometr |B| / Kroky (6s)" else "Magnetometer |B| / Steps (6s)",
                value = "${m.magneticFieldUt?.let { String.format(Locale.US, "%.1f µT", it) } ?: "—"} / ${m.stepsLast6s}",
                valueColor = if (m.walkingVsGnssMismatch || ((m.magneticFieldUt ?: 48f) !in 18f..95f)) AmberWarning else TextPrimaryDark
            )
        }
    }
}

@Composable
private fun PhysicsMetricRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondaryDark,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
        )
    }
}

@Composable
fun IncidentTimelineSubView(
    incidents: List<SecurityIncident>,
    isCs: Boolean,
    onClearIncidents: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCs) "ZACHYCENÉ BEZPEČNOSTNÍ UDÁLOSTI (${incidents.size})" else "RECORDED SECURITY INCIDENTS (${incidents.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark
            )
            if (incidents.isNotEmpty()) {
                TextButton(
                    onClick = onClearIncidents,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, tint = RoseCritical, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isCs) "Vymazat" else "Clear", fontSize = 11.sp, color = RoseCritical)
                }
            }
        }

        if (incidents.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = EmeraldSignal,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isCs) "Žádné bezpečnostní incidenty" else "No Security Incidents Logged",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isCs) {
                            "Jakákoli detekovaná anomálie GNSS, rušení, odchylka TA/RSRP, IMSI Catcher nebo časový skok se automaticky zapíše do tohoto deníku."
                        } else {
                            "Any detected GNSS spoofing, jamming, TA/RSRP physics anomaly, IMSI Catcher, or clock skew will be logged here automatically."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }
        } else {
            incidents.forEach { inc ->
                val sevColor = if (inc.severity == ThreatSeverity.CRITICAL) RoseCritical else AmberWarning
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = sevColor.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, sevColor.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(sevColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = inc.severity.name,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = DarkSlate900
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = inc.category.name.replace('_', ' '),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = sevColor
                                )
                            }
                            Text(
                                text = fmt.format(Date(inc.timestampMs)),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondaryDark
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isCs) inc.titleCs else inc.titleEn,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isCs) inc.detailCs else inc.detailEn,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }
    }
}
