package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SatelliteInfo
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

enum class SkyPlotColorMode {
    CONSTELLATION,
    CN0_SIGNAL
}

@Composable
fun SkyPlotView(
    satellites: List<SatelliteInfo>,
    lang: String = "CS",
    modifier: Modifier = Modifier
) {
    val isCs = lang != "EN"
    var colorMode by remember { mutableStateOf(SkyPlotColorMode.CONSTELLATION) }

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

        Spacer(modifier = Modifier.height(14.dp))

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

                // Plot satellites
                satellites.forEach { sat ->
                    val elRad = (90f - sat.elevationDegrees.coerceIn(0f, 90f)) / 90f
                    val satDist = r * elRad

                    // Azimuth: 0° North, 90° East (clock-wise from top)
                    val azRad = Math.toRadians((sat.azimuthDegrees - 90.0)).toFloat()
                    val sx = cx + satDist * cos(azRad)
                    val sy = cy + satDist * sin(azRad)

                    val isDualFreq = sat.bandLabel.contains("L5") || sat.bandLabel.contains("E5") || sat.bandLabel.contains("B2")

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

                    // Outer green ring for L5/E5a dual frequency
                    if (isDualFreq) {
                        drawCircle(
                            color = EmeraldSignal,
                            radius = if (sat.usedInFix) 8.dp.toPx() else 6.5.dp.toPx(),
                            center = Offset(sx, sy),
                            style = Stroke(width = 1.5f)
                        )
                    }

                    if (sat.usedInFix) {
                        // White halo border for active fix
                        drawCircle(
                            color = Color.White,
                            radius = 5.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                        // Solid inner core
                        drawCircle(
                            color = baseColor,
                            radius = 4.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    } else {
                        // Dimmed dot for unused satellites
                        drawCircle(
                            color = baseColor.copy(alpha = 0.45f),
                            radius = 3.5.dp.toPx(),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dynamic, 100% accurate Legend strictly matching selected mode
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
                    Box(modifier = Modifier.size(7.dp).background(Color.White, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCs) "Ve fixu" else "In fix", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)

                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(7.dp).border(1.dp, Color.Gray, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCs) "Mimo fix" else "Unused", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)

                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(9.dp).border(1.5.dp, EmeraldSignal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCs) "Zelený kroužek = L5/E5a" else "Green ring = L5/E5a", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = EmeraldSignal)
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
                    LegendItem(color = Color.Gray, label = if (isCs) "Mimo fix" else "Unused")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(7.dp).border(1.5.dp, Color.White, CircleShape).background(EmeraldSignal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCs) "Bílá záře = Ve fixu" else "White border = In fix", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)

                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(9.dp).border(1.5.dp, EmeraldSignal, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isCs) "Zelený kroužek = Pásmo L5/E5a" else "Green ring = L5/E5a band", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = EmeraldSignal)
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
