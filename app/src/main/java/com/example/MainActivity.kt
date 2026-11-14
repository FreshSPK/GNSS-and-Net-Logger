package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.TelemetryRecord
import com.example.service.TelemetryLoggingService
import com.example.ui.components.AboutAndHelpDialog
import com.example.ui.tabs.*
import com.example.ui.theme.DarkSlate800
import com.example.ui.theme.DarkSlate900
import com.example.ui.theme.RoseCritical
import com.example.ui.theme.SignalLoggerTheme
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Měření", Icons.Default.Speed),
    CELLULAR_RF("Mobily", Icons.Default.CellTower),
    GNSS("Satelity", Icons.Default.SatelliteAlt),
    MAP("Mapa", Icons.Default.Map),
    CHARTS("Grafy", Icons.Default.ShowChart),
    HISTORY("Historie", Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {}
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SignalLoggerApp

        setContent {
            val settings by app.settings.collectAsStateWithLifecycle()
            val gnssState by app.gnssEngine.gnssState.collectAsStateWithLifecycle()
            val cellularState by app.cellularEngine.cellularState.collectAsStateWithLifecycle()
            val envState by app.environmentEngine.envState.collectAsStateWithLifecycle()

            val isLogging by TelemetryLoggingService.isLogging.collectAsStateWithLifecycle()
            val isPaused by TelemetryLoggingService.isPaused.collectAsStateWithLifecycle()
            val loggedPoints by TelemetryLoggingService.loggedPointsCount.collectAsStateWithLifecycle()
            val sessionStartTimeMs by TelemetryLoggingService.sessionStartTimeMs.collectAsStateWithLifecycle()
            val sessionDistanceMeters by TelemetryLoggingService.sessionDistanceMeters.collectAsStateWithLifecycle()
            val handoverCount by TelemetryLoggingService.handoverCount.collectAsStateWithLifecycle()
            val handoverCountSim1 by TelemetryLoggingService.handoverCountSim1.collectAsStateWithLifecycle()
            val handoverCountSim2 by TelemetryLoggingService.handoverCountSim2.collectAsStateWithLifecycle()
            val chartPoints by TelemetryLoggingService.recentChartPoints.collectAsStateWithLifecycle()
            val liveMapTrail by TelemetryLoggingService.liveMapTrail.collectAsStateWithLifecycle()

            val sessions by app.database.telemetryDao().getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())

            var currentTab by remember { mutableStateOf(AppTab.OVERVIEW) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showHelpDialog by remember { mutableStateOf(false) }
            var showExitConfirmDialog by remember { mutableStateOf(false) }
            var replayRecords by remember { mutableStateOf<List<TelemetryRecord>?>(null) }
            var focusedReplayPoint by remember { mutableStateOf<TelemetryRecord?>(null) }
            var historicalChartTitle by remember { mutableStateOf<String?>(null) }
            var historicalChartRecords by remember { mutableStateOf<List<TelemetryRecord>?>(null) }

            val coroutineScope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            // Lifecycle-aware sensor & GNSS pause when UI moves to background and no logging session is active
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> app.setUiInForeground(true)
                        Lifecycle.Event.ON_STOP -> app.setUiInForeground(false)
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val currentLang = remember(settings.language) {
                if (settings.language == "SYSTEM") {
                    val defaultLang = java.util.Locale.getDefault().language
                    if (defaultLang.equals("cs", ignoreCase = true)) "CS" else "EN"
                } else settings.language
            }
            val isCs = currentLang != "EN"

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                app.reconcileEngines()
            }

            LaunchedEffect(Unit) {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                val notGranted = permissions.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (notGranted.isNotEmpty()) {
                    permissionLauncher.launch(notGranted.toTypedArray())
                }
            }

            SignalLoggerTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.app_logo),
                                        contentDescription = "Logo",
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "GNSS & Net Logger",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isCs) "Aplikaci vytvořil Jan Kufa z VUT" else "Developed by Jan Kufa (BUT Brno)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.testTag("settings_top_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Tune, contentDescription = if (isCs) "Nastavení" else "Settings", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { showHelpDialog = true },
                                    modifier = Modifier.testTag("help_top_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = if (isCs) "Nápověda a autor" else "Help & Credits", tint = MaterialTheme.colorScheme.primary)
                                }

                                IconButton(
                                    onClick = {
                                        if (isLogging) {
                                            showExitConfirmDialog = true
                                        } else {
                                            app.shutdownApp()
                                            finishAndRemoveTask()
                                        }
                                    },
                                    modifier = Modifier.testTag("exit_app_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = if (isCs) "Ukončit aplikaci" else "Exit Application",
                                        tint = RoseCritical
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = DarkSlate900
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = DarkSlate900
                        ) {
                            AppTab.entries.forEach { tab ->
                                val tabTitle = when (tab) {
                                    AppTab.OVERVIEW -> if (isCs) "Měření" else "Overview"
                                    AppTab.CELLULAR_RF -> if (isCs) "Mobily" else "Cellular"
                                    AppTab.GNSS -> if (isCs) "Satelity" else "Satellites"
                                    AppTab.MAP -> if (isCs) "Mapa" else "Map"
                                    AppTab.CHARTS -> if (isCs) "Grafy" else "Charts"
                                    AppTab.HISTORY -> if (isCs) "Historie" else "History"
                                }
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tabTitle) },
                                    label = { Text(text = tabTitle, fontSize = 11.sp, fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal) },
                                    modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            AppTab.OVERVIEW -> {
                                OverviewTab(
                                    gnss = gnssState,
                                    cellular = cellularState,
                                    env = envState,
                                    isLogging = isLogging,
                                    isPaused = isPaused,
                                    loggedPoints = loggedPoints,
                                    sessionStartTimeMs = sessionStartTimeMs,
                                    sessionDistanceMeters = sessionDistanceMeters,
                                    handoverCount = handoverCount,
                                    handoverCountSim1 = handoverCountSim1,
                                    handoverCountSim2 = handoverCountSim2,
                                    fieldsSelection = settings.fieldsSelection,
                                    lang = currentLang,
                                    onStartLogging = {
                                        val intent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                            action = TelemetryLoggingService.ACTION_START
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                    },
                                    onPauseLogging = {
                                        val intent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                            action = TelemetryLoggingService.ACTION_PAUSE
                                        }
                                        startService(intent)
                                    },
                                    onResumeLogging = {
                                        val intent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                            action = TelemetryLoggingService.ACTION_RESUME
                                        }
                                        startService(intent)
                                    },
                                    onStopLogging = {
                                        val intent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                            action = TelemetryLoggingService.ACTION_STOP
                                        }
                                        startService(intent)
                                    },
                                    onAddWaypoint = { note ->
                                        val intent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                            action = TelemetryLoggingService.ACTION_ADD_WAYPOINT
                                            putExtra(TelemetryLoggingService.EXTRA_WAYPOINT_NOTE, note)
                                        }
                                        startService(intent)
                                    },
                                    onOpenBatterySettings = { openBatteryOptimizationSettings() },
                                    onUpdateFieldsSelection = { newFields ->
                                        app.updateSettings(settings.copy(fieldsSelection = newFields))
                                    }
                                )
                            }
                            AppTab.CELLULAR_RF -> {
                                CellularRfTab(
                                    cellular = cellularState,
                                    isLogging = isLogging,
                                    handoverCount = handoverCount,
                                    handoverCountSim1 = handoverCountSim1,
                                    handoverCountSim2 = handoverCountSim2,
                                    lang = currentLang
                                )
                            }
                            AppTab.GNSS -> {
                                GnssTab(
                                    gnss = gnssState,
                                    lang = currentLang,
                                    onResetAgcBaseline = { app.gnssEngine.resetAgcBaseline() }
                                )
                            }
                            AppTab.MAP -> {
                                MapTab(
                                    gnss = gnssState,
                                    cellular = cellularState,
                                    liveTrail = liveMapTrail,
                                    replayRecords = replayRecords,
                                    focusedReplayPoint = focusedReplayPoint,
                                    darkMapMode = settings.darkMapMode,
                                    onToggleDarkMap = { enabled ->
                                        app.updateSettings(settings.copy(darkMapMode = enabled))
                                    },
                                    lang = currentLang,
                                    onClearReplay = {
                                        replayRecords = null
                                        focusedReplayPoint = null
                                    },
                                    onClearFocusedPoint = { focusedReplayPoint = null },
                                    onClearLiveTrail = { TelemetryLoggingService.clearLiveMapTrail() }
                                )
                            }
                            AppTab.CHARTS -> {
                                ChartsTab(
                                    chartPoints = chartPoints,
                                    historicalSessionTitle = historicalChartTitle,
                                    historicalRecords = historicalChartRecords,
                                    onClearHistorical = {
                                        historicalChartTitle = null
                                        historicalChartRecords = null
                                    },
                                    lang = currentLang
                                )
                            }
                            AppTab.HISTORY -> {
                                HistoryTab(
                                    sessions = sessions,
                                    lang = currentLang,
                                    onSelectSessionForReplay = { sessionId ->
                                        coroutineScope.launch {
                                            val records = app.database.telemetryDao().getRecordsListForSession(sessionId)
                                            focusedReplayPoint = null
                                            replayRecords = records
                                            currentTab = AppTab.MAP
                                        }
                                    },
                                    onSelectSessionForCharts = { session ->
                                        coroutineScope.launch {
                                            val records = app.database.telemetryDao().getRecordsListForSession(session.id)
                                            historicalChartTitle = session.title
                                            historicalChartRecords = records
                                            currentTab = AppTab.CHARTS
                                        }
                                    },
                                    onSelectPointOnMap = { sessionRecords, record ->
                                        replayRecords = sessionRecords
                                        focusedReplayPoint = record
                                        currentTab = AppTab.MAP
                                    },
                                    onRenameSession = { sessionId, newTitle ->
                                        coroutineScope.launch {
                                            app.database.telemetryDao().renameSession(sessionId, newTitle)
                                        }
                                    },
                                    onDeleteSession = { session ->
                                        coroutineScope.launch {
                                            app.database.telemetryDao().deleteSession(session)
                                        }
                                    },
                                    onDeleteRecord = { record ->
                                        coroutineScope.launch {
                                            app.database.telemetryDao().deleteRecord(record)
                                        }
                                    },
                                    loadRecords = { sId ->
                                        app.database.telemetryDao().getRecordsListForSession(sId)
                                    }
                                )
                            }
                        }
                    }
                }

                if (showSettingsDialog) {
                    SettingsDialog(
                        settings = settings,
                        lang = currentLang,
                        onDismiss = { showSettingsDialog = false },
                        onOpenBatterySettings = { openBatteryOptimizationSettings() },
                        onSaveSettings = { newSettings ->
                            app.updateSettings(newSettings)
                        }
                    )
                }

                if (showHelpDialog) {
                    AboutAndHelpDialog(
                        lang = currentLang,
                        onDismiss = { showHelpDialog = false }
                    )
                }

                if (showExitConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showExitConfirmDialog = false },
                        containerColor = DarkSlate800,
                        title = {
                            Text(
                                text = if (isCs) "Ukončit aplikaci?" else "Exit Application?",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                text = if (isCs) {
                                    "Aktuálně probíhá záznam telemetrie do databáze. Chcete záznam bezpečně uložit a ukončit celou aplikaci, nebo jen zavřít okno a ponechat záznam na pozadí?"
                                } else {
                                    "Field telemetry logging is currently running. Stop & save the session before exiting, or keep recording in the background?"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showExitConfirmDialog = false
                                    val stopIntent = Intent(this@MainActivity, TelemetryLoggingService::class.java).apply {
                                        action = TelemetryLoggingService.ACTION_SHUTDOWN
                                    }
                                    startService(stopIntent)
                                    app.shutdownApp()
                                    finishAndRemoveTask()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseCritical)
                            ) {
                                Text(if (isCs) "Uložit & Ukončit" else "Stop & Exit", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { showExitConfirmDialog = false }) {
                                    Text(if (isCs) "Zrušit" else "Cancel")
                                }
                                TextButton(onClick = {
                                    showExitConfirmDialog = false
                                    moveTaskToBack(true)
                                }) {
                                    Text(if (isCs) "Na pozadí" else "Background")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
