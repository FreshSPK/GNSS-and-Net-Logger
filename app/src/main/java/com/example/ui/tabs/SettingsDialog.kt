package com.example.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import com.example.ui.theme.*

@Composable
fun SettingsDialog(
    settings: LoggingSettings,
    lang: String,
    onDismiss: () -> Unit,
    onSaveSettings: (LoggingSettings) -> Unit
) {
    var isDemo by remember { mutableStateOf(settings.isDemoMode) }
    var selectedLang by remember { mutableStateOf(settings.language) }
    var triggerMode by remember { mutableStateOf(settings.triggerMode) }
    var interval by remember { mutableStateOf(settings.timeIntervalSeconds) }
    var distThreshold by remember { mutableStateOf(settings.distanceThresholdMeters) }
    var qnhText by remember { mutableStateOf(settings.qnhHpa.toString()) }
    var taMultiplier by remember { mutableStateOf(settings.taMultiplierMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nastavení parametrů měření",
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
                // Language Switch
                Text(
                    text = "Jazyk aplikace",
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

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(14.dp))

                // Simulation / Demo Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simulační / Demo režim",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Generuje syntetický pohyb kolem FEKT VUT a kolísání signálu pro testování bez chození venku",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isDemo, onCheckedChange = { isDemo = it })
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(14.dp))

                // Timing Advance (TA) Distance Multiplier Setting
                Text(
                    text = "Výpočet vzdálenosti z Timing Advance (TA)",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zvolte konstantu pro krok TA dle kalibrace Vašeho zařízení:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = taMultiplier == 2,
                        onClick = { taMultiplier = 2 },
                        label = { Text("2× (156 m / krok)") }
                    )
                    FilterChip(
                        selected = taMultiplier == 1,
                        onClick = { taMultiplier = 1 },
                        label = { Text("1× (78 m / krok)") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(14.dp))

                // Log Trigger Mode
                Text(
                    text = "Kritérium ukládání vzorků",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.TIME,
                        onClick = { triggerMode = LogTriggerMode.TIME },
                        label = { Text("Čas") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.DISTANCE,
                        onClick = { triggerMode = LogTriggerMode.DISTANCE },
                        label = { Text("Vzdálenost") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.HYBRID,
                        onClick = { triggerMode = LogTriggerMode.HYBRID },
                        label = { Text("Hybridní") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (triggerMode != LogTriggerMode.DISTANCE) {
                    Text(text = "Časový interval: ${interval}s", style = MaterialTheme.typography.bodySmall)
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
                    Text(text = "Vzdálenostní krok: ${distThreshold.toInt()}m", style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5f, 10f, 25f, 50f).forEach { meters ->
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

                // QNH Setting
                OutlinedTextField(
                    value = qnhText,
                    onValueChange = { qnhText = it },
                    label = { Text("Základní referenční tlak QNH (hPa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                            taMultiplierMode = taMultiplier
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Uložit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zrušit")
            }
        }
    )
}
