package com.example.ui.tabs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.export.Exporter
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryTab(
    sessions: List<LogSession>,
    lang: String,
    onSelectSessionForReplay: (Long) -> Unit,
    onSelectSessionForCharts: (LogSession) -> Unit = {},
    onSelectPointOnMap: (List<TelemetryRecord>, TelemetryRecord) -> Unit = { _, _ -> },
    onRenameSession: (Long, String) -> Unit,
    onDeleteSession: (LogSession) -> Unit,
    onDeleteRecord: (TelemetryRecord) -> Unit,
    loadRecords: suspend (Long) -> List<TelemetryRecord>
) {
    val isCs = lang != "EN"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    var inspectedSession by remember { mutableStateOf<LogSession?>(null) }
    var inspectedRecords by remember { mutableStateOf<List<TelemetryRecord>?>(null) }

    var sessionToRename by remember { mutableStateOf<LogSession?>(null) }
    var renameText by remember { mutableStateOf("") }

    var sessionForExportDialog by remember { mutableStateOf<LogSession?>(null) }
    var selectedExportFormat by remember { mutableStateOf("CSV") }

    if (inspectedSession != null && inspectedRecords != null) {
        BackHandler {
            inspectedSession = null
            inspectedRecords = null
        }

        SessionDetailInspector(
            session = inspectedSession!!,
            records = inspectedRecords!!,
            lang = lang,
            onBack = {
                inspectedSession = null
                inspectedRecords = null
            },
            onExportCsv = {
                sessionForExportDialog = inspectedSession
                selectedExportFormat = "CSV"
            },
            onOpenCharts = {
                val target = inspectedSession!!
                inspectedSession = null
                inspectedRecords = null
                onSelectSessionForCharts(target)
            },
            onDeletePoint = { record ->
                onDeleteRecord(record)
                inspectedRecords = inspectedRecords?.filter { it.id != record.id }
            },
            onOpenPointOnMap = { record ->
                val list = inspectedRecords ?: emptyList()
                inspectedSession = null
                inspectedRecords = null
                onSelectPointOnMap(list, record)
            }
        )
        return
    }

    val filtered = remember(sessions, searchQuery) {
        if (searchQuery.isBlank()) sessions
        else sessions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.operatorSummary.contains(searchQuery, ignoreCase = true) ||
                it.primaryTech.contains(searchQuery, ignoreCase = true) ||
                it.primaryBand.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = if (isCs) "HISTORIE MĚŘICÍCH RELACÍ" else "MEASUREMENT SESSIONS HISTORY",
                style = MaterialTheme.typography.titleMedium,
                color = CyanTertiary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isCs) "Lokální SQLite databáze • Export CSV / 3D KML / GPX / GeoJSON" else "Local SQLite Room DB • Export CSV / 3D KML / GPX / GeoJSON",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("history_search_field"),
            placeholder = { Text(text = if (isCs) "Hledat relaci podle názvu, technologie, pásma..." else "Search sessions by title, network, band...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCs) "Zatím žádné zaznamenané relace. Spusťte měření na záložce Měření." else "No sessions recorded yet. Start logging from the Measurement tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        lang = lang,
                        onOpenInspector = {
                            coroutineScope.launch {
                                val list = loadRecords(session.id)
                                inspectedRecords = list
                                inspectedSession = session
                            }
                        },
                        onRename = {
                            sessionToRename = session
                            renameText = session.title
                        },
                        onReplay = { onSelectSessionForReplay(session.id) },
                        onCharts = { onSelectSessionForCharts(session) },
                        onDelete = { onDeleteSession(session) },
                        onExport = { format ->
                            sessionForExportDialog = session
                            selectedExportFormat = format
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_vut_logo),
                            contentDescription = "VUT Logo",
                            modifier = Modifier.height(28.dp).width(90.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Rename Session Dialog
    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text(if (isCs) "Přejmenovat relaci" else "Rename Session", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(if (isCs) "Nový název relace" else "New session title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenameSession(sessionToRename!!.id, renameText.trim())
                    }
                    sessionToRename = null
                }) {
                    Text(if (isCs) "Uložit" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text(if (isCs) "Zrušit" else "Cancel")
                }
            }
        )
    }

    // Export & Download Options Dialog
    if (sessionForExportDialog != null) {
        val s = sessionForExportDialog!!
        AlertDialog(
            onDismissRequest = { sessionForExportDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, tint = CyanTertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCs) "Export & Stažení do zařízení" else "Export & Download to Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = s.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isCs) "Zvolte formát exportu:" else "Select export format:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("CSV", "KML", "GPX", "GEOJSON").forEach { fmt ->
                            val isSel = selectedExportFormat == fmt
                            OutlinedButton(
                                onClick = { selectedExportFormat = fmt },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) CyanTertiary.copy(alpha = 0.2f) else DarkSlate700
                                ),
                                border = if (isSel) ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary)) else null,
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (fmt == "GEOJSON") "JSON" else fmt,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) CyanTertiary else TextSecondaryDark,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val fmt = selectedExportFormat
                            sessionForExportDialog = null
                            coroutineScope.launch {
                                saveSessionFileToDownloads(context, s, fmt, loadRecords)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("download_to_device_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = DarkSlate900, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCs) "Stáhnout do zařízení (Downloads)" else "Save to Device (Downloads)",
                            fontWeight = FontWeight.Bold,
                            color = DarkSlate900
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val fmt = selectedExportFormat
                            sessionForExportDialog = null
                            coroutineScope.launch {
                                exportSessionFile(context, s, fmt, loadRecords)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("share_export_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCs) "Sdílet soubor (Share dialog)" else "Share file (System share)",
                            color = TextPrimaryDark
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sessionForExportDialog = null }) {
                    Text(if (isCs) "Zavřít" else "Close", color = TextSecondaryDark)
                }
            }
        )
    }
}

