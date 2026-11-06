package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CellularState
import com.example.model.SimCellInfo
import com.example.ui.i18n.Strings
import com.example.ui.theme.*

@Composable
fun CellularRfTab(
    cellular: CellularState,
    lang: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Dual-SIM Stacked Monitor
        Text(
            text = "Dual-SIM Cellular RF Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        DetailedSimCard(sim = cellular.sim1, lang = lang)
        Spacer(modifier = Modifier.height(14.dp))
        DetailedSimCard(sim = cellular.sim2, lang = lang)

        Spacer(modifier = Modifier.height(16.dp))

        // Serving & Neighbor Cells with Delta
        Text(
            text = Strings.get("neighbors_title", lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (cellular.sim1.neighbors.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "No neighbor cells detected on current carrier channel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            cellular.sim1.neighbors.forEach { n ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${n.tech} PCI: ${n.pci}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = n.band,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "EARFCN/NR: ${n.earfcn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${n.rsrp} dBm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyanTertiary
                            )
                            Text(
                                text = "Δ from serving: +${n.deltaFromServingDb} dB",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberWarning
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedSimCard(sim: SimCellInfo, lang: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sim.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (sim.networkGen.contains("5G")) Purple5G else CyanSecondary,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sim.networkGen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = sim.operatorAlpha,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Signal Metrics
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(
                    label = "RSRP",
                    value = sim.rsrp?.let { "$it dBm" } ?: "N/A",
                    modifier = Modifier.weight(1f),
                    color = when {
                        sim.rsrp == null -> TextSecondaryDark
                        sim.rsrp >= -85 -> EmeraldSignal
                        sim.rsrp >= -100 -> AmberWarning
                        else -> RoseCritical
                    }
                )
                MetricItem(
                    label = "RSRQ",
                    value = sim.rsrq?.let { "$it dB" } ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
                MetricItem(
                    label = "SINR",
                    value = sim.sinr?.let { String.format("%.1f dB", it) } ?: "N/A",
                    modifier = Modifier.weight(1f),
                    color = if (sim.sinr != null && sim.sinr > 10f) EmeraldSignal else TextPrimaryDark
                )
                MetricItem(
                    label = "RSSI",
                    value = sim.rssi?.let { "$it dBm" } ?: "N/A",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decoded Cell Identity
            Text(
                text = "Cell Identity & Decoded IDs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = "TAC / LAC", value = sim.tac, modifier = Modifier.weight(1f))
                MetricItem(label = "Cell ID (CI)", value = sim.ci, modifier = Modifier.weight(1f))
                MetricItem(label = "eNB / gNB", value = sim.enbOrGnbId, modifier = Modifier.weight(1f))
                MetricItem(label = "Sector", value = sim.sectorId, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = "PCI", value = sim.pci.toString(), modifier = Modifier.weight(1f))
                MetricItem(label = "ARFCN", value = sim.earfcnOrArfcn.toString(), modifier = Modifier.weight(1f))
                MetricItem(label = "Band", value = sim.band, modifier = Modifier.weight(2f))
            }

            // 5G specific SS & CSI metrics
            if (sim.networkGen.contains("5G") && (sim.ssRsrp != null || sim.csiRsrp != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "5G NR Split Metrology (SS vs. CSI)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Purple5G,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "SS-RSRP", value = sim.ssRsrp?.let { "$it dBm" } ?: "N/A", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-RSRQ", value = sim.ssRsrq?.let { "$it dB" } ?: "N/A", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-SINR", value = sim.ssSinr?.let { "${String.format("%.1f", it)} dB" } ?: "N/A", modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "CSI-RSRP", value = sim.csiRsrp?.let { "$it dBm" } ?: "N/A", modifier = Modifier.weight(1f))
                    MetricItem(label = "CSI-RSRQ", value = sim.csiRsrq?.let { "$it dB" } ?: "N/A", modifier = Modifier.weight(1f))
                    MetricItem(label = "CSI-SINR", value = sim.csiSinr?.let { "${String.format("%.1f", it)} dB" } ?: "N/A", modifier = Modifier.weight(1f))
                }
            }

            // Timing Advance & Physical Distance
            sim.timingAdvance?.let { ta ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Timing Advance: $ta steps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Est. BTS Distance: ${String.format("%.0f m", sim.estimatedDistanceM ?: (ta * 78.12))}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimaryDark
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextSecondaryDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}
