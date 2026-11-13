package com.example.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import com.example.model.BatteryState
import com.example.model.EnvironmentState
import com.example.model.MotionSensorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class EnvironmentSensorEngine(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

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

    fun updateDemoMotion(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float, azimuth: Float) {
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
                compassAzimuthDeg = azimuth
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
                latestAx = e.values[0]
                latestAy = e.values[1]
                latestAz = e.values[2]
                latestMag = sqrt(latestAx * latestAx + latestAy * latestAy + latestAz * latestAz)
                computeOrientationInternal()
                maybeEmitMotionState()
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (e.values.size < 3) return
                latestGx = e.values[0]
                latestGy = e.values[1]
                latestGz = e.values[2]
                maybeEmitMotionState()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (e.values.size < 3) return
                System.arraycopy(e.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
                computeOrientationInternal()
                maybeEmitMotionState()
            }
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
        if (now - lastEmitMs < 100L) return // Max 10 Hz state emissions to reduce Compose recomposition overhead
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
                compassAzimuthDeg = latestAzimuth
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateBarometricAltitude(pressureHpa: Float, qnhHpa: Float): Float {
        if (qnhHpa <= 0f || pressureHpa <= 0f) return 0f
        return (44330f * (1f - (pressureHpa / qnhHpa).pow(1f / 5.255f)))
    }
}
