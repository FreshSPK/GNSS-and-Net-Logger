package com.example.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun OverviewTab(
    gnss: GnssState,
    cellular: CellularState,
    env: EnvironmentState,
    isLogging: Boolean,
    isPaused: Boolean,
    loggedPoints: Int,
    fieldsSelection: LoggedFieldsSelection,
    lang: String,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onUpdateFieldsSelection: (LoggedFieldsSelection) -> Unit
) {
    var showFieldsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Logging Control Banner (Start, Pause, Resume, Stop + Log Fields selector)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isLogging && !isPaused -> EmeraldSignal.copy(alpha = 0.15f)
                    isLogging && isPaused -> AmberWarning.copy(alpha = 0.15f)
                    else -> DarkSlate800
                }
            ),
            border = when {
                isLogging && !isPaused -> CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSignal))
                isLogging && isPaused -> CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberWarning))
                else -> null
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when {
                                            isLogging && !isPaused -> EmeraldSignal
                                            isLogging && isPaused -> AmberWarning
                                            else -> RoseCritical
                                        },
                                        RoundedCornerShape(5.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isLogging && !isPaused -> "ZÁZNAM AKTIVNÍ"
                                    isLogging && isPaused -> "ZÁZNAM POZASTAVEN"
                                    else -> "ZÁZNAM NEAKTIVNÍ"
                                },
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isLogging && !isPaused -> EmeraldSignal
                                    isLogging && isPaused -> AmberWarning
                                    else -> TextSecondaryDark
                                },
                                fontSize = 14.sp
                            )
                        }
                        if (isLogging) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Uloženo vzorků: $loggedPoints",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Field selector button
                    OutlinedButton(
                        onClick = { showFieldsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Položky", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Control Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isLogging) {
                        Button(
                            onClick = onStartLogging,
                            modifier = Modifier.fillMaxWidth().testTag("start_logging_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkSlate900)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ZAHÁJIT ZÁZNAM MĚŘENÍ", fontWeight = FontWeight.Bold, color = DarkSlate900)
                        }
                    } else {
                        // Pause / Resume
                        Button(
                            onClick = if (isPaused) onResumeLogging else onPauseLogging,
                            modifier = Modifier.weight(1f).testTag("pause_resume_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) EmeraldSignal else AmberWarning
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isPaused) "Pokračovat" else "Pozastavit", fontWeight = FontWeight.Bold)
                        }

                        // Stop
                        Button(
                            onClick = onStopLogging,
                            modifier = Modifier.weight(1f).testTag("stop_logging_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseCritical),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Zastavit", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GNSS POLOHA & RYCHLOST (Style exactly as in User screenshot image_5dc8b95e.png)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GNSS POLOHA & RYCHLOST",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                    }
                    Text(
                        text = if (gnss.hasFix) "3D GNSS Fix Aktivní" else "Vyhledávání...",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (gnss.hasFix) EmeraldSignal else AmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ZEMĚPÍSNÁ ŠÍŘKA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = String.format("%.6f°", gnss.latitude),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "ZEMĚPÍSNÁ DÉLKA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = String.format("%.6f°", gnss.longitude),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "RYCHLOST (PŘESNOST)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "${String.format("%.1f", gnss.speedKmh)} km/h",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanTertiary
                        )
                        Text(text = "±0,7 km/h", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "HORIZ. PŘESNOST", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "±${String.format("%.1f", gnss.horizontalAccuracyM)} m",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldSignal
                        )
                        Text(text = "68% interval", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "VÝŠKA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "${String.format("%.1f", gnss.altitudeMsl)} m",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        Text(text = "±${String.format("%.1f", gnss.verticalAccuracyM)} m", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "AZIMUT (SMĚR)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "${String.format("%.0f", gnss.bearingDeg)}°",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AmberWarning
                        )
                        Text(text = "T (SMĚR)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // DOP PŘESNOST (DILUTION OF PRECISION)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DOP PŘESNOST (DILUTION OF PRECISION)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )
                Text(
                    text = "Geometrie rozložení satelitů na obloze",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "HDOP (Horizontální)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = String.format("%.2f", gnss.hdop),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldSignal
                        )
                        Text(text = "Vliv na polohu X/Y", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "VDOP (Vertikální)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = String.format("%.2f", gnss.vdop),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        Text(text = "Vliv na výšku Z", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "PDOP (Prostorový 3D)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = String.format("%.2f", gnss.pdop),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanTertiary
                        )
                        Text(text = "Celková geometrie", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SATELITY & PÁSMA GNSS
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SATELITY & PÁSMA GNSS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    Text(
                        text = "${gnss.satellitesUsedInFix} použito z ${gnss.satellitesInView} viditelných",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (gnss.l5Count > 0) "DVOUFREKVENČNÍ PŘÍJEM L1+L5" else "JEDNOFREKVENČNÍ PŘÍJEM L1",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (gnss.l5Count > 0) EmeraldSignal else TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "L1: ${gnss.l1Count} • L5: ${gnss.l5Count}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConstellationMiniBox("GPS", gnss.constellationCountMap["GPS"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("GLONASS", gnss.constellationCountMap["GLONASS"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("BeiDou", gnss.constellationCountMap["BeiDou"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("Galileo", gnss.constellationCountMap["Galileo"], modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // MOBILNÍ SÍŤ & OPERÁTOR (SIM 1 & SIM 2 with Bargraphs)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "MOBILNÍ SÍŤ & OPERÁTOR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )

                Spacer(modifier = Modifier.height(8.dp))

                SimCardRow(sim = cellular.sim1)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))
                SimCardRow(sim = cellular.sim2)
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
                    .height(30.dp)
                    .width(100.dp)
            )
        }
    }

    // Modal bottom sheet / dialog for selective logging fields
    if (showFieldsDialog) {
        AlertDialog(
            onDismissRequest = { showFieldsDialog = false },
            title = { Text(text = "Výběr položek k logování", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Zvolte parametry a telemetrická data, která se mají ukládat při každém kroku:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FieldCheckbox(
                        label = "GNSS satelitní data & DOP",
                        checked = fieldsSelection.logGnss,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logGnss = it)) }
                    )
                    FieldCheckbox(
                        label = "Mobilní RF parametry (RSRP, SINR, TA)",
                        checked = fieldsSelection.logCellularRf,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logCellularRf = it)) }
                    )
                    FieldCheckbox(
                        label = "Sousední buňky & RF průzkum",
                        checked = fieldsSelection.logNeighbors,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logNeighbors = it)) }
                    )
                    FieldCheckbox(
                        label = "Environmentální senzory (Barometr, IMU)",
                        checked = fieldsSelection.logSensors,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logSensors = it)) }
                    )
                    FieldCheckbox(
                        label = "Síťová latence (Socket Ping 8.8.8.8)",
                        checked = fieldsSelection.logPing,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logPing = it)) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFieldsDialog = false }) {
                    Text("Hotovo")
                }
            }
        )
    }
}

