package com.example.ui.tabs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryTab(
    sessions: List<LogSession>,
    lang: String,
    onSelectSessionForReplay: (Long) -> Unit,
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
                coroutineScope.launch {
                    exportSessionFile(context, inspectedSession!!, "CSV", loadRecords)
                }
            },
            onDeletePoint = { record ->
                onDeleteRecord(record)
                inspectedRecords = inspectedRecords?.filter { it.id != record.id }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isCs) "HISTORIE MĚŘICÍCH RELACÍ" else "MEASUREMENT SESSIONS HISTORY",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isCs) "Lokální SQLite databáze bez cloudových závislostí" else "Local SQLite Room database without external cloud keys",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
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
                        onDelete = { onDeleteSession(session) },
                        onExport = { format ->
                            coroutineScope.launch {
                                exportSessionFile(context, session, format, loadRecords)
                            }
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
}

@Composable
fun SessionCard(
    session: LogSession,
    lang: String,
    onOpenInspector: () -> Unit,
    onRename: () -> Unit,
    onReplay: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                HistoryMetricItem(label = if (isCs) "Vzdálenost" else "Distance", value = String.format("%.1f km", session.distanceMeters / 1000.0), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = if (isCs) "Max rychlost" else "Max Speed", value = String.format("%.0f km/h", session.maxSpeedKmh), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = if (isCs) "Síť & Operátor" else "Net & Operator", value = "${session.primaryTech} ${session.operatorSummary}", modifier = Modifier.weight(1.5f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    FilledTonalButton(
                        onClick = onOpenInspector,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isCs) "Procházet body" else "Inspect Points", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedButton(
                        onClick = onReplay,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isCs) "Mapa" else "Map", fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Export", fontSize = 11.sp)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                onClick = { showMenu = false; onExport("CSV") }
                            )
                            DropdownMenuItem(
                                text = { Text("Export 3D KML") },
                                onClick = { showMenu = false; onExport("KML") }
                            )
                            DropdownMenuItem(
                                text = { Text("Export GeoJSON") },
                                onClick = { showMenu = false; onExport("GEOJSON") }
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = if (isCs) "Smazat" else "Delete", tint = RoseCritical)
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
    onDeletePoint: (TelemetryRecord) -> Unit
) {
    val isCs = lang != "EN"
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = if (isCs) "Zpět" else "Back", tint = TextPrimaryDark)
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
                        text = if (isCs) "Editace a inspekce ${records.size} z ${session.pointCount} záznamů" else "Inspection of ${records.size} of ${session.pointCount} records",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
            }

            Button(
                onClick = onExportCsv,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = DarkSlate900)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Export CSV", fontWeight = FontWeight.Bold, color = DarkSlate900, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records, key = { it.id }) { r ->
                val timeStr = timeFormat.format(Date(r.timestampMs))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${r.id} • $timeStr",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            IconButton(onClick = { onDeletePoint(r) }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = if (isCs) "Smazat bod" else "Delete Point", tint = RoseCritical, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "GNSS: ${String.format("%.5f", r.latitude)}, ${String.format("%.5f", r.longitude)} (±${String.format("%.1f", r.horizontalAccuracyM)}m)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = CyanTertiary
                            )
                            Text(
                                text = "Sats: ${r.satellitesUsedInFix}/${r.satellitesInView} (L5: ${if (r.dualFrequencyRatioPct > 0) "6" else "0"})",
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
                            Text(
                                text = "HDOP: ${String.format(Locale.US, "%.2f", r.hdop)} • VDOP: ${String.format(Locale.US, "%.2f", r.vdop)} • PDOP: ${String.format(Locale.US, "%.2f", r.pdop)}",
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
                            Text(
                                text = "${if (isCs) "Operátor" else "Operator"}: ${r.sim1Operator} (${r.sim1Band})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = AmberWarning
                            )
                            Text(
                                text = "RSRP: ${r.sim1Rsrp ?: "N/A"} dBm",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if ((r.sim1Rsrp ?: -120) >= -85) EmeraldSignal else AmberWarning
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
        val filename = when (format) {
            "CSV" -> "${session.title.replace(" ", "_")}_${session.id}.csv"
            "KML" -> "${session.title.replace(" ", "_")}_${session.id}.kml"
            else -> "${session.title.replace(" ", "_")}_${session.id}.geojson"
        }
        val file = File(exportDir, filename)

        val content = when (format) {
            "CSV" -> Exporter.exportToCsv(session, records)
            "KML" -> Exporter.exportToKml(session, records)
            else -> Exporter.exportToGeoJson(session, records)
        }

        FileOutputStream(file).use { it.write(content.toByteArray()) }

        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when (format) {
                        "CSV" -> "text/csv"
                        "KML" -> "application/vnd.google-earth.kml+xml"
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
