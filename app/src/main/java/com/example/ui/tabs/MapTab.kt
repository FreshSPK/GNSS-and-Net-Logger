package com.example.ui.tabs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bts.BtsGeoUtils
import com.example.data.db.BtsTowerEntity
import com.example.data.db.TelemetryRecord
import com.example.model.CellularState
import com.example.model.GnssState
import com.example.model.MapTrailPoint
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

enum class MapVisualizationMode {
    RSRP_SIGNAL,
    SPEED,
    SATELLITES,
    PDOP,
    BTS_ONLY
}

private data class SelectedBtsSite(
    val plmn: String,
    val operatorName: String,
    val locationName: String,
    val enodebId: Long,
    val primaryCellId: Long,
    val primaryBand: String,
    val primaryPci: Int,
    val primaryTac: Int,
    val latitude: Double,
    val longitude: Double,
    val coordSource: String,
    val districtCode: String,
    val updatedDate: String,
    val bandsSummary: List<String>,
    val isServingSlot: Int? = null,
    val siteCells: List<BtsTowerEntity> = emptyList(),
    val isLoadingCells: Boolean = false
)

private fun shortMapOperatorName(raw: String): String {
    val cleaned = raw.replace(Regex("""\s*[-–]?\s*CZ$""", RegexOption.IGNORE_CASE), "").trim()
    return cleaned.ifEmpty { raw }
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
                satellitesUsed < 5 -> AndroidColor.parseColor("#F43F5E")   // Red
                satellitesUsed <= 20 -> AndroidColor.parseColor("#F97316") // Orange
                satellitesUsed <= 30 -> AndroidColor.parseColor("#EAB308") // Yellow / Gold
                satellitesUsed <= 50 -> AndroidColor.parseColor("#10B981") // Emerald Green
                else -> AndroidColor.parseColor("#8B5CF6")                 // Distinct Violet
            }
        }
        MapVisualizationMode.PDOP -> {
            when {
                satellitesUsed < 4 || pdop <= 0f || pdop >= 9.0f -> AndroidColor.parseColor("#64748B")
                pdop < 0.7f -> AndroidColor.parseColor("#10B981")          // Emerald Green (Best)
                pdop < 1.5f -> AndroidColor.parseColor("#EAB308")          // Yellow / Gold
                pdop < 2.5f -> AndroidColor.parseColor("#F97316")          // Orange
                pdop <= 3.5f -> AndroidColor.parseColor("#8B5CF6")         // Distinct Violet
                else -> AndroidColor.parseColor("#F43F5E")                 // Red (Worst)
            }
        }
        MapVisualizationMode.BTS_ONLY -> AndroidColor.TRANSPARENT
    }
}

private fun operatorMarkerArgb(plmn: String): Int = when (plmn) {
    "23001" -> TMobileMagenta.toArgb()
    "23002" -> O2Blue.toArgb()
    "23003" -> VodafoneRed.toArgb()
    else -> CyanTertiary.toArgb()
}

private fun operatorComposeColor(plmn: String): Color = when (plmn) {
    "23001" -> TMobileMagenta
    "23002" -> O2Blue
    "23003" -> VodafoneRed
    else -> CyanTertiary
}

