package com.example.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.model.GnssState
import com.example.model.SatelliteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class GnssTelemetryEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gnssState = MutableStateFlow(GnssState())
    val gnssState: StateFlow<GnssState> = _gnssState.asStateFlow()

    private var lastLocation: Location? = null
    private var agcBaselineDb: Double? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            updateLocationState(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val gnssStatusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            parseGnssStatus(status)
        }
    }

    private val gnssMeasurementsCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            analyzeAgcAndInterference(event)
        }
    }

    fun start() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            val lm = locationManager ?: return
            // Register for location updates (GPS + Network)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener)
            }

            // Register GnssStatus callback
            lm.registerGnssStatusCallback(gnssStatusCallback, null)

            // Register GnssMeasurements callback for AGC Jamming/Interference detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    lm.registerGnssMeasurementsCallback(gnssMeasurementsCallback, null)
                } catch (e: Exception) {
                    // Some OEM chipsets don't expose raw measurements
                }
            }
        } catch (e: SecurityException) {
            // Permission missing
        } catch (e: Exception) {
            // Location manager error
        }
    }

    fun stop() {
        try {
            locationManager?.removeUpdates(locationListener)
            locationManager?.unregisterGnssStatusCallback(gnssStatusCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager?.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
            }
        } catch (e: Exception) {
            // Safe teardown
        }
    }

    fun updateDemoState(state: GnssState) {
        _gnssState.value = state
    }

    private fun updateLocationState(loc: Location) {
        val speedMs = if (loc.hasSpeed()) loc.speed else 0f
        val speedKmh = speedMs * 3.6f
        val hAcc = if (loc.hasAccuracy()) loc.accuracy else 0f
        val vAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasVerticalAccuracy()) {
            loc.verticalAccuracyMeters
        } else hAcc * 1.5f

        val current = _gnssState.value
        _gnssState.value = current.copy(
            hasFix = true,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeMsl = loc.altitude,
            altitudeWgs84 = loc.altitude, // Best available representation
            speedKmh = speedKmh,
            speedMs = speedMs,
            bearingDeg = if (loc.hasBearing()) loc.bearing else current.bearingDeg,
            horizontalAccuracyM = hAcc,
            verticalAccuracyM = vAcc
        )
    }

    private fun parseGnssStatus(status: GnssStatus) {
        val satCount = status.satelliteCount
        val satList = mutableListOf<SatelliteInfo>()
        var usedCount = 0
        var dualFreqCount = 0

        val constellationMap = mutableMapOf<String, Pair<Int, Int>>() // Constellation -> (used, total)

        for (i in 0 until satCount) {
            val constType = status.getConstellationType(i)
            val constName = getConstellationName(constType)
            val svid = status.getSvid(i)
            val cn0 = status.getCn0DbHz(i)
            val elev = status.getElevationDegrees(i)
            val azim = status.getAzimuthDegrees(i)
            val used = status.usedInFix(i)
            if (used) usedCount++

            var hasCarrier = false
            var carrierHz = 0f
            var bandLabel = "L1"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(i)) {
                hasCarrier = true
                carrierHz = status.getCarrierFrequencyHz(i)
                bandLabel = getCarrierBandLabel(constType, carrierHz)
                if (bandLabel.contains("L5") || bandLabel.contains("E5") || bandLabel.contains("B2")) {
                    dualFreqCount++
                }
            }

            satList.add(
                SatelliteInfo(
                    svid = svid,
                    constellationType = constType,
                    constellationName = constName,
                    cn0DbHz = cn0,
                    elevationDegrees = elev,
                    azimuthDegrees = azim,
                    hasCarrierFrequency = hasCarrier,
                    carrierFrequencyHz = carrierHz,
                    bandLabel = bandLabel,
                    usedInFix = used
                )
            )

            // Constellation count aggregation
            val currentPair = constellationMap[constName] ?: Pair(0, 0)
            constellationMap[constName] = Pair(
                currentPair.first + if (used) 1 else 0,
                currentPair.second + 1
            )
        }

        // DOP approximations based on satellite count & distribution
        val hdop = if (usedCount > 3) (20.0f / usedCount.coerceAtLeast(4)).coerceIn(0.7f, 9.9f) else 15f
        val vdop = hdop * 1.4f
        val pdop = sqrt(hdop * hdop + vdop * vdop)

        val dualRatio = if (usedCount > 0) (dualFreqCount.toFloat() / usedCount.toFloat() * 100f).coerceAtMost(100f) else 0f

        val current = _gnssState.value
        _gnssState.value = current.copy(
            satellitesInView = satCount,
            satellitesUsedInFix = usedCount,
            satellites = satList,
            hdop = hdop,
            vdop = vdop,
            pdop = pdop,
            dualFrequencyRatioPct = dualRatio,
            constellationCountMap = constellationMap
        )
    }

    private fun analyzeAgcAndInterference(event: GnssMeasurementsEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        var sumAgc = 0.0
        var count = 0
        for (m in event.measurements) {
            if (m.hasAutomaticGainControlLevelDb()) {
                sumAgc += m.automaticGainControlLevelDb
                count++
            }
        }

        if (count > 0) {
            val avgAgc = sumAgc / count
            if (agcBaselineDb == null) {
                agcBaselineDb = avgAgc
            }

            val baseline = agcBaselineDb ?: avgAgc
            val diff = baseline - avgAgc
            // Significant AGC drop indicates RF front-end gain reduction, often caused by high out-of-band/in-band jamming power
            val interferenceScore = (diff * 5f).toFloat().coerceIn(0f, 100f)
            val jamming = interferenceScore > 65f

            val current = _gnssState.value
            _gnssState.value = current.copy(
                agcInterferenceScore = interferenceScore,
                jammingAlert = jamming
            )
        }
    }

    private fun getConstellationName(type: Int): String {
        return when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
            GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            GnssStatus.CONSTELLATION_IRNSS -> "NavIC"
            else -> "Other"
        }
    }

    private fun getCarrierBandLabel(constellation: Int, freqHz: Float): String {
        val freqMhz = freqHz / 1e6
        return when (constellation) {
            GnssStatus.CONSTELLATION_GPS -> {
                when {
                    freqMhz in 1570.0..1580.0 -> "L1 (1575.42 MHz)"
                    freqMhz in 1170.0..1185.0 -> "L5 (1176.45 MHz)"
                    freqMhz in 1220.0..1235.0 -> "L2 (1227.60 MHz)"
                    else -> String.format("%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_GALILEO -> {
                when {
                    freqMhz in 1570.0..1580.0 -> "E1 (1575.42 MHz)"
                    freqMhz in 1170.0..1185.0 -> "E5a (1176.45 MHz)"
                    freqMhz in 1200.0..1215.0 -> "E5b (1207.14 MHz)"
                    else -> String.format("%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_BEIDOU -> {
                when {
                    freqMhz in 1555.0..1570.0 -> "B1I (1561.09 MHz)"
                    freqMhz in 1570.0..1580.0 -> "B1C (1575.42 MHz)"
                    freqMhz in 1170.0..1185.0 -> "B2a (1176.45 MHz)"
                    else -> String.format("%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_GLONASS -> {
                when {
                    freqMhz in 1595.0..1610.0 -> "G1 (~1602 MHz)"
                    freqMhz in 1240.0..1255.0 -> "G2 (~1246 MHz)"
                    else -> String.format("%.1f MHz", freqMhz)
                }
            }
            else -> String.format("%.1f MHz", freqMhz)
        }
    }
}
