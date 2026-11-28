package com.example.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.BatteryState
import com.example.model.EnvironmentState
import com.example.model.MotionSensorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class ImuStationaryTracker(
    private val requiredCalmMs: Long = 4000L,
    private val magWindowMs: Long = 4500L
) {
    private data class MagSample(val elapsedMs: Long, val fieldUt: Float, val azimuthDeg: Float)

    private val magHistory = ArrayDeque<MagSample>()
    private var calmSinceElapsedMs: Long? = null

    fun update(
        accelMag: Float,
        gx: Float,
        gy: Float,
        gz: Float,
        magFieldUt: Float?,
        azimuthDeg: Float,
        stepsLast6s: Int,
        nowElapsedMs: Long,
        hasAccelSample: Boolean = true,
        hasGyroSample: Boolean = true,
        hasMagSample: Boolean = magFieldUt != null
    ): Pair<Boolean, Int> {
        if (!hasAccelSample) {
            calmSinceElapsedMs = null
            return false to 0
        }

        if (hasMagSample && magFieldUt != null) {
            magHistory.addLast(MagSample(nowElapsedMs, magFieldUt, azimuthDeg))
            while (magHistory.isNotEmpty() && nowElapsedMs - magHistory.first().elapsedMs > magWindowMs) {
                magHistory.removeFirst()
            }
        }

        var magSpan = 0f
        var azSpan = 0f
        if (magHistory.size >= 2) {
            var minB = Float.MAX_VALUE
            var maxB = -Float.MAX_VALUE
            val baseAz = magHistory.first().azimuthDeg
            var minSigned = 0f
            var maxSigned = 0f
            for (sample in magHistory) {
                if (sample.fieldUt < minB) minB = sample.fieldUt
                if (sample.fieldUt > maxB) maxB = sample.fieldUt
                val diff = ((sample.azimuthDeg - baseAz + 540f) % 360f) - 180f
                if (diff < minSigned) minSigned = diff
                if (diff > maxSigned) maxSigned = diff
            }
            magSpan = maxB - minB
            azSpan = maxSigned - minSigned
        }

        val accelDev = abs(accelMag - 9.81f)
        val gyroMax = if (hasGyroSample) maxOf(abs(gx), abs(gy), abs(gz)) else 0f
        val magCalm = !hasMagSample || magHistory.isEmpty() || (magSpan < 4.0f && azSpan < 15.0f)

        val isCalmSample = stepsLast6s == 0 &&
            accelDev < 0.75f &&
            gyroMax < 0.25f &&
            magCalm

        if (!isCalmSample) {
            calmSinceElapsedMs = null
            return false to 0
        }

        if (calmSinceElapsedMs == null) {
            calmSinceElapsedMs = nowElapsedMs
        }
        val calmMs = (nowElapsedMs - calmSinceElapsedMs!!).coerceAtLeast(0L)
        val stationary = calmMs >= requiredCalmMs
        val durationSec = if (stationary) (calmMs / 1000L).toInt().coerceAtMost(3600) else 0
        return stationary to durationSec
    }

    fun reset() {
        magHistory.clear()
        calmSinceElapsedMs = null
    }
}

