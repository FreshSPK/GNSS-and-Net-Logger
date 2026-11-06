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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.CyanTertiary

@Composable
fun RealtimeSparklineChart(
    title: String,
    currentValue: String,
    unit: String,
    values: List<Float?>,
    lineColor: Color = CyanTertiary,
    minY: Float? = null,
    maxY: Float? = null,
    modifier: Modifier = Modifier
) {
    val cleanValues = values.map { it ?: 0f }
    val autoMin = minY ?: (cleanValues.minOrNull() ?: 0f)
    val autoMax = maxY ?: (cleanValues.maxOrNull() ?: 100f)
    val range = if (autoMax - autoMin > 0.001f) autoMax - autoMin else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSlate800, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.titleMedium,
                    color = lineColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(DarkSlate700.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw subtle horizontal grid lines (0%, 50%, 100%)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(0f, h),
                    end = Offset(w, h),
                    strokeWidth = 1f
                )

                if (cleanValues.size >= 2) {
                    val stepX = w / (cleanValues.size - 1).coerceAtLeast(1)
                    val path = Path()

                    cleanValues.forEachIndexed { i, v ->
                        val normY = ((v - autoMin) / range).coerceIn(0f, 1f)
                        val y = h - (normY * h)
                        val x = i * stepX
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Draw end dot
                    val lastX = (cleanValues.size - 1) * stepX
                    val lastNormY = ((cleanValues.last() - autoMin) / range).coerceIn(0f, 1f)
                    val lastY = h - (lastNormY * h)
                    drawCircle(
                        color = lineColor,
                        radius = 4.dp.toPx(),
                        center = Offset(lastX, lastY)
                    )
                }
            }
        }
    }
}