@Composable
private fun ConstellationMiniBox(name: String, counts: Pair<Int, Int>?, modifier: Modifier = Modifier) {
    val used = counts?.first ?: 0
    val total = counts?.second ?: 0
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate700.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
            Text(
                text = "$used/$total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (used > 0) EmeraldSignal else Color.Gray
            )
            Text(text = "L1/L5", style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp), color = TextSecondaryDark)
        }
    }
}

@Composable
private fun SimCardRow(sim: SimCellInfo) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(if (sim.slotIndex == 0) CyanSecondary else AmberWarning, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SIM ${sim.slotIndex + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${sim.operatorAlpha} • ${sim.networkGen}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (sim.slotIndex == 0) "DATA AKTIVNÍ" else "HLAS",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (sim.slotIndex == 0) EmeraldSignal else TextSecondaryDark
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Signal strength bargraph
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
                text = "${sim.band} (PCI ${sim.pci})",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
            Text(
                text = "${sim.rsrp ?: "N/A"} dBm",
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

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SINR: ${sim.sinr?.let { String.format("%.0f dB", it) } ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondaryDark
            )
            Text(
                text = "Vzdálenost: ${sim.estimatedDistanceM?.let { String.format("%.0f m (TA %d)", it, sim.timingAdvance ?: 0) } ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun FieldCheckbox(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
