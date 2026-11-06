package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.SignalLoggerApp
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.model.ChartPoint
import com.example.model.LogTriggerMode
import com.example.model.LoggingSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

class TelemetryLoggingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var logLoopJob: Job? = null

    private var currentSessionId: Long? = null
    private var currentSession: LogSession? = null
    private var lastLoggedLocation: Location? = null
    private var lastLogTimestampMs: Long = 0
    private var loggedCount = 0
    private var totalDistanceM = 0.0
    private var maxSpeedKmh = 0.0

    companion object {
        const val ACTION_START = "ACTION_START_LOGGING"
        const val ACTION_PAUSE = "ACTION_PAUSE_LOGGING"
        const val ACTION_RESUME = "ACTION_RESUME_LOGGING"
        const val ACTION_STOP = "ACTION_STOP_LOGGING"
        const val NOTIFICATION_CHANNEL_ID = "channel_field_logger_service"
        const val NOTIFICATION_ID = 1001

        private val _isLogging = MutableStateFlow(false)
        val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _activeSessionId = MutableStateFlow<Long?>(null)
        val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

        private val _loggedPointsCount = MutableStateFlow(0)
        val loggedPointsCount: StateFlow<Int> = _loggedPointsCount.asStateFlow()

        // Real-time chart ring buffer
        private val _recentChartPoints = MutableStateFlow<List<ChartPoint>>(emptyList())
        val recentChartPoints: StateFlow<List<ChartPoint>> = _recentChartPoints.asStateFlow()

        fun addChartPoint(point: ChartPoint) {
            val list = _recentChartPoints.value.toMutableList()
            list.add(point)
            if (list.size > 80) {
                list.removeAt(0)
            }
            _recentChartPoints.value = list
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLogging()
            ACTION_PAUSE -> pauseLogging()
            ACTION_RESUME -> resumeLogging()
            ACTION_STOP -> stopLogging()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLogging() {
        if (_isLogging.value) return

        val app = application as SignalLoggerApp
        val settings = app.settings.value

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SignalLogger::LoggingWakeLock").apply {
            acquire(10 * 60 * 60 * 1000L) // up to 10 hours
        }

        val notification = buildNotification("Field logging active", "Recording GNSS & RF Telemetry...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isLogging.value = true
        _isPaused.value = false
        loggedCount = 0
        totalDistanceM = 0.0
        maxSpeedKmh = 0.0
        _loggedPointsCount.value = 0

        serviceScope.launch {
            val dao = app.database.telemetryDao()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val sessionTitle = "Session " + dateFormat.format(Date())

            val newSession = LogSession(
                title = sessionTitle,
                startTimeMs = System.currentTimeMillis()
            )
            val id = dao.insertSession(newSession)
            currentSessionId = id
            currentSession = newSession.copy(id = id)
            _activeSessionId.value = id

            startLoop(settings)
        }
    }

    private fun pauseLogging() {
        if (_isLogging.value && !_isPaused.value) {
            _isPaused.value = true
            updateNotification("Logging PAUSED - samples on hold")
        }
    }

    private fun resumeLogging() {
        if (_isLogging.value && _isPaused.value) {
            _isPaused.value = false
            updateNotification("Logging RESUMED - collecting samples")
        }
    }

    private fun startLoop(settings: LoggingSettings) {
        logLoopJob?.cancel()
        logLoopJob = serviceScope.launch {
            val app = application as SignalLoggerApp
            while (isActive && _isLogging.value) {
                if (!_isPaused.value) {
                    val gnss = app.gnssEngine.gnssState.value
                    val cellular = app.cellularEngine.cellularState.value
                    val env = app.environmentEngine.envState.value
                    val motion = env.motion

                    var shouldLog = false
                    val now = System.currentTimeMillis()

                    val loc = Location("current").apply {
                        latitude = gnss.latitude
                        longitude = gnss.longitude
                        altitude = gnss.altitudeMsl
                    }

                    when (settings.triggerMode) {
                        LogTriggerMode.TIME -> {
                            val intervalMs = settings.timeIntervalSeconds * 1000L
                            if (now - lastLogTimestampMs >= intervalMs) {
                                shouldLog = true
                            }
                        }
                        LogTriggerMode.DISTANCE -> {
                            if (lastLoggedLocation == null) {
                                shouldLog = true
                            } else {
                                val dist = lastLoggedLocation!!.distanceTo(loc)
                                if (dist >= settings.distanceThresholdMeters) {
                                shouldLog = true
                                }
                            }
                        }
                        LogTriggerMode.HYBRID -> {
                            val intervalMs = settings.timeIntervalSeconds * 1000L
                            val dist = lastLoggedLocation?.distanceTo(loc) ?: Float.MAX_VALUE
                            if (now - lastLogTimestampMs >= intervalMs || dist >= settings.distanceThresholdMeters) {
                                shouldLog = true
                            }
                        }
                    }

                    if (shouldLog && currentSessionId != null) {
                        if (lastLoggedLocation != null) {
                            val stepDist = lastLoggedLocation!!.distanceTo(loc).toDouble()
                            if (stepDist in 0.1..2000.0) {
                                totalDistanceM += stepDist
                            }
                        }
                        lastLoggedLocation = loc
                        lastLogTimestampMs = now
                        loggedCount++
                        _loggedPointsCount.value = loggedCount

                        if (gnss.speedKmh > maxSpeedKmh) {
                            maxSpeedKmh = gnss.speedKmh.toDouble()
                        }

                        val sim1 = cellular.sim1
                        val sim2 = cellular.sim2
                        val bestNeighbor = sim1.neighbors.firstOrNull()

                        val fields = settings.fieldsSelection

                        val record = TelemetryRecord(
                            sessionId = currentSessionId!!,
                            timestampMs = now,
                            latitude = if (fields.logGnss) gnss.latitude else 0.0,
                            longitude = if (fields.logGnss) gnss.longitude else 0.0,
                            altitudeMsl = if (fields.logGnss) gnss.altitudeMsl else 0.0,
                            altitudeWgs84 = if (fields.logGnss) gnss.altitudeWgs84 else 0.0,
                            speedKmh = if (fields.logGnss) gnss.speedKmh else 0f,
                            speedMs = if (fields.logGnss) gnss.speedMs else 0f,
                            bearingDeg = if (fields.logGnss) gnss.bearingDeg else motion.compassAzimuthDeg,
                            horizontalAccuracyM = if (fields.logGnss) gnss.horizontalAccuracyM else 0f,
                            verticalAccuracyM = if (fields.logGnss) gnss.verticalAccuracyM else 0f,
                            satellitesInView = if (fields.logGnss) gnss.satellitesInView else 0,
                            satellitesUsedInFix = if (fields.logGnss) gnss.satellitesUsedInFix else 0,
                            pdop = if (fields.logGnss) gnss.pdop else 0f,
                            hdop = if (fields.logGnss) gnss.hdop else 0f,
                            vdop = if (fields.logGnss) gnss.vdop else 0f,
                            dualFrequencyRatioPct = if (fields.logGnss) gnss.dualFrequencyRatioPct else 0f,
                            agcInterferenceScore = if (fields.logGnss) gnss.agcInterferenceScore else 0f,
                            constellationBreakdown = if (fields.logGnss) gnss.constellationCountMap.entries.joinToString(",") { "${it.key}:${it.value.first}/${it.value.second}" } else "",
                            sim1Operator = if (fields.logCellularRf) sim1.operatorAlpha else "",
                            sim1NetworkGen = if (fields.logCellularRf) sim1.networkGen else "",
                            sim1TacLac = if (fields.logCellularRf) sim1.tac else "",
                            sim1CellId = if (fields.logCellularRf) sim1.ci else "",
                            sim1EnbOrGnb = if (fields.logCellularRf) sim1.enbOrGnbId else "",
                            sim1SectorId = if (fields.logCellularRf) sim1.sectorId else "",
                            sim1Pci = if (fields.logCellularRf) sim1.pci else 0,
                            sim1Arfcn = if (fields.logCellularRf) sim1.earfcnOrArfcn else 0,
                            sim1Band = if (fields.logCellularRf) sim1.band else "",
                            sim1Rsrp = if (fields.logCellularRf) sim1.rsrp else null,
                            sim1Rsrq = if (fields.logCellularRf) sim1.rsrq else null,
                            sim1Rssi = if (fields.logCellularRf) sim1.rssi else null,
                            sim1Sinr = if (fields.logCellularRf) sim1.sinr else null,
                            sim1SsRsrp = if (fields.logCellularRf) sim1.ssRsrp else null,
                            sim1SsRsrq = if (fields.logCellularRf) sim1.ssRsrq else null,
                            sim1SsSinr = if (fields.logCellularRf) sim1.ssSinr else null,
                            sim1CsiRsrp = if (fields.logCellularRf) sim1.csiRsrp else null,
                            sim1CsiRsrq = if (fields.logCellularRf) sim1.csiRsrq else null,
                            sim1CsiSinr = if (fields.logCellularRf) sim1.csiSinr else null,
                            sim1TimingAdvance = if (fields.logCellularRf) sim1.timingAdvance else null,
                            sim1EstimatedDistanceM = if (fields.logCellularRf) sim1.estimatedDistanceM else null,
                            sim2Operator = if (fields.logCellularRf) sim2.operatorAlpha else "",
                            sim2NetworkGen = if (fields.logCellularRf) sim2.networkGen else "",
                            sim2Band = if (fields.logCellularRf) sim2.band else "",
                            sim2Rsrp = if (fields.logCellularRf) sim2.rsrp else null,
                            sim2Sinr = if (fields.logCellularRf) sim2.sinr else null,
                            bestNeighborRsrp = if (fields.logNeighbors) bestNeighbor?.rsrp else null,
                            neighborServingDeltaDb = if (fields.logNeighbors) bestNeighbor?.deltaFromServingDb else null,
                            pressureHpa = if (fields.logSensors) env.pressureHpa else null,
                            baroRelativeAltM = if (fields.logSensors) env.relativeAltitudeM else null,
                            seaLevelPressureHpa = if (fields.logSensors) env.seaLevelPressureHpa else null,
                            pingRttMs = if (fields.logPing) cellular.pingRttMs else null,
                            pingPacketLoss = if (fields.logPing) cellular.pingPacketLoss else false,
                            sim1Plmn = if (fields.logCellularRf) sim1.plmn else "N/A",
                            sim2Plmn = if (fields.logCellularRf) sim2.plmn else "N/A",
                            batteryTempC = if (fields.logBattery) env.battery.temperatureC else null,
                            batteryLevelPct = if (fields.logBattery) env.battery.levelPct else null,
                            nmeaSentence = if (fields.logNmea) gnss.latestNmeaSentence else null,
                            rawDopplerHz = if (fields.logGnss) gnss.rawDopplerHz else null
                        )

                        app.database.telemetryDao().insertRecord(record)

                        addChartPoint(
                            ChartPoint(
                                timestampMs = now,
                                rsrp = sim1.rsrp?.toFloat(),
                                speedKmh = gnss.speedKmh,
                                satCount = gnss.satellitesUsedInFix,
                                pingMs = cellular.pingRttMs?.toFloat(),
                                accelMagnitude = motion.accelMagnitude,
                                gyroZ = motion.gyroZ,
                                compassAzimuth = motion.compassAzimuthDeg,
                                batteryTempC = env.battery.temperatureC
                            )
                        )

                        if (loggedCount % 5 == 0) {
                            updateNotification("Logging: $loggedCount points | Sats: ${gnss.satellitesUsedInFix} | RSRP: ${sim1.rsrp ?: "N/A"} dBm")
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopLogging() {
        if (!_isLogging.value) return

        logLoopJob?.cancel()
        _isLogging.value = false
        _isPaused.value = false

        serviceScope.launch {
            val app = application as SignalLoggerApp
            currentSessionId?.let { sId ->
                val dao = app.database.telemetryDao()
                val session = dao.getSessionById(sId)
                if (session != null) {
                    val updated = session.copy(
                        endTimeMs = System.currentTimeMillis(),
                        pointCount = loggedCount,
                        distanceMeters = totalDistanceM,
                        maxSpeedKmh = maxSpeedKmh,
                        operatorSummary = app.cellularEngine.cellularState.value.sim1.operatorAlpha,
                        primaryTech = app.cellularEngine.cellularState.value.sim1.networkGen,
                        primaryBand = app.cellularEngine.cellularState.value.sim1.band
                    )
                    dao.updateSession(updated)
                }
            }

            currentSessionId = null
            _activeSessionId.value = null

            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            } catch (e: Exception) {}

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Field Logger Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground telemetry recording notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, TelemetryLoggingService::class.java).apply {
            action = if (_isPaused.value) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TelemetryLoggingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                if (_isPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (_isPaused.value) "Pokračovat" else "Pozastavit",
                pausePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Zastavit", stopPendingIntent)

        return builder.build()
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification("GNSS & Net Logger", content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}
    }
}
