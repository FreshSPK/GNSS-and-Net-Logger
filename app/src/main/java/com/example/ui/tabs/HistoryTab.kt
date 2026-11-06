package com.example.ui.tabs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.core.content.FileProvider
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.export.Exporter
import com.example.ui.i18n.Strings
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
    onDeleteSession: (LogSession) -> Unit,
    loadRecords: suspend (Long) -> List<TelemetryRecord>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

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
        Text(
            text = Strings.get("history_title", lang),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("history_search_field"),
            placeholder = { Text(text = Strings.get("filter_search", lang)) },
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
                    text = Strings.get("no_sessions", lang),
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
                        onReplay = { onSelectSessionForReplay(session.id) },
                        onDelete = { onDeleteSession(session) },
                        onExport = { format ->
                            coroutineScope.launch {
                                exportSessionFile(context, session, format, loadRecords)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    session: LogSession,
    lang: String,
    onReplay: () -> Unit,
    onDelete: () -> Unit,
    onExport: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val startTimeStr = remember(session.startTimeMs) { dateFormat.format(Date(session.startTimeMs)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = startTimeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                HistoryMetricItem(label = "Points", value = session.pointCount.toString(), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = "Distance", value = String.format("%.1f km", session.distanceMeters / 1000.0), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = "Max Speed", value = String.format("%.0f km/h", session.maxSpeedKmh), modifier = Modifier.weight(1f))
                HistoryMetricItem(label = "Tech/Operator", value = "${session.primaryTech} ${session.operatorSummary}", modifier = Modifier.weight(1.5f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Replay on Map, Export CSV/KML/GeoJSON, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    FilledTonalButton(
                        onClick = onReplay,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = Strings.get("show_on_map", lang), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { showMenu = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Export", fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Strings.get("export_csv", lang)) },
                                onClick = { showMenu = false; onExport("CSV") }
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.get("export_kml", lang)) },
                                onClick = { showMenu = false; onExport("KML") }
                            )
                            DropdownMenuItem(
                                text = { Text(Strings.get("export_geojson", lang)) },
                                onClick = { showMenu = false; onExport("GEOJSON") }
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = Strings.get("delete_session", lang), tint = RoseCritical)
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
                Toast.makeText(context, "No records to export in this session", Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }

        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val filename = when (format) {
            "CSV" -> "Session_${session.id}_${session.startTimeMs}.csv"
            "KML" -> "Session_${session.id}_${session.startTimeMs}.kml"
            else -> "Session_${session.id}_${session.startTimeMs}.geojson"
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
                    putExtra(Intent.EXTRA_SUBJECT, "Signal & GNSS Field Logger Export: ${session.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share / Save Export"))
            } catch (e: Exception) {
                Toast.makeText(context, "Export saved to: ${file.name}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
