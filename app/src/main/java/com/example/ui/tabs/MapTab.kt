package com.example.ui.tabs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.db.TelemetryRecord
import com.example.model.CellularState
import com.example.model.GnssState
import com.example.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

enum class MapVisualizationMode {
    RSRP_SIGNAL,
    SPEED,
    SATELLITES,
    PDOP
}

@Composable
fun MapTab(
    gnss: GnssState,
    cellular: CellularState,
    replayRecords: List<TelemetryRecord>?,
    lang: String,
    onClearReplay: () -> Unit
) {
    val context = LocalContext.current
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var livePolyline by remember { mutableStateOf<Polyline?>(null) }
    var activeMode by remember { mutableStateOf(MapVisualizationMode.RSRP_SIGNAL) }

    val livePoints = remember { mutableStateListOf<GeoPoint>() }

    LaunchedEffect(gnss.latitude, gnss.longitude) {
        if (replayRecords == null && gnss.hasFix && gnss.latitude != 0.0 && gnss.longitude != 0.0) {
            val point = GeoPoint(gnss.latitude, gnss.longitude)
            livePoints.add(point)
            if (livePoints.size > 800) livePoints.removeAt(0)
            mapViewInstance?.let { mv ->
                locationMarker?.position = point
                livePolyline?.setPoints(livePoints)
                mv.invalidate()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("map_view_container"),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(16.5)

                    val initPos = if (gnss.latitude != 0.0) GeoPoint(gnss.latitude, gnss.longitude) else GeoPoint(49.2266, 16.5746) // FEKT VUT
                    controller.setCenter(initPos)

                    val line = Polyline().apply {
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.color = AndroidColor.parseColor("#06B6D4")
                    }
                    overlays.add(line)
                    livePolyline = line

                    val marker = Marker(this).apply {
                        position = initPos
                        title = "Aktuální pozice"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(marker)
                    locationMarker = marker

                    mapViewInstance = this
                }
            },
            update = { mv ->
                if (replayRecords != null && replayRecords.isNotEmpty()) {
                    mv.overlays.removeAll { it is Polyline && it != livePolyline }
                    val replayPolyline = Polyline().apply {
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.color = when (activeMode) {
                            MapVisualizationMode.RSRP_SIGNAL -> AndroidColor.parseColor("#10B981")
                            MapVisualizationMode.SPEED -> AndroidColor.parseColor("#3B82F6")
                            MapVisualizationMode.SATELLITES -> AndroidColor.parseColor("#F59E0B")
                            MapVisualizationMode.PDOP -> AndroidColor.parseColor("#06B6D4")
                        }
                        val geoPoints = replayRecords.map { GeoPoint(it.latitude, it.longitude) }
                        setPoints(geoPoints)
                    }
                    mv.overlays.add(replayPolyline)
                    mv.controller.setCenter(GeoPoint(replayRecords.first().latitude, replayRecords.first().longitude))
                    mv.invalidate()
                }
            }
        )

        // Top Switcher Bar matching image_7a3c57d5.png (RSRP Signál, Rychlost, Satelity, PDOP + Author badge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isCs = lang != "EN"
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    ModeButton(
                        label = if (isCs) "RSRP Signál" else "RSRP Signal",
                        selected = activeMode == MapVisualizationMode.RSRP_SIGNAL,
                        onClick = { activeMode = MapVisualizationMode.RSRP_SIGNAL }
                    )
                    ModeButton(
                        label = if (isCs) "Rychlost" else "Speed",
                        selected = activeMode == MapVisualizationMode.SPEED,
                        onClick = { activeMode = MapVisualizationMode.SPEED }
                    )
                    ModeButton(
                        label = if (isCs) "Satelity" else "Satellites",
                        selected = activeMode == MapVisualizationMode.SATELLITES,
                        onClick = { activeMode = MapVisualizationMode.SATELLITES }
                    )
                    ModeButton(
                        label = "PDOP",
                        selected = activeMode == MapVisualizationMode.PDOP,
                        onClick = { activeMode = MapVisualizationMode.PDOP }
                    )
                }
            }

            // Author Pill
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(RoseCritical, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Jan Kufa • VUT",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }
            }
        }

        // Floating Legend Box in Top-Left matching image_7a3c57d5.png
        val isCs = lang != "EN"
        Card(
            modifier = Modifier
                .padding(start = 12.dp, top = 64.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.92f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (activeMode) {
                    MapVisualizationMode.RSRP_SIGNAL -> {
                        Text(text = if (isCs) "LEGENDA RSRP" else "RSRP LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = EmeraldSignal, text = if (isCs) "> -80 dBm (Výborný)" else "> -80 dBm (Excellent)")
                        LegendRow(color = Color(0xFF84CC16), text = if (isCs) "-80 až -90 dBm" else "-80 to -90 dBm")
                        LegendRow(color = AmberWarning, text = if (isCs) "-90 až -105 dBm" else "-90 to -105 dBm")
                        LegendRow(color = RoseCritical, text = if (isCs) "< -105 dBm (Slabý)" else "< -105 dBm (Poor)")
                    }
                    MapVisualizationMode.SPEED -> {
                        Text(text = if (isCs) "LEGENDA RYCHLOST" else "SPEED LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = BlueSatellite, text = if (isCs) "< 10 km/h (Pěšky)" else "< 10 km/h (Walk)")
                        LegendRow(color = CyanTertiary, text = if (isCs) "10 až 50 km/h (Město)" else "10 to 50 km/h (Urban)")
                        LegendRow(color = EmeraldSignal, text = if (isCs) "50 až 90 km/h (Silnice)" else "50 to 90 km/h (Road)")
                        LegendRow(color = RoseCritical, text = if (isCs) "> 90 km/h (Dálnice)" else "> 90 km/h (Highway)")
                    }
                    MapVisualizationMode.SATELLITES -> {
                        Text(text = if (isCs) "LEGENDA SATELITY" else "SATELLITES LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = RoseCritical, text = if (isCs) "< 5 satelitů (Slabý fix)" else "< 5 sats (Poor fix)")
                        LegendRow(color = AmberWarning, text = if (isCs) "5 až 10 (Základní fix)" else "5 to 10 (Basic fix)")
                        LegendRow(color = EmeraldSignal, text = if (isCs) "11 až 25 (Výborný fix)" else "11 to 25 (Great fix)")
                        LegendRow(color = BlueSatellite, text = if (isCs) "> 25 satelitů (Top fix)" else "> 25 sats (Optimal)")
                    }
                    MapVisualizationMode.PDOP -> {
                        Text(text = if (isCs) "LEGENDA PDOP" else "PDOP LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = EmeraldSignal, text = if (isCs) "< 1.5 (Ideální)" else "< 1.5 (Ideal)")
                        LegendRow(color = CyanTertiary, text = if (isCs) "1.5 až 3.0 (Výborný)" else "1.5 to 3.0 (Good)")
                        LegendRow(color = AmberWarning, text = if (isCs) "3.0 až 5.0 (Vyhovující)" else "3.0 to 5.0 (Moderate)")
                        LegendRow(color = RoseCritical, text = if (isCs) "> 5.0 (Slabá geometrie)" else "> 5.0 (Poor geometry)")
                    }
                }
            }
        }

        // Replay active banner if loaded from history
        if (replayRecords != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 60.dp)
                    .align(Alignment.TopCenter),
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
                    Text(
                        text = "Přehrávání relace (${replayRecords.size} bodů)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = onClearReplay) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Zrušit", tint = Color.White)
                    }
                }
            }
        }

        // Bottom Right Floating Map Controls (+ / - zoom and Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In / Out
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Column {
                    IconButton(onClick = { mapViewInstance?.controller?.zoomIn() }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimaryDark)
                    }
                    HorizontalDivider(color = DarkSlate700)
                    IconButton(onClick = { mapViewInstance?.controller?.zoomOut() }) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimaryDark)
                    }
                }
            }

            // Recenter FAB matching bright cyan circle in image_7a3c57d5.png
            FloatingActionButton(
                onClick = {
                    if (gnss.latitude != 0.0 && gnss.longitude != 0.0) {
                        mapViewInstance?.controller?.animateTo(GeoPoint(gnss.latitude, gnss.longitude))
                    }
                },
                modifier = Modifier.testTag("recenter_map_button"),
                containerColor = CyanTertiary,
                contentColor = DarkSlate900,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Vycentrovat")
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) BlueSatellite else Color.Transparent,
            contentColor = if (selected) Color.White else TextSecondaryDark
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = TextPrimaryDark)
    }
}
