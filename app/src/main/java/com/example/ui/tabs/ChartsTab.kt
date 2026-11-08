package com.example.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ChartPoint
import com.example.ui.components.DualRealtimeSparklineChart
import com.example.ui.components.RealtimeSparklineChart
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun ChartsTab(
    chartPoints: List<ChartPoint>,
    lang: String
) {
    val isCs = lang != "EN"

    val rsrpSim1Values = chartPoints.map { it.rsrp }
    val rsrpSim2Values = chartPoints.map { it.rsrpSim2 }
    val speedValues = chartPoints.map { it.speedKmh }
    val satValues = chartPoints.map { it.satCount.toFloat() }
    val pingValues = chartPoints.map { it.pingMs }
    val accelValues = chartPoints.map { it.accelMagnitude }
    val gyroValues = chartPoints.map { it.gyroZ }
    val compassValues = chartPoints.map { it.compassAzimuth }

    val lastPoint = chartPoints.lastOrNull()
    val lastRsrp1 = lastPoint?.rsrp?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val lastRsrp2 = lastPoint?.rsrpSim2?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    val sim1Label = lastPoint?.sim1Operator?.takeIf { it.isNotBlank() } ?: "SIM 1"
    val sim2Label = lastPoint?.sim2Operator?.takeIf { it.isNotBlank() } ?: "SIM 2"

    val lastSpeed = chartPoints.lastOrNull()?.let { String.format("%.1f", it.speedKmh) } ?: "0.0"
    val lastSats = chartPoints.lastOrNull()?.satCount?.toString() ?: "0"
    val lastPing = chartPoints.lastOrNull()?.pingMs?.let { String.format("%.0f", it) } ?: "—"
    val lastAccel = chartPoints.lastOrNull()?.let { String.format("%.2f", it.accelMagnitude) } ?: "9.81"
    val lastGyro = chartPoints.lastOrNull()?.let { String.format("%.2f", it.gyroZ) } ?: "0.00"
    val lastCompass = chartPoints.lastOrNull()?.let { String.format("%.0f°", it.compassAzimuth) } ?: "143°"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = if (isCs) "Časové průběhy v reálném čase" else "Real-time Telemetry Waveforms",
            style = MaterialTheme.typography.titleMedium,
            color = CyanTertiary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Serving RSRP Chart (Dual SIM: SIM 1 vs SIM 2)
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
            unit = "dBm",
            minY = -125f,
            maxY = -65f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Speed Chart
        RealtimeSparklineChart(
            title = if (isCs) "Rychlost pohybu vozidla / chodce" else "Movement Speed (Vehicle / Walk)",
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
            title = if (isCs) "Počet satelitů v fixu a v dosahu" else "Satellites in Fix and in View",
            currentValue = lastSats,
            unit = if (isCs) "satelitů" else "sats",
            values = satValues,
            lineColor = BlueSatellite,
            minY = 0f,
            maxY = 35f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Ping Latency Chart
        RealtimeSparklineChart(
            title = if (isCs) "Latence pingu (Google DNS 8.8.8.8)" else "Ping RTT Latency (Google DNS 8.8.8.8)",
            currentValue = lastPing,
            unit = "ms",
            values = pingValues,
            lineColor = AmberWarning,
            minY = 0f,
            maxY = 200f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Accelerometer Magnitude Chart
        RealtimeSparklineChart(
            title = if (isCs) "Akcelerometr: Výsledné přetížení / otřesy" else "Accelerometer: Resultant G-Force / Jolt",
            currentValue = lastAccel,
            unit = "m/s²",
            values = accelValues,
            lineColor = Color(0xFFA855F7),
            minY = 5f,
            maxY = 18f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 6. Gyroscope Z Chart
        RealtimeSparklineChart(
            title = if (isCs) "Gyroskop: Úhlová rychlost zatáčení (Z-osa)" else "Gyroscope: Yaw Angular Velocity (Z-axis)",
            currentValue = lastGyro,
            unit = "rad/s",
            values = gyroValues,
            lineColor = Color(0xFFEC4899),
            minY = -1.5f,
            maxY = 1.5f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 7. Magnetometer Compass Chart
        RealtimeSparklineChart(
            title = if (isCs) "Magnetický kompas: Azimut natočení" else "Magnetic Compass: Azimuth Heading",
            currentValue = lastCompass,
            unit = "T (směr)",
            values = compassValues,
            lineColor = Color(0xFFF97316),
            minY = 0f,
            maxY = 360f
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 8. Battery Temperature Chart (v1.1 Thermal Throttle Monitor)
        val batteryTempValues = chartPoints.map { it.batteryTempC }
        val lastTemp = chartPoints.lastOrNull()?.batteryTempC?.let { String.format("%.1f", it) } ?: "30.4"
        RealtimeSparklineChart(
            title = if (isCs) "Teplota baterie telefonu (Termální monitoring)" else "Battery Temperature (Thermal Monitoring)",
            currentValue = lastTemp,
            unit = "°C",
            values = batteryTempValues,
            lineColor = Color(0xFFE11D48),
            minY = 20f,
            maxY = 48f
        )

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
                contentDescription = "VUT Logo",
                modifier = Modifier.height(28.dp).width(90.dp)
            )
        }
    }
}
