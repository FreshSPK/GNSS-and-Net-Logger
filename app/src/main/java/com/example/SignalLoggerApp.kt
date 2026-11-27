package com.example

import android.app.Application
import android.content.Context
import com.example.bts.BtsRepository
import com.example.data.db.AppDatabase
import com.example.model.AnomalySeverity
import com.example.model.AnomalyType
import com.example.model.CellularAnomaly
import com.example.model.ChartPoint
import com.example.model.GnssState
import com.example.model.LoggedFieldsSelection
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import com.example.model.MapTrailPoint
import com.example.model.SimCellInfo
import com.example.security.SecurityMonitorEngine
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
    val btsRepository by lazy { BtsRepository(this, database.btsDao()) }
    val cellularEngine by lazy { CellularTelemetryEngine(this, applicationScope) }
    val gnssEngine by lazy { GnssTelemetryEngine(this, applicationScope) }
    val environmentEngine by lazy { EnvironmentSensorEngine(this) }
    val securityEngine by lazy { SecurityMonitorEngine(this, applicationScope) }
    val demoEngine by lazy { DemoSimulationEngine() }

    private val _settings = MutableStateFlow(LoggingSettings())
    val settings: StateFlow<LoggingSettings> = _settings.asStateFlow()

    @Volatile
    private var isUiInForeground: Boolean = false
    private var wasDemoMode: Boolean = false

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
        val autoGeocode = sp.getBoolean("auto_geocode_bts", true)

        val fields = LoggedFieldsSelection(
            logGnss = sp.getBoolean("field_gnss", true),
            logCellularRf = sp.getBoolean("field_cellular_rf", true),
            logSensors = sp.getBoolean("field_sensors", true),
            logPing = sp.getBoolean("field_ping", true),
            logNeighbors = sp.getBoolean("field_neighbors", true),
            logBattery = sp.getBoolean("field_battery", true),
            logNmea = sp.getBoolean("field_nmea", false),
            logSpoofing = sp.getBoolean("field_spoofing", true)
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
            autoGeocodeBts = autoGeocode,
            fieldsSelection = fields
        )
        _settings.value = loaded
        wasDemoMode = loaded.isDemoMode

        applicationScope.launch {
            btsRepository.seedBundledSampleIfNeeded()
        }
        cellularEngine.setBtsEnricher { sim ->
            enrichSimCell(sim, gnssEngine.gnssState.value, _settings.value.isDemoMode)
        }

        environmentEngine.setQnh(loaded.qnhHpa)
        cellularEngine.setTaMultiplierMode(loaded.taMultiplierMode)
        reconcileEngines()
    }

    private suspend fun enrichSimCell(
        sim: SimCellInfo,
        gnss: GnssState,
        isDemo: Boolean
    ): SimCellInfo {
        val match = btsRepository.matchSimCell(
            sim = sim,
            ueLat = gnss.latitude,
            ueLon = gnss.longitude,
            hasGnssFix = gnss.hasFix,
            isDemoMode = isDemo
        ) ?: return sim

        if (_settings.value.autoGeocodeBts && !isDemo && match.latitude == null && match.locationName.isNotBlank()) {
            applicationScope.launch {
                btsRepository.geocodeSiteIfNotAttempted(
                    plmn = match.operatorPlmn,
                    cellId = match.cellId,
                    enodebId = match.enodebId,
                    locationName = match.locationName
                )
            }
        }

        val updatedAnomalies = if (match.pciMismatch || match.tacMismatch) {
            val isCs = _settings.value.language != "EN"
            val details = buildList {
                if (match.pciMismatch) add("PCI ${sim.pci} ≠ DB ${match.dbPci}")
                if (match.tacMismatch) add("TAC ${sim.tac} ≠ DB ${match.dbTac}")
            }.joinToString(", ")
            val extra = CellularAnomaly(
                type = AnomalyType.BTS_DB_MISMATCH,
                severity = AnomalySeverity.WARNING,
                title = if (isCs) "Nesoulad s databází BTS ($details)" else "BTS Database Mismatch ($details)",
                description = if (isCs) {
                    "Identifikátor buňky (${match.cellId}) souhlasí se známou BTS (${match.locationName}), ale PCI/TAC neodpovídá záznamu."
                } else {
                    "Cell ID (${match.cellId}) matches known site (${match.locationName}), but live PCI/TAC differs from database."
                }
            )
            if (sim.anomalies.any { it.type == AnomalyType.BTS_DB_MISMATCH }) sim.anomalies else sim.anomalies + extra
        } else {
            sim.anomalies
        }

        return sim.copy(btsMatch = match, anomalies = updatedAnomalies)
    }

    fun setUiInForeground(inForeground: Boolean) {
        isUiInForeground = inForeground
        reconcileEngines()
    }

    fun reconcileEngines() {
        val current = _settings.value
        if (wasDemoMode != current.isDemoMode) {
            wasDemoMode = current.isDemoMode
            gnssEngine.resetState()
            cellularEngine.resetState()
            environmentEngine.resetToLiveDefaults()
            securityEngine.resetSessionBaselines()
            TelemetryLoggingService.clearLiveMapTrail()
            TelemetryLoggingService.clearRecentChartPoints()
        }

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
                val demoGnss = step.first
                val enrichedSim1 = enrichSimCell(step.second.sim1, demoGnss, isDemo = true)
                val enrichedSim2 = enrichSimCell(step.second.sim2, demoGnss, isDemo = true)
                gnssEngine.updateDemoState(demoGnss)
                cellularEngine.updateDemoData(enrichedSim1, enrichedSim2, step.second.pingRttMs)
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
                if (!_settings.value.isDemoMode) {
                    gnssEngine.checkFixTimeout()
                }
                val c = cellularEngine.cellularState.value
                val g = gnssEngine.gnssState.value
                val e = environmentEngine.envState.value
                val now = System.currentTimeMillis()

                securityEngine.evaluateNow(
                    gnss = g,
                    cellular = c,
                    env = e,
                    isDemoMode = _settings.value.isDemoMode
                )

                if (g.hasFix && (g.latitude != 0.0 || g.longitude != 0.0)) {
                    TelemetryLoggingService.addMapTrailPoint(
                        MapTrailPoint(
                            latitude = g.latitude,
                            longitude = g.longitude,
                            sim1Rsrp = c.sim1.rsrp,
                            sim2Rsrp = c.sim2.rsrp,
                            speedKmh = g.speedKmh,
                            satellitesUsed = g.satellitesUsedInFix,
                            satellitesInView = g.satellitesInView,
                            pdop = g.pdop,
                            timestampMs = now
                        )
                    )
                }

                if (!TelemetryLoggingService.isLogging.value) {
                    TelemetryLoggingService.addChartPoint(
                        ChartPoint(
                            timestampMs = now,
                            rsrp = c.sim1.rsrp?.toFloat(),
                            rsrpSim2 = c.sim2.rsrp?.toFloat(),
                            sinrSim1 = c.sim1.sinr,
                            sinrSim2 = c.sim2.sinr,
                            speedKmh = if (g.hasFix) g.speedKmh else 0f,
                            satCount = g.satellitesUsedInFix,
                            satInViewCount = g.satellitesInView,
                            pdop = if (g.hasFix && g.satellitesUsedInFix >= 4 && g.pdop > 0f && g.pdop < 9.0f) g.pdop else null,
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

    fun runDeepSecurityScan() {
        if (securityEngine.securityState.value.isDeepScanning) return
        applicationScope.launch {
            securityEngine.setDeepScanActive(true)
            try {
                if (!_settings.value.isDemoMode) {
                    cellularEngine.requestImmediateRefresh()
                    securityEngine.syncNtpNow(force = true)
                    delay(950)
                } else {
                    delay(450)
                }
                securityEngine.evaluateNow(
                    gnss = gnssEngine.gnssState.value,
                    cellular = cellularEngine.cellularState.value,
                    env = environmentEngine.envState.value,
                    isDemoMode = _settings.value.isDemoMode
                )
            } finally {
                securityEngine.setDeepScanActive(false, completedEpochMs = System.currentTimeMillis())
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
            .putBoolean("auto_geocode_bts", newSettings.autoGeocodeBts)
            .putBoolean("field_gnss", newSettings.fieldsSelection.logGnss)
            .putBoolean("field_cellular_rf", newSettings.fieldsSelection.logCellularRf)
            .putBoolean("field_neighbors", newSettings.fieldsSelection.logNeighbors)
            .putBoolean("field_sensors", newSettings.fieldsSelection.logSensors)
            .putBoolean("field_battery", newSettings.fieldsSelection.logBattery)
            .putBoolean("field_ping", newSettings.fieldsSelection.logPing)
            .putBoolean("field_nmea", newSettings.fieldsSelection.logNmea)
            .putBoolean("field_spoofing", newSettings.fieldsSelection.logSpoofing)
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