@Composable
fun SessionCard(
    session: LogSession,
    lang: String,
    onOpenInspector: () -> Unit,
    onRename: () -> Unit,
    onReplay: () -> Unit,
    onCharts: () -> Unit,
    onDelete: () -> Unit,
    onExport: (String) -> Unit
) {
    val isCs = lang != "EN"
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val startTimeStr = remember(session.startTimeMs) { dateFormat.format(Date(session.startTimeMs)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenInspector() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    IconButton(onClick = onRename, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = if (isCs) "Přejmenovat" else "Rename", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = startTimeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                HistoryMetricItem(label = if (isCs) "Vzorků" else "Samples", value = session.pointCount.toString(), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = if (isCs) "Vzdálenost" else "Distance", value = String.format(Locale.US, "%.2f km", session.distanceMeters / 1000.0), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = if (isCs) "Max rychlost" else "Max Speed", value = String.format(Locale.US, "%.0f km/h", session.maxSpeedKmh), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = if (isCs) "Síť" else "Net", value = "${session.primaryTech} ${session.operatorSummary}", modifier = Modifier.weight(1.4f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onOpenInspector,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.List, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isCs) "Body" else "Points", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onReplay,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isCs) "Mapa" else "Map", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCharts,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = if (isCs) "Grafy" else "Charts", fontSize = 11.sp)
                }

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { showMenu = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Export", fontSize = 11.sp)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = { showMenu = false; onExport("CSV") })
                        DropdownMenuItem(text = { Text("Export 3D KML") }, onClick = { showMenu = false; onExport("KML") })
                        DropdownMenuItem(text = { Text("Export GPX 1.1") }, onClick = { showMenu = false; onExport("GPX") })
                        DropdownMenuItem(text = { Text("Export GeoJSON") }, onClick = { showMenu = false; onExport("GEOJSON") })
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = if (isCs) "Smazat" else "Delete", tint = RoseCritical, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SessionDetailInspector(
    session: LogSession,
    records: List<TelemetryRecord>,
    lang: String,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onOpenCharts: () -> Unit,
    onDeletePoint: (TelemetryRecord) -> Unit,
    onOpenPointOnMap: (TelemetryRecord) -> Unit = {}
) {
    val isCs = lang != "EN"
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    // Computed session analytics summary
    val stats = remember(records) {
        val r1Vals = records.mapNotNull { it.sim1Rsrp }
        val r2Vals = records.mapNotNull { it.sim2Rsrp }
        val pdopVals = records.filter { it.satellitesUsedInFix >= 4 && it.pdop > 0f && it.pdop < 9f }.map { it.pdop }
        val waypoints = records.count { !it.waypointNote.isNullOrBlank() }
        val nrCount = records.count { it.sim1NetworkGen.contains("5G") }
        val lteCount = records.count { it.sim1NetworkGen.contains("4G") || it.sim1NetworkGen.contains("LTE") }
        val totalRec = records.size.coerceAtLeast(1)

        mapOf(
            "r1Avg" to r1Vals.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            "r1Min" to r1Vals.minOrNull(),
            "r1Max" to r1Vals.maxOrNull(),
            "r2Avg" to r2Vals.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            "pdopAvg" to pdopVals.takeIf { it.isNotEmpty() }?.average(),
            "waypoints" to waypoints,
            "pct5g" to (nrCount * 100 / totalRec),
            "pct4g" to (lteCount * 100 / totalRec)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (isCs) "Zpět" else "Back", tint = TextPrimaryDark)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (isCs) "Inspekce ${records.size} záznamů" else "Inspection of ${records.size} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenCharts,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanTertiary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (isCs) "Grafy" else "Charts", fontSize = 12.sp)
                }
                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = DarkSlate900)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Export", fontWeight = FontWeight.Bold, color = DarkSlate900, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Session Analytics Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isCs) "SOUHRNNÁ STATISTIKA RELACE" else "SESSION ANALYTICS SUMMARY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = CyanTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    HistoryMetricItem(
                        label = "SIM 1 RSRP",
                        value = (stats["r1Avg"] as? Int)?.let { "$it dBm" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    HistoryMetricItem(
                        label = "SIM 2 RSRP",
                        value = (stats["r2Avg"] as? Int)?.let { "$it dBm" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    HistoryMetricItem(
                        label = if (isCs) "Prům. PDOP" else "Avg PDOP",
                        value = (stats["pdopAvg"] as? Double)?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    HistoryMetricItem(
                        label = if (isCs) "Podíl 5G/4G" else "5G/4G Mix",
                        value = "${stats["pct5g"]}%/${stats["pct4g"]}%",
                        modifier = Modifier.weight(1f)
                    )
                }
                val r1Min = stats["r1Min"] as? Int
                val r1Max = stats["r1Max"] as? Int
                val wpCount = stats["waypoints"] as? Int ?: 0
                if (r1Min != null || wpCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isCs) {
                            "SIM1 rozsah: ${r1Min ?: "—"} až ${r1Max ?: "—"} dBm • Značky (Waypoints): $wpCount"
                        } else {
                            "SIM1 range: ${r1Min ?: "—"} to ${r1Max ?: "—"} dBm • Waypoints: $wpCount"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondaryDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records, key = { it.id }) { r ->
                val timeStr = timeFormat.format(Date(r.timestampMs))
                val hasDop = r.satellitesUsedInFix >= 4 && r.pdop > 0f && r.pdop < 9.0f
                val hasCoords = r.latitude != 0.0 || r.longitude != 0.0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasCoords) { onOpenPointOnMap(r) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${r.id} • $timeStr",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                if (!r.waypointNote.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(AmberWarning.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "★ ${r.waypointNote}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberWarning
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasCoords) {
                                    IconButton(
                                        onClick = { onOpenPointOnMap(r) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PinDrop,
                                            contentDescription = if (isCs) "Zobrazit bod na mapě" else "Show point on map",
                                            tint = CyanTertiary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeletePoint(r) }, modifier = Modifier.size(26.dp)) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = if (isCs) "Smazat bod" else "Delete Point", tint = RoseCritical, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "GNSS: ${String.format(Locale.US, "%.5f", r.latitude)}, ${String.format(Locale.US, "%.5f", r.longitude)} (±${String.format(Locale.US, "%.1f", r.horizontalAccuracyM)}m)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = CyanTertiary
                            )
                            Text(
                                text = "Sats: ${r.satellitesUsedInFix}/${r.satellitesInView} (L1/L5: ${String.format(Locale.US, "%.0f%%", r.dualFrequencyRatioPct)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val dopText = if (hasDop) {
                                "HDOP: ${String.format(Locale.US, "%.2f", r.hdop)} • VDOP: ${String.format(Locale.US, "%.2f", r.vdop)} • PDOP: ${String.format(Locale.US, "%.2f", r.pdop)}"
                            } else "HDOP: — • VDOP: — • PDOP: —"
                            Text(
                                text = dopText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = TextSecondaryDark
                            )
                            Text(
                                text = if (isCs) "Rychlost: ${String.format(Locale.US, "%.1f km/h", r.speedKmh)}" else "Speed: ${String.format(Locale.US, "%.1f km/h", r.speedKmh)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = TextPrimaryDark
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val s1Txt = if (r.sim1Operator.isNotEmpty()) "S1: ${r.sim1Operator} ${r.sim1Rsrp?.let { "$it dBm" } ?: ""}".trim() else "S1: N/A"
                            val s2Txt = if (r.sim2Operator.isNotEmpty()) "S2: ${r.sim2Operator} ${r.sim2Rsrp?.let { "$it dBm" } ?: ""}".trim() else ""
                            Text(
                                text = if (s2Txt.isNotEmpty()) "$s1Txt • $s2Txt" else s1Txt,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = AmberWarning
                            )
                            val mainRsrp = r.sim1Rsrp ?: r.sim2Rsrp
                            Text(
                                text = "RSRP: ${mainRsrp?.let { "$it dBm" } ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if ((mainRsrp ?: -120) >= -85) EmeraldSignal else AmberWarning
                            )
                        }

                        if (r.rawDopplerHz != null || r.batteryTempC != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Doppler: ${r.rawDopplerHz?.let { String.format(Locale.US, "%.0f Hz", it) } ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = CyanTertiary
                                )
                                Text(
                                    text = "Baterie: ${r.batteryTempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "—"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryMetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondaryDark)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = TextPrimaryDark
        )
    }
}

private fun writeSessionStream(format: String, session: LogSession, records: List<TelemetryRecord>, out: OutputStream) {
    BufferedOutputStream(out, 32 * 1024).use { bos ->
        when (format) {
            "CSV" -> Exporter.writeCsvToStream(session, records, bos)
            "KML" -> Exporter.writeKmlToStream(session, records, bos)
            "GPX" -> Exporter.writeGpxToStream(session, records, bos)
            else -> Exporter.writeGeoJsonToStream(session, records, bos)
        }
    }
}

private suspend fun exportSessionFile(
    context: Context,
    session: LogSession,
    format: String,
    loadRecords: suspend (Long) -> List<TelemetryRecord>
) {
    withContext(Dispatchers.IO) {
        val records = loadRecords(session.id)
        if (records.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Žádné záznamy k exportu v této relaci", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val cleanTitle = session.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val ext = when (format) {
            "CSV" -> "csv"
            "KML" -> "kml"
            "GPX" -> "gpx"
            else -> "geojson"
        }
        val file = File(exportDir, "${cleanTitle}_${session.id}.$ext")
        FileOutputStream(file).use { fos -> writeSessionStream(format, session, records, fos) }

        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (format) {
                        "CSV" -> "text/csv"
                        "KML" -> "application/vnd.google-earth.kml+xml"
                        "GPX" -> "application/gpx+xml"
                        else -> "application/geo+json"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "GNSS & Net Logger Export: ${session.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Sdílet / Uložit export"))
            } catch (e: Exception) {
                Toast.makeText(context, "Export uložen do: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private suspend fun saveSessionFileToDownloads(
    context: Context,
    session: LogSession,
    format: String,
    loadRecords: suspend (Long) -> List<TelemetryRecord>
) {
    withContext(Dispatchers.IO) {
        val records = loadRecords(session.id)
        if (records.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Žádné záznamy k exportu v této relaci", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val cleanTitle = session.title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val ext = when (format) {
            "CSV" -> "csv"
            "KML" -> "kml"
            "GPX" -> "gpx"
            else -> "geojson"
        }
        val filename = "${cleanTitle}_${session.id}.$ext"
        val mimeType = when (format) {
            "CSV" -> "text/csv"
            "KML" -> "application/vnd.google-earth.kml+xml"
            "GPX" -> "application/gpx+xml"
            else -> "application/geo+json"
        }

        var savedPath = ""
        var success = false

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/GNSS_Net_Logger")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        writeSessionStream(format, session, records, os)
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    success = true
                    savedPath = "Download/GNSS_Net_Logger/$filename"
                }
            }

            if (!success) {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "GNSS_Net_Logger").apply { if (!exists()) mkdirs() }
                val targetFile = File(appFolder, filename)
                FileOutputStream(targetFile).use { fos -> writeSessionStream(format, session, records, fos) }
                success = true
                savedPath = targetFile.absolutePath
            }
        } catch (_: Exception) {
            success = false
        }

        withContext(Dispatchers.Main) {
            if (success) {
                Toast.makeText(context, "✓ Soubor stažen do: $savedPath", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Nepodařilo se uložit soubor do zařízení", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
