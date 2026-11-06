package com.example.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GnssState
import com.example.ui.components.SkyPlotView
import com.example.ui.i18n.Strings
import com.example.ui.theme.*

@Composable
fun GnssTab(
    gnss: GnssState,
    lang: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Constellation Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Strings.get("constellation_status", lang),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fix: ${if (gnss.hasFix) "3D LOCK" else "ACQUIRING"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (gnss.hasFix) EmeraldSignal else AmberWarning
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Constellation Cards
        Row(modifier = Modifier.fillMaxWidth()) {
            ConstellationPill(name = "GPS", counts = gnss.constellationCountMap["GPS"], modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
            ConstellationPill(name = "GLONASS", counts = gnss.constellationCountMap["GLONASS"], modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
            ConstellationPill(name = "Galileo", counts = gnss.constellationCountMap["Galileo"], modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
            ConstellationPill(name = "BeiDou", counts = gnss.constellationCountMap["BeiDou"], modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SkyPlot (Polar Diagram)
        Text(
            text = Strings.get("skyplot_title", lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        SkyPlotView(satellites = gnss.satellites)

        Spacer(modifier = Modifier.height(16.dp))

        // Dilution of Precision & Multi-Frequency Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Geometry & Carrier Frequencies",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(title = "PDOP", value = String.format("%.2f", gnss.pdop), modifier = Modifier.weight(1f))
                    MetricBox(title = "HDOP", value = String.format("%.2f", gnss.hdop), modifier = Modifier.weight(1f))
                    MetricBox(title = "VDOP", value = String.format("%.2f", gnss.vdop), modifier = Modifier.weight(1f))
                    MetricBox(
                        title = "Dual-Band",
                        value = "${String.format("%.0f", gnss.dualFrequencyRatioPct)}%",
                        subtext = "L1/L5, E1/E5a",
                        color = BlueSatellite,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AGC & Jamming Monitor
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (gnss.jammingAlert) RoseCritical.copy(alpha = 0.2f) else DarkSlate800
            ),
            border = if (gnss.jammingAlert) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RoseCritical)) else null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Strings.get("jamming_monitor", lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (gnss.jammingAlert) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = RoseCritical
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (gnss.jammingAlert) Strings.get("jamming_alert", lang) else Strings.get("nominal_state", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (gnss.jammingAlert) RoseCritical else EmeraldSignal
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (gnss.agcInterferenceScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (gnss.jammingAlert) RoseCritical else CyanTertiary,
                    trackColor = DarkSlate700
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Interference Index: ${String.format("%.1f", gnss.agcInterferenceScore)}% (calculated from GnssMeasurements AGC delta)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = TextSecondaryDark
                )
            }
        }
    }
}

@Composable
private fun ConstellationPill(
    name: String,
    counts: Pair<Int, Int>?,
    modifier: Modifier = Modifier
) {
    val used = counts?.first ?: 0
    val total = counts?.second ?: 0
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = name, style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$used / $total",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (used > 0) EmeraldSignal else Color.Gray
            )
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtext: String? = null,
    color: Color = TextPrimaryDark,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (subtext != null) {
            Text(text = subtext, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
        }
    }
}
