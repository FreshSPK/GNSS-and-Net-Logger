package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SatelliteInfo
import com.example.ui.theme.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class SkyPlotColorMode {
    CONSTELLATION,
    CN0_SIGNAL
}

private data class GroupedSkySat(
    val key: String,
    val svid: Int,
    val constellationName: String,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
    val isDualFreq: Boolean,
    val bandsJoined: String
)

@Composable
fun SkyPlotView(
    satellites: List<SatelliteInfo>,
    lang: String = "CS",
    modifier: Modifier = Modifier
) {
    val isCs = lang != "EN"
    var colorMode by remember { mutableStateOf(SkyPlotColorMode.CONSTELLATION) }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val groupedSatellites = remember(satellites) {
        satellites
            .groupBy { "${it.constellationType}_${it.svid}" }
            .map { (key, group) ->
                val first = group.first()
                GroupedSkySat(
                    key = key,
                    svid = first.svid,
                    constellationName = first.constellationName,
                    elevationDegrees = first.elevationDegrees,
                    azimuthDegrees = first.azimuthDegrees,
                    cn0DbHz = group.maxOf { it.cn0DbHz },
                    usedInFix = group.any { it.usedInFix },
                    isDualFreq = group.any {
                        it.bandLabel.contains("L5") || it.bandLabel.contains("E5") || it.bandLabel.contains("B2")
                    },
                    bandsJoined = group.map { it.bandLabel }.distinct().joinToString(" + ")
                )
            }
    }

    val selectedSat = remember(groupedSatellites, selectedKey) {
        selectedKey?.let { key ->
            groupedSatellites.firstOrNull { it.key == key }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSlate800, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Selector: Constellation vs C/N0 Signal Strength
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSlate700.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (colorMode == SkyPlotColorMode.CONSTELLATION) CyanSecondary else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { colorMode = SkyPlotColorMode.CONSTELLATION }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCs) "Podle konstelace" else "By Constellation",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (colorMode == SkyPlotColorMode.CONSTELLATION) Color.White else TextSecondaryDark
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (colorMode == SkyPlotColorMode.CN0_SIGNAL) CyanSecondary else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { colorMode = SkyPlotColorMode.CN0_SIGNAL }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCs) "Podle síly C/N₀" else "By C/N₀ Signal",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (colorMode == SkyPlotColorMode.CN0_SIGNAL) Color.White else TextSecondaryDark
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Skyplot polar chart with N/E/S/W compass & elevation labels
        Box(
            modifier = Modifier.size(268.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isCs) "S (0°)" else "N (0°)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyanTertiary,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Text(
                text = if (isCs) "J" else "S",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            Text(
                text = if (isCs) "Z" else "W",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = if (isCs) "V" else "E",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                modifier = Modifier.align(Alignment.CenterEnd)
            )

            Box(
                modifier = Modifier
                    .size(236.dp)
                    .background(DarkSlate700.copy(alpha = 0.35f), RoundedCornerShape(118.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(groupedSatellites) {
                            detectTapGestures { tapOffset ->
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val r = (size.width / 2f) * 0.9f
                                var nearest: GroupedSkySat? = null
                                var nearestDist = 42f
                                groupedSatellites.forEach { sat ->
                                    val elRad = (90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f
                                    val satDist = r * elRad
                                    val azRad = Math.toRadians((sat.azimuthDegrees - 90.0)).toFloat()
                                    val sx = cx + satDist * cos(azRad)
                                    val sy = cy + satDist * sin(azRad)
                                    val d = hypot(tapOffset.x - sx, tapOffset.y - sy)
                                    if (d < nearestDist) {
                                        nearestDist = d
                                        nearest = sat
                                    }
                                }
                                val tappedKey = nearest?.key
                                selectedKey = if (tappedKey == selectedKey) null else tappedKey
                            }
                        }
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = (size.width / 2f) * 0.9f

                    // Polar circles: 0° horizon, 30°, 60° elevation
                    drawCircle(color = Color.White.copy(alpha = 0.22f), radius = r, style = Stroke(width = 1.5f))
                    drawCircle(color = Color.White.copy(alpha = 0.16f), radius = r * 0.66f, style = Stroke(width = 1f))
                    drawCircle(color = Color.White.copy(alpha = 0.12f), radius = r * 0.33f, style = Stroke(width = 1f))

                    // Crosshairs: N-S, E-W
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(cx, cy - r),
                        end = Offset(cx, cy + r),
                        strokeWidth = 1f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.18f),
                        start = Offset(cx - r, cy),
                        end = Offset(cx + r, cy),
                        strokeWidth = 1f
                    )

                    groupedSatellites.forEach { sat ->
                        val elRad = (90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f
                        val satDist = r * elRad

                        val azRad = Math.toRadians((sat.azimuthDegrees - 90.0)).toFloat()
                        val sx = cx + satDist * cos(azRad)
                        val sy = cy + satDist * sin(azRad)

                        val isSelected = selectedKey == sat.key

                        val baseColor = if (colorMode == SkyPlotColorMode.CONSTELLATION) {
                            when (sat.constellationName.uppercase()) {
                                "GPS" -> CyanTertiary
                                "GALILEO" -> EmeraldSignal
                                "GLONASS" -> AmberWarning
                                "BEIDOU" -> Purple5G
                                else -> BlueSatellite
                            }
                        } else {
                            when {
                                !sat.usedInFix -> Color.Gray
                                sat.cn0DbHz >= 35f -> EmeraldSignal
                                sat.cn0DbHz >= 25f -> CyanTertiary
                                else -> AmberWarning
                            }
                        }

                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = 11.dp.toPx(),
                                center = Offset(sx, sy),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        if (sat.isDualFreq) {
                            drawCircle(
                                color = EmeraldSignal,
                                radius = if (sat.usedInFix) 8.2.dp.toPx() else 6.8.dp.toPx(),
                                center = Offset(sx, sy),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        if (sat.usedInFix) {
                            // In fix: solid saturated dot + crisp white ring (matches legend)
                            drawCircle(
                                color = Color.White,
                                radius = 5.6.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                            drawCircle(
                                color = baseColor,
                                radius = 4.3.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                        } else {
                            // Out of fix: hollow outline circle without solid fill (matches legend)
                            drawCircle(
                                color = baseColor.copy(alpha = 0.85f),
                                radius = 4.0.dp.toPx(),
                                center = Offset(sx, sy),
                                style = Stroke(width = 1.6.dp.toPx())
                            )
                        }
                    }
                }

                // Subtle elevation angle guides
                Text(
                    text = "60°",
                    fontSize = 8.sp,
                    color = TextSecondaryDark.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 69.dp)
                )
                Text(
                    text = "30°",
                    fontSize = 8.sp,
                    color = TextSecondaryDark.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 37.dp)
                )
            }
        }

        // Selected Satellite Quick Inspection Card
        if (selectedSat != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate700)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedSat.constellationName} #${selectedSat.svid} (${selectedSat.bandsJoined})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    Text(
                        text = "C/N₀: ${String.format(Locale.US, "%.1f", selectedSat.cn0DbHz)} dB-Hz • El ${selectedSat.elevationDegrees.toInt()}° Az ${selectedSat.azimuthDegrees.toInt()}°",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimaryDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Synchronized Legend (1:1 visual match with Skyplot markers)
        if (colorMode == SkyPlotColorMode.CONSTELLATION) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(color = CyanTertiary, label = "GPS")
                    LegendItem(color = EmeraldSignal, label = "Galileo")
                    LegendItem(color = AmberWarning, label = "GLONASS")
                    LegendItem(color = Purple5G, label = "BeiDou")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .border(1.5.dp, Color.White, CircleShape)
                            .background(CyanTertiary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Plný + bílý okraj = Ve fixu" else "Filled + white border = In fix",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .border(1.6.dp, TextSecondaryDark, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Prázdný kroužek = Mimo fix" else "Hollow ring = Unused",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).border(1.5.dp, EmeraldSignal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Zelený vnější kroužek = Pásmo L5/E5a/B2a" else "Green outer ring = L5/E5a/B2a band",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = EmeraldSignal
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LegendItem(color = EmeraldSignal, label = if (isCs) "≥ 35 dB-Hz (Silný)" else "≥ 35 dB-Hz (Strong)")
                    LegendItem(color = CyanTertiary, label = "25–34 dB-Hz")
                    LegendItem(color = AmberWarning, label = if (isCs) "< 25 dB-Hz (Slabý)" else "< 25 dB-Hz (Weak)")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .border(1.5.dp, Color.White, CircleShape)
                            .background(EmeraldSignal, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Plný + bílý okraj = Ve fixu" else "Filled + white border = In fix",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )

                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .border(1.6.dp, Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Šedý kroužek = Mimo fix" else "Gray ring = Unused",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).border(1.5.dp, EmeraldSignal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCs) "Zelený vnější kroužek = Pásmo L5/E5a/B2a" else "Green outer ring = L5/E5a/B2a band",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = EmeraldSignal
                    )
                }
            }
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
