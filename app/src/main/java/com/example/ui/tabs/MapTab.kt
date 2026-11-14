package com.example.ui.tabs

import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.db.TelemetryRecord
import com.example.model.CellularState
import com.example.model.GnssState
import com.example.model.MapTrailPoint
import com.example.ui.theme.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MapVisualizationMode {
    RSRP_SIGNAL,
    SPEED,
    SATELLITES,
    PDOP
}

private fun computeSegmentColor(
    mode: MapVisualizationMode,
    simSlot: Int,
    sim1Rsrp: Int?,
    sim2Rsrp: Int?,
    speedKmh: Float,
    satellitesUsed: Int,
    pdop: Float
): Int {
    return when (mode) {
        MapVisualizationMode.RSRP_SIGNAL -> {
            val rsrp = if (simSlot == 1) (sim1Rsrp ?: sim2Rsrp) else (sim2Rsrp ?: sim1Rsrp)
            when {
                rsrp == null -> AndroidColor.parseColor("#64748B")
                rsrp >= -80 -> AndroidColor.parseColor("#10B981")
                rsrp >= -90 -> AndroidColor.parseColor("#84CC16")
                rsrp >= -105 -> AndroidColor.parseColor("#F59E0B")
                else -> AndroidColor.parseColor("#F43F5E")
            }
        }
        MapVisualizationMode.SPEED -> {
            when {
                speedKmh < 10f -> AndroidColor.parseColor("#3B82F6")
                speedKmh < 50f -> AndroidColor.parseColor("#06B6D4")
                speedKmh < 90f -> AndroidColor.parseColor("#10B981")
                else -> AndroidColor.parseColor("#F43F5E")
            }
        }
        MapVisualizationMode.SATELLITES -> {
            when {
                satellitesUsed < 5 -> AndroidColor.parseColor("#F43F5E")
                satellitesUsed <= 20 -> AndroidColor.parseColor("#F59E0B")
                satellitesUsed <= 30 -> AndroidColor.parseColor("#06B6D4")
                satellitesUsed <= 50 -> AndroidColor.parseColor("#10B981")
                else -> AndroidColor.parseColor("#3B82F6")
            }
        }
        MapVisualizationMode.PDOP -> {
            when {
                satellitesUsed < 4 || pdop <= 0f || pdop >= 9.0f -> AndroidColor.parseColor("#64748B")
                pdop < 1.0f -> AndroidColor.parseColor("#10B981")
                pdop < 2.0f -> AndroidColor.parseColor("#06B6D4")
                pdop < 4.0f -> AndroidColor.parseColor("#F59E0B")
                else -> AndroidColor.parseColor("#F43F5E")
            }
        }
    }
}

