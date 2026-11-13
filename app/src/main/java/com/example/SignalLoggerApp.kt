package com.example

import android.app.Application
import android.content.Context
import com.example.data.db.AppDatabase
import com.example.model.ChartPoint
import com.example.model.LoggedFieldsSelection
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import com.example.model.MapTrailPoint
import com.example.service.TelemetryLoggingService
import com.example.telemetry.CellularTelemetryEngine
import com.example.telemetry.DemoSimulationEngine
import com.example.telemetry.EnvironmentSensorEngine
import com.example.telemetry.GnssTelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class SignalLoggerApp : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.getInstance(this) }
    val cellularEngine by lazy { CellularTelemetryEngine(this, applicationScope) }
    val gnssEngine by lazy { GnssTelemetryEngine(this, applicationScope) }
    val environmentEngine by lazy { EnvironmentSensorEngine(this) }
    val demoEngine by lazy { DemoSimulationEngine() }

    private val _settings = MutableStateFlow(LoggingSettings())
    val settings: StateFlow<LoggingSettings> = _settings.asStateFlow()

    @Volatile
    private var isUiInForeground: Boolean = false

    private var demoLoopJob: Job? = null
    private var chartFeedJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        // Configure osmdroid user agent & disk cache paths
        Configuration.getInstance().userAgentValue = packageName
        val osmDir = File(cacheDir, "osmdroid")
        if (!osmDir.exists()) osmDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmDir
        Configuration.getInstance().osmdroidTileCache = File(osmDir, "tiles")

        // Load preferences (including full field selections, TA mode, darkMapMode, audio alerts)
        val sp = getSharedPreferences("signal_logger_prefs", Context.MODE_PRIVATE)
        val isDemo = sp.getBoolean("is_demo_mode", false)
        val lang = sp.getString("language", "SYSTEM") ?: "SYSTEM"
        val triggerName = sp.getString("trigger_mode", "TIME") ?: "TIME"
        val triggerMode = runCatching { LogTriggerMode.valueOf(triggerName) }.getOrDefault(LogTriggerMode.TIME)
        val interval = sp.getInt("time_interval", 1)
        val dist = sp.getFloat("dist_threshold", 5f)
        val qnh = sp.getFloat("qnh_hpa", 1013.25f)
        val taMode = sp.getInt("ta_multiplier_mode", 2)
        val darkMap = sp.getBoolean("dark_map_mode", true)
        val audioAlerts = sp.getBoolean("audio_anomaly_alerts", false)

        val fields = LoggedFieldsSelection(
            logGnss = sp.getBoolean("field_gnss", true),
            logCellularRf = sp.getBoolean("field_cellular_rf", true),
            logNeighbors = sp.getBoolean("field_neighbors", true),
            logSensors = sp.getBoolean("field_sensors", true),
            logBattery = sp.getBoolean("field_battery", true),
            logPing = sp.getBoolean("field_ping", true),
            logNmea = sp.getBoolean("field_nmea", false)
        )

        val loaded = LoggingSettings(
            triggerMode = triggerMode,
            timeIntervalSeconds = interval,
            distanceThresholdMeters = dist,
            taMultiplierMode = taMode,
            isDemoMode = isDemo,
            language = lang,
            qnhHpa = qnh,
            darkMapMode = darkMap,
            audioAnomalyAlerts = audioAlerts,
            fieldsSelection = fields
        )
        _settings.value = loaded

        environmentEngine.setQnh(loaded.qnhHpa)
        cellularEngine.setTaMultiplierMode(loaded.taMultiplierMode)
        reconcileEngines()
    }

    fun setUiInForeground(inForeground: Boolean) {
        isUiInForeground = inForeground
        reconcileEngines()
    }

    fun reconcileEngines() {
        val current = _settings.value
        val loggingActive = TelemetryLoggingService.isLogging.value
        val needsTelemetry = isUiInForeground || loggingActive

        if (needsTelemetry && !current.isDemoMode) {
            cellularEngine.setTaMultiplierMode(current.taMultiplierMode)
            cellularEngine.setPingEnabled(current.fieldsSelection.logPing)
            cellularEngine.start()
            gnssEngine.start()
            environmentEngine.start()
        } else {
            cellularEngine.stop()
            gnssEngine.stop()
            environmentEngine.stop()
        }

        if (needsTelemetry && current.isDemoMode) {
            startDemoLoop()
        } else {
            demoLoopJob?.cancel()
            demoLoopJob = null
        }

        if (needsTelemetry) {
            startChartFeedLoop()
        } else {
            chartFeedJob?.cancel()
            chartFeedJob = null
        }
    }

    private fun startDemoLoop() {
        if (demoLoopJob?.isActive == true) return
        demoLoopJob = applicationScope.launch {
            while (isActive && _settings.value.isDemoMode && (isUiInForeground || TelemetryLoggingService.isLogging.value)) {
                val step = demoEngine.nextSimulationStep(_settings.value.taMultiplierMode)
                gnssEngine.updateDemoState(step.first)
                cellularEngine.updateDemoData(step.second.sim1, step.second.sim2, step.second.pingRttMs)
                environmentEngine.updateDemoPressure(step.third.pressureHpa ?: 1013.25f, step.third.baseQnhHpa)
                val m = step.third.motion
                environmentEngine.updateDemoMotion(m.accelX, m.accelY, m.accelZ, m.gyroX, m.gyroY, m.gyroZ, m.compassAzimuthDeg)
                delay(1000)
            }
        }
    }

    private fun startChartFeedLoop() {
        if (chartFeedJob?.isActive == true) return
        chartFeedJob = applicationScope.launch {
            while (isActive && (isUiInForeground || TelemetryLoggingService.isLogging.value)) {
                val c = cellularEngine.cellularState.value
                val g = gnssEngine.gnssState.value
                val now = System.currentTimeMillis()

                if (g.hasFix && (g.latitude != 0.0 || g.longitude != 0.0)) {
                    TelemetryLoggingService.addMapTrailPoint(
                        MapTrailPoint(
                            latitude = g.latitude,
                            longitude = g.longitude,
                            sim1Rsrp = c.sim1.rsrp,
                            sim2Rsrp = c.sim2.rsrp,
                            speedKmh = g.speedKmh,
                            satellitesUsed = g.satellitesUsedInFix,
                            pdop = g.pdop,
                            timestampMs = now
                        )
                    )
                }

                if (!TelemetryLoggingService.isLogging.value) {
                    val e = environmentEngine.envState.value
                    TelemetryLoggingService.addChartPoint(
                        ChartPoint(
                            timestampMs = now,
                            rsrp = c.sim1.rsrp?.toFloat(),
                            rsrpSim2 = c.sim2.rsrp?.toFloat(),
                            sinrSim1 = c.sim1.sinr,
                            sinrSim2 = c.sim2.sinr,
                            speedKmh = g.speedKmh,
                            satCount = g.satellitesUsedInFix,
                            pdop = if (g.hasFix && g.pdop > 0f && g.pdop < 9.0f) g.pdop else null,
                            agcScore = g.agcInterferenceScore,
                            pingMs = c.pingRttMs?.toFloat(),
                            accelMagnitude = e.motion.accelMagnitude,
                            gyroZ = e.motion.gyroZ,
                            compassAzimuth = e.motion.compassAzimuthDeg,
                            batteryTempC = e.battery.temperatureC,
                            sim1Operator = c.sim1.displayName.ifBlank { "SIM 1" },
                            sim2Operator = c.sim2.displayName.ifBlank { "SIM 2" }
                        )
                    )
                }
                delay(1000)
            }
        }
    }

    fun updateSettings(newSettings: LoggingSettings) {
        _settings.value = newSettings
        val sp = getSharedPreferences("signal_logger_prefs", Context.MODE_PRIVATE)
        sp.edit()
            .putBoolean("is_demo_mode", newSettings.isDemoMode)
            .putString("language", newSettings.language)
            .putString("trigger_mode", newSettings.triggerMode.name)
            .putInt("time_interval", newSettings.timeIntervalSeconds)
            .putFloat("dist_threshold", newSettings.distanceThresholdMeters)
            .putFloat("qnh_hpa", newSettings.qnhHpa)
            .putInt("ta_multiplier_mode", newSettings.taMultiplierMode)
            .putBoolean("dark_map_mode", newSettings.darkMapMode)
            .putBoolean("audio_anomaly_alerts", newSettings.audioAnomalyAlerts)
            .putBoolean("field_gnss", newSettings.fieldsSelection.logGnss)
            .putBoolean("field_cellular_rf", newSettings.fieldsSelection.logCellularRf)
            .putBoolean("field_neighbors", newSettings.fieldsSelection.logNeighbors)
            .putBoolean("field_sensors", newSettings.fieldsSelection.logSensors)
            .putBoolean("field_battery", newSettings.fieldsSelection.logBattery)
            .putBoolean("field_ping", newSettings.fieldsSelection.logPing)
            .putBoolean("field_nmea", newSettings.fieldsSelection.logNmea)
            .apply()

        environmentEngine.setQnh(newSettings.qnhHpa)
        cellularEngine.setTaMultiplierMode(newSettings.taMultiplierMode)
        reconcileEngines()
    }

    fun shutdownApp() {
        isUiInForeground = false
        demoLoopJob?.cancel()
        chartFeedJob?.cancel()
        cellularEngine.stop()
        gnssEngine.stop()
        environmentEngine.stop()
    }
}
