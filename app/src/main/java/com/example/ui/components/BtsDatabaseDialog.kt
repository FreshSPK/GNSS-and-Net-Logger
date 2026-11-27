package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bts.BtsGeoUtils
import com.example.bts.BtsImportResult
import com.example.bts.BtsRepository
import com.example.data.db.BtsTowerEntity
import com.example.data.db.OperatorBtsStats
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BtsDatabaseDialog(
    btsRepository: BtsRepository,
    lang: String,
    initialQuery: String = "",
    onDismiss: () -> Unit,
    onShowTowerOnMap: (lat: Double, lon: Double, label: String) -> Unit = { _, _, _ -> }
) {
    val isCs = lang != "EN"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val statsList by btsRepository.operatorStats.collectAsStateWithLifecycle(initialValue = emptyList())
    val districts by btsRepository.districtCodes.collectAsStateWithLifecycle(initialValue = emptyList())
    val importProgress by btsRepository.importProgress.collectAsStateWithLifecycle()
    val isGeocoding by btsRepository.isGeocoding.collectAsStateWithLifecycle()
    val geocodeProgress by btsRepository.geocodeProgress.collectAsStateWithLifecycle()

    var selectedImportOperator by remember { mutableStateOf<String?>(null) } // null = auto
    var replaceOperatorOnImport by remember { mutableStateOf(false) }
    var lastImportSummary by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf(initialQuery) }
    var filterOperatorPlmn by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<BtsTowerEntity>>(emptyList()) }
    var selectedDistrict by remember { mutableStateOf<String?>(null) } // null = All districts
    var lastBatchGeocodeSummary by remember { mutableStateOf<String?>(null) }

    var editingTower by remember { mutableStateOf<BtsTowerEntity?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun refreshSearch() {
        scope.launch {
            searchResults = btsRepository.searchTowers(searchQuery, filterOperatorPlmn, limit = 100)
        }
    }

    LaunchedEffect(searchQuery, filterOperatorPlmn, statsList) {
        searchResults = btsRepository.searchTowers(searchQuery, filterOperatorPlmn, limit = 100)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var totalImported = 0
            var totalCoords = 0
            var totalSkipped = 0
            var totalMs = 0L
            val formats = mutableSetOf<String>()
            for (uri in uris) {
                runCatching {
                    val res: BtsImportResult = btsRepository.importFromUri(
                        uri = uri,
                        operatorPlmnOverride = selectedImportOperator,
                        replaceOperator = replaceOperatorOnImport
                    )
                    totalImported += res.importedCount
                    totalCoords += res.withCoordsCount
                    totalSkipped += res.skippedCount
                    totalMs += res.durationMs
                    formats.add(res.detectedFormat)
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        (if (isCs) "Chyba importu: " else "Import error: ") + (err.localizedMessage ?: "CSV"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            if (totalImported > 0) {
                lastImportSummary = if (isCs) {
                    "Importováno $totalImported vysílačů ($totalCoords s GPS, přeskočeno $totalSkipped prázdných) za ${totalMs} ms • ${formats.joinToString()}"
                } else {
                    "Imported $totalImported cells ($totalCoords with GPS, $totalSkipped empty skipped) in ${totalMs} ms • ${formats.joinToString()}"
                }
            }
        }
    }

    val statsByPlmn = remember(statsList) { statsList.associateBy { it.operatorPlmn } }
    val totalTowers = remember(statsList) { statsList.sumOf { it.totalCount } }
    val totalLocated = remember(statsList) { statsList.sumOf { it.locatedCount } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            shape = RoundedCornerShape(18.dp),
            color = DarkSlate900,
            border = BorderStroke(1.dp, DarkSlate700)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSlate800)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = CyanTertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isCs) "Databáze BTS ČR (3 operátoři)" else "Czech Operators BTS DB",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = if (isCs) {
                                    "Celkem $totalTowers buněk • S GPS: $totalLocated"
                                } else {
                                    "Total $totalTowers cells • With GPS: $totalLocated"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = CyanTertiary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("bts_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isCs) "Zavřít" else "Close",
                            tint = TextPrimaryDark
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("bts_dialog_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    // 3 Czech Operators Summary Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OperatorStatsTile(
                                title = "T-Mobile",
                                plmn = "23001",
                                accent = TMobileMagenta,
                                stats = statsByPlmn["23001"],
                                isCs = isCs,
                                modifier = Modifier.weight(1f),
                                onDelete = {
                                    scope.launch { btsRepository.deleteOperator("23001") }
                                }
                            )
                            OperatorStatsTile(
                                title = "O2 CZ",
                                plmn = "23002",
                                accent = O2Blue,
                                stats = statsByPlmn["23002"],
                                isCs = isCs,
                                modifier = Modifier.weight(1f),
                                onDelete = {
                                    scope.launch { btsRepository.deleteOperator("23002") }
                                }
                            )
                            OperatorStatsTile(
                                title = "Vodafone",
                                plmn = "23003",
                                accent = VodafoneRed,
                                stats = statsByPlmn["23003"],
                                isCs = isCs,
                                modifier = Modifier.weight(1f),
                                onDelete = {
                                    scope.launch { btsRepository.deleteOperator("23003") }
                                }
                            )
                        }
                    }

                    // Streaming Import Card (2-6 MB GSMweb / CLF / OpenCelliD CSV)
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (isCs) "IMPORT BTS (.CSV / .CLF / .NTM / .KML / .GZ / .ZIP)" else "IMPORT BTS (.CSV / .CLF / .NTM / .KML / .GZ / .ZIP)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanTertiary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isCs) {
                                        "Podporované formáty: GSMweb.cz (tlte/o2lte/vlte CSV), CLF v3/v4, NetMonster (.ntm / nm2300x), CellMapper (eNB CSV i signal log), KML, OpenCelliD (230.csv.gz) a .ZIP.\nPriorita přesnosti GPS: Ruční > CLF/NTM/KML > ČÚZK RÚIAN > CellMapper eNB > BeaconDB > OpenCelliD (méně přesné zdroje nikdy nepřepíší přesnější polohu stožáru)."
                                    } else {
                                        "Supported formats: GSMweb.cz CSV, CLF v3/v4, NetMonster (.ntm / nm2300x), CellMapper (eNB Tower CSV & logs), KML, OpenCelliD (230.csv.gz) & .ZIP archives.\nGPS priority: Manual > CLF/NTM/KML > CUZK RUIAN > CellMapper eNB > BeaconDB > OpenCelliD (coarse centroids never overwrite exact mast coordinates)."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSecondaryDark
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gsmweb.cz/"))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_gsmweb_portal_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, CyanTertiary.copy(alpha = 0.55f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = CyanTertiary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "GSMweb.cz (CSV/CLF)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyanTertiary
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://opencellid.org/downloads.php"))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_opencellid_downloads_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, EmeraldSignal.copy(alpha = 0.55f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = EmeraldSignal,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (isCs) "OpenCelliD (230.csv.gz)" else "OpenCelliD (CZ 230)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EmeraldSignal
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.cellmapper.net/"))
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_cellmapper_portal_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.55f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "CellMapper (eNB)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AmberWarning
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (isCs) "Přiřadit operátora při importu:" else "Assign operator on import:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimaryDark
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedImportOperator == null,
                                        onClick = { selectedImportOperator = null },
                                        label = { Text(if (isCs) "Auto (název)" else "Auto (file)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedImportOperator == "23001",
                                        onClick = { selectedImportOperator = "23001" },
                                        label = { Text("T-Mobile (01)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedImportOperator == "23002",
                                        onClick = { selectedImportOperator = "23002" },
                                        label = { Text("O2 CZ (02)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedImportOperator == "23003",
                                        onClick = { selectedImportOperator = "23003" },
                                        label = { Text("Vodafone (03)", fontSize = 11.sp) }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isCs) "Nahradit stávající záznamy operátora" else "Replace existing operator rows",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = replaceOperatorOnImport,
                                        onCheckedChange = { replaceOperatorOnImport = it }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (importProgress.isImporting) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = CyanPrimary,
                                        trackColor = DarkSlate700
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isCs) {
                                            "Importuji ${importProgress.fileName}: ${importProgress.importedCount} buněk (${importProgress.processedLines} řádků)..."
                                        } else {
                                            "Importing ${importProgress.fileName}: ${importProgress.importedCount} cells (${importProgress.processedLines} lines)..."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyanTertiary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                lastImportSummary?.let { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = EmeraldSignal.copy(alpha = 0.14f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = msg,
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = EmeraldSignal
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                        enabled = !importProgress.isImporting,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("bts_import_file_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileUpload,
                                            contentDescription = null,
                                            tint = DarkSlate900,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCs) "Vybrat CSV / CLF / NTM / KML" else "Import CSV / CLF / NTM / KML",
                                            fontWeight = FontWeight.Bold,
                                            color = DarkSlate900,
                                            fontSize = 11.sp
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                val res = btsRepository.importBundledSamples()
                                                lastImportSummary = if (isCs) {
                                                    "Načten vestavěný vzorek: ${res.importedCount} buněk (T-Mobile + O2)"
                                                } else {
                                                    "Bundled sample loaded: ${res.importedCount} cells (T-Mobile + O2)"
                                                }
                                            }
                                        },
                                        enabled = !importProgress.isImporting,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("bts_load_sample_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = if (isCs) "Ukázka CZ" else "CZ Sample", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Precision Geolocation Card (CUZK RUIAN + BeaconDB + Fallback)
                    if (totalTowers > 0) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (isCs) "PŘESNÉ DOPLNĚNÍ GPS (ČÚZK RÚIAN + BEACONDB)" else "PRECISION TOWER GPS (CUZK RUIAN + BEACONDB)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isCs) {
                                            "1) Adresy z GSMwebu (ulice + č.p.) lokalizuje na budovu s přesností ~2–10 m přes oficiální registr ČÚZK RÚIAN.\n2) Stožáry v lese / bez ulice dohledá bez API klíče přes BeaconDB (api.beacondb.net).\nPozn.: Přímo na gsmweb.cz/celltrack/clf/ je zakázán výpis složky (403) – na GSMwebu stahujte přes hlavní menu, nebo využijte automatické zaměření zde."
                                        } else {
                                            "1) Resolves GSMweb street addresses to ~2–10m building accuracy via official Czech CUZK RUIAN.\n2) Resolves rural/streetless masts via BeaconDB cell lookup (no API key required)."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = TextSecondaryDark
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        item {
                                            FilterChip(
                                                selected = selectedDistrict == null,
                                                onClick = { selectedDistrict = null },
                                                label = { Text(if (isCs) "Všechny" else "All Sites", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                            )
                                        }
                                        items(districts) { code ->
                                            FilterChip(
                                                selected = selectedDistrict == code,
                                                onClick = { selectedDistrict = code },
                                                label = { Text(code, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    lastBatchGeocodeSummary?.let { summaryText ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = EmeraldSignal.copy(alpha = 0.14f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = summaryText,
                                                modifier = Modifier.padding(8.dp),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = EmeraldSignal
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (isGeocoding) {
                                        val (done, tot) = geocodeProgress
                                        LinearProgressIndicator(
                                            progress = { if (tot > 0) done.toFloat() / tot else 0f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = AmberWarning,
                                            trackColor = DarkSlate700
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val scopeLabel = selectedDistrict ?: if (isCs) "Vše" else "All"
                                        Text(
                                            text = if (isCs) "Lokalizuji ($scopeLabel): $done / $tot stožárů..." else "Resolving ($scopeLabel): $done / $tot masts...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = AmberWarning
                                        )
                                    } else {
                                        val districtBtnLabel = selectedDistrict ?: if (isCs) "všechny okresy" else "All Sites"
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    val summary = btsRepository.batchGeocodeSites(
                                                        districtCode = selectedDistrict,
                                                        plmnFilter = filterOperatorPlmn,
                                                        limit = 50
                                                    )
                                                    val msg = if (isCs) {
                                                        "Nalezeno ${summary.resolved}/${summary.attempted} stožárů • RÚIAN: ${summary.ruianCount} • BeaconDB: ${summary.beaconDbCount} • Ostatní: ${summary.fallbackCount}"
                                                    } else {
                                                        "Resolved ${summary.resolved}/${summary.attempted} masts • RUIAN: ${summary.ruianCount} • BeaconDB: ${summary.beaconDbCount} • Other: ${summary.fallbackCount}"
                                                    }
                                                    lastBatchGeocodeSummary = msg
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    refreshSearch()
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("bts_batch_geocode_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.TravelExplore, contentDescription = null, tint = DarkSlate900, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isCs) "Dohledat GPS ($districtBtnLabel • max 50)" else "Resolve GPS ($districtBtnLabel • up to 50)",
                                                fontWeight = FontWeight.Bold,
                                                color = DarkSlate900,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search & Filter Card
                    item {
                        Column {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bts_search_field"),
                                placeholder = {
                                    Text(
                                        text = if (isCs) "Hledat CellID, eNodeB, PCI, okres (BM) nebo ulici..." else "Search CellID, eNodeB, PCI, district or street...",
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(imageVector = Icons.Default.Clear, contentDescription = null)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = filterOperatorPlmn == null,
                                    onClick = { filterOperatorPlmn = null },
                                    label = { Text(if (isCs) "Všichni" else "All", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = filterOperatorPlmn == "23001",
                                    onClick = { filterOperatorPlmn = "23001" },
                                    label = { Text("T-Mobile", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = filterOperatorPlmn == "23002",
                                    onClick = { filterOperatorPlmn = "23002" },
                                    label = { Text("O2 CZ", fontSize = 11.sp) }
                                )
                                FilterChip(
                                    selected = filterOperatorPlmn == "23003",
                                    onClick = { filterOperatorPlmn = "23003" },
                                    label = { Text("Vodafone", fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    if (searchResults.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                            ) {
                                Text(
                                    text = if (isCs) "Žádné BTS neodpovídají dotazu. Načtěte CSV operátorů výše." else "No matching BTS entries found. Import operator CSVs above.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    } else {
                        items(searchResults, key = { "${it.operatorPlmn}_${it.cellId}" }) { tower ->
                            BtsTowerRowCard(
                                tower = tower,
                                isCs = isCs,
                                onGeocode = {
                                    scope.launch {
                                        val res = btsRepository.geocodeAndSaveSiteDetailed(
                                            plmn = tower.operatorPlmn,
                                            cellId = tower.cellId,
                                            enodebId = tower.enodebId,
                                            locationName = tower.locationName,
                                            tac = tower.tac,
                                            band = tower.band
                                        )
                                        if (res != null) {
                                            Toast.makeText(
                                                context,
                                                if (isCs) "GPS nalezena (${res.source}): ${String.format(Locale.US, "%.5f, %.5f", res.latitude, res.longitude)}"
                                                else "GPS resolved (${res.source}): ${String.format(Locale.US, "%.5f, %.5f", res.latitude, res.longitude)}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isCs) "GPS se nepodařilo dohledat ani přes RÚIAN/BeaconDB (zkuste zadat ručně)" else "Could not resolve GPS via RUIAN/BeaconDB (try manual entry)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        refreshSearch()
                                    }
                                },
                                onEditCoords = { editingTower = tower },
                                onShowOnMap = {
                                    if (tower.latitude != null && tower.longitude != null) {
                                        onShowTowerOnMap(
                                            tower.latitude,
                                            tower.longitude,
                                            "${BtsGeoUtils.operatorDisplayName(tower.operatorPlmn)} eNB ${tower.enodebId}: ${tower.locationName}"
                                        )
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }

                    if (totalTowers > 0) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseCritical),
                                border = BorderStroke(1.dp, RoseCritical.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isCs) "Vymazat celou databázi BTS" else "Clear Entire BTS Database", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    editingTower?.let { tower ->
        var latText by remember(tower) { mutableStateOf(tower.latitude?.toString() ?: "") }
        var lonText by remember(tower) { mutableStateOf(tower.longitude?.toString() ?: "") }
        var applyToSite by remember(tower) { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { editingTower = null },
            containerColor = DarkSlate800,
            title = {
                Text(
                    text = if (isCs) "Upravit GPS vysílače BTS" else "Edit BTS Coordinates",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "${BtsGeoUtils.operatorDisplayName(tower.operatorPlmn)} • CI ${tower.cellId} (eNB ${tower.enodebId}/${tower.sectorId})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CyanTertiary
                    )
                    if (tower.locationName.isNotBlank()) {
                        Text(
                            text = tower.locationName,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondaryDark
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(if (isCs) "Zeměpisná šířka (Latitude, např. 49.22695)" else "Latitude (e.g. 49.22695)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = { lonText = it },
                        label = { Text(if (isCs) "Zeměpisná délka (Longitude, např. 16.57545)" else "Longitude (e.g. 16.57545)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = applyToSite, onCheckedChange = { applyToSite = it })
                        Text(
                            text = if (isCs) "Použít pro všechna pásma/sektory stožáru" else "Apply to all bands/sectors on this mast",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lat = latText.trim().replace(',', '.').toDoubleOrNull()
                    val lon = lonText.trim().replace(',', '.').toDoubleOrNull()
                    if (BtsGeoUtils.isValidCzechCoord(lat, lon)) {
                        scope.launch {
                            btsRepository.saveSiteCoordinates(
                                plmn = tower.operatorPlmn,
                                cellId = tower.cellId,
                                enodebId = tower.enodebId,
                                locationName = tower.locationName,
                                lat = lat!!,
                                lon = lon!!,
                                source = "MANUAL",
                                applyToEntireSite = applyToSite
                            )
                            editingTower = null
                            refreshSearch()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            if (isCs) "Zadejte platné souřadnice ČR (Lat 48.3–51.2, Lon 11.9–19.0)" else "Enter valid Czechia WGS84 coordinates",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text(if (isCs) "Uložit" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTower = null }) {
                    Text(if (isCs) "Zrušit" else "Cancel")
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = DarkSlate800,
            title = { Text(if (isCs) "Smazat databázi BTS?" else "Clear BTS Database?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (isCs) "Opravdu odstranit všech $totalTowers importovaných záznamů vysílačů?" else "Delete all $totalTowers imported BTS tower records?",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        scope.launch { btsRepository.clearAll() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseCritical)
                ) {
                    Text(if (isCs) "Smazat vše" else "Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(if (isCs) "Zrušit" else "Cancel")
                }
            }
        )
    }
}

@Composable
private fun OperatorStatsTile(
    title: String,
    plmn: String,
    accent: Color,
    stats: OperatorBtsStats?,
    isCs: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit
) {
    val total = stats?.totalCount ?: 0
    val located = stats?.locatedCount ?: 0
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (total > 0) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = if (isCs) "Smazat $title" else "Delete $title",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Text(
                text = "PLMN $plmn",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimaryDark
            )
            Text(
                text = if (isCs) "GPS: $located" else "GPS: $located",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = if (located > 0) EmeraldSignal else TextSecondaryDark
            )
        }
    }
}

@Composable
private fun BtsTowerRowCard(
    tower: BtsTowerEntity,
    isCs: Boolean,
    onGeocode: () -> Unit,
    onEditCoords: () -> Unit,
    onShowOnMap: () -> Unit
) {
    val accent = when (tower.operatorPlmn) {
        "23001" -> TMobileMagenta
        "23002" -> O2Blue
        "23003" -> VodafoneRed
        else -> CyanTertiary
    }
    val hasCoords = BtsGeoUtils.isValidCzechCoord(tower.latitude, tower.longitude)

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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = BtsGeoUtils.operatorDisplayName(tower.operatorPlmn),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CI ${tower.cellId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimaryDark
                    )
                }
                Text(
                    text = listOfNotNull(
                        tower.band.takeIf { it.isNotBlank() }?.let { "${it}M" },
                        tower.pci.takeIf { it >= 0 }?.let { "PCI $it" },
                        tower.tac.takeIf { it > 0 }?.let { "TAC $it" }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    fontFamily = FontFamily.Monospace,
                    color = CyanTertiary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = tower.locationName.ifBlank { if (isCs) "(Bez popisu lokality)" else "(No site description)" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimaryDark
            )

            val metaParts = buildList {
                if (tower.enodebId > 0) add("eNB ${tower.enodebId}/${tower.sectorId}")
                if (tower.districtCode.isNotBlank()) add("${if (isCs) "Okr" else "Dist"} ${tower.districtCode}")
                if (tower.updatedDate.isNotBlank()) add(tower.updatedDate)
                if (tower.gsmCid.isNotBlank()) add("GSM ${tower.gsmCid}")
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = TextSecondaryDark
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasCoords) {
                    val srcColor = when (tower.coordSource.uppercase(Locale.ROOT)) {
                        "MANUAL", "CLF", "NTM", "KML", "RUIAN", "IMPORT" -> EmeraldSignal
                        "CELLMAPPER", "BEACONDB" -> CyanTertiary
                        else -> AmberWarning
                    }
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.5f, %.5f (%s)",
                            tower.latitude,
                            tower.longitude,
                            tower.coordSource.ifBlank { "GPS" }
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        fontFamily = FontFamily.Monospace,
                        color = srcColor,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (isCs) "Bez GPS souřadnic" else "No GPS coordinates",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (hasCoords) {
                        OutlinedButton(
                            onClick = onShowOnMap,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isCs) "Mapa" else "Map", fontSize = 10.sp)
                        }
                    } else if (tower.locationName.isNotBlank() || tower.cellId > 0L) {
                        OutlinedButton(
                            onClick = onGeocode,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LocationSearching, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(if (isCs) "Najít GPS" else "Geocode", fontSize = 10.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onEditCoords,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.EditLocationAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isCs) "GPS" else "Edit", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
