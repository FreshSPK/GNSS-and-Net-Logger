package com.example.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GnssState
import com.example.model.SatelliteInfo
import com.example.ui.components.SkyPlotView
import com.example.ui.theme.*

enum class GnssSubTab {
    SKYPLOT_RADAR,
    SIGNALY_CN0,
    PODROBNA_TABULKA
}

@Composable
fun GnssTab(
    gnss: GnssState,
    lang: String
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "L5", "GPS", "GALILEO", "GLONASS", "BEIDOU"
    var activeSubTab by remember { mutableStateOf(GnssSubTab.SKYPLOT_RADAR) }
    var showDopInfoDialog by remember { mutableStateOf(false) }

    val filteredSatellites = remember(gnss.satellites, selectedFilter) {
        when (selectedFilter) {
            "L5" -> gnss.satellites.filter { it.bandLabel.contains("L5") || it.bandLabel.contains("E5") || it.bandLabel.contains("B2") }
            "GPS" -> gnss.satellites.filter { it.constellationName.equals("GPS", ignoreCase = true) }
            "GALILEO" -> gnss.satellites.filter { it.constellationName.equals("Galileo", ignoreCase = true) }
            "GLONASS" -> gnss.satellites.filter { it.constellationName.equals("GLONASS", ignoreCase = true) }
            "BEIDOU" -> gnss.satellites.filter { it.constellationName.equals("BeiDou", ignoreCase = true) }
            else -> gnss.satellites
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header matching image_ca27dc1f.png
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GNSS SATELITY A PÁSMA",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${gnss.satellitesUsedInFix} ",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "/ ${gnss.satellitesInView} v dosahu",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryDark
                    )
                }
            }

            // Dual-band Badge Box
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = EmeraldSignal.copy(alpha = 0.15f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSignal))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "DUAL-BAND L1+L5",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
                    )
                    Text(
                        text = "L1: ${gnss.l1Count} | L5: ${gnss.l5Count}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Constellation / Band Filter Pills (Všechny, Pouze L5/E5a, GPS, Galileo, GLONASS, BeiDou)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("Všechny (${gnss.satellitesInView})") }
            )
            FilterChip(
                selected = selectedFilter == "L5",
                onClick = { selectedFilter = "L5" },
                label = { Text("Pouze L5/E5a (${gnss.l5Count})") }
            )
            FilterChip(
                selected = selectedFilter == "GPS",
                onClick = { selectedFilter = "GPS" },
                label = { Text("GPS") }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "GALILEO",
                onClick = { selectedFilter = "GALILEO" },
                label = { Text("Galileo") }
            )
            FilterChip(
                selected = selectedFilter == "GLONASS",
                onClick = { selectedFilter = "GLONASS" },
                label = { Text("GLONASS") }
            )
            FilterChip(
                selected = selectedFilter == "BEIDOU",
                onClick = { selectedFilter = "BEIDOU" },
                label = { Text("BeiDou") }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subtabs: Skyplot Radar | Signály C/N0 | Podrobná tabulka
        TabRow(
            selectedTabIndex = activeSubTab.ordinal,
            containerColor = DarkSlate800,
            contentColor = CyanTertiary
        ) {
            Tab(
                selected = activeSubTab == GnssSubTab.SKYPLOT_RADAR,
                onClick = { activeSubTab = GnssSubTab.SKYPLOT_RADAR },
                text = { Text("Skyplot Radar", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeSubTab == GnssSubTab.SIGNALY_CN0,
                onClick = { activeSubTab = GnssSubTab.SIGNALY_CN0 },
                text = { Text("Signály C/N0", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
            Tab(
                selected = activeSubTab == GnssSubTab.PODROBNA_TABULKA,
                onClick = { activeSubTab = GnssSubTab.PODROBNA_TABULKA },
                text = { Text("Podrobná tabulka", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (activeSubTab) {
            GnssSubTab.SKYPLOT_RADAR -> {
                SkyPlotView(satellites = filteredSatellites)

                Spacer(modifier = Modifier.height(12.dp))

                // Explanatory card matching image_ca27dc1f.png
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "VYSVĚTLIVKY • BÍLÁ ZÁŘE = VE FIXU • ZELENÝ KRUH = PÁSMO L5/E5a",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "● GPS (L1/L5)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = CyanTertiary)
                            Text(text = "● Galileo (E1/E5)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = EmeraldSignal)
                            Text(text = "● GLONASS (G1)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = AmberWarning)
                            Text(text = "● BeiDou (B1/B2)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Purple5G)
                        }
                    }
                }
            }
            GnssSubTab.SIGNALY_CN0 -> {
                // Bar charts of C/N0 for each satellite
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Úrovně signálu C/N0 (dB-Hz)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        filteredSatellites.forEach { sat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${sat.constellationName} #${sat.svid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(90.dp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (sat.usedInFix) TextPrimaryDark else Color.Gray
                                )
                                LinearProgressIndicator(
                                    progress = { (sat.cn0DbHz / 50f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp),
                                    color = if (sat.cn0DbHz >= 35f) EmeraldSignal else if (sat.cn0DbHz >= 25f) CyanTertiary else AmberWarning,
                                    trackColor = DarkSlate700
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = String.format("%.0f dB", sat.cn0DbHz),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(44.dp),
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }
            }
            GnssSubTab.PODROBNA_TABULKA -> {
                // Full detailed satellite list table
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "SVID", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                            Text(text = "Konstelace", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.5f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                            Text(text = "Pásmo", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                            Text(text = "C/N0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                            Text(text = "El/Az", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.2f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                            Text(text = "Fix", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.8f), color = CyanTertiary, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = DarkSlate700, modifier = Modifier.padding(vertical = 6.dp))

                        filteredSatellites.forEach { sat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "#${sat.svid}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text(text = sat.constellationName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.5f))
                                Text(text = sat.bandLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f), color = if (sat.bandLabel.contains("L5") || sat.bandLabel.contains("E5")) EmeraldSignal else TextPrimaryDark)
                                Text(text = String.format("%.0f", sat.cn0DbHz), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                                Text(text = "${sat.elevationDegrees.toInt()}°/${sat.azimuthDegrees.toInt()}°", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.2f))
                                Text(
                                    text = if (sat.usedInFix) "ANO" else "NE",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.8f),
                                    fontWeight = FontWeight.Bold,
                                    color = if (sat.usedInFix) EmeraldSignal else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DOP Card with Info Explanation Dialog Trigger
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Faktory degradace přesnosti (DOP)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showDopInfoDialog = true }) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = "DOP Vysvětlení", tint = CyanTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "HDOP", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                        Text(text = String.format("%.2f", gnss.hdop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = EmeraldSignal)
                        Text(text = "Horizontální", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "VDOP", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                        Text(text = String.format("%.2f", gnss.vdop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                        Text(text = "Vertikální", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "PDOP", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
                        Text(text = String.format("%.2f", gnss.pdop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyanTertiary)
                        Text(text = "Prostorový 3D", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

    if (showDopInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDopInfoDialog = false },
            title = { Text(text = "Vysvětlení hodnot DOP (Dilution of Precision)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Faktory DOP vyjadřují geometrické uspořádání viditelných satelitů vůči přijímači:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "• < 1.0 (Ideální): Mimořádná přesnost, satelity rovnoměrně rozprostřeny.", style = MaterialTheme.typography.bodySmall)
                    Text(text = "• 1.0 - 2.0 (Výborné): Běžná kvalita pro otevřené prostranství.", style = MaterialTheme.typography.bodySmall)
                    Text(text = "• 2.0 - 5.0 (Dobré): Plně vyhovující pro navigaci a měření v terénu.", style = MaterialTheme.typography.bodySmall)
                    Text(text = "• > 5.0 (Slabé): Satelity jsou v jedné přímce nebo stíněny zástavbou.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HDOP = horizontální poloha (zeměpisná šířka a délka)\nVDOP = vertikální poloha (nadmořská výška)\nPDOP = celkový 3D polohový rozptyl (sqrt(HDOP² + VDOP²))",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = CyanSecondary
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showDopInfoDialog = false }) {
                    Text("Rozumím")
                }
            }
        )
    }
}
