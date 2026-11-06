package com.example

import android.app.Application
import android.content.Context
import com.example.data.db.AppDatabase
import com.example.model.LoggingSettings
import com.example.telemetry.CellularTelemetryEngine
import com.example.telemetry.DemoSimulationEngine
import com.example.telemetry.EnvironmentSensorEngine
import com.example.telemetry.GnssTelemetryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun onCreate() {
        super.onCreate()

        // Configure osmdroid user agent & disk cache paths
        Configuration.getInstance().userAgentValue = packageName
        val osmDir = File(cacheDir, "osmdroid")
        if (!osmDir.exists()) osmDir.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmDir
        Configuration.getInstance().osmdroidTileCache = File(osmDir, "tiles")

        // Load preferences
        val sp = getSharedPreferences("signal_logger_prefs", Context.MODE_PRIVATE)
        val isDemo = sp.getBoolean("is_demo_mode", false)
        val lang = sp.getString("language", "SYSTEM") ?: "SYSTEM"
        val trigger = sp.getString("trigger_mode", "TIME") ?: "TIME"
        val interval = sp.getInt("time_interval", 1)
        val dist = sp.getFloat("dist_threshold", 5f)
        val qnh = sp.getFloat("qnh_hpa", 1013.25f)

        _settings.value = LoggingSettings(
            triggerMode = com.example.model.LogTriggerMode.valueOf(trigger),
            timeIntervalSeconds = interval,
            distanceThresholdMeters = dist,
            isDemoMode = isDemo,
            language = lang,
            qnhHpa = qnh
        )

        // Start hardware engines
        if (!isDemo) {
            cellularEngine.start()
            gnssEngine.start()
            environmentEngine.start()
        }

        // Demo loop if demo is enabled
        applicationScope.launch {
            while (true) {
                if (_settings.value.isDemoMode) {
                    val step = demoEngine.nextSimulationStep(_settings.value.taMultiplierMode)
                    gnssEngine.updateDemoState(step.first)
                    cellularEngine.updateDemoData(step.second.sim1, step.second.sim2, step.second.pingRttMs)
                    environmentEngine.updateDemoPressure(step.third.pressureHpa ?: 1013.25f, step.third.baseQnhHpa)
                    val m = step.third.motion
                    environmentEngine.updateDemoMotion(m.accelX, m.accelY, m.accelZ, m.gyroX, m.gyroY, m.gyroZ, m.compassAzimuthDeg)
                }
                kotlinx.coroutines.delay(1000)
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
            .apply()

        environmentEngine.setQnh(newSettings.qnhHpa)

        if (newSettings.isDemoMode) {
            cellularEngine.stop()
            gnssEngine.stop()
            environmentEngine.stop()
        } else {
            cellularEngine.start()
            gnssEngine.start()
            environmentEngine.start()
        }
    }
}
