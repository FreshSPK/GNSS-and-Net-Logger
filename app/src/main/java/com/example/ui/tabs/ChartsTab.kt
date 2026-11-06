package com.example.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ChartPoint
import com.example.ui.components.RealtimeSparklineChart
import com.example.ui.i18n.Strings
import com.example.ui.theme.*

@Composable
fun ChartsTab(
    chartPoints: List<ChartPoint>,
    lang: String
) {
    val rsrpValues = chartPoints.map { it.rsrp }
    val speedValues = chartPoints.map { it.speedKmh }
    val satValues = chartPoints.map { it.satCount.toFloat() }
    val pingValues = chartPoints.map { it.pingMs }

    val lastRsrp = chartPoints.lastOrNull()?.rsrp?.let { String.format("%.0f", it) } ?: "—"
    val lastSpeed = chartPoints.lastOrNull()?.let { String.format("%.1f", it.speedKmh) } ?: "0.0"
    val lastSats = chartPoints.lastOrNull()?.satCount?.toString() ?: "0"
    val lastPing = chartPoints.lastOrNull()?.pingMs?.let { String.format("%.0f", it) } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = Strings.get("charts_title", lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Serving RSRP Chart
        RealtimeSparklineChart(
            title = Strings.get("rsrp_chart", lang),
            currentValue = lastRsrp,
            unit = "dBm",
            values = rsrpValues,
            lineColor = CyanTertiary,
            minY = -125f,
            maxY = -65f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Speed Chart
        RealtimeSparklineChart(
            title = Strings.get("speed_chart", lang),
            currentValue = lastSpeed,
            unit = "km/h",
            values = speedValues,
            lineColor = EmeraldSignal,
            minY = 0f,
            maxY = 120f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Satellite in Fix Count Chart
        RealtimeSparklineChart(
            title = Strings.get("sat_chart", lang),
            currentValue = lastSats,
            unit = "sats",
            values = satValues,
            lineColor = BlueSatellite,
            minY = 0f,
            maxY = 35f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Ping Latency Chart
        RealtimeSparklineChart(
            title = Strings.get("ping_chart", lang),
            currentValue = lastPing,
            unit = "ms",
            values = pingValues,
            lineColor = AmberWarning,
            minY = 0f,
            maxY = 200f
        )
    }
}