@Composable
fun MapTab(
    gnss: GnssState,
    cellular: CellularState,
    liveTrail: List<MapTrailPoint>,
    replayRecords: List<TelemetryRecord>?,
    focusedReplayPoint: TelemetryRecord? = null,
    darkMapMode: Boolean = true,
    onToggleDarkMap: (Boolean) -> Unit = {},
    lang: String,
    onClearReplay: () -> Unit,
    onClearFocusedPoint: () -> Unit = {},
    onClearLiveTrail: () -> Unit = {}
) {
    val isCs = lang != "EN"
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var activeMode by remember { mutableStateOf(MapVisualizationMode.RSRP_SIGNAL) }
    var mapRsrpSimSlot by remember {
        mutableStateOf(if (cellular.sim2.isDataActive && !cellular.sim1.isDataActive) 2 else 1)
    }
    var autoFollow by remember { mutableStateOf(true) }
    var selectedReplayPoint by remember { mutableStateOf<TelemetryRecord?>(null) }

    val trackOverlays = remember { mutableListOf<org.osmdroid.views.overlay.Overlay>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle sync for OSMDroid MapView (actual detach happens in AndroidView.onRelease)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val target = mapViewInstance ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> target.onResume()
                Lifecycle.Event.ON_PAUSE -> target.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Apply dark or light tile color filter when darkMapMode or MapView changes
    LaunchedEffect(mapViewInstance, darkMapMode) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (darkMapMode) {
            val invertMatrix = ColorMatrix(
                floatArrayOf(
                    -0.92f, 0f, 0f, 0f, 232f,
                    0f, -0.92f, 0f, 0f, 232f,
                    0f, 0f, -0.92f, 0f, 238f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            mv.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(invertMatrix))
        } else {
            mv.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mv.invalidate()
    }

    // Update live position marker & auto-follow smoothly in Live mode (no zoom reset)
    LaunchedEffect(gnss.latitude, gnss.longitude, replayRecords, focusedReplayPoint, autoFollow, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (replayRecords == null && gnss.hasFix && (gnss.latitude != 0.0 || gnss.longitude != 0.0)) {
            val point = GeoPoint(gnss.latitude, gnss.longitude)
            locationMarker?.position = point
            if (autoFollow && focusedReplayPoint == null) {
                mv.controller.animateTo(point)
            }
            mv.invalidate()
        }
    }

    // Center camera when opening replay session or jumping to a specific history point
    LaunchedEffect(replayRecords, focusedReplayPoint, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (focusedReplayPoint != null && (focusedReplayPoint.latitude != 0.0 || focusedReplayPoint.longitude != 0.0)) {
            autoFollow = false
            selectedReplayPoint = focusedReplayPoint
            mv.controller.setZoom(17.5)
            mv.controller.setCenter(GeoPoint(focusedReplayPoint.latitude, focusedReplayPoint.longitude))
            mv.invalidate()
        } else if (replayRecords != null && replayRecords.isNotEmpty()) {
            selectedReplayPoint = null
            val firstValid = replayRecords.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 }
            if (firstValid != null) {
                mv.controller.setZoom(15.5)
                mv.controller.setCenter(GeoPoint(firstValid.latitude, firstValid.longitude))
                mv.invalidate()
            }
        } else {
            selectedReplayPoint = null
        }
    }

    // Unified colored polyline renderer for BOTH Live trail AND Historical Replay
    LaunchedEffect(replayRecords, liveTrail, activeMode, mapRsrpSimSlot, focusedReplayPoint, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect

        if (trackOverlays.isNotEmpty()) {
            mv.overlays.removeAll(trackOverlays.toSet())
            trackOverlays.clear()
        }

        if (replayRecords != null && replayRecords.isNotEmpty()) {
            val validRecords = replayRecords.filter { it.latitude != 0.0 || it.longitude != 0.0 }
            if (validRecords.size >= 2) {
                var curPoints = mutableListOf<GeoPoint>()
                var curColor = 0

                for (i in 0 until validRecords.size - 1) {
                    val r = validRecords[i]
                    val nextR = validRecords[i + 1]
                    val segColor = computeSegmentColor(
                        mode = activeMode,
                        simSlot = mapRsrpSimSlot,
                        sim1Rsrp = r.sim1Rsrp,
                        sim2Rsrp = r.sim2Rsrp,
                        speedKmh = r.speedKmh,
                        satellitesUsed = r.satellitesUsedInFix,
                        pdop = r.pdop
                    )

                    if (curPoints.isEmpty()) {
                        curPoints.add(GeoPoint(r.latitude, r.longitude))
                        curPoints.add(GeoPoint(nextR.latitude, nextR.longitude))
                        curColor = segColor
                    } else if (segColor == curColor) {
                        curPoints.add(GeoPoint(nextR.latitude, nextR.longitude))
                    } else {
                        val poly = Polyline().apply {
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.color = curColor
                            setPoints(curPoints)
                        }
                        mv.overlays.add(poly)
                        trackOverlays.add(poly)

                        curPoints = mutableListOf(GeoPoint(r.latitude, r.longitude), GeoPoint(nextR.latitude, nextR.longitude))
                        curColor = segColor
                    }
                }

                if (curPoints.isNotEmpty()) {
                    val poly = Polyline().apply {
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.color = curColor
                        setPoints(curPoints)
                    }
                    mv.overlays.add(poly)
                    trackOverlays.add(poly)
                }
            }

            // Start, End, Waypoint, and Focused Point markers
            validRecords.forEachIndexed { idx, r ->
                val isWaypoint = !r.waypointNote.isNullOrBlank()
                val isFocused = focusedReplayPoint != null && r.id == focusedReplayPoint.id
                if (isFocused || isWaypoint || idx == 0 || idx == validRecords.lastIndex) {
                    val m = Marker(mv).apply {
                        position = GeoPoint(r.latitude, r.longitude)
                        title = when {
                            isFocused -> if (isCs) "Vybraný bod #${r.id}" else "Selected #${r.id}"
                            isWaypoint -> "★ ${r.waypointNote}"
                            idx == 0 -> "Start"
                            else -> if (isCs) "Konec" else "End"
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedReplayPoint = r
                            true
                        }
                    }
                    mv.overlays.add(m)
                    trackOverlays.add(m)
                }
            }

            // Hide live cursor marker while replaying historical track
            locationMarker?.let { mv.overlays.remove(it) }
        } else {
            // LIVE MODE: draw segmented colored path from background liveTrail
            if (liveTrail.size >= 2) {
                var curPoints = mutableListOf<GeoPoint>()
                var curColor = 0

                for (i in 0 until liveTrail.size - 1) {
                    val p = liveTrail[i]
                    val nextP = liveTrail[i + 1]
                    val segColor = computeSegmentColor(
                        mode = activeMode,
                        simSlot = mapRsrpSimSlot,
                        sim1Rsrp = p.sim1Rsrp,
                        sim2Rsrp = p.sim2Rsrp,
                        speedKmh = p.speedKmh,
                        satellitesUsed = p.satellitesUsed,
                        pdop = p.pdop
                    )

                    if (curPoints.isEmpty()) {
                        curPoints.add(GeoPoint(p.latitude, p.longitude))
                        curPoints.add(GeoPoint(nextP.latitude, nextP.longitude))
                        curColor = segColor
                    } else if (segColor == curColor) {
                        curPoints.add(GeoPoint(nextP.latitude, nextP.longitude))
                    } else {
                        val poly = Polyline().apply {
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.color = curColor
                            setPoints(curPoints)
                        }
                        mv.overlays.add(poly)
                        trackOverlays.add(poly)

                        curPoints = mutableListOf(GeoPoint(p.latitude, p.longitude), GeoPoint(nextP.latitude, nextP.longitude))
                        curColor = segColor
                    }
                }

                if (curPoints.isNotEmpty()) {
                    val poly = Polyline().apply {
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.color = curColor
                        setPoints(curPoints)
                    }
                    mv.overlays.add(poly)
                    trackOverlays.add(poly)
                }
            }

            // Keep live position marker above polylines
            locationMarker?.let { marker ->
                mv.overlays.remove(marker)
                mv.overlays.add(marker)
            }
        }
        mv.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("map_view_container"),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(16.5)

                    val initPos = when {
                        focusedReplayPoint != null && (focusedReplayPoint.latitude != 0.0 || focusedReplayPoint.longitude != 0.0) ->
                            GeoPoint(focusedReplayPoint.latitude, focusedReplayPoint.longitude)
                        gnss.latitude != 0.0 || gnss.longitude != 0.0 ->
                            GeoPoint(gnss.latitude, gnss.longitude)
                        liveTrail.isNotEmpty() ->
                            GeoPoint(liveTrail.last().latitude, liveTrail.last().longitude)
                        else -> GeoPoint(49.2266, 16.5746)
                    }
                    controller.setCenter(initPos)

                    val marker = Marker(this).apply {
                        position = initPos
                        title = if (isCs) "Aktuální pozice" else "Current position"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(marker)
                    locationMarker = marker
                    onResume()

                    mapViewInstance = this
                }
            },
            onRelease = { mv ->
                if (mapViewInstance === mv) {
                    mapViewInstance = null
                }
                locationMarker = null
                trackOverlays.clear()
                mv.onPause()
                mv.overlays.clear()
                mv.onDetach()
            }
        )

        // Top Switcher Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    ModeButton(
                        label = "RSRP",
                        selected = activeMode == MapVisualizationMode.RSRP_SIGNAL,
                        onClick = { activeMode = MapVisualizationMode.RSRP_SIGNAL }
                    )
                    ModeButton(
                        label = if (isCs) "Rychlost" else "Speed",
                        selected = activeMode == MapVisualizationMode.SPEED,
                        onClick = { activeMode = MapVisualizationMode.SPEED }
                    )
                    ModeButton(
                        label = if (isCs) "Satelity" else "Sats",
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

            // Dark/Light map quick toggle
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
            ) {
                IconButton(
                    onClick = { onToggleDarkMap(!darkMapMode) },
                    modifier = Modifier.size(36.dp).testTag("toggle_dark_map_button")
                ) {
                    Icon(
                        imageVector = if (darkMapMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isCs) "Přepnout režim mapy" else "Toggle Dark Map",
                        tint = if (darkMapMode) AmberWarning else CyanTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Floating Legend Box in Top-Left (S1/S2 toggle available in both Live & Replay)
        Card(
            modifier = Modifier
                .padding(start = 12.dp, top = 66.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.92f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (activeMode) {
                    MapVisualizationMode.RSRP_SIGNAL -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = if (isCs) "LEGENDA RSRP" else "RSRP LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                            FilterChip(
                                selected = mapRsrpSimSlot == 1,
                                onClick = { mapRsrpSimSlot = 1 },
                                label = { Text("S1", fontSize = 9.sp) },
                                modifier = Modifier.height(22.dp)
                            )
                            FilterChip(
                                selected = mapRsrpSimSlot == 2,
                                onClick = { mapRsrpSimSlot = 2 },
                                label = { Text("S2", fontSize = 9.sp) },
                                modifier = Modifier.height(22.dp)
                            )
                        }
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
                        Text(text = if (isCs) "LEGENDA SATELITY (POUŽITÉ)" else "SATELLITES LEGEND (USED)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = RoseCritical, text = if (isCs) "< 5 satelitů (Slabý fix)" else "< 5 sats (Poor fix)")
                        LegendRow(color = AmberWarning, text = if (isCs) "5 až 20 satelitů" else "5 to 20 sats")
                        LegendRow(color = CyanTertiary, text = if (isCs) "21 až 30 satelitů" else "21 to 30 sats")
                        LegendRow(color = EmeraldSignal, text = if (isCs) "31 až 50 satelitů" else "31 to 50 sats")
                        LegendRow(color = BlueSatellite, text = if (isCs) "> 50 satelitů (Nejlepší)" else "> 50 sats (Best)")
                    }
                    MapVisualizationMode.PDOP -> {
                        Text(text = if (isCs) "LEGENDA PDOP" else "PDOP LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = EmeraldSignal, text = if (isCs) "< 1.0 (Nejlepší)" else "< 1.0 (Best)")
                        LegendRow(color = CyanTertiary, text = if (isCs) "1.0 až 2.0 (Výborný)" else "1.0 to 2.0 (Good)")
                        LegendRow(color = AmberWarning, text = if (isCs) "2.0 až 4.0 (Zhoršený)" else "2.0 to 4.0 (Fair)")
                        LegendRow(color = RoseCritical, text = if (isCs) "≥ 4.0 (Nejhorší)" else "≥ 4.0 (Worst)")
                    }
                }
            }
        }

        // Bottom-left status banner: Replay mode OR Live trail point count + Clear button
        if (replayRecords != null) {
            Card(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 24.dp, end = 76.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyanSecondary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCs) "Záznam relace (${replayRecords.size} bodů)" else "Replay track (${replayRecords.size} pts)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                    IconButton(onClick = {
                        onClearFocusedPoint()
                        onClearReplay()
                    }, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = if (isCs) "Zrušit" else "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        } else if (liveTrail.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 24.dp, end = 76.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.92f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCs) "Živá trasa: ${liveTrail.size} b." else "Live trail: ${liveTrail.size} pts",
                        color = CyanTertiary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onClearLiveTrail, modifier = Modifier.size(22.dp).testTag("clear_live_trail_button")) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = if (isCs) "Vymazat živou stopu" else "Clear live trail",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Selected Marker Inspector Card (from clicking marker OR deep-linking from History point)
        selectedReplayPoint?.let { pt ->
            val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
            Card(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = 74.dp, end = 76.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.96f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pt.waypointNote?.let { "★ $it (Bod #${pt.id})" } ?: "Bod #${pt.id} (${tf.format(Date(pt.timestampMs))})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        IconButton(onClick = {
                            selectedReplayPoint = null
                            onClearFocusedPoint()
                        }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = TextSecondaryDark, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = "S1: ${pt.sim1Rsrp ?: "N/A"} dBm (${pt.sim1Band}) • S2: ${pt.sim2Rsrp ?: "N/A"} dBm",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimaryDark
                    )
                    val hasValidPdop = pt.satellitesUsedInFix >= 4 && pt.pdop > 0f && pt.pdop < 9.0f
                    val pdopText = if (hasValidPdop) String.format(Locale.US, "%.2f", pt.pdop) else "—"
                    Text(
                        text = "${String.format(Locale.US, "%.1f", pt.speedKmh)} km/h • Sat: ${pt.satellitesUsedInFix}/${pt.satellitesInView} • PDOP: $pdopText",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )
                }
            }
        }

        // Bottom Right Floating Map Controls (+ / -, Auto-follow, Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (replayRecords == null) {
                SmallFloatingActionButton(
                    onClick = { autoFollow = !autoFollow },
                    containerColor = if (autoFollow) EmeraldSignal else DarkSlate800,
                    contentColor = if (autoFollow) DarkSlate900 else TextSecondaryDark,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (autoFollow) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                        contentDescription = if (isCs) "Automatické sledování polohy" else "Auto-Follow Position",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { mapViewInstance?.controller?.zoomIn() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom In", tint = TextPrimaryDark, modifier = Modifier.size(20.dp))
                    }
                    HorizontalDivider(color = DarkSlate700, modifier = Modifier.width(36.dp))
                    IconButton(
                        onClick = { mapViewInstance?.controller?.zoomOut() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom Out", tint = TextPrimaryDark, modifier = Modifier.size(20.dp))
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    val target = when {
                        focusedReplayPoint != null && (focusedReplayPoint.latitude != 0.0 || focusedReplayPoint.longitude != 0.0) ->
                            GeoPoint(focusedReplayPoint.latitude, focusedReplayPoint.longitude)
                        replayRecords != null && replayRecords.isNotEmpty() ->
                            replayRecords.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 }?.let { GeoPoint(it.latitude, it.longitude) }
                        gnss.latitude != 0.0 || gnss.longitude != 0.0 ->
                            GeoPoint(gnss.latitude, gnss.longitude)
                        liveTrail.isNotEmpty() ->
                            GeoPoint(liveTrail.last().latitude, liveTrail.last().longitude)
                        else -> null
                    }
                    target?.let { mapViewInstance?.controller?.animateTo(it) }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("recenter_map_button"),
                containerColor = CyanTertiary,
                contentColor = DarkSlate900,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = if (isCs) "Vycentrovat" else "Recenter", modifier = Modifier.size(20.dp))
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
