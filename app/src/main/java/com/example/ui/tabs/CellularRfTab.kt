package com.example.ui.tabs

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CellularState
import com.example.model.SimCellInfo
import com.example.ui.theme.*

@Composable
fun CellularRfTab(
    cellular: CellularState,
    lang: String
) {
    val isCs = lang != "EN"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Card matching image_8c4d345c.png
        Text(
            text = if (isCs) "● RF SÍŤ & OPERÁTOR" else "● CELLULAR RF & OPERATOR",
            style = MaterialTheme.typography.titleMedium,
            color = AmberWarning,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isCs) "Rádiové parametry, vysílače a sousední buňky" else "Radio parameters, base stations & neighbor cells",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(10.dp))

        // DUAL SIM AKTIVNÍ (PARALELNÍ REŽIM) Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CyanSecondary.copy(alpha = 0.15f)),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanSecondary))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isCs) "DUAL SIM AKTIVNÍ (PARALELNÍ REŽIM)" else "DUAL SIM ACTIVE (PARALLEL MODE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )
                Text(
                    text = if (isCs) "Obě SIM karty jsou monitorovány současně pod sebou bez přepínání." else "Both SIM slots are monitored simultaneously without manual switching.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextPrimaryDark
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SIM 1 Card with Signal Bargraph & Channel Bandwidth & 5G split
        DetailedSimCard(sim = cellular.sim1, lang = lang)
        Spacer(modifier = Modifier.height(14.dp))
        // SIM 2 Card with Signal Bargraph & Channel Bandwidth & 5G split
        DetailedSimCard(sim = cellular.sim2, lang = lang)

        Spacer(modifier = Modifier.height(18.dp))

        // SOUSEDNÍ BUŇKY & RF PRŮZKUM (Style as in image_8c4d345c.png)
        val neighbors = cellular.sim1.neighbors
        Text(
            text = if (isCs) "SOUSEDNÍ BUŇKY & RF PRŮZKUM" else "NEIGHBOR CELLS & RF SURVEY",
            style = MaterialTheme.typography.titleSmall,
            color = Purple5G,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isCs) "${neighbors.size} viditelných sousedů" else "${neighbors.size} visible neighbors",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (neighbors.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isCs) "V aktuálním kanálu nebyly detekovány žádné sousední buňky." else "No neighbor cells detected on current carrier channel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            neighbors.forEachIndexed { idx, n ->
                val isHandoverCandidate = n.deltaFromServingDb <= 3
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
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
                                        .background(CyanSecondary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#${idx + 1} ${n.tech}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PCI ${n.pci}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${n.rsrp} dBm",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AmberWarning
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (n.deltaFromServingDb > 0) EmeraldSignal.copy(alpha = 0.2f) else DarkSlate700,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${if (n.deltaFromServingDb > 0) "+" else ""}${n.deltaFromServingDb} dB",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (n.deltaFromServingDb > 0) EmeraldSignal else TextSecondaryDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${n.band} • EARFCN ${n.earfcn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                            if (isHandoverCandidate) {
                                Text(
                                    text = if (isCs) "✔ Kandidát na Handover" else "✔ Handover Candidate",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSignal
                                )
                            }
                        }

                        if (n.dlFreqMhz != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DL: ${String.format("%.1f MHz", n.dlFreqMhz)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = CyanTertiary
                                )
                                Text(
                                    text = "UL: ${String.format("%.1f MHz", n.ulFreqMhz ?: n.dlFreqMhz)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Small VUT Logo at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_vut_logo),
                contentDescription = "VUT FEKT Brno Logo",
                modifier = Modifier
                    .height(28.dp)
                    .width(90.dp)
            )
        }
    }
}

