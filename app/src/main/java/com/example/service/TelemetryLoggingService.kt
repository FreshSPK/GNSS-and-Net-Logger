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
        const val ACTION_STOP = "ACTION_STOP_LOGGING"
        const val NOTIFICATION_CHANNEL_ID = "channel_field_logger_service"
        const val NOTIFICATION_ID = 1001

        private val _isLogging = MutableStateFlow(false)
        val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

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
            if (list.size > 60) {
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
            ACTION_STOP -> stopLogging()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLogging() {
        if (_isLogging.value) return

        val app = application as SignalLoggerApp
        val settings = app.settings.value

        // Acquire partial wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SignalLogger::LoggingWakeLock").apply {
            acquire(10 * 60 * 60 * 1000L) // up to 10 hours
        }

        // Create foreground notification
        val notification = buildNotification("Field logging active", "Recording GNSS & RF Telemetry...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _isLogging.value = true
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

            // Start logging loop
            startLoop(settings)
        }
    }

    private fun startLoop(settings: LoggingSettings) {
        logLoopJob?.cancel()
        logLoopJob = serviceScope.launch {
            val app = application as SignalLoggerApp
            while (isActive && _isLogging.value) {
                val gnss = app.gnssEngine.gnssState.value
                val cellular = app.cellularEngine.cellularState.value
                val env = app.environmentEngine.envState.value

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

                    // Save record
                    val sim1 = cellular.sim1
                    val sim2 = cellular.sim2
                    val bestNeighbor = sim1.neighbors.firstOrNull()

                    val record = TelemetryRecord(
                        sessionId = currentSessionId!!,
                        timestampMs = now,
                        latitude = gnss.latitude,
                        longitude = gnss.longitude,
                        altitudeMsl = gnss.altitudeMsl,
                        altitudeWgs84 = gnss.altitudeWgs84,
                        speedKmh = gnss.speedKmh,
                        speedMs = gnss.speedMs,
                        bearingDeg = gnss.bearingDeg,
                        horizontalAccuracyM = gnss.horizontalAccuracyM,
                        verticalAccuracyM = gnss.verticalAccuracyM,
                        satellitesInView = gnss.satellitesInView,
                        satellitesUsedInFix = gnss.satellitesUsedInFix,
                        pdop = gnss.pdop,
                        hdop = gnss.hdop,
                        vdop = gnss.vdop,
                        dualFrequencyRatioPct = gnss.dualFrequencyRatioPct,
                        agcInterferenceScore = gnss.agcInterferenceScore,
                        constellationBreakdown = gnss.constellationCountMap.entries.joinToString(",") { "${it.key}:${it.value.first}/${it.value.second}" },
                        sim1Operator = sim1.operatorAlpha,
                        sim1NetworkGen = sim1.networkGen,
                        sim1TacLac = sim1.tac,
                        sim1CellId = sim1.ci,
                        sim1EnbOrGnb = sim1.enbOrGnbId,
                        sim1SectorId = sim1.sectorId,
                        sim1Pci = sim1.pci,
                        sim1Arfcn = sim1.earfcnOrArfcn,
                        sim1Band = sim1.band,
                        sim1Rsrp = sim1.rsrp,
                        sim1Rsrq = sim1.rsrq,
                        sim1Rssi = sim1.rssi,
                        sim1Sinr = sim1.sinr,
                        sim1SsRsrp = sim1.ssRsrp,
                        sim1SsRsrq = sim1.ssRsrq,
                        sim1SsSinr = sim1.ssSinr,
                        sim1CsiRsrp = sim1.csiRsrp,
                        sim1CsiRsrq = sim1.csiRsrq,
                        sim1CsiSinr = sim1.csiSinr,
                        sim1TimingAdvance = sim1.timingAdvance,
                        sim1EstimatedDistanceM = sim1.estimatedDistanceM,
                        sim2Operator = sim2.operatorAlpha,
                        sim2NetworkGen = sim2.networkGen,
                        sim2Band = sim2.band,
                        sim2Rsrp = sim2.rsrp,
                        sim2Sinr = sim2.sinr,
                        bestNeighborRsrp = bestNeighbor?.rsrp,
                        neighborServingDeltaDb = bestNeighbor?.deltaFromServingDb,
                        pressureHpa = env.pressureHpa,
                        baroRelativeAltM = env.relativeAltitudeM,
                        pingRttMs = cellular.pingRttMs,
                        pingPacketLoss = cellular.pingPacketLoss
                    )

                    app.database.telemetryDao().insertRecord(record)

                    // Also add to chart buffer
                    addChartPoint(
                        ChartPoint(
                            timestampMs = now,
                            rsrp = sim1.rsrp?.toFloat(),
                            speedKmh = gnss.speedKmh,
                            satCount = gnss.satellitesUsedInFix,
                            pingMs = cellular.pingRttMs?.toFloat()
                        )
                    )

                    // Update notification periodically
                    if (loggedCount % 5 == 0) {
                        updateNotification("Logging: $loggedCount points | Sats: ${gnss.satellitesUsedInFix} | RSRP: ${sim1.rsrp ?: "N/A"} dBm")
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

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = buildNotification("Signal & GNSS Field Logger", content)
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
