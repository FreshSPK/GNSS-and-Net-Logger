package com.example.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.model.EnvironmentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

class EnvironmentSensorEngine(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _envState = MutableStateFlow(EnvironmentState())
    val envState: StateFlow<EnvironmentState> = _envState.asStateFlow()

    fun start() {
        pressureSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
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
        _envState.value = EnvironmentState(
            pressureHpa = pressureHpa,
            baseQnhHpa = qnhHpa,
            relativeAltitudeM = alt
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PRESSURE) {
            val pressure = event.values[0]
            val qnh = _envState.value.baseQnhHpa
            val relativeAlt = calculateBarometricAltitude(pressure, qnh)
            _envState.value = _envState.value.copy(
                pressureHpa = pressure,
                relativeAltitudeM = relativeAlt
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Hypsometric formula for international standard atmosphere:
     * h = 44330 * (1 - (p / p0)^(1 / 5.255))
     */
    private fun calculateBarometricAltitude(pressureHpa: Float, qnhHpa: Float): Float {
        if (qnhHpa <= 0f || pressureHpa <= 0f) return 0f
        return (44330f * (1f - (pressureHpa / qnhHpa).pow(1f / 5.255f)))
    }
}