private fun createBtsMarkerDrawable(
    context: Context,
    fillArgb: Int,
    strokeArgb: Int,
    label: String,
    isServing: Boolean = false
): BitmapDrawable {
    val sizePx = if (isServing) 52 else 38
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f - 4f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillArgb
        style = Paint.Style.FILL
    }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeArgb
        style = Paint.Style.STROKE
        strokeWidth = if (isServing) 4.5f else 2.5f
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = if (isServing) 18f else 13f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    canvas.drawCircle(cx, cy, radius, fillPaint)
    canvas.drawCircle(cx, cy, radius, strokePaint)
    val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, baseline, textPaint)
    return BitmapDrawable(context.resources, bmp)
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
    onClearLiveTrail: () -> Unit = {},
    loadNearbyBts: suspend (lat: Double, lon: Double) -> List<BtsTowerEntity> = { _, _ -> emptyList() },
    loadSiteCells: suspend (plmn: String, enodebId: Long, cellId: Long) -> List<BtsTowerEntity> = { _, _, _ -> emptyList() },
    onInspectBtsInDb: (query: String) -> Unit = {},
    focusedBts: Triple<Double, Double, String>? = null,
    onClearFocusedBts: () -> Unit = {}
) {
    val isCs = lang != "EN"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var locationMarker by remember { mutableStateOf<Marker?>(null) }
    var selectedPointMarker by remember { mutableStateOf<Marker?>(null) }
    var activeMode by remember { mutableStateOf(MapVisualizationMode.RSRP_SIGNAL) }
    var mapRsrpSimSlot by remember {
        mutableStateOf(if (cellular.sim2.isDataActive && !cellular.sim1.isDataActive) 2 else 1)
    }
    var autoFollow by remember { mutableStateOf(true) }
    var showBtsLayer by remember { mutableStateOf(true) }
    var selectedReplayPoint by remember { mutableStateOf<TelemetryRecord?>(null) }
    var selectedBtsSite by remember { mutableStateOf<SelectedBtsSite?>(null) }
    var mapViewportCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val latestReplayRecords by rememberUpdatedState(replayRecords)
    val latestActiveMode by rememberUpdatedState(activeMode)
    val latestContext by rememberUpdatedState(context)

    val trackOverlays = remember { mutableListOf<org.osmdroid.views.overlay.Overlay>() }
    val btsOverlays = remember { mutableListOf<org.osmdroid.views.overlay.Overlay>() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Intercept system Back gesture inside MapTab before MainActivity handles tab switching
    BackHandler(
        enabled = selectedBtsSite != null || selectedReplayPoint != null || focusedBts != null || replayRecords != null
    ) {
        when {
            selectedBtsSite != null -> selectedBtsSite = null
            selectedReplayPoint != null -> {
                selectedReplayPoint = null
                onClearFocusedPoint()
            }
            focusedBts != null -> onClearFocusedBts()
            replayRecords != null -> {
                onClearFocusedPoint()
                onClearReplay()
            }
        }
    }

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
    LaunchedEffect(gnss.latitude, gnss.longitude, replayRecords, focusedReplayPoint, autoFollow, activeMode, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (replayRecords == null && activeMode != MapVisualizationMode.BTS_ONLY && gnss.hasFix && (gnss.latitude != 0.0 || gnss.longitude != 0.0)) {
            val point = GeoPoint(gnss.latitude, gnss.longitude)
            locationMarker?.position = point
            if (autoFollow && focusedReplayPoint == null && focusedBts == null) {
                mv.controller.animateTo(point)
            }
            mv.invalidate()
        }
    }

    // Center camera when jumping to a focused BTS tower
    LaunchedEffect(focusedBts, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        val target = focusedBts ?: return@LaunchedEffect
        autoFollow = false
        showBtsLayer = true
        mv.controller.setZoom(16.5)
        mv.controller.setCenter(GeoPoint(target.first, target.second))
        mv.invalidate()
    }

    // Center camera when opening replay session or jumping to a specific history point
    LaunchedEffect(replayRecords, focusedReplayPoint, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (focusedReplayPoint != null && (focusedReplayPoint.latitude != 0.0 || focusedReplayPoint.longitude != 0.0)) {
            autoFollow = false
            selectedBtsSite = null
            selectedReplayPoint = focusedReplayPoint
            if (activeMode == MapVisualizationMode.BTS_ONLY) {
                activeMode = MapVisualizationMode.RSRP_SIGNAL
            }
            mv.controller.setZoom(17.5)
            mv.controller.setCenter(GeoPoint(focusedReplayPoint.latitude, focusedReplayPoint.longitude))
            mv.invalidate()
        } else if (replayRecords != null && replayRecords.isNotEmpty()) {
            selectedReplayPoint = null
            if (activeMode == MapVisualizationMode.BTS_ONLY) {
                activeMode = MapVisualizationMode.RSRP_SIGNAL
            }
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

    // Highlight marker for tapped replay route point
    LaunchedEffect(selectedReplayPoint, activeMode, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        selectedPointMarker?.let { mv.overlays.remove(it) }
        selectedPointMarker = null

        val pt = selectedReplayPoint
        if (pt != null && activeMode != MapVisualizationMode.BTS_ONLY && (pt.latitude != 0.0 || pt.longitude != 0.0)) {
            val idx = (replayRecords?.indexOfFirst { it.id == pt.id } ?: -1).let { if (it >= 0) "#${it + 1}" else "•" }
            val highlight = Marker(mv).apply {
                position = GeoPoint(pt.latitude, pt.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createBtsMarkerDrawable(
                    context = context,
                    fillArgb = CyanTertiary.toArgb(),
                    strokeArgb = AndroidColor.WHITE,
                    label = idx,
                    isServing = true
                )
                setOnMarkerClickListener { _, _ -> true }
            }
            mv.overlays.add(highlight)
            selectedPointMarker = highlight
        }
        mv.invalidate()
    }

    // Unified colored polyline renderer for BOTH Live trail AND Historical Replay (suppressed in BTS_ONLY)
    LaunchedEffect(replayRecords, liveTrail, activeMode, mapRsrpSimSlot, focusedReplayPoint, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect

        if (trackOverlays.isNotEmpty()) {
            mv.overlays.removeAll(trackOverlays.toSet())
            trackOverlays.clear()
        }

        if (activeMode == MapVisualizationMode.BTS_ONLY) {
            locationMarker?.let { mv.overlays.remove(it) }
            mv.invalidate()
            return@LaunchedEffect
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
                val sessionIdx = replayRecords.indexOfFirst { it.id == r.id }.let { if (it >= 0) it + 1 else idx + 1 }
                if (isFocused || isWaypoint || idx == 0 || idx == validRecords.lastIndex) {
                    val m = Marker(mv).apply {
                        position = GeoPoint(r.latitude, r.longitude)
                        title = when {
                            isFocused -> if (isCs) "Vybraný bod #$sessionIdx" else "Selected #$sessionIdx"
                            isWaypoint -> "★ ${r.waypointNote}"
                            idx == 0 -> "Start"
                            else -> if (isCs) "Konec" else "End"
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setOnMarkerClickListener { _, _ ->
                            selectedBtsSite = null
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

    // Render BTS Layer: Serving BTS markers, RF link lines from UE to BTS, Focused BTS, and nearby towers
    LaunchedEffect(
        showBtsLayer,
        activeMode,
        cellular.sim1.btsMatch,
        cellular.sim2.btsMatch,
        gnss.latitude,
        gnss.longitude,
        gnss.hasFix,
        focusedBts,
        replayRecords,
        mapViewportCenter,
        autoFollow,
        mapViewInstance
    ) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (btsOverlays.isNotEmpty()) {
            mv.overlays.removeAll(btsOverlays.toSet())
            btsOverlays.clear()
        }
        if (!showBtsLayer) {
            mv.invalidate()
            return@LaunchedEffect
        }

        val uePoint = if (gnss.hasFix && (gnss.latitude != 0.0 || gnss.longitude != 0.0)) {
            GeoPoint(gnss.latitude, gnss.longitude)
        } else null

        val servingCoords = mutableSetOf<String>()

        // 1. Serving SIM 1 & SIM 2 BTS + RF Link Line (skip dashed link line in BTS_ONLY pure map mode)
        val servingMatches = listOf(1 to cellular.sim1.btsMatch, 2 to cellular.sim2.btsMatch)
        for ((slot, match) in servingMatches) {
            if (match?.latitude != null && match.longitude != null) {
                val btsPt = GeoPoint(match.latitude, match.longitude)
                val siteKey = "${match.operatorPlmn}:${match.enodebId}:${match.locationName}"
                servingCoords.add(siteKey)
                val opArgb = operatorMarkerArgb(match.operatorPlmn)

                if (uePoint != null && replayRecords == null && activeMode != MapVisualizationMode.BTS_ONLY) {
                    val linkLine = Polyline().apply {
                        outlinePaint.color = opArgb
                        outlinePaint.alpha = 210
                        outlinePaint.strokeWidth = 6f
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
                        setPoints(listOf(uePoint, btsPt))
                    }
                    mv.overlays.add(linkLine)
                    btsOverlays.add(linkLine)
                }

                val marker = Marker(mv).apply {
                    position = btsPt
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createBtsMarkerDrawable(
                        context = context,
                        fillArgb = opArgb,
                        strokeArgb = AndroidColor.WHITE,
                        label = "S$slot",
                        isServing = true
                    )
                    setOnMarkerClickListener { _, _ ->
                        selectedReplayPoint = null
                        val initial = SelectedBtsSite(
                            plmn = match.operatorPlmn,
                            operatorName = shortMapOperatorName(match.operatorName),
                            locationName = match.locationName,
                            enodebId = match.enodebId,
                            primaryCellId = match.cellId,
                            primaryBand = match.dbBand,
                            primaryPci = 0,
                            primaryTac = 0,
                            latitude = match.latitude,
                            longitude = match.longitude,
                            coordSource = match.coordSource,
                            districtCode = match.districtCode,
                            updatedDate = match.updatedDate,
                            bandsSummary = match.coLocatedBands,
                            isServingSlot = slot,
                            isLoadingCells = true
                        )
                        selectedBtsSite = initial
                        coroutineScope.launch {
                            val cells = runCatching {
                                loadSiteCells(match.operatorPlmn, match.enodebId, match.cellId)
                            }.getOrDefault(emptyList())
                            if (selectedBtsSite?.enodebId == match.enodebId && selectedBtsSite?.primaryCellId == match.cellId) {
                                selectedBtsSite = initial.copy(siteCells = cells, isLoadingCells = false)
                            }
                        }
                        true
                    }
                }
                mv.overlays.add(marker)
                btsOverlays.add(marker)
            }
        }

        // 2. Focused BTS pin (opened from Search/RF tab)
        focusedBts?.let { (lat, lon, label) ->
            val focusMarker = Marker(mv).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createBtsMarkerDrawable(
                    context = context,
                    fillArgb = AmberWarning.toArgb(),
                    strokeArgb = AndroidColor.WHITE,
                    label = "BTS",
                    isServing = true
                )
                title = label
                snippet = String.format(Locale.US, "%.5f, %.5f", lat, lon)
            }
            mv.overlays.add(focusMarker)
            btsOverlays.add(focusMarker)
        }

        // 3. Nearby imported Czech BTS towers in database around active center
        val queryLat = (if (!autoFollow || activeMode == MapVisualizationMode.BTS_ONLY) mapViewportCenter?.first else null)
            ?: focusedBts?.first
            ?: uePoint?.latitude
            ?: replayRecords?.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 }?.latitude
            ?: mv.mapCenter.latitude
        val queryLon = (if (!autoFollow || activeMode == MapVisualizationMode.BTS_ONLY) mapViewportCenter?.second else null)
            ?: focusedBts?.second
            ?: uePoint?.longitude
            ?: replayRecords?.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 }?.longitude
            ?: mv.mapCenter.longitude

        val nearby = runCatching { loadNearbyBts(queryLat, queryLon) }.getOrDefault(emptyList())
        val dedupedMasts = nearby
            .distinctBy { "${it.operatorPlmn}:${it.enodebId}:${it.locationName}" }
            .filter { "${it.operatorPlmn}:${it.enodebId}:${it.locationName}" !in servingCoords }
            .take(220)

        for (tower in dedupedMasts) {
            val tLat = tower.latitude ?: continue
            val tLon = tower.longitude ?: continue
            val shortOp = when (tower.operatorPlmn) {
                "23001" -> "TM"
                "23002" -> "O2"
                "23003" -> "VF"
                else -> "CZ"
            }
            val opLabel = shortMapOperatorName(BtsGeoUtils.operatorDisplayName(tower.operatorPlmn))
            val marker = Marker(mv).apply {
                position = GeoPoint(tLat, tLon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createBtsMarkerDrawable(
                    context = context,
                    fillArgb = operatorMarkerArgb(tower.operatorPlmn),
                    strokeArgb = DarkSlate900.toArgb(),
                    label = shortOp,
                    isServing = false
                )
                setOnMarkerClickListener { _, _ ->
                    selectedReplayPoint = null
                    val initial = SelectedBtsSite(
                        plmn = tower.operatorPlmn,
                        operatorName = opLabel,
                        locationName = tower.locationName,
                        enodebId = tower.enodebId,
                        primaryCellId = tower.cellId,
                        primaryBand = tower.band,
                        primaryPci = tower.pci,
                        primaryTac = tower.tac,
                        latitude = tLat,
                        longitude = tLon,
                        coordSource = tower.coordSource,
                        districtCode = tower.districtCode,
                        updatedDate = tower.updatedDate,
                        bandsSummary = listOfNotNull(tower.band.takeIf { it.isNotBlank() }),
                        isServingSlot = null,
                        isLoadingCells = true
                    )
                    selectedBtsSite = initial
                    coroutineScope.launch {
                        val cells = runCatching {
                            loadSiteCells(tower.operatorPlmn, tower.enodebId, tower.cellId)
                        }.getOrDefault(emptyList())
                        if (selectedBtsSite?.enodebId == tower.enodebId && selectedBtsSite?.primaryCellId == tower.cellId) {
                            val collectedBands = cells.mapNotNull { it.band.takeIf { b -> b.isNotBlank() } }.distinct()
                            selectedBtsSite = initial.copy(
                                siteCells = cells,
                                bandsSummary = collectedBands.ifEmpty { initial.bandsSummary },
                                isLoadingCells = false
                            )
                        }
                    }
                    true
                }
            }
            mv.overlays.add(marker)
            btsOverlays.add(marker)
        }

        // Keep live cursor above BTS overlays when not in BTS_ONLY or replay
        if (replayRecords == null && activeMode != MapVisualizationMode.BTS_ONLY) {
            locationMarker?.let { marker ->
                mv.overlays.remove(marker)
                mv.overlays.add(marker)
            }
        }
        // Keep highlighted replay point marker on top of everything
        selectedPointMarker?.let { sel ->
            mv.overlays.remove(sel)
            mv.overlays.add(sel)
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

                    // Bottom overlay (index 0): detect taps on historical replay polyline points
                    val tapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p == null) return false
                            val recs = latestReplayRecords
                            if (recs != null && recs.isNotEmpty() && latestActiveMode != MapVisualizationMode.BTS_ONLY) {
                                val proj = projection
                                val tapPx = proj.toPixels(p, Point())
                                val maxTapDistPx = 44f * latestContext.resources.displayMetrics.density
                                val candPx = Point()
                                var bestRecord: TelemetryRecord? = null
                                var bestDistPx = Double.MAX_VALUE
                                for (r in recs) {
                                    if (r.latitude == 0.0 && r.longitude == 0.0) continue
                                    proj.toPixels(GeoPoint(r.latitude, r.longitude), candPx)
                                    val d = hypot(
                                        (candPx.x - tapPx.x).toDouble(),
                                        (candPx.y - tapPx.y).toDouble()
                                    )
                                    if (d < bestDistPx) {
                                        bestDistPx = d
                                        bestRecord = r
                                    }
                                }
                                if (bestRecord != null && bestDistPx <= maxTapDistPx) {
                                    selectedBtsSite = null
                                    selectedReplayPoint = bestRecord
                                    return true
                                }
                            }
                            if (selectedBtsSite != null || selectedReplayPoint != null) {
                                selectedBtsSite = null
                                selectedReplayPoint = null
                                return true
                            }
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    })
                    overlays.add(0, tapEventsOverlay)

                    val marker = Marker(this).apply {
                        position = initPos
                        title = if (isCs) "Aktuální pozice" else "Current position"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    overlays.add(marker)
                    locationMarker = marker
                    addMapListener(DelayedMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val c = mapCenter
                            mapViewportCenter = c.latitude to c.longitude
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            val c = mapCenter
                            mapViewportCenter = c.latitude to c.longitude
                            return false
                        }
                    }, 350L))
                    onResume()

                    mapViewInstance = this
                }
            },
            onRelease = { mv ->
                if (mapViewInstance === mv) {
                    mapViewInstance = null
                }
                locationMarker = null
                selectedPointMarker = null
                trackOverlays.clear()
                btsOverlays.clear()
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
                modifier = Modifier.weight(1f, fill = false),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp)
                ) {
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
                    ModeButton(
                        label = if (isCs) "Jen BTS" else "BTS Only",
                        selected = activeMode == MapVisualizationMode.BTS_ONLY,
                        onClick = {
                            activeMode = MapVisualizationMode.BTS_ONLY
                            showBtsLayer = true
                            autoFollow = false
                            mapViewInstance?.let { mv ->
                                mapViewportCenter = mv.mapCenter.latitude to mv.mapCenter.longitude
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Top-right quick toggles: BTS Layer + Dark/Light map
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (showBtsLayer) CyanTertiary.copy(alpha = 0.24f) else DarkSlate800.copy(alpha = 0.95f)
                    )
                ) {
                    IconButton(
                        onClick = {
                            val next = !showBtsLayer
                            showBtsLayer = next
                            if (!next && activeMode == MapVisualizationMode.BTS_ONLY) {
                                activeMode = MapVisualizationMode.RSRP_SIGNAL
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("toggle_bts_layer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = if (isCs) "Zobrazit/Skrýt vrstvu BTS" else "Toggle BTS Layer",
                            tint = if (showBtsLayer) CyanTertiary else TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.95f))
                ) {
                    IconButton(
                        onClick = { onToggleDarkMap(!darkMapMode) },
                        modifier = Modifier.size(48.dp).testTag("toggle_dark_map_button")
                    ) {
                        Icon(
                            imageVector = if (darkMapMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isCs) "Přepnout režim mapy" else "Toggle Dark Map",
                            tint = if (darkMapMode) AmberWarning else CyanTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Floating Legend Box in Top-Left
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
                        LegendRow(color = Color(0xFFF97316), text = if (isCs) "5 až 20 satelitů" else "5 to 20 sats")
                        LegendRow(color = Color(0xFFEAB308), text = if (isCs) "21 až 30 satelitů" else "21 to 30 sats")
                        LegendRow(color = EmeraldSignal, text = if (isCs) "31 až 50 satelitů" else "31 to 50 sats")
                        LegendRow(color = Purple5G, text = if (isCs) "> 50 satelitů (Nejlepší)" else "> 50 sats (Best)")
                    }
                    MapVisualizationMode.PDOP -> {
                        Text(text = if (isCs) "LEGENDA PDOP" else "PDOP LEGEND", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextSecondaryDark)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = EmeraldSignal, text = if (isCs) "< 0.7 (Nejlepší)" else "< 0.7 (Best)")
                        LegendRow(color = Color(0xFFEAB308), text = if (isCs) "0.7 až 1.5 (Velmi dobrý)" else "0.7 to 1.5 (Very good)")
                        LegendRow(color = Color(0xFFF97316), text = if (isCs) "1.5 až 2.5 (Střední)" else "1.5 to 2.5 (Moderate)")
                        LegendRow(color = Purple5G, text = if (isCs) "2.5 až 3.5 (Zhoršený)" else "2.5 to 3.5 (Fair)")
                        LegendRow(color = RoseCritical, text = if (isCs) "> 3.5 (Nejhorší)" else "> 3.5 (Worst)")
                    }
                    MapVisualizationMode.BTS_ONLY -> {
                        Text(text = if (isCs) "MAPA VYSÍLAČŮ BTS" else "BTS TOWER MAP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyanTertiary)
                        Spacer(modifier = Modifier.height(6.dp))
                        LegendRow(color = TMobileMagenta, text = "TM • T-Mobile (23001)")
                        LegendRow(color = O2Blue, text = "O2 • O2 / CETIN (23002)")
                        LegendRow(color = VodafoneRed, text = "VF • Vodafone (23003)")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) "Posuňte mapu • klepněte na BTS pro detail" else "Pan map • tap any BTS for detail",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }

        val hasBottomBanner = activeMode != MapVisualizationMode.BTS_ONLY && (replayRecords != null || liveTrail.isNotEmpty())

        // Bottom-left status banner: Replay mode OR Live trail point count + Clear button (hidden in BTS_ONLY)
        if (activeMode != MapVisualizationMode.BTS_ONLY) {
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
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (isCs) "Záznam relace (${replayRecords.size} bodů)" else "Replay track (${replayRecords.size} pts)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (isCs) "Klepnutím na trasu zobrazíte bod" else "Tap anywhere on track for point info",
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = {
                            selectedReplayPoint = null
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
                            text = if (isCs) "Živá trasa (od startu): ${liveTrail.size} b." else "Live trail (since start): ${liveTrail.size} pts",
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
        }

        // Rich Tapped BTS Site Detail Card (bottom sheet overlay)
        selectedBtsSite?.let { site ->
            val opColor = operatorComposeColor(site.plmn)
            val distFromUe = if (gnss.hasFix && (gnss.latitude != 0.0 || gnss.longitude != 0.0)) {
                BtsGeoUtils.haversineDistanceMeters(gnss.latitude, gnss.longitude, site.latitude, site.longitude)
            } else null
            val azFromUe = if (gnss.hasFix && (gnss.latitude != 0.0 || gnss.longitude != 0.0)) {
                BtsGeoUtils.bearingDegrees(gnss.latitude, gnss.longitude, site.latitude, site.longitude)
            } else null

            Card(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = if (hasBottomBanner) 78.dp else 24.dp, end = 76.dp)
                    .align(Alignment.BottomStart)
                    .testTag("selected_bts_detail_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.97f)),
                border = BorderStroke(1.2.dp, opColor.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = opColor
                            ) {
                                Text(
                                    text = "${site.operatorName} (${site.plmn})",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                            site.isServingSlot?.let { slot ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldSignal.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = if (isCs) "SLOUŽÍCÍ S$slot" else "SERVING S$slot",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSignal
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = { selectedBtsSite = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = if (isCs) "Zavřít detail BTS" else "Close BTS detail",
                                tint = TextSecondaryDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = site.locationName.ifBlank { "eNodeB ${site.enodebId} • CI ${site.primaryCellId}" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val metaLine = buildList {
                        if (site.enodebId > 0) add("eNB ${site.enodebId}") else add("CI ${site.primaryCellId}")
                        if (site.districtCode.isNotBlank()) add("${if (isCs) "Okres" else "Dist"} ${site.districtCode}")
                        if (site.updatedDate.isNotBlank()) add(site.updatedDate)
                        if (site.coordSource.isNotBlank()) add(site.coordSource)
                    }.joinToString(" • ")
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )

                    Text(
                        text = buildString {
                            append(String.format(Locale.US, "GPS: %.5f, %.5f", site.latitude, site.longitude))
                            if (distFromUe != null && azFromUe != null) {
                                append(" • ${BtsGeoUtils.formatDistance(distFromUe)} (${azFromUe.toInt()}° ${BtsGeoUtils.bearingToCardinal(azFromUe, isCs)})")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = CyanTertiary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (site.isLoadingCells) {
                        Text(
                            text = if (isCs) "Načítám sektory a pásma stožáru..." else "Loading mast sectors & bands...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextSecondaryDark
                        )
                    } else if (site.siteCells.isNotEmpty()) {
                        Text(
                            text = if (isCs) "Buňky na vysílači (${site.siteCells.size}):" else "Cells at mast (${site.siteCells.size}):",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 96.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            site.siteCells.take(12).forEach { cell ->
                                val bandTxt = if (cell.band.isNotBlank()) {
                                    if (cell.band.all { it.isDigit() }) "${cell.band} MHz" else cell.band
                                } else "—"
                                val pciTxt = if (cell.pci > 0) "PCI ${cell.pci}" else "PCI —"
                                val tacTxt = if (cell.tac > 0) "TAC ${cell.tac}" else "TAC —"
                                Text(
                                    text = "CI ${cell.cellId} • $bandTxt • $pciTxt • $tacTxt",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    } else {
                        val bandLabel = if (site.primaryBand.isNotBlank()) "${site.primaryBand} MHz" else "—"
                        val pciLabel = if (site.primaryPci > 0) "PCI ${site.primaryPci}" else "PCI —"
                        val tacLabel = if (site.primaryTac > 0) "TAC ${site.primaryTac}" else "TAC —"
                        Text(
                            text = "CI ${site.primaryCellId} • $bandLabel • $pciLabel • $tacLabel",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val query = if (site.enodebId > 0) site.enodebId.toString() else site.primaryCellId.toString()
                                onInspectBtsInDb(query)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = DarkSlate900, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCs) "Detail / Upravit v DB" else "View / Edit in DB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkSlate900
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                autoFollow = false
                                mapViewInstance?.controller?.animateTo(GeoPoint(site.latitude, site.longitude))
                            },
                            modifier = Modifier.heightIn(min = 38.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = if (isCs) "Vycentrovat" else "Center", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Selected Replay Point Inspector Card (from tapping any point on track, marker, or deep-linking from History)
        if (selectedBtsSite == null) {
            selectedReplayPoint?.let { pt ->
                val tf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                Card(
                    modifier = Modifier
                        .padding(start = 12.dp, bottom = 78.dp, end = 76.dp)
                        .align(Alignment.BottomStart)
                        .testTag("selected_replay_point_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.97f)),
                    border = BorderStroke(1.2.dp, CyanTertiary)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val ptNumber = (replayRecords?.indexOfFirst { it.id == pt.id } ?: -1).let { if (it >= 0) it + 1 else pt.id }
                            Text(
                                text = pt.waypointNote?.let { "★ $it (Bod #$ptNumber • ${tf.format(Date(pt.timestampMs))})" }
                                    ?: (if (isCs) "Bod #$ptNumber (${tf.format(Date(pt.timestampMs))})" else "Point #$ptNumber (${tf.format(Date(pt.timestampMs))})"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = CyanTertiary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                selectedReplayPoint = null
                                onClearFocusedPoint()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = if (isCs) "Zavřít" else "Close", tint = TextSecondaryDark, modifier = Modifier.size(15.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val s1Op = shortMapOperatorName(pt.sim1Operator)
                        val s1Sinr = pt.sim1Sinr?.let { String.format(Locale.US, " SINR %.0fdB", it) } ?: ""
                        Text(
                            text = "S1 $s1Op (${pt.sim1NetworkGen} ${pt.sim1Band}): ${pt.sim1Rsrp ?: "N/A"} dBm$s1Sinr • PCI ${if (pt.sim1Pci > 0) pt.sim1Pci else "—"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                        if (pt.sim2Rsrp != null || pt.sim2Operator.isNotBlank()) {
                            val s2Op = shortMapOperatorName(pt.sim2Operator.ifBlank { "SIM 2" })
                            val s2Sinr = pt.sim2Sinr?.let { String.format(Locale.US, " SINR %.0fdB", it) } ?: ""
                            Text(
                                text = "S2 $s2Op (${pt.sim2NetworkGen} ${pt.sim2Band}): ${pt.sim2Rsrp ?: "N/A"} dBm$s2Sinr • PCI ${if (pt.sim2Pci > 0) pt.sim2Pci else "—"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimaryDark
                            )
                        }

                        val btsInfo = listOfNotNull(
                            pt.sim1BtsLocation?.let { loc ->
                                val d = pt.sim1BtsDistanceM?.let { " (${BtsGeoUtils.formatDistance(it)})" } ?: ""
                                "S1 BTS: $loc$d"
                            },
                            pt.sim2BtsLocation?.let { loc ->
                                val d = pt.sim2BtsDistanceM?.let { " (${BtsGeoUtils.formatDistance(it)})" } ?: ""
                                "S2 BTS: $loc$d"
                            }
                        )
                        if (btsInfo.isNotEmpty()) {
                            Text(
                                text = btsInfo.joinToString(" | "),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                color = EmeraldSignal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        val hasValidPdop = pt.satellitesUsedInFix >= 4 && pt.pdop > 0f && pt.pdop < 9.0f
                        val pdopText = if (hasValidPdop) String.format(Locale.US, "%.2f", pt.pdop) else "—"
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.5f, %.5f • %.1f km/h • Sat %d/%d • PDOP %s • %dm",
                                pt.latitude,
                                pt.longitude,
                                pt.speedKmh,
                                pt.satellitesUsedInFix,
                                pt.satellitesInView,
                                pdopText,
                                pt.altitudeMsl.toInt()
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextSecondaryDark
                        )
                    }
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
            if (replayRecords == null && activeMode != MapVisualizationMode.BTS_ONLY) {
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

        if (focusedBts != null && selectedBtsSite == null && selectedReplayPoint == null) {
            Card(
                modifier = Modifier
                    .padding(start = 12.dp, bottom = if (hasBottomBanner) 78.dp else 24.dp, end = 76.dp)
                    .align(Alignment.BottomStart),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800.copy(alpha = 0.96f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CellTower, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = focusedBts.third,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onClearFocusedBts, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = if (isCs) "Zavřít" else "Close", tint = TextSecondaryDark, modifier = Modifier.size(14.dp))
                    }
                }
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
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false
        )
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
