package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanTertiary
import com.example.ui.theme.DarkSlate700
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RealtimeSparklineChart(
    title: String,
    currentValue: String,
    unit: String,
    values: List<Float?>,
    timestampsMs: List<Long> = emptyList(),
    lineColor: Color = CyanTertiary,
    minY: Float? = null,
    maxY: Float? = null,
    modifier: Modifier = Modifier
) {
    val nonNulls = remember(values) { values.filterNotNull() }
    val dataMin = nonNulls.minOrNull() ?: 0f
    val dataMax = nonNulls.maxOrNull() ?: 100f

    // Adaptive bounds: expand suggested bounds whenever real data exceeds them
    val autoMin = if (minY != null) minOf(minY, dataMin) else dataMin
    val autoMax = if (maxY != null) maxOf(maxY, dataMax * 1.05f) else (dataMax + 1f)
    val range = if (autoMax - autoMin > 0.001f) autoMax - autoMin else 1f
    val cleanValues = remember(values, autoMin) { values.map { it ?: autoMin } }

    var inspectedIndex by remember(values.size) { mutableStateOf<Int?>(null) }
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val displayedValStr = inspectedIndex?.let { idx ->
        values.getOrNull(idx)?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
    } ?: currentValue

    val inspectedTimeStr = inspectedIndex?.let { idx ->
        timestampsMs.getOrNull(idx)?.let { tf.format(Date(it)) }
    }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (inspectedTimeStr != null) {
                    Text(
                        text = "@ $inspectedTimeStr",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = lineColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displayedValStr,
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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-axis scale labels
            Column(
                modifier = Modifier
                    .height(92.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = formatAxisVal(autoMax), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = formatAxisVal((autoMin + autoMax) / 2f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = formatAxisVal(autoMin), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(92.dp)
                    .background(DarkSlate700.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(cleanValues) {
                            detectTapGestures { offset ->
                                if (cleanValues.size >= 2 && size.width > 0) {
                                    val idx = ((offset.x / size.width) * (cleanValues.size - 1))
                                        .roundToInt().coerceIn(0, cleanValues.lastIndex)
                                    inspectedIndex = if (inspectedIndex == idx) null else idx
                                }
                            }
                        }
                        .pointerInput(cleanValues) {
                            detectHorizontalDragGestures(
                                onDragEnd = { inspectedIndex = null },
                                onDragCancel = { inspectedIndex = null }
                            ) { change, _ ->
                                if (cleanValues.size >= 2 && size.width > 0) {
                                    val idx = ((change.position.x / size.width) * (cleanValues.size - 1))
                                        .roundToInt().coerceIn(0, cleanValues.lastIndex)
                                    inspectedIndex = idx
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, 0f), end = Offset(w, 0f), strokeWidth = 1f)
                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = 1f)
                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, h), end = Offset(w, h), strokeWidth = 1f)

                    if (cleanValues.size >= 2) {
                        val stepX = w / (cleanValues.size - 1).coerceAtLeast(1)
                        val path = Path()
                        var started = false

                        values.forEachIndexed { i, rawVal ->
                            if (rawVal != null) {
                                val normY = ((rawVal - autoMin) / range).coerceIn(0f, 1f)
                                val y = h - (normY * h)
                                val x = i * stepX
                                if (!started) {
                                    path.moveTo(x, y)
                                    started = true
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                        }

                        if (started) {
                            drawPath(path = path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
                        }

                        // Highlight inspected point or end point
                        val highlightIdx = inspectedIndex ?: values.indexOfLast { it != null }
                        if (highlightIdx in values.indices && values[highlightIdx] != null) {
                            val v = values[highlightIdx]!!
                            val hx = highlightIdx * stepX
                            val hy = h - (((v - autoMin) / range).coerceIn(0f, 1f) * h)
                            if (inspectedIndex != null) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.45f),
                                    start = Offset(hx, 0f),
                                    end = Offset(hx, h),
                                    strokeWidth = 1.5f
                                )
                            }
                            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(hx, hy))
                        }
                    }
                }
            }
        }

        if (timestampsMs.size >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = tf.format(Date(timestampsMs.first())), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = tf.format(Date(timestampsMs.last())), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
            }
        }
    }
}

