package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SatelliteInfo
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.AmberWarning
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SkyPlotView(
    satellites: List<SatelliteInfo>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSlate800, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(DarkSlate700.copy(alpha = 0.35f), RoundedCornerShape(120.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = (size.width / 2f) * 0.9f

                // Polar circles: 0° horizon, 30°, 60° elevation
                drawCircle(color = Color.White.copy(alpha = 0.2f), radius = r, style = Stroke(width = 1.5f))
                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = r * 0.66f, style = Stroke(width = 1f))
                drawCircle(color = Color.White.copy(alpha = 0.12f), radius = r * 0.33f, style = Stroke(width = 1f))

                // Crosshairs: N-S, E-W
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(cx, cy - r),
                    end = Offset(cx, cy + r),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = Offset(cx - r, cy),
                    end = Offset(cx + r, cy),
                    strokeWidth = 1f
                )

                // Plot satellites
                satellites.forEach { sat ->
                    // Elevation: 0° is horizon (radius r), 90° is zenith (center)
                    val elRad = (90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f
                    val satDist = r * elRad

                    // Azimuth: 0° North, 90° East (clock-wise from top)
                    val azRad = Math.toRadians((sat.azimuthDegrees - 90.0)).toFloat()
                    val sx = cx + satDist * cos(azRad)
                    val sy = cy + satDist * sin(azRad)

                    val satColor = when {
                        !sat.usedInFix -> Color.Gray
                        sat.cn0DbHz >= 35f -> EmeraldSignal
                        sat.cn0DbHz >= 25f -> CyanTertiary
                        else -> AmberWarning
                    }

                    // Dot
                    drawCircle(
                        color = satColor,
                        radius = if (sat.usedInFix) 5.dp.toPx() else 3.5.dp.toPx(),
                        center = Offset(sx, sy)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(color = EmeraldSignal, label = "C/N0 ≥ 35 (Strong)")
            LegendItem(color = CyanTertiary, label = "25-34 dB-Hz")
            LegendItem(color = AmberWarning, label = "< 25 dB-Hz")
            LegendItem(color = Color.Gray, label = "Unused")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
