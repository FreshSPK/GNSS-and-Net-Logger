package com.example.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    settings: LoggingSettings,
    lang: String,
    onDismiss: () -> Unit,
    onOpenBatterySettings: () -> Unit = {},
    onSaveSettings: (LoggingSettings) -> Unit
) {
    var isDemo by remember { mutableStateOf(settings.isDemoMode) }
    var selectedLang by remember { mutableStateOf(settings.language) }
    var triggerMode by remember { mutableStateOf(settings.triggerMode) }
    var interval by remember { mutableStateOf(settings.timeIntervalSeconds) }
    var distThreshold by remember { mutableStateOf(settings.distanceThresholdMeters) }
    var qnhText by remember { mutableStateOf(settings.qnhHpa.toString()) }
    var taMultiplier by remember { mutableStateOf(settings.taMultiplierMode) }
    var darkMap by remember { mutableStateOf(settings.darkMapMode) }
    var audioAlerts by remember { mutableStateOf(settings.audioAnomalyAlerts) }

    val effectiveIsCs = remember(selectedLang, lang) {
        if (selectedLang == "SYSTEM") {
            val defaultLang = java.util.Locale.getDefault().language
            defaultLang.equals("cs", ignoreCase = true)
        } else {
            selectedLang == "CS"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (effectiveIsCs) "Nastavení parametrů měření" else "Measurement Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Quick Presets
                Text(
                    text = if (effectiveIsCs) "Rychlé profily měření" else "Quick Measurement Presets",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = {
                            triggerMode = LogTriggerMode.HYBRID
                            interval = 5
                            distThreshold = 10f
                        },
                        label = {
                            Text(
                                text = if (effectiveIsCs) "Pěší (5s/10m)" else "Walk (5s/10m)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                    AssistChip(
                        onClick = {
                            triggerMode = LogTriggerMode.HYBRID
                            interval = 10
                            distThreshold = 50f
                        },
                        label = {
                            Text(
                                text = if (effectiveIsCs) "Auto (10s/50m)" else "Drive (10s/50m)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                    AssistChip(
                        onClick = {
                            triggerMode = LogTriggerMode.TIME
                            interval = 10
                        },
                        label = {
                            Text(
                                text = if (effectiveIsCs) "Statické (10s)" else "Static (10s)",
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))

                // Language Switch
                Text(
                    text = if (effectiveIsCs) "Jazyk aplikace" else "App Language",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedLang == "SYSTEM",
                        onClick = { selectedLang = "SYSTEM" },
                        label = { Text("Auto") }
                    )
                    FilterChip(
                        selected = selectedLang == "CS",
                        onClick = { selectedLang = "CS" },
                        label = { Text("Čeština") }
                    )
                    FilterChip(
                        selected = selectedLang == "EN",
                        onClick = { selectedLang = "EN" },
                        label = { Text("English") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))

                // Simulation / Demo Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (effectiveIsCs) "Simulační / Demo režim" else "Simulation / Demo Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (effectiveIsCs) {
                                "Generuje syntetický pohyb kolem FEKT VUT pro testování bez chození venku"
                            } else {
                                "Generates synthetic GNSS track around BUT FEKT for indoor testing"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isDemo, onCheckedChange = { isDemo = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dark Map Tiles Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (effectiveIsCs) "Tmavý styl mapy (Noční režim)" else "Dark Map Tiles (Night Mode)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (effectiveIsCs) {
                                "Barevně invertuje dlaždice OSM pro čitelnost v noci a v autě"
                            } else {
                                "Inverts OpenStreetMap tiles for night/cockpit readability"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = darkMap, onCheckedChange = { darkMap = it })
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Acoustic Anomaly Alerts Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (effectiveIsCs) "Akustické varování při anomálii" else "Audio Alert on Anomaly",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (effectiveIsCs) {
                                "Krátké pípnutí při GNSS jammingu nebo vynuceném 2G downgradu"
                            } else {
                                "Short beep during GNSS jamming or forced 2G downgrade"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = audioAlerts, onCheckedChange = { audioAlerts = it })
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))

                // Timing Advance (TA) Distance Multiplier Setting
                Text(
                    text = if (effectiveIsCs) "Výpočet vzdálenosti z Timing Advance (TA)" else "Timing Advance (TA) Distance Calculation",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = taMultiplier == 2,
                        onClick = { taMultiplier = 2 },
                        label = { Text(if (effectiveIsCs) "2× (156 m / krok)" else "2× (156 m / step)") }
                    )
                    FilterChip(
                        selected = taMultiplier == 1,
                        onClick = { taMultiplier = 1 },
                        label = { Text(if (effectiveIsCs) "1× (78 m / krok)" else "1× (78 m / step)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))

                // Log Trigger Mode
                Text(
                    text = if (effectiveIsCs) "Kritérium ukládání vzorků" else "Log Trigger Criterion",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.TIME,
                        onClick = { triggerMode = LogTriggerMode.TIME },
                        label = { Text(if (effectiveIsCs) "Čas" else "Time") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.DISTANCE,
                        onClick = { triggerMode = LogTriggerMode.DISTANCE },
                        label = { Text(if (effectiveIsCs) "Vzdálenost" else "Distance") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.HYBRID,
                        onClick = { triggerMode = LogTriggerMode.HYBRID },
                        label = { Text(if (effectiveIsCs) "Hybridní" else "Hybrid") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (triggerMode != LogTriggerMode.DISTANCE) {
                    Text(
                        text = if (effectiveIsCs) "Časový interval: ${interval}s" else "Time interval: ${interval}s",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 5, 10, 30).forEach { sec ->
                            FilterChip(
                                selected = interval == sec,
                                onClick = { interval = sec },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (triggerMode != LogTriggerMode.TIME) {
                    Text(
                        text = if (effectiveIsCs) "Vzdálenostní krok: ${distThreshold.toInt()}m" else "Distance step: ${distThreshold.toInt()}m",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5f, 10f, 15f, 25f, 50f).forEach { meters ->
                            FilterChip(
                                selected = distThreshold == meters,
                                onClick = { distThreshold = meters },
                                label = { Text("${meters.toInt()}m") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = qnhText,
                    onValueChange = { qnhText = it },
                    label = { Text(if (effectiveIsCs) "Základní referenční tlak QNH (hPa)" else "Base QNH Reference Pressure (hPa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onOpenBatterySettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberWarning)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (effectiveIsCs) "Nastavení optimalizace baterie (dlouhý běh)" else "Battery Optimization Settings (Background Run)",
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qnh = qnhText.toFloatOrNull() ?: 1013.25f
                    onSaveSettings(
                        settings.copy(
                            isDemoMode = isDemo,
                            language = selectedLang,
                            triggerMode = triggerMode,
                            timeIntervalSeconds = interval,
                            distanceThresholdMeters = distThreshold,
                            qnhHpa = qnh,
                            taMultiplierMode = taMultiplier,
                            darkMapMode = darkMap,
                            audioAnomalyAlerts = audioAlerts
                        )
                    )
                    onDismiss()
                }
            ) {
                Text(if (effectiveIsCs) "Uložit" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (effectiveIsCs) "Zrušit" else "Cancel")
            }
        }
    )
}
