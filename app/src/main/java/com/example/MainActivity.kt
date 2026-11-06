package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.TelemetryRecord
import com.example.service.TelemetryLoggingService
import com.example.ui.components.AboutAndHelpDialog
import com.example.ui.tabs.*
import com.example.ui.theme.DarkSlate900
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
            val chartPoints by TelemetryLoggingService.recentChartPoints.collectAsStateWithLifecycle()

            val sessions by app.database.telemetryDao().getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())

            var currentTab by remember { mutableStateOf(AppTab.OVERVIEW) }
            var showSettingsDialog by remember { mutableStateOf(false) }
            var showHelpDialog by remember { mutableStateOf(false) }
            var replayRecords by remember { mutableStateOf<List<TelemetryRecord>?>(null) }

            val coroutineScope = rememberCoroutineScope()

            val currentLang = remember(settings.language) {
                if (settings.language == "SYSTEM") {
                    val defaultLang = java.util.Locale.getDefault().language
                    if (defaultLang.equals("cs", ignoreCase = true)) "CS" else "EN"
                } else settings.language
            }

            // Permissions launcher
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Granted */ }

            LaunchedEffect(Unit) {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
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
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "GNSS & Net Logger",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Aplikaci vytvořil Jan Kufa z VUT",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            actions = {
                                // Settings button (tuning parameters)
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.testTag("settings_top_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Tune, contentDescription = "Nastavení", tint = Color.White)
                                }

                                // Help / Info button (i)
                                IconButton(
                                    onClick = { showHelpDialog = true },
                                    modifier = Modifier.testTag("help_top_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Info, contentDescription = "Nápověda a autor", tint = MaterialTheme.colorScheme.primary)
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
                            AppTab.values().forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                                    label = { Text(text = tab.title, fontSize = 11.sp, fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal) },
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
                                    onUpdateFieldsSelection = { newFields ->
                                        app.updateSettings(settings.copy(fieldsSelection = newFields))
                                    }
                                )
                            }
                            AppTab.CELLULAR_RF -> {
                                CellularRfTab(
                                    cellular = cellularState,
                                    lang = currentLang
                                )
                            }
                            AppTab.GNSS -> {
                                GnssTab(
                                    gnss = gnssState,
                                    lang = currentLang
                                )
                            }
                            AppTab.MAP -> {
                                MapTab(
                                    gnss = gnssState,
                                    cellular = cellularState,
                                    replayRecords = replayRecords,
                                    lang = currentLang,
                                    onClearReplay = { replayRecords = null }
                                )
                            }
                            AppTab.CHARTS -> {
                                ChartsTab(
                                    chartPoints = chartPoints,
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
                                            replayRecords = records
                                            currentTab = AppTab.MAP
                                        }
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
                        onSaveSettings = { newSettings ->
                            app.updateSettings(newSettings)
                        }
                    )
                }

                if (showHelpDialog) {
                    AboutAndHelpDialog(
                        onDismiss = { showHelpDialog = false }
                    )
                }
            }
        }
    }
}
