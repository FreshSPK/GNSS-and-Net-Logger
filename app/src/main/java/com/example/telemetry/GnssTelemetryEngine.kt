package com.example.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.model.GnssState
import com.example.model.SatelliteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GnssTelemetryEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _gnssState = MutableStateFlow(GnssState())
    val gnssState: StateFlow<GnssState> = _gnssState.asStateFlow()

    private var lastValidGpsLocation: Location? = null
    private var agcBaselineDb: Double? = null
    private var isNmeaDopAvailable = false
    @Volatile
    private var isStarted = false
    private var startElapsedMs: Long = 0L
    private var measuredTtffSec: Float? = null
    private var latestGeoidSepM: Double? = null
    private var gnssThread: android.os.HandlerThread? = null
    private var gnssHandler: android.os.Handler? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (filterGpsLocation(location)) {
                updateLocationState(location)
            }
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
            parseRawMeasurements(event)
        }
    }

    private val nmeaListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        OnNmeaMessageListener { message, _ ->
            processNmeaSentence(message.trim())
        }
    } else null

    fun start() {
        if (isStarted) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        isStarted = true
        startElapsedMs = android.os.SystemClock.elapsedRealtime()
        measuredTtffSec = null

        val thread = android.os.HandlerThread("GnssTelemetryWorker").apply { start() }
        val handler = android.os.Handler(thread.looper)
        gnssThread = thread
        gnssHandler = handler

        try {
            val lm = locationManager ?: return

            // CRITICAL: Listen EXCLUSIVELY to GPS_PROVIDER. Do NOT listen to NETWORK_PROVIDER
            // which injects distant cell-tower/Wi-Fi locations and creates zig-zag spikes.
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, thread.looper)
            }

            // Register GnssStatus callback on background worker handler
            lm.registerGnssStatusCallback(gnssStatusCallback, handler)

            // Register GnssMeasurements callback for AGC Jamming & Raw Doppler/ADR
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    lm.registerGnssMeasurementsCallback(gnssMeasurementsCallback, handler)
                } catch (_: Exception) {}

                // Register NMEA message listener on background worker handler
                try {
                    nmeaListener?.let { lm.addNmeaListener(it, handler) }
                } catch (_: Exception) {}
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false
        try {
            locationManager?.removeUpdates(locationListener)
            locationManager?.unregisterGnssStatusCallback(gnssStatusCallback)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager?.unregisterGnssMeasurementsCallback(gnssMeasurementsCallback)
                nmeaListener?.let { locationManager?.removeNmeaListener(it) }
            }
        } catch (_: Exception) {}
        try {
            gnssThread?.quitSafely()
        } catch (_: Exception) {}
        gnssThread = null
        gnssHandler = null
    }

    fun resetAgcBaseline() {
        agcBaselineDb = null
        val cur = _gnssState.value
        _gnssState.value = cur.copy(agcInterferenceScore = 0f, jammingAlert = false)
    }

    fun updateDemoState(state: GnssState) {
        _gnssState.value = state
    }

    /**
     * Rejects invalid fixes, non-GPS providers, poor accuracy, and multipath jump spikes.
     */
    private fun filterGpsLocation(loc: Location): Boolean {
        // Must be GPS provider
        if (loc.provider != null && loc.provider != LocationManager.GPS_PROVIDER) return false

        // Coordinate validity
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return false
        if (loc.latitude < -90.0 || loc.latitude > 90.0 || loc.longitude < -180.0 || loc.longitude > 180.0) return false

        // Accuracy filter: discard fixes with accuracy > 25 meters
        if (loc.hasAccuracy() && loc.accuracy > 25f) return false

        // Multipath jump filter
        val last = lastValidGpsLocation
        if (last != null) {
            val distM = last.distanceTo(loc)
            val dtSec = (loc.time - last.time) / 1000.0
            if (dtSec in 0.1..15.0) {
                val impliedSpeedMs = distM / dtSec
                // If jump is > 65 m/s (234 km/h) without high-speed tag, it is a multipath spike
                if (impliedSpeedMs > 65.0 && (!loc.hasSpeed() || loc.speed < 45f)) {
                    return false
                }
            }
        }

        lastValidGpsLocation = loc
        return true
    }

    private fun updateLocationState(loc: Location) {
        if (measuredTtffSec == null && startElapsedMs > 0L) {
            measuredTtffSec = ((android.os.SystemClock.elapsedRealtime() - startElapsedMs) / 1000f).coerceAtLeast(0.1f)
        }

        val speedMs = if (loc.hasSpeed()) loc.speed else 0f
        val speedKmh = speedMs * 3.6f
        val speedAccKmh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasSpeedAccuracy()) {
            loc.speedAccuracyMetersPerSecond * 3.6f
        } else null

        val hAcc = if (loc.hasAccuracy()) loc.accuracy else 0f
        val vAcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && loc.hasVerticalAccuracy()) {
            loc.verticalAccuracyMeters
        } else hAcc * 1.5f

        val geoid = latestGeoidSepM
        val wgsAlt = if (geoid != null) loc.altitude + geoid else loc.altitude

        val current = _gnssState.value
        _gnssState.value = current.copy(
            hasFix = true,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeMsl = loc.altitude,
            altitudeWgs84 = wgsAlt,
            speedKmh = speedKmh,
            speedMs = speedMs,
            speedAccuracyKmh = speedAccKmh,
            geoidSeparationM = geoid,
            ttffSeconds = measuredTtffSec,
            bearingDeg = if (loc.hasBearing()) loc.bearing else current.bearingDeg,
            horizontalAccuracyM = hAcc,
            verticalAccuracyM = vAcc
        )
    }

    /**
     * NMEA parser directly matching standard GNSS specification (and GPSTest):
     * Parses real hardware PDOP, HDOP, VDOP from GSA sentences, and HDOP + Geoid Sep from GGA.
     */
    private fun processNmeaSentence(sentence: String) {
        if (sentence.isEmpty()) return

        val cleanSentence = sentence.trim()
        val cur = _gnssState.value

        // Store sentence for display
        if (cleanSentence.startsWith("\$GNGGA") || cleanSentence.startsWith("\$GNRMC") || cleanSentence.startsWith("\$GPGGA")) {
            _gnssState.value = cur.copy(latestNmeaSentence = cleanSentence)
        }

        // Parse GSA sentences (PDOP, HDOP, VDOP)
        // Format: $GNGSA,A,3,sv1,...,sv12,PDOP,HDOP,VDOP*checksum
        if (cleanSentence.startsWith("\$GNGSA") || cleanSentence.startsWith("\$GPGSA") ||
            cleanSentence.startsWith("\$GAGSA") || cleanSentence.startsWith("\$GLGSA") ||
            cleanSentence.startsWith("\$GBGSA") || cleanSentence.startsWith("\$BDGSA")) {

            val body = cleanSentence.substringBefore('*')
            val tokens = body.split(',')
            if (tokens.size >= 18) {
                val pdopVal = tokens[15].toFloatOrNull()
                val hdopVal = tokens[16].toFloatOrNull()
                val vdopVal = tokens[17].toFloatOrNull()

                if (pdopVal != null && pdopVal > 0f) {
                    isNmeaDopAvailable = true
                    _gnssState.value = _gnssState.value.copy(
                        pdop = pdopVal,
                        hdop = hdopVal ?: (pdopVal * 0.7f),
                        vdop = vdopVal ?: (pdopVal * 0.7f)
                    )
                }
            }
        } else if (cleanSentence.startsWith("\$GNGGA") || cleanSentence.startsWith("\$GPGGA")) {
            val body = cleanSentence.substringBefore('*')
            val tokens = body.split(',')
            if (tokens.size > 11) {
                val hdopVal = tokens[8].toFloatOrNull()
                val geoidSep = tokens[11].toDoubleOrNull()
                if (geoidSep != null) {
                    latestGeoidSepM = geoidSep
                }
                if (hdopVal != null && hdopVal > 0f) {
                    val c = _gnssState.value
                    _gnssState.value = c.copy(
                        hdop = hdopVal,
                        pdop = if (c.pdop <= 0f) hdopVal * 1.4f else c.pdop,
                        geoidSeparationM = geoidSep ?: c.geoidSeparationM
                    )
                }
            }
        }
    }

    private fun parseGnssStatus(status: GnssStatus) {
        val satCount = status.satelliteCount
        val satList = mutableListOf<SatelliteInfo>()
        var usedCount = 0
        var dualFreqUsedCount = 0
        var dualFreqViewCount = 0

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
                    dualFreqViewCount++
                    if (used) dualFreqUsedCount++
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

        // Geometric DOP calculation if NMEA has not arrived yet
        var currentPdop = _gnssState.value.pdop
        var currentHdop = _gnssState.value.hdop
        var currentVdop = _gnssState.value.vdop

        if (!isNmeaDopAvailable || currentPdop <= 0f) {
            val usedSats = satList.filter { it.usedInFix }
            if (usedSats.size >= 4) {
                // Compute geometry spread: dispersion of unit vectors
                var sumSinEl = 0.0
                var sumCosElSinAz = 0.0
                var sumCosElCosAz = 0.0
                for (s in usedSats) {
                    val elRad = Math.toRadians(s.elevationDegrees.toDouble())
                    val azRad = Math.toRadians(s.azimuthDegrees.toDouble())
                    sumSinEl += sin(elRad)
                    sumCosElSinAz += cos(elRad) * sin(azRad)
                    sumCosElCosAz += cos(elRad) * cos(azRad)
                }
                val n = usedSats.size.toDouble()
                val meanSinEl = sumSinEl / n
                val meanHorizSpread = sqrt((sumCosElSinAz / n) * (sumCosElSinAz / n) + (sumCosElCosAz / n) * (sumCosElCosAz / n))
                
                // Good geometry produces low meanHorizSpread and balanced elevation
                val geomQuality = (1.0 - meanHorizSpread.coerceIn(0.0, 0.95)) * (meanSinEl.coerceIn(0.2, 0.85))
                val calculatedPdop = (2.5 / (geomQuality * sqrt(n / 4.0))).toFloat().coerceIn(0.8f, 15f)
                val calculatedHdop = (calculatedPdop * 0.65f).coerceIn(0.6f, 10f)
                val calculatedVdop = sqrt(calculatedPdop * calculatedPdop - calculatedHdop * calculatedHdop).coerceIn(0.6f, 12f)

                currentPdop = calculatedPdop
                currentHdop = calculatedHdop
                currentVdop = calculatedVdop
            } else {
                currentPdop = 0f
                currentHdop = 0f
                currentVdop = 0f
            }
        }

        val dualRatio = if (usedCount > 0) {
            (dualFreqUsedCount.toFloat() / usedCount.toFloat() * 100f).coerceAtMost(100f)
        } else 0f

        val effectiveL5Count = if (usedCount > 0) dualFreqUsedCount else dualFreqViewCount
        val effectiveL1Count = if (usedCount > 0) {
            (usedCount - dualFreqUsedCount).coerceAtLeast(0)
        } else {
            (satCount - dualFreqViewCount).coerceAtLeast(0)
        }

        val current = _gnssState.value
        _gnssState.value = current.copy(
            satellitesInView = satCount,
            satellitesUsedInFix = usedCount,
            satellites = satList,
            hdop = currentHdop,
            vdop = currentVdop,
            pdop = currentPdop,
            l1Count = effectiveL1Count,
            l5Count = effectiveL5Count,
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
            val interferenceScore = (diff * 5f).toFloat().coerceIn(0f, 100f)
            val jamming = interferenceScore > 65f

            val current = _gnssState.value
            _gnssState.value = current.copy(
                agcInterferenceScore = interferenceScore,
                jammingAlert = jamming
            )
        }
    }

    private fun parseRawMeasurements(event: GnssMeasurementsEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        var adrValidCount = 0
        var sumDopplerHz = 0.0
        var dopplerCount = 0

        for (m in event.measurements) {
            val state = m.accumulatedDeltaRangeState
            if ((state and android.location.GnssMeasurement.ADR_STATE_VALID) != 0) {
                adrValidCount++
            }

            val prRate = m.pseudorangeRateMetersPerSecond
            if (prRate in -6000.0..6000.0 && prRate != 0.0) {
                val carrierHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && m.hasCarrierFrequencyHz()) {
                    m.carrierFrequencyHz.toDouble()
                } else 1575420000.0
                val dopplerHz = - (prRate * carrierHz / 299792458.0)
                sumDopplerHz += dopplerHz
                dopplerCount++
            }
        }

        val avgDoppler = if (dopplerCount > 0) (sumDopplerHz / dopplerCount).toFloat() else null
        val current = _gnssState.value
        _gnssState.value = current.copy(
            rawDopplerHz = avgDoppler,
            adrValidCount = adrValidCount
        )
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
                    else -> String.format(java.util.Locale.US, "%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_GALILEO -> {
                when {
                    freqMhz in 1570.0..1580.0 -> "E1 (1575.42 MHz)"
                    freqMhz in 1170.0..1185.0 -> "E5a (1176.45 MHz)"
                    freqMhz in 1200.0..1215.0 -> "E5b (1207.14 MHz)"
                    else -> String.format(java.util.Locale.US, "%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_BEIDOU -> {
                when {
                    freqMhz in 1555.0..1570.0 -> "B1I (1561.09 MHz)"
                    freqMhz in 1570.0..1580.0 -> "B1C (1575.42 MHz)"
                    freqMhz in 1170.0..1185.0 -> "B2a (1176.45 MHz)"
                    else -> String.format(java.util.Locale.US, "%.1f MHz", freqMhz)
                }
            }
            GnssStatus.CONSTELLATION_GLONASS -> {
                when {
                    freqMhz in 1595.0..1610.0 -> "G1 (~1602 MHz)"
                    freqMhz in 1240.0..1255.0 -> "G2 (~1246 MHz)"
                    else -> String.format(java.util.Locale.US, "%.1f MHz", freqMhz)
                }
            }
            else -> String.format(java.util.Locale.US, "%.1f MHz", freqMhz)
        }
    }
}
