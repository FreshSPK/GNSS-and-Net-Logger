package com.example.ui.tabs

import android.content.Context
import android.os.PowerManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun OverviewTab(
    gnss: GnssState,
    cellular: CellularState,
    env: EnvironmentState,
    isLogging: Boolean,
    isPaused: Boolean,
    loggedPoints: Int,
    sessionStartTimeMs: Long? = null,
    sessionDistanceMeters: Double = 0.0,
    handoverCount: Int = 0,
    handoverCountSim1: Int = 0,
    handoverCountSim2: Int = 0,
    fieldsSelection: LoggedFieldsSelection,
    lang: String,
    onStartLogging: () -> Unit,
    onPauseLogging: () -> Unit,
    onResumeLogging: () -> Unit,
    onStopLogging: () -> Unit,
    onAddWaypoint: (String) -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onUpdateFieldsSelection: (LoggedFieldsSelection) -> Unit
) {
    val isCs = lang != "EN"
    val context = LocalContext.current
    var showFieldsDialog by remember { mutableStateOf(false) }
    var showWaypointDialog by remember { mutableStateOf(false) }
    var waypointNoteInput by remember { mutableStateOf("") }

    // Live session chronometer
    var elapsedSec by remember { mutableStateOf(0L) }
    LaunchedEffect(isLogging, sessionStartTimeMs) {
        if (isLogging && sessionStartTimeMs != null) {
            while (true) {
                elapsedSec = ((System.currentTimeMillis() - sessionStartTimeMs) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        } else {
            elapsedSec = 0L
        }
    }

    val isBatteryOptIgnored = remember {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        } catch (_: Exception) {
            true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Logging Control Banner (Start, Pause, Resume, Stop, Waypoint + Chronometer)
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
                    Column(modifier = Modifier.weight(1f)) {
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
                                    isLogging && !isPaused -> if (isCs) "ZÁZNAM AKTIVNÍ" else "LOGGING ACTIVE"
                                    isLogging && isPaused -> if (isCs) "ZÁZNAM POZASTAVEN" else "LOGGING PAUSED"
                                    else -> if (isCs) "ZÁZNAM NEAKTIVNÍ" else "LOGGING INACTIVE"
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
                            Spacer(modifier = Modifier.height(6.dp))
                            val hrs = elapsedSec / 3600
                            val mins = (elapsedSec % 3600) / 60
                            val secs = elapsedSec % 60
                            val timerStr = String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
                            val distStr = String.format(Locale.US, "%.2f km", sessionDistanceMeters / 1000.0)
                            Text(
                                text = if (isCs) {
                                    "$timerStr  •  Vzorků: $loggedPoints  •  $distStr\nHO SIM1: $handoverCountSim1  |  SIM2: $handoverCountSim2  (Celkem: $handoverCount)"
                                } else {
                                    "$timerStr  •  Samples: $loggedPoints  •  $distStr\nHO SIM1: $handoverCountSim1  |  SIM2: $handoverCountSim2  (Total: $handoverCount)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimaryDark
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
                        Text(text = if (isCs) "Položky" else "Fields", fontSize = 12.sp)
                    }
                }

                if (!isBatteryOptIgnored) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier.fillMaxWidth().testTag("battery_opt_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = AmberWarning.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.BatteryAlert, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCs) {
                                    "Optimalizace baterie může ukončit logování na pozadí • Klepněte pro povolení"
                                } else {
                                    "Battery optimization may kill background logging • Tap to allow unrestricted"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = AmberWarning,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Control Action Buttons
                if (!isLogging) {
                    Button(
                        onClick = onStartLogging,
                        modifier = Modifier.fillMaxWidth().testTag("start_logging_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkSlate900)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCs) "ZAHÁJIT ZÁZNAM MĚŘENÍ" else "START FIELD LOGGING",
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate900
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                            Text(
                                text = if (isPaused) (if (isCs) "Pokračovat" else "Resume") else (if (isCs) "Pozastavit" else "Pause"),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Waypoint Marker
                        FilledTonalButton(
                            onClick = {
                                waypointNoteInput = ""
                                showWaypointDialog = true
                            },
                            modifier = Modifier.testTag("add_waypoint_button"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddLocationAlt, contentDescription = if (isCs) "Přidat značku" else "Add Waypoint", tint = CyanTertiary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isCs) "Značka" else "Mark", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            Text(text = if (isCs) "Zastavit" else "Stop", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GNSS POLOHA & RYCHLOST
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
                        text = if (isCs) "GNSS POLOHA & RYCHLOST" else "GNSS POSITION & SPEED",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    Text(
                        text = if (gnss.hasFix) (if (isCs) "3D GNSS Fix Aktivní" else "3D GNSS Fix Active") else (if (isCs) "Vyhledávání..." else "Searching..."),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (gnss.hasFix) EmeraldSignal else AmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "ZEMĚPISNÁ ŠÍŘKA" else "LATITUDE", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = if (gnss.hasFix || gnss.latitude != 0.0) String.format(Locale.US, "%.6f°", gnss.latitude) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "ZEMĚPISNÁ DÉLKA" else "LONGITUDE", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = if (gnss.hasFix || gnss.longitude != 0.0) String.format(Locale.US, "%.6f°", gnss.longitude) else "—",
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
                        Text(text = if (isCs) "RYCHLOST" else "SPEED", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "${String.format(Locale.US, "%.1f", gnss.speedKmh)} km/h",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyanTertiary
                        )
                        val spdAccStr = gnss.speedAccuracyKmh?.let { "±${String.format(Locale.US, "%.1f", it)} km/h" } ?: "—"
                        Text(text = spdAccStr, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "HORIZ. PŘESNOST" else "HORIZ. ACCURACY", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = if (gnss.hasFix) "±${String.format(Locale.US, "%.1f", gnss.horizontalAccuracyM)} m" else "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (gnss.hasFix) EmeraldSignal else TextSecondaryDark
                        )
                        Text(text = "68% interval", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "VÝŠKA (MSL)" else "ALTITUDE (MSL)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = if (gnss.hasFix) "${String.format(Locale.US, "%.1f", gnss.altitudeMsl)} m" else "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = if (gnss.hasFix && gnss.verticalAccuracyM > 0f) "±${String.format(Locale.US, "%.1f", gnss.verticalAccuracyM)} m" else "—",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = TextSecondaryDark
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "AZIMUT (SMĚR)" else "AZIMUTH", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                        Text(
                            text = "${String.format(Locale.US, "%.0f", gnss.bearingDeg)}°",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = AmberWarning
                        )
                        Text(text = "T (BEARING)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
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
                    text = if (isCs) "DOP PŘESNOST (DILUTION OF PRECISION)" else "DILUTION OF PRECISION (DOP)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )
                Text(
                    text = if (isCs) "Geometrie rozložení satelitů na obloze" else "Satellite constellation geometry distribution",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )

                Spacer(modifier = Modifier.height(10.dp))

                val hasValidDop = gnss.hasFix && gnss.satellitesUsedInFix >= 4 && gnss.pdop > 0f && gnss.pdop < 9.0f
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "HDOP (Horizontální)" else "HDOP (Horizontal)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = if (hasValidDop && gnss.hdop > 0f) String.format(Locale.US, "%.2f", gnss.hdop) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (hasValidDop) EmeraldSignal else TextSecondaryDark
                        )
                        Text(text = if (isCs) "Vliv na polohu X/Y" else "Impact on X/Y", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "VDOP (Vertikální)" else "VDOP (Vertical)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = if (hasValidDop && gnss.vdop > 0f) String.format(Locale.US, "%.2f", gnss.vdop) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (hasValidDop) TextPrimaryDark else TextSecondaryDark
                        )
                        Text(text = if (isCs) "Vliv na výšku Z" else "Impact on Altitude Z", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "PDOP (Prostorový 3D)" else "PDOP (Spatial 3D)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = if (hasValidDop) String.format(Locale.US, "%.2f", gnss.pdop) else "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (hasValidDop) CyanTertiary else TextSecondaryDark
                        )
                        Text(text = if (isCs) "Celková geometrie" else "Total geometry", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
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
                        text = if (isCs) "SATELITY & PÁSMA GNSS" else "GNSS SATELLITES & BANDS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    Text(
                        text = if (isCs) {
                            val uniq = if (gnss.uniqueSatellitesInView > 0) " (${gnss.uniqueSatellitesUsedInFix}/${gnss.uniqueSatellitesInView} sat.)" else ""
                            "${gnss.satellitesUsedInFix}/${gnss.satellitesInView} sig.$uniq"
                        } else {
                            val uniq = if (gnss.uniqueSatellitesInView > 0) " (${gnss.uniqueSatellitesUsedInFix}/${gnss.uniqueSatellitesInView} sats)" else ""
                            "${gnss.satellitesUsedInFix}/${gnss.satellitesInView} sig.$uniq"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (gnss.l5Count > 0) (if (isCs) "DVOUFREKVENČNÍ PŘÍJEM (L1+L5/E5a)" else "DUAL-BAND FIX (L1+L5/E5a)") else (if (isCs) "JEDNOFREKVENČNÍ PŘÍJEM" else "SINGLE-BAND FIX"),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (gnss.l5Count > 0) EmeraldSignal else TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "L1/E1/B1/G1: ${gnss.l1Count} • L5/E5a/B2a: ${gnss.l5Count}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConstellationMiniBox("GPS", "L1/L5", gnss.constellationCountMap["GPS"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("GLONASS", "G1/G2", gnss.constellationCountMap["GLONASS"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("BeiDou", "B1/B2a", gnss.constellationCountMap["BeiDou"], modifier = Modifier.weight(1f))
                    ConstellationMiniBox("Galileo", "E1/E5a", gnss.constellationCountMap["Galileo"], modifier = Modifier.weight(1f))
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
                    text = if (isCs) "MOBILNÍ SÍŤ & OPERÁTOR" else "CELLULAR NETWORK & OPERATOR",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )

                Spacer(modifier = Modifier.height(8.dp))

                SimCardRow(sim = cellular.sim1, lang = lang)
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(12.dp))
                SimCardRow(sim = cellular.sim2, lang = lang)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SENZORY & HARDWAROVÁ TELEMETRIE
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isCs) "SENZORY & TELEMETRIE ZAŘÍZENÍ" else "SENSORS & HARDWARE TELEMETRY",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "Barometr (QNH)" else "Baro (QNH)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        Text(
                            text = env.pressureHpa?.let { "${String.format(Locale.US, "%.1f", it)} hPa" } ?: "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = env.seaLevelPressureHpa?.let { "MSL: ${String.format(Locale.US, "%.1f", it)} hPa" }
                                ?: (if (isCs) "Senzor nedostupný" else "Unavailable"),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = TextSecondaryDark
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "Baterie & Teplota" else "Battery & Thermal", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        val bTemp = env.battery.temperatureC
                        val bColor = when {
                            bTemp == null -> TextSecondaryDark
                            bTemp >= 38f -> AmberWarning
                            else -> EmeraldSignal
                        }
                        Text(
                            text = bTemp?.let { "${String.format(Locale.US, "%.1f", it)} °C" } ?: "—",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = bColor
                        )
                        val pctStr = env.battery.levelPct?.let { "$it%" } ?: "—"
                        val voltStr = env.battery.voltageMv?.let { "$it mV" } ?: "—"
                        Text(text = "$pctStr • $voltStr", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = if (isCs) "Odezva (Ping)" else "Ping RTT (8.8.8.8)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
                        val pingText = when {
                            !fieldsSelection.logPing -> if (isCs) "Vypnuto" else "Off"
                            cellular.pingRttMs != null -> "${cellular.pingRttMs} ms"
                            cellular.pingPacketLoss -> if (isCs) "ZTRÁTA" else "LOSS"
                            else -> "—"
                        }
                        Text(
                            text = pingText,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                !fieldsSelection.logPing -> TextSecondaryDark
                                cellular.pingPacketLoss -> RoseCritical
                                cellular.pingRttMs != null -> EmeraldSignal
                                else -> TextSecondaryDark
                            }
                        )
                        Text(text = "Socket probe :53", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
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
                    .height(30.dp)
                    .width(100.dp)
            )
        }
    }

    // Waypoint creation dialog
    if (showWaypointDialog) {
        val presets = if (isCs) {
            listOf("Ztráta signálu / Stín", "Tunel / Podjezd", "Věž BTS / Vysílač", "Rušení / Anomálie", "Kontrolní bod")
        } else {
            listOf("Signal Drop / Shadow", "Tunnel / Underpass", "BTS Tower", "RF Interference", "Checkpoint")
        }
        AlertDialog(
            onDismissRequest = { showWaypointDialog = false },
            title = { Text(text = if (isCs) "Vložit značku (Waypoint)" else "Insert Field Waypoint", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = if (isCs) "Zvolte rychlý štítek nebo napište vlastní poznámku k aktuálním souřadnicím:" else "Select a quick preset or type a custom note for the current point:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    presets.chunked(2).forEach { rowPresets ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowPresets.forEach { preset ->
                                AssistChip(
                                    onClick = { waypointNoteInput = preset },
                                    label = { Text(preset, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = waypointNoteInput,
                        onValueChange = { waypointNoteInput = it },
                        label = { Text(if (isCs) "Poznámka ke značce" else "Waypoint note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddWaypoint(waypointNoteInput.ifBlank { if (isCs) "Značka v terénu" else "Field Waypoint" })
                    showWaypointDialog = false
                }) {
                    Text(if (isCs) "Uložit značku" else "Save Mark")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWaypointDialog = false }) {
                    Text(if (isCs) "Zrušit" else "Cancel")
                }
            }
        )
    }

    // Modal bottom sheet / dialog for selective logging fields
    if (showFieldsDialog) {
        AlertDialog(
            onDismissRequest = { showFieldsDialog = false },
            title = { Text(text = if (isCs) "Výběr položek k logování" else "Select Logging Fields", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = if (isCs) "Zvolte parametry a telemetrická data, která se mají ukládat při každém kroku:" else "Select which telemetry data sets will be stored with each recorded point:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    FieldCheckbox(
                        label = if (isCs) "GNSS satelitní data & DOP" else "GNSS satellite data & DOP",
                        checked = fieldsSelection.logGnss,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logGnss = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Mobilní RF parametry (RSRP, SINR, TA)" else "Cellular RF parameters (RSRP, SINR, TA)",
                        checked = fieldsSelection.logCellularRf,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logCellularRf = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Sousední buňky & RF průzkum" else "Neighbor cells & RF survey",
                        checked = fieldsSelection.logNeighbors,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logNeighbors = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Environmentální senzory (Barometr, IMU)" else "Environmental sensors (Barometer, IMU)",
                        checked = fieldsSelection.logSensors,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logSensors = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Teplota a stav baterie zařízení" else "Device battery thermal & voltage",
                        checked = fieldsSelection.logBattery,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logBattery = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Surový tok NMEA vět (\$GNGGA)" else "Raw NMEA sentence stream (\$GNGGA)",
                        checked = fieldsSelection.logNmea,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logNmea = it)) }
                    )
                    FieldCheckbox(
                        label = if (isCs) "Síťová latence (Socket Ping 8.8.8.8)" else "Network latency (Socket Ping 8.8.8.8)",
                        checked = fieldsSelection.logPing,
                        onChecked = { onUpdateFieldsSelection(fieldsSelection.copy(logPing = it)) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showFieldsDialog = false }) {
                    Text(if (isCs) "Hotovo" else "Done")
                }
            }
        )
    }
}

@Composable
private fun ConstellationMiniBox(name: String, bandCaption: String, counts: Pair<Int, Int>?, modifier: Modifier = Modifier) {
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
            Text(text = bandCaption, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp), color = TextSecondaryDark)
        }
    }
}

@Composable
private fun SimCardRow(sim: SimCellInfo, lang: String = "CS") {
    val isCs = lang != "EN"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (sim.isDataActive) EmeraldSignal.copy(alpha = 0.15f) else DarkSlate700
            ) {
                Text(
                    text = if (sim.isDataActive) (if (isCs) "DATA AKTIVNÍ" else "DATA ACTIVE") else (if (isCs) "HLAS & SMS" else "VOICE & SMS"),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sim.isDataActive) EmeraldSignal else TextSecondaryDark,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val rsrp = sim.rsrp ?: -120
        val normSignal = ((rsrp - (-125f)) / ((-65f) - (-125f))).coerceIn(0f, 1f)
        val barColor = when {
            sim.rsrp == null -> TextSecondaryDark
            rsrp >= -85 -> EmeraldSignal
            rsrp >= -100 -> AmberWarning
            else -> RoseCritical
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bandDesc = buildString {
                append(sim.band)
                if (!sim.nrBand.isNullOrBlank()) {
                    append(" + ")
                    append(sim.nrBand.substringBefore(" "))
                }
                append(" (PCI ${if (sim.pci > 0) sim.pci else "N/A"})")
                if (!sim.nrStateLabel.isNullOrBlank()) {
                    append(" • ${sim.nrStateLabel}")
                }
            }
            Text(
                text = bandDesc,
                style = MaterialTheme.typography.bodySmall,
                color = if (sim.networkGen.contains("5G")) Purple5G else TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
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
            progress = { if (sim.rsrp != null) normSignal else 0f },
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
                text = "SINR: ${sim.sinr?.let { String.format(Locale.US, "%.0f dB", it) } ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TextSecondaryDark
            )
            Text(
                text = if (isCs) {
                    "Vzdálenost: ${sim.estimatedDistanceM?.let { String.format(Locale.US, "%.0f m (TA %d)", it, sim.timingAdvance ?: 0) } ?: "N/A"}"
                } else {
                    "Distance: ${sim.estimatedDistanceM?.let { String.format(Locale.US, "%.0f m (TA %d)", it, sim.timingAdvance ?: 0) } ?: "N/A"}"
                },
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