class EnvironmentSensorEngine(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val stepDetectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _envState = MutableStateFlow(EnvironmentState())
    val envState: StateFlow<EnvironmentState> = _envState.asStateFlow()

    private val gravityValues = FloatArray(3)
    private val geomagneticValues = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val inclinationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false
    @Volatile
    private var isStarted = false
    private var lastEmitMs = 0L
    private var sensorThread: android.os.HandlerThread? = null
    private var sensorHandler: android.os.Handler? = null
    private val stationaryTracker = ImuStationaryTracker()
    private var hasAccelSample = false
    private var hasGyroSample = false
    private var hasMagSample = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val i = intent ?: return
            val tempRaw = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val tempC = if (tempRaw > 0) tempRaw / 10.0f else null
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val levelPct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
            val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            val cur = _envState.value
            _envState.value = cur.copy(
                battery = BatteryState(
                    temperatureC = tempC,
                    levelPct = levelPct,
                    voltageMv = if (voltage > 0) voltage else null,
                    isCharging = isCharging
                )
            )
        }
    }

    private var isBatteryRegistered = false

    fun start() {
        if (isStarted) return
        isStarted = true
        val thread = android.os.HandlerThread("SensorWorker").apply { start() }
        val handler = android.os.Handler(thread.looper)
        sensorThread = thread
        sensorHandler = handler

        pressureSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler) }
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler) }
        magSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler) }

        val canUseHwSteps = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (canUseHwSteps) {
            try {
                stepDetectorSensor?.let {
                    sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, handler)
                }
            } catch (_: SecurityException) {}
        }

        try {
            if (!isBatteryRegistered) {
                context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                isBatteryRegistered = true
            }
        } catch (_: Exception) {}
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        sensorManager?.unregisterListener(this)
        try {
            sensorThread?.quitSafely()
        } catch (_: Exception) {}
        sensorThread = null
        sensorHandler = null
        stationaryTracker.reset()
        hasAccelSample = false
        hasGyroSample = false
        hasMagSample = false
        try {
            if (isBatteryRegistered) {
                context.unregisterReceiver(batteryReceiver)
                isBatteryRegistered = false
            }
        } catch (_: Exception) {}
    }

    fun setQnh(qnhHpa: Float) {
        val current = _envState.value
        val alt = current.pressureHpa?.let { calculateBarometricAltitude(it, qnhHpa) }
        _envState.value = current.copy(baseQnhHpa = qnhHpa, relativeAltitudeM = alt)
    }

    fun resetToLiveDefaults() {
        val qnh = _envState.value.baseQnhHpa
        stationaryTracker.reset()
        hasAccelSample = false
        hasGyroSample = false
        hasMagSample = false
        hasGravity = false
        hasGeomagnetic = false
        stepPeakTimestampsMs.clear()
        lastStepRecordedMs = 0L
        _envState.value = EnvironmentState(baseQnhHpa = qnh)
    }

    fun updateDemoPressure(pressureHpa: Float, qnhHpa: Float) {
        val alt = calculateBarometricAltitude(pressureHpa, qnhHpa)
        val curBattery = _envState.value.battery
        _envState.value = _envState.value.copy(
            pressureHpa = pressureHpa,
            baseQnhHpa = qnhHpa,
            relativeAltitudeM = alt,
            seaLevelPressureHpa = pressureHpa + 32.5f,
            battery = curBattery.copy(
                temperatureC = 30.2f,
                levelPct = 85,
                voltageMv = 4050,
                isCharging = false
            )
        )
    }

    fun updateDemoMotion(
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float,
        gy: Float,
        gz: Float,
        azimuth: Float,
        verticalYawRadPerSec: Float = gz,
        magneticFieldUt: Float? = 48.5f,
        stepsLast6s: Int = 0,
        isStationary: Boolean = false,
        stationaryDurationSec: Int = 0
    ) {
        val mag = sqrt(ax * ax + ay * ay + az * az)
        _envState.value = _envState.value.copy(
            motion = MotionSensorState(
                accelX = ax,
                accelY = ay,
                accelZ = az,
                accelMagnitude = mag,
                gyroX = gx,
                gyroY = gy,
                gyroZ = gz,
                compassAzimuthDeg = azimuth,
                verticalYawRadPerSec = verticalYawRadPerSec,
                magneticFieldUt = magneticFieldUt,
                stepsLast6s = stepsLast6s,
                isWalkingDetected = stepsLast6s >= 4,
                isStationary = isStationary,
                stationaryDurationSec = stationaryDurationSec
            )
        )
    }

    private var latestAx = 0f
    private var latestAy = 0f
    private var latestAz = 9.81f
    private var latestMag = 9.81f
    private var latestGx = 0f
    private var latestGy = 0f
    private var latestGz = 0f
    private var latestAzimuth = 0f
    private var latestVerticalYaw = 0f
    private var latestMagFieldUt: Float? = null
    private val stepPeakTimestampsMs = ArrayDeque<Long>()
    private var stepArmed = false
    private var lastStepRecordedMs = 0L

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        when (e.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                if (e.values.isEmpty()) return
                val pressure = e.values[0]
                val qnh = _envState.value.baseQnhHpa
                val relativeAlt = calculateBarometricAltitude(pressure, qnh)
                _envState.value = _envState.value.copy(
                    pressureHpa = pressure,
                    relativeAltitudeM = relativeAlt
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (e.values.size < 3) return
                System.arraycopy(e.values, 0, gravityValues, 0, 3)
                hasGravity = true
                hasAccelSample = true
                latestAx = e.values[0]
                latestAy = e.values[1]
                latestAz = e.values[2]
                latestMag = sqrt(latestAx * latestAx + latestAy * latestAy + latestAz * latestAz)
                updateAccelStepCadence(latestMag)
                computeOrientationInternal()
                maybeEmitMotionState()
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (e.values.size < 3) return
                hasGyroSample = true
                latestGx = e.values[0]
                latestGy = e.values[1]
                latestGz = e.values[2]
                latestVerticalYaw = if (hasGravity) {
                    val gNorm = sqrt(
                        gravityValues[0] * gravityValues[0] +
                            gravityValues[1] * gravityValues[1] +
                            gravityValues[2] * gravityValues[2]
                    )
                    if (gNorm > 2f) {
                        (latestGx * gravityValues[0] + latestGy * gravityValues[1] + latestGz * gravityValues[2]) / gNorm
                    } else latestGz
                } else latestGz
                maybeEmitMotionState()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (e.values.size < 3) return
                System.arraycopy(e.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
                hasMagSample = true
                latestMagFieldUt = sqrt(
                    geomagneticValues[0] * geomagneticValues[0] +
                        geomagneticValues[1] * geomagneticValues[1] +
                        geomagneticValues[2] * geomagneticValues[2]
                )
                computeOrientationInternal()
                maybeEmitMotionState()
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                recordStepImpulse(android.os.SystemClock.elapsedRealtime())
                maybeEmitMotionState()
            }
        }
    }

    private fun updateAccelStepCadence(accelMag: Float) {
        val dev = abs(accelMag - 9.81f)
        if (!stepArmed && dev < 0.7f) {
            stepArmed = true
        } else if (stepArmed && dev in 1.45f..8.5f) {
            val now = android.os.SystemClock.elapsedRealtime()
            val dt = now - lastStepRecordedMs
            if (dt in 280L..1200L || lastStepRecordedMs == 0L) {
                recordStepImpulse(now)
            } else if (dt > 1200L) {
                // Re-arm on first step of new gait sequence
                lastStepRecordedMs = now
            }
            stepArmed = false
        }
    }

    private fun recordStepImpulse(nowElapsedMs: Long) {
        if (nowElapsedMs - lastStepRecordedMs < 260L && lastStepRecordedMs != 0L) return
        lastStepRecordedMs = nowElapsedMs
        stepPeakTimestampsMs.addLast(nowElapsedMs)
        pruneOldSteps(nowElapsedMs)
    }

    private fun pruneOldSteps(nowElapsedMs: Long) {
        while (stepPeakTimestampsMs.isNotEmpty() && nowElapsedMs - stepPeakTimestampsMs.first() > 6000L) {
            stepPeakTimestampsMs.removeFirst()
        }
    }

    private fun computeOrientationInternal() {
        if (hasGravity && hasGeomagnetic) {
            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravityValues, geomagneticValues)) {
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0f) azimuth += 360f
                latestAzimuth = azimuth
            }
        }
    }

    private fun maybeEmitMotionState() {
        val now = android.os.SystemClock.elapsedRealtime()
        pruneOldSteps(now)
        val stepsCount = stepPeakTimestampsMs.size
        val (isStationary, stationarySec) = stationaryTracker.update(
            accelMag = latestMag,
            gx = latestGx,
            gy = latestGy,
            gz = latestGz,
            magFieldUt = latestMagFieldUt,
            azimuthDeg = latestAzimuth,
            stepsLast6s = stepsCount,
            nowElapsedMs = now,
            hasAccelSample = hasAccelSample,
            hasGyroSample = hasGyroSample,
            hasMagSample = hasMagSample
        )

        val wasStationary = _envState.value.motion.isStationary
        if (now - lastEmitMs < 100L && !(wasStationary && !isStationary)) {
            return // Max 10 Hz emissions, except immediate wake-up when motion starts
        }
        lastEmitMs = now

        val cur = _envState.value
        _envState.value = cur.copy(
            motion = cur.motion.copy(
                accelX = latestAx,
                accelY = latestAy,
                accelZ = latestAz,
                accelMagnitude = latestMag,
                gyroX = latestGx,
                gyroY = latestGy,
                gyroZ = latestGz,
                compassAzimuthDeg = latestAzimuth,
                verticalYawRadPerSec = latestVerticalYaw,
                magneticFieldUt = latestMagFieldUt,
                stepsLast6s = stepsCount,
                isWalkingDetected = stepsCount >= 4,
                isStationary = isStationary,
                stationaryDurationSec = stationarySec
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateBarometricAltitude(pressureHpa: Float, qnhHpa: Float): Float {
        if (qnhHpa <= 0f || pressureHpa <= 0f) return 0f
        return (44330f * (1f - (pressureHpa / qnhHpa).pow(1f / 5.255f)))
    }
}
