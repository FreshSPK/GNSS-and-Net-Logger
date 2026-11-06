package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CellularState
import com.example.model.EnvironmentState
import com.example.model.GnssState
import com.example.ui.i18n.Strings
import com.example.ui.theme.*

@Composable
fun OverviewTab(
    gnss: GnssState,
    cellular: CellularState,
    env: EnvironmentState,
    isLogging: Boolean,
    loggedPoints: Int,
    lang: String,
    onToggleLogging: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Logging Control Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLogging) EmeraldSignal.copy(alpha = 0.15f) else DarkSlate800
            ),
            border = if (isLogging) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSignal)) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isLogging) EmeraldSignal else RoseCritical, RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLogging) Strings.get("logging_active", lang) else Strings.get("logging_idle", lang),
                            fontWeight = FontWeight.Bold,
                            color = if (isLogging) EmeraldSignal else TextSecondaryDark,
                            fontSize = 14.sp
                        )
                    }
                    if (isLogging) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Points logged: $loggedPoints",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onToggleLogging,
                    modifier = Modifier.testTag("toggle_logging_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLogging) RoseCritical else EmeraldSignal
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isLogging) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLogging) Strings.get("btn_stop_log", lang) else Strings.get("btn_start_log", lang),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // HUD Metric Grid
        Text(
            text = Strings.get("live_telemetry_hud", lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            HudMetricCard(
                title = Strings.get("speed", lang),
                value = String.format("%.1f", gnss.speedKmh),
                unit = "km/h",
                subtext = String.format("%.1f m/s", gnss.speedMs),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            HudMetricCard(
                title = Strings.get("altitude", lang),
                value = String.format("%.0f", gnss.altitudeMsl),
                unit = "m MSL",
                subtext = "WGS84: ${String.format("%.0f", gnss.altitudeWgs84)}m",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            HudMetricCard(
                title = Strings.get("accuracy", lang),
                value = String.format("±%.1f", gnss.horizontalAccuracyM),
                unit = "m",
                subtext = "V: ±${String.format("%.1f", gnss.verticalAccuracyM)}m",
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            HudMetricCard(
                title = Strings.get("satellites", lang),
                value = "${gnss.satellitesUsedInFix} / ${gnss.satellitesInView}",
                unit = "used",
                subtext = "PDOP: ${String.format("%.1f", gnss.pdop)} | HDOP: ${String.format("%.1f", gnss.hdop)}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            HudMetricCard(
                title = Strings.get("dual_freq_ratio", lang),
                value = String.format("%.0f", gnss.dualFrequencyRatioPct),
                unit = "%",
                subtext = "L1/L5 Multi-Band",
                accentColor = BlueSatellite,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            HudMetricCard(
                title = Strings.get("ping_latency", lang),
                value = cellular.pingRttMs?.toString() ?: "—",
                unit = "ms",
                subtext = if (cellular.pingPacketLoss) "Packet loss!" else "Google DNS RTT",
                accentColor = if (cellular.pingPacketLoss) RoseCritical else EmeraldSignal,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dual-SIM Quick Glance
        Text(
            text = "Active Cellular Links",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        SimSummaryCard(sim = cellular.sim1)
        Spacer(modifier = Modifier.height(8.dp))
        SimSummaryCard(sim = cellular.sim2)

        Spacer(modifier = Modifier.height(16.dp))

        // Barometric Sensor Card
        env.pressureHpa?.let { p ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Strings.get("pressure", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("%.2f hPa", p),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanTertiary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Barometric Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format("Rel Alt: %.1f m", env.relativeAltitudeM ?: 0f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HudMetricCard(
    title: String,
    value: String,
    unit: String,
    subtext: String,
    accentColor: Color = CyanTertiary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SimSummaryCard(sim: com.example.model.SimCellInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sim.displayName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(if (sim.networkGen.contains("5G")) Purple5G else CyanSecondary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sim.networkGen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${sim.band} | PCI: ${sim.pci} | EARFCN: ${sim.earfcnOrArfcn}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (sim.rsrp != null) "${sim.rsrp} dBm" else "N/A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        sim.rsrp == null -> Color.Gray
                        sim.rsrp!! >= -85 -> EmeraldSignal
                        sim.rsrp!! >= -100 -> AmberWarning
                        else -> RoseCritical
                    }
                )
                Text(
                    text = if (sim.sinr != null) "SINR: ${String.format("%.1f", sim.sinr)} dB" else "SINR: N/A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
