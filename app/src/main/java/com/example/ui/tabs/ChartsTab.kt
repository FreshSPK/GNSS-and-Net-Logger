package com.example.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.TelemetryRecord
import com.example.model.ChartPoint
import com.example.ui.components.DualRealtimeSparklineChart
import com.example.ui.components.RealtimeSparklineChart
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun ChartsTab(
    chartPoints: List<ChartPoint>,
    historicalSessionTitle: String? = null,
    historicalRecords: List<TelemetryRecord>? = null,
    onClearHistorical: () -> Unit = {},
    lang: String
) {
    val isCs = lang != "EN"

    // Convert historical records if viewing from History tab; otherwise use live ring buffer
    val effectivePoints = remember(chartPoints, historicalRecords) {
        if (historicalRecords != null && historicalRecords.isNotEmpty()) {
            // Downsample gracefully if > 240 points for crisp sparkline rendering
            val step = (historicalRecords.size / 240).coerceAtLeast(1)
            historicalRecords.filterIndexed { idx, _ -> idx % step == 0 || idx == historicalRecords.lastIndex }
                .map { r ->
                    ChartPoint(
                        timestampMs = r.timestampMs,
                        rsrp = r.sim1Rsrp?.toFloat(),
                        rsrpSim2 = r.sim2Rsrp?.toFloat(),
                        sinrSim1 = r.sim1Sinr,
                        sinrSim2 = r.sim2Sinr,
                        speedKmh = r.speedKmh,
                        satCount = r.satellitesUsedInFix,
                        satInViewCount = r.satellitesInView,
                        pdop = if (r.satellitesUsedInFix >= 4 && r.pdop > 0f && r.pdop < 9.0f) r.pdop else null,
                        agcScore = r.agcInterferenceScore,
                        pingMs = r.pingRttMs?.toFloat(),
                        accelMagnitude = 9.81f,
                        gyroZ = 0f,
                        compassAzimuth = r.bearingDeg,
                        batteryTempC = r.batteryTempC,
                        sim1Operator = r.sim1Operator.ifBlank { "SIM 1" },
                        sim2Operator = r.sim2Operator.ifBlank { "SIM 2" }
                    )
                }
        } else {
            chartPoints
        }
    }

    val timestamps = effectivePoints.map { it.timestampMs }
    val rsrpSim1Values = effectivePoints.map { it.rsrp }
    val rsrpSim2Values = effectivePoints.map { it.rsrpSim2 }
    val sinrSim1Values = effectivePoints.map { it.sinrSim1 }
    val sinrSim2Values = effectivePoints.map { it.sinrSim2 }
    val speedValues = effectivePoints.map { it.speedKmh }
    val satValues = effectivePoints.map { it.satCount.toFloat() }
    val satInViewValues = effectivePoints.map { it.satInViewCount.toFloat() }
    val pdopValues = effectivePoints.map { it.pdop }
    val agcValues = effectivePoints.map { it.agcScore }
    val pingValues = effectivePoints.map { it.pingMs }
    val accelValues = effectivePoints.map { it.accelMagnitude }
    val gyroValues = effectivePoints.map { it.gyroZ }
    val compassValues = effectivePoints.map { it.compassAzimuth }
    val batteryTempValues = effectivePoints.map { it.batteryTempC }

    val lastPoint = effectivePoints.lastOrNull()
    val lastRsrp1 = lastPoint?.rsrp?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val lastRsrp2 = lastPoint?.rsrpSim2?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val lastSinr1 = lastPoint?.sinrSim1?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val lastSinr2 = lastPoint?.sinrSim2?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val sim1Label = lastPoint?.sim1Operator?.takeIf { it.isNotBlank() } ?: "SIM 1"
    val sim2Label = lastPoint?.sim2Operator?.takeIf { it.isNotBlank() } ?: "SIM 2"

    val lastSpeed = lastPoint?.let { String.format(Locale.US, "%.1f", it.speedKmh) } ?: "0.0"
    val lastSats = lastPoint?.satCount?.toString() ?: "0"
    val lastSatsInView = lastPoint?.satInViewCount?.toString() ?: "0"
    val lastPdop = lastPoint?.pdop?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
    val lastAgc = lastPoint?.let { String.format(Locale.US, "%.1f", it.agcScore) } ?: "0.0"
    val lastPing = lastPoint?.pingMs?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val lastAccel = lastPoint?.let { String.format(Locale.US, "%.2f", it.accelMagnitude) } ?: "—"
    val lastGyro = lastPoint?.let { String.format(Locale.US, "%.2f", it.gyroZ) } ?: "—"
    val lastCompass = lastPoint?.let { String.format(Locale.US, "%.0f°", it.compassAzimuth) } ?: "—"
    val lastTemp = lastPoint?.batteryTempC?.let { String.format(Locale.US, "%.1f", it) } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (historicalRecords != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyanSecondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCs) "Analýza relace: ${historicalSessionTitle ?: ""}" else "Session Charts: ${historicalSessionTitle ?: ""}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isCs) "Přejetím prstem po grafu odečtete hodnoty" else "Touch & drag any chart to inspect sample values",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                        )
                    }
                    IconButton(onClick = onClearHistorical) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Zpět na živé grafy", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Text(
                text = if (isCs) "Časové průběhy v reálném čase" else "Real-time Telemetry Waveforms",
                style = MaterialTheme.typography.titleMedium,
                color = CyanTertiary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isCs) "Přejetím prstem po ose grafu odečtete hodnotu v čase" else "Touch & drag horizontally on any chart to inspect values",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 1. Serving RSRP Chart (SIM 1 vs SIM 2)
        DualRealtimeSparklineChart(
            title = if (isCs) "RSRP signál v čase (SIM 1 vs SIM 2)" else "RSRP Signal over Time (SIM 1 vs SIM 2)",
            label1 = "S1: $sim1Label",
            currentValue1 = lastRsrp1,
            values1 = rsrpSim1Values,
            color1 = CyanTertiary,
            label2 = "S2: $sim2Label",
            currentValue2 = lastRsrp2,
            values2 = rsrpSim2Values,
            color2 = AmberWarning,
            timestampsMs = timestamps,
            unit = "dBm",
            minY = -125f,
            maxY = -65f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Serving SINR Chart (SIM 1 vs SIM 2)
        DualRealtimeSparklineChart(
            title = if (isCs) "SINR odstup od šumu (SIM 1 vs SIM 2)" else "SINR Quality over Time (SIM 1 vs SIM 2)",
            label1 = "S1",
            currentValue1 = lastSinr1,
            values1 = sinrSim1Values,
            color1 = EmeraldSignal,
            label2 = "S2",
            currentValue2 = lastSinr2,
            values2 = sinrSim2Values,
            color2 = Purple5G,
            timestampsMs = timestamps,
            unit = "dB",
            minY = -10f,
            maxY = 30f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Speed Chart (adaptive maxY expands seamlessly above 120 km/h on highways)
        RealtimeSparklineChart(
            title = if (isCs) "Rychlost pohybu vozidla / chodce" else "Movement Speed (Vehicle / Walk)",
            currentValue = lastSpeed,
            unit = "km/h",
            values = speedValues,
            timestampsMs = timestamps,
            lineColor = EmeraldSignal,
            minY = 0f,
            maxY = 80f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Satellite Used vs Visible Count Chart
        DualRealtimeSparklineChart(
            title = if (isCs) "Počet satelitů (použité vs. viditelné)" else "Satellites (Used in Fix vs. Visible)",
            label1 = if (isCs) "Ve fixu" else "Used",
            currentValue1 = lastSats,
            values1 = satValues,
            color1 = EmeraldSignal,
            label2 = if (isCs) "Viditelné" else "Visible",
            currentValue2 = lastSatsInView,
            values2 = satInViewValues,
            color2 = BlueSatellite,
            timestampsMs = timestamps,
            unit = if (isCs) "sat." else "sats",
            minY = 0f,
            maxY = 60f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. PDOP Geometry Chart
        RealtimeSparklineChart(
            title = if (isCs) "3D geometrie PDOP v čase (< 1.0 nejlepší, ≥ 4.0 nejhorší)" else "PDOP 3D Constellation Geometry (< 1.0 best, ≥ 4.0 worst)",
            currentValue = lastPdop,
            unit = "PDOP",
            values = pdopValues,
            timestampsMs = timestamps,
            lineColor = CyanTertiary,
            minY = 0f,
            maxY = 4f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 6. AGC Interference Chart
        RealtimeSparklineChart(
            title = if (isCs) "AGC Interferenční index (Rušení GNSS)" else "AGC Interference Index (GNSS Jamming)",
            currentValue = lastAgc,
            unit = "%",
            values = agcValues,
            timestampsMs = timestamps,
            lineColor = Color(0xFFF43F5E),
            minY = 0f,
            maxY = 100f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Ping Latency Chart
        RealtimeSparklineChart(
            title = if (isCs) "Latence pingu (8.8.8.8)" else "Ping RTT Latency (8.8.8.8)",
            currentValue = lastPing,
            unit = "ms",
            values = pingValues,
            timestampsMs = timestamps,
            lineColor = AmberWarning,
            minY = 0f,
            maxY = 150f
        )

        if (historicalRecords == null) {
            Spacer(modifier = Modifier.height(14.dp))

            RealtimeSparklineChart(
                title = if (isCs) "Akcelerometr: Výsledné přetížení / otřesy" else "Accelerometer: Resultant G-Force / Jolt",
                currentValue = lastAccel,
                unit = "m/s²",
                values = accelValues,
                timestampsMs = timestamps,
                lineColor = Color(0xFFA855F7),
                minY = 5f,
                maxY = 18f
            )

            Spacer(modifier = Modifier.height(14.dp))

            RealtimeSparklineChart(
                title = if (isCs) "Gyroskop: Úhlová rychlost zatáčení (Z-osa)" else "Gyroscope: Yaw Angular Velocity (Z-axis)",
                currentValue = lastGyro,
                unit = "rad/s",
                values = gyroValues,
                timestampsMs = timestamps,
                lineColor = Color(0xFFEC4899),
                minY = -1.5f,
                maxY = 1.5f
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        RealtimeSparklineChart(
            title = if (isCs) "Azimut natočení / kurzu" else "Compass / Course Azimuth",
            currentValue = lastCompass,
            unit = "°",
            values = compassValues,
            timestampsMs = timestamps,
            lineColor = Color(0xFFF97316),
            minY = 0f,
            maxY = 360f
        )

        Spacer(modifier = Modifier.height(14.dp))

        RealtimeSparklineChart(
            title = if (isCs) "Teplota baterie telefonu (Termální monitoring)" else "Battery Temperature (Thermal Monitoring)",
            currentValue = lastTemp,
            unit = "°C",
            values = batteryTempValues,
            timestampsMs = timestamps,
            lineColor = Color(0xFFE11D48),
            minY = 20f,
            maxY = 48f
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_vut_logo),
                contentDescription = "VUT Logo",
                modifier = Modifier.height(28.dp).width(90.dp)
            )
        }
    }
}
