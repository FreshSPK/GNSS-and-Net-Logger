package com.example.ui.tabs

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
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
import com.example.ui.i18n.Strings
import com.example.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

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
    val livePoints = remember { mutableStateListOf<Pair<GeoPoint, Int>>() } // GeoPoint + satCount

    // Track historical or live points
    LaunchedEffect(gnss.latitude, gnss.longitude) {
        if (replayRecords == null && gnss.hasFix && gnss.latitude != 0.0 && gnss.longitude != 0.0) {
            val point = GeoPoint(gnss.latitude, gnss.longitude)
            livePoints.add(Pair(point, gnss.satellitesUsedInFix))
            if (livePoints.size > 500) {
                livePoints.removeAt(0)
            }
            mapViewInstance?.let { mv ->
                locationMarker?.position = point
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

                    val marker = Marker(this).apply {
                        position = initPos
                        title = "Current Position"
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
                    // Render replay path with satellite count color coding
                    val replayPolyline = Polyline().apply {
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.color = AndroidColor.parseColor("#06B6D4")
                        val geoPoints = replayRecords.map { GeoPoint(it.latitude, it.longitude) }
                        setPoints(geoPoints)
                    }
                    mv.overlays.add(replayPolyline)
                    mv.controller.setCenter(GeoPoint(replayRecords.first().latitude, replayRecords.first().longitude))
                    mv.invalidate()
                }
            }
        )

        // Replay Banner
        if (replayRecords != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyanSecondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Strings.get("replay_active", lang)} (${replayRecords.size} pts)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = onClearReplay) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = Strings.get("clear_replay", lang), tint = Color.White)
                    }
                }
            }
        }

        // Live HUD Overlay (Speed, RSRP dBm, Accuracy, DOP, Satellites, Ping)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.92f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Top row HUD
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MapHudItem(label = "SPD", value = "${String.format("%.1f", gnss.speedKmh)} km/h", color = TextPrimaryDark)
                    MapHudItem(label = "RSRP", value = cellular.sim1.rsrp?.let { "$it dBm" } ?: "N/A", color = CyanTertiary)
                    MapHudItem(label = "ACC", value = "±${String.format("%.1f", gnss.horizontalAccuracyM)}m", color = EmeraldSignal)
                    MapHudItem(label = "SATS", value = "${gnss.satellitesUsedInFix} / ${gnss.satellitesInView}", color = BlueSatellite)
                    MapHudItem(label = "PING", value = cellular.pingRttMs?.let { "${it}ms" } ?: "—", color = AmberWarning)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Satellite Color-Coding Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Satellites Track Color:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )
                    Row {
                        SatColorPill(color = RoseCritical, label = "< 5")
                        Spacer(modifier = Modifier.width(4.dp))
                        SatColorPill(color = AmberWarning, label = "5-10")
                        Spacer(modifier = Modifier.width(4.dp))
                        SatColorPill(color = EmeraldSignal, label = "11-25")
                        Spacer(modifier = Modifier.width(4.dp))
                        SatColorPill(color = BlueSatellite, label = "> 25")
                    }
                }
            }
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = {
                if (gnss.latitude != 0.0 && gnss.longitude != 0.0) {
                    mapViewInstance?.controller?.animateTo(GeoPoint(gnss.latitude, gnss.longitude))
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (replayRecords != null) 80.dp else 20.dp, end = 16.dp)
                .testTag("recenter_map_button"),
            containerColor = CyanPrimary,
            contentColor = DarkSlate900
        ) {
            Icon(imageVector = Icons.Default.MyLocation, contentDescription = Strings.get("center_location", lang))
        }
    }
}

@Composable
private fun MapHudItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun SatColorPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = TextSecondaryDark)
    }
}
