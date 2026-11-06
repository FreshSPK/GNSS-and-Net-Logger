package com.example.ui.tabs

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import com.example.ui.i18n.Strings
import com.example.ui.theme.*

@Composable
fun SettingsDialog(
    settings: LoggingSettings,
    lang: String,
    onDismiss: () -> Unit,
    onSaveSettings: (LoggingSettings) -> Unit
) {
    val context = LocalContext.current
    var isDemo by remember { mutableStateOf(settings.isDemoMode) }
    var selectedLang by remember { mutableStateOf(settings.language) }
    var triggerMode by remember { mutableStateOf(settings.triggerMode) }
    var interval by remember { mutableStateOf(settings.timeIntervalSeconds) }
    var distThreshold by remember { mutableStateOf(settings.distanceThresholdMeters) }
    var qnhText by remember { mutableStateOf(settings.qnhHpa.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = Strings.get("settings_title", lang),
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
                    text = Strings.get("language_switch", lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
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
                            text = "Simulation / Demo Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Synthetic GNSS track around VUT FEKT, handovers, and RF fluctuating metrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isDemo, onCheckedChange = { isDemo = it })
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(14.dp))

                // Log Trigger Mode
                Text(
                    text = Strings.get("trigger_mode", lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.TIME,
                        onClick = { triggerMode = LogTriggerMode.TIME },
                        label = { Text("Time") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.DISTANCE,
                        onClick = { triggerMode = LogTriggerMode.DISTANCE },
                        label = { Text("Distance") }
                    )
                    FilterChip(
                        selected = triggerMode == LogTriggerMode.HYBRID,
                        onClick = { triggerMode = LogTriggerMode.HYBRID },
                        label = { Text("Hybrid") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time Interval Selector
                if (triggerMode != LogTriggerMode.DISTANCE) {
                    Text(text = "${Strings.get("time_interval", lang)}: ${interval}s", style = MaterialTheme.typography.bodySmall)
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

                // Distance Threshold Selector
                if (triggerMode != LogTriggerMode.TIME) {
                    Text(text = "${Strings.get("dist_threshold", lang)}: ${distThreshold.toInt()}m", style = MaterialTheme.typography.bodySmall)
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

                // QNH Setting
                OutlinedTextField(
                    value = qnhText,
                    onValueChange = { qnhText = it },
                    label = { Text("Base QNH Barometric Reference (hPa)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(14.dp))

                // Author Attribution & Academic Branding
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate700.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.School, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Strings.get("author_title", lang),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyanTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Created by Ing. Jan Kufa, Ph.D.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = Strings.get("author_dept", lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Profile: https://www.vut.cz/lide/jan-kufa-125512",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = CyanSecondary,
                            modifier = Modifier.clickable {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.vut.cz/lide/jan-kufa-125512"))
                                context.startActivity(browserIntent)
                            }
                        )
                        Text(
                            text = "Contact: kufa@vut.cz",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondaryDark
                        )
                    }
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
                            qnhHpa = qnh
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