@Composable
fun DualRealtimeSparklineChart(
    title: String,
    label1: String,
    currentValue1: String,
    values1: List<Float?>,
    color1: Color = CyanTertiary,
    label2: String,
    currentValue2: String,
    values2: List<Float?>,
    color2: Color = Color(0xFFF59E0B),
    timestampsMs: List<Long> = emptyList(),
    unit: String = "dBm",
    minY: Float = -125f,
    maxY: Float = -65f,
    modifier: Modifier = Modifier
) {
    val allNonNull = remember(values1, values2) { (values1 + values2).filterNotNull() }
    val effMin = minOf(minY, allNonNull.minOrNull() ?: minY)
    val effMax = maxOf(maxY, allNonNull.maxOrNull() ?: maxY)
    val range = if (effMax - effMin > 0.001f) effMax - effMin else 1f

    val maxCount = maxOf(values1.size, values2.size)
    var inspectedIndex by remember(maxCount) { mutableStateOf<Int?>(null) }
    val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val dispVal1 = inspectedIndex?.let { idx ->
        values1.getOrNull(idx)?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    } ?: currentValue1

    val dispVal2 = inspectedIndex?.let { idx ->
        values2.getOrNull(idx)?.let { String.format(Locale.US, "%.0f", it) } ?: "—"
    } ?: currentValue2

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
            inspectedIndex?.let { idx ->
                timestampsMs.getOrNull(idx)?.let { ts ->
                    Text(
                        text = "@ ${tf.format(Date(ts))}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CyanTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(color1, RoundedCornerShape(5.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label1, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "$dispVal1 $unit", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = color1, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(color2, RoundedCornerShape(5.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = label2, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "$dispVal2 $unit", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = color2, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .height(105.dp)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(text = formatAxisVal(effMax), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = formatAxisVal((effMin + effMax) / 2f), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = formatAxisVal(effMin), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(105.dp)
                    .background(DarkSlate700.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(maxCount) {
                            detectTapGestures { offset ->
                                if (maxCount >= 2 && size.width > 0) {
                                    val idx = ((offset.x / size.width) * (maxCount - 1))
                                        .roundToInt().coerceIn(0, maxCount - 1)
                                    inspectedIndex = if (inspectedIndex == idx) null else idx
                                }
                            }
                        }
                        .pointerInput(maxCount) {
                            detectHorizontalDragGestures(
                                onDragEnd = { inspectedIndex = null },
                                onDragCancel = { inspectedIndex = null }
                            ) { change, _ ->
                                if (maxCount >= 2 && size.width > 0) {
                                    val idx = ((change.position.x / size.width) * (maxCount - 1))
                                        .roundToInt().coerceIn(0, maxCount - 1)
                                    inspectedIndex = idx
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, 0f), end = Offset(w, 0f), strokeWidth = 1f)
                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = 1f)
                    drawLine(color = Color.White.copy(alpha = 0.08f), start = Offset(0f, h), end = Offset(w, h), strokeWidth = 1f)

                    if (values1.size >= 2) {
                        val stepX = w / (values1.size - 1).coerceAtLeast(1)
                        val path1 = Path()
                        var s1 = false
                        values1.forEachIndexed { i, v ->
                            if (v != null) {
                                val y = h - (((v - effMin) / range).coerceIn(0f, 1f) * h)
                                val x = i * stepX
                                if (!s1) { path1.moveTo(x, y); s1 = true } else path1.lineTo(x, y)
                            }
                        }
                        if (s1) drawPath(path = path1, color = color1, style = Stroke(width = 2.5.dp.toPx()))

                        val hIdx = inspectedIndex ?: values1.indexOfLast { it != null }
                        if (hIdx in values1.indices && values1[hIdx] != null) {
                            val hy = h - (((values1[hIdx]!! - effMin) / range).coerceIn(0f, 1f) * h)
                            drawCircle(color = color1, radius = 4.dp.toPx(), center = Offset(hIdx * stepX, hy))
                        }
                    }

                    if (values2.size >= 2) {
                        val stepX = w / (values2.size - 1).coerceAtLeast(1)
                        val path2 = Path()
                        var s2 = false
                        values2.forEachIndexed { i, v ->
                            if (v != null) {
                                val y = h - (((v - effMin) / range).coerceIn(0f, 1f) * h)
                                val x = i * stepX
                                if (!s2) { path2.moveTo(x, y); s2 = true } else path2.lineTo(x, y)
                            }
                        }
                        if (s2) drawPath(path = path2, color = color2, style = Stroke(width = 2.5.dp.toPx()))

                        val hIdx = inspectedIndex ?: values2.indexOfLast { it != null }
                        if (hIdx in values2.indices && values2[hIdx] != null) {
                            val hy = h - (((values2[hIdx]!! - effMin) / range).coerceIn(0f, 1f) * h)
                            drawCircle(color = color2, radius = 4.dp.toPx(), center = Offset(hIdx * stepX, hy))
                        }
                    }

                    inspectedIndex?.let { idx ->
                        if (maxCount >= 2) {
                            val hx = idx * (w / (maxCount - 1))
                            drawLine(color = Color.White.copy(alpha = 0.45f), start = Offset(hx, 0f), end = Offset(hx, h), strokeWidth = 1.5f)
                        }
                    }
                }
            }
        }

        if (timestampsMs.size >= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = tf.format(Date(timestampsMs.first())), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
                Text(text = tf.format(Date(timestampsMs.last())), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = TextSecondaryDark)
            }
        }
    }
}

private fun formatAxisVal(value: Float): String {
    return if (kotlin.math.abs(value) >= 10f || value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}