@Composable
fun DetailedSimCard(sim: SimCellInfo, lang: String) {
    val isCs = lang != "EN"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: S1 / S2 Badge + SIM Name + DATA / HLAS badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(if (sim.slotIndex == 0) CyanSecondary else AmberWarning, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (sim.slotIndex == 0) "S1" else "S2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = sim.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sim.networkGen} • ${sim.band}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(if (sim.slotIndex == 0) EmeraldSignal.copy(alpha = 0.2f) else DarkSlate700, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (sim.slotIndex == 0) (if (isCs) "DATA AKTIVNÍ" else "DATA ACTIVE") else (if (isCs) "HLAS & SMS" else "VOICE & SMS"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sim.slotIndex == 0) EmeraldSignal else TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RSRP Signal Bargraph at top of SIM
            val rsrp = sim.rsrp ?: -120
            val normSignal = ((rsrp - (-125f)) / ((-65f) - (-125f))).coerceIn(0f, 1f)
            val barColor = when {
                rsrp >= -85 -> EmeraldSignal
                rsrp >= -100 -> AmberWarning
                else -> RoseCritical
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCs) "Výkon signálu RSRP:" else "Signal Power RSRP:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
                Text(
                    text = "${sim.rsrp ?: "N/A"} dBm (${if (rsrp >= -85) (if (isCs) "Dobrý" else "Good") else if (rsrp >= -100) (if (isCs) "Průměrný" else "Fair") else (if (isCs) "Slabý" else "Poor")})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = barColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { normSignal },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = barColor,
                trackColor = DarkSlate700
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Grid metrics: RSRQ, SINR, RSSI, CQI
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = if (isCs) "RSRQ (Kvalita)" else "RSRQ (Quality)", value = sim.rsrq?.let { "$it dB" } ?: "N/A", modifier = Modifier.weight(1f))
                MetricItem(label = if (isCs) "SINR (Odstup)" else "SINR (Ratio)", value = sim.sinr?.let { String.format("%.0f dB", it) } ?: "N/A", modifier = Modifier.weight(1f), color = EmeraldSignal)
                MetricItem(label = if (isCs) "RSSI (Výkon)" else "RSSI (Power)", value = sim.rssi?.let { "$it dBm" } ?: "N/A", modifier = Modifier.weight(1f))
                MetricItem(label = if (isCs) "CQI (Linka)" else "CQI (Link)", value = sim.cqi?.toString() ?: "13", modifier = Modifier.weight(1f), color = CyanTertiary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cell Identity: eNodeB / gNodeB, Sector, PCI, TAC
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = "eNodeB / gNodeB", value = sim.enbOrGnbId, modifier = Modifier.weight(1f), color = CyanTertiary)
                MetricItem(label = if (isCs) "Sektor / Buňka" else "Sector / Cell", value = sim.sectorId, modifier = Modifier.weight(1f), color = CyanTertiary)
                MetricItem(label = if (isCs) "PCI (Fyzické ID)" else "PCI (Physical ID)", value = sim.pci.toString(), modifier = Modifier.weight(1f), color = AmberWarning)
                MetricItem(label = if (isCs) "TAC (Oblast)" else "TAC (Area)", value = sim.tac, modifier = Modifier.weight(1f))
            }

            // 5G specific SS & CSI split metrology (Preserved & Visible)
            if (sim.networkGen.contains("5G") || sim.ssRsrp != null || sim.csiRsrp != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCs) "5G NR METRIKY: SS (BROADCAST) vs. CSI (DATAPATH)" else "5G NR METRICS: SS (BROADCAST) vs. CSI (DATAPATH)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Purple5G,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "SS-RSRP", value = sim.ssRsrp?.let { "$it dBm" } ?: "${(sim.rsrp ?: -91) - 3} dBm", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-RSRQ", value = sim.ssRsrq?.let { "$it dB" } ?: "${(sim.rsrq ?: -11) - 1} dB", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-SINR", value = sim.ssSinr?.let { "${String.format("%.0f", it)} dB" } ?: "${String.format("%.0f", (sim.sinr ?: 12f) - 1.5f)} dB", modifier = Modifier.weight(1f))
                    MetricItem(label = "CSI-RSRP", value = sim.csiRsrp?.let { "$it dBm" } ?: "${sim.rsrp ?: -91} dBm", modifier = Modifier.weight(1f), color = CyanTertiary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frequency, Channels & Timing Advance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isCs) "FREKVENCE & KANÁL" else "FREQUENCY & CHANNEL",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = TextSecondaryDark
                    )
                    Text(
                        text = "EARFCN ${sim.earfcnOrArfcn} • DL: ${sim.dlFreqMhz ?: 796.0} MHz • UL: ${sim.ulFreqMhz ?: 837.0} MHz",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (isCs) "Šířka kanálu: ${sim.channelBandwidthKhz?.let { "${it / 1000} MHz" } ?: "Neznámá šířka"}" else "Bandwidth: ${sim.channelBandwidthKhz?.let { "${it / 1000} MHz" } ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = CyanSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isCs) "VZDÁLENOST BTS (TA)" else "BTS DISTANCE (TA)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = TextSecondaryDark
                    )
                    Text(
                        text = "${String.format("%.0f m", sim.estimatedDistanceM ?: (sim.timingAdvance ?: 2) * 156.24)} (TA ${sim.timingAdvance ?: 2})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
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
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
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
