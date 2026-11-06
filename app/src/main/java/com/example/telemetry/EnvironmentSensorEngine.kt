package com.example.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private var hasGravity = false
    private var hasGeomagnetic = false

    fun start() {
        pressureSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    fun setQnh(qnhHpa: Float) {
        val current = _envState.value
        val alt = current.pressureHpa?.let { calculateBarometricAltitude(it, qnhHpa) }
        _envState.value = current.copy(baseQnhHpa = qnhHpa, relativeAltitudeM = alt)
    }

    fun updateDemoPressure(pressureHpa: Float, qnhHpa: Float) {
        val alt = calculateBarometricAltitude(pressureHpa, qnhHpa)
        val curMotion = _envState.value.motion
        _envState.value = _envState.value.copy(
            pressureHpa = pressureHpa,
            baseQnhHpa = qnhHpa,
            relativeAltitudeM = alt
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

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        when (e.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val pressure = e.values[0]
                val qnh = _envState.value.baseQnhHpa
                val relativeAlt = calculateBarometricAltitude(pressure, qnh)
                _envState.value = _envState.value.copy(
                    pressureHpa = pressure,
                    relativeAltitudeM = relativeAlt
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(e.values, 0, gravityValues, 0, 3)
                hasGravity = true
                val ax = e.values[0]
                val ay = e.values[1]
                val az = e.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)
                val curMotion = _envState.value.motion
                _envState.value = _envState.value.copy(
                    motion = curMotion.copy(
                        accelX = ax,
                        accelY = ay,
                        accelZ = az,
                        accelMagnitude = mag
                    )
                )
                computeOrientation()
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = e.values[0]
                val gy = e.values[1]
                val gz = e.values[2]
                val curMotion = _envState.value.motion
                _envState.value = _envState.value.copy(
                    motion = curMotion.copy(
                        gyroX = gx,
                        gyroY = gy,
                        gyroZ = gz
                    )
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(e.values, 0, geomagneticValues, 0, 3)
                hasGeomagnetic = true
                computeOrientation()
            }
        }
    }

    private fun computeOrientation() {
        if (hasGravity && hasGeomagnetic) {
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravityValues, geomagneticValues)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                // Azimuth in degrees 0..360
                var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f
                val curMotion = _envState.value.motion
                _envState.value = _envState.value.copy(
                    motion = curMotion.copy(compassAzimuthDeg = azimuth)
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun calculateBarometricAltitude(pressureHpa: Float, qnhHpa: Float): Float {
        if (qnhHpa <= 0f || pressureHpa <= 0f) return 0f
        return (44330f * (1f - (pressureHpa / qnhHpa).pow(1f / 5.255f)))
    }
}
