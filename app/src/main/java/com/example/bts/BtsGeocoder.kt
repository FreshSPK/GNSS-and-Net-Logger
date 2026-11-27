package com.example.bts

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

data class BtsGeocodeResult(
    val latitude: Double,
    val longitude: Double,
    val source: String, // "RUIAN", "BEACONDB", "GEOCODER", "NOMINATIM"
    val detail: String = ""
)

object BtsGeocoder {

    /**
     * Multi-tier Czech BTS location resolver:
     * 1. Official Czech CUZK RUIAN GeocodeSOE (~2-10m address precision when street/number exists)
     * 2. BeaconDB Wireless Geolocate API (crowdsourced RF cell lookup without API key; preferred first for forest/no-street masts)
     * 3. Android platform Geocoder (cs-CZ)
     * 4. OpenStreetMap Nominatim (CZ fallback)
     */
    suspend fun resolveTowerCoordinates(
        context: Context,
        plmn: String,
        cellId: Long,
        tac: Int,
        band: String,
        rawUmisteni: String
    ): BtsGeocodeResult? {
        val hasAddressDetail = rawUmisteni.isNotBlank() && BtsGeoUtils.hasStreetOrNumberHint(rawUmisteni)

        if (hasAddressDetail) {
            tryCuZkRuianGeocoder(rawUmisteni)?.let { return it }
        } else if (cellId > 0L) {
            tryBeaconDbGeolocate(plmn, cellId, tac, band)?.let { return it }
        }

        if (rawUmisteni.isNotBlank()) {
            val cleaned = BtsGeoUtils.cleanLocationForGeocoding(rawUmisteni)
            if (!hasAddressDetail) {
                // Rural / town-only RUIAN still beats generic centroid if specific candidate scores well
                tryCuZkRuianGeocoder(rawUmisteni, minScore = 80.0)?.let { return it }
            }
            tryPlatformGeocoder(context, cleaned)?.let { (lat, lon) ->
                return BtsGeocodeResult(lat, lon, "GEOCODER")
            }
            tryNominatimGeocoder(context.packageName, cleaned)?.let { (lat, lon) ->
                return BtsGeocodeResult(lat, lon, "NOMINATIM")
            }
        }

        if (hasAddressDetail && cellId > 0L) {
            tryBeaconDbGeolocate(plmn, cellId, tac, band)?.let { return it }
        }

        return null
    }

    /**
     * Backward-compatible address-only Czech geocoding helper.
     */
    suspend fun geocodeCzechLocation(context: Context, rawUmisteni: String): Pair<Double, Double>? {
        if (rawUmisteni.isBlank()) return null
        tryCuZkRuianGeocoder(rawUmisteni)?.let { return it.latitude to it.longitude }
        val query = BtsGeoUtils.cleanLocationForGeocoding(rawUmisteni)
        tryPlatformGeocoder(context, query)?.let { return it }
        return tryNominatimGeocoder(context.packageName, query)
    }

    internal suspend fun tryCuZkRuianGeocoder(
        rawUmisteni: String,
        minScore: Double = 68.0
    ): BtsGeocodeResult? = withContext(Dispatchers.IO) {
        val queries = BtsGeoUtils.buildRuianAddressQueries(rawUmisteni)
        for (q in queries) {
            var conn: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val url = URL(
                    "https://ags.cuzk.gov.cz/arcgis/rest/services/RUIAN/MapServer/exts/GeocodeSOE/findAddressCandidates" +
                        "?SingleLine=$encoded&outSR=4326&maxLocations=3&f=json"
                )
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "GNSSNetLogger/1.8 (VUT-FEKT)")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseCuZkRuianResponse(body, minScore)
                    if (parsed != null) return@withContext parsed
                }
            } catch (_: Exception) {
                // Try next query variant
            } finally {
                conn?.disconnect()
            }
        }
        null
    }

    internal fun parseCuZkRuianResponse(jsonBody: String, minScore: Double = 68.0): BtsGeocodeResult? {
        return try {
            val root = JSONObject(jsonBody)
            val candidates = root.optJSONArray("candidates") ?: return null
            var best: BtsGeocodeResult? = null
            var bestScore = minScore - 0.001
            for (i in 0 until candidates.length()) {
                val cand = candidates.optJSONObject(i) ?: continue
                val score = cand.optDouble("score", 0.0)
                if (score <= bestScore) continue
                val loc = cand.optJSONObject("location") ?: continue
                val lon = loc.optDouble("x", Double.NaN)
                val lat = loc.optDouble("y", Double.NaN)
                if (!BtsGeoUtils.isValidCzechCoord(lat, lon)) continue
                val matchedAddr = cand.optString("address", "")
                bestScore = score
                best = BtsGeocodeResult(
                    latitude = lat,
                    longitude = lon,
                    source = "RUIAN",
                    detail = matchedAddr
                )
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    internal suspend fun tryBeaconDbGeolocate(
        plmn: String,
        cellId: Long,
        tac: Int,
        band: String
    ): BtsGeocodeResult? = withContext(Dispatchers.IO) {
        if (cellId <= 0L) return@withContext null
        val plmnsToTry = buildList {
            val norm = BtsGeoUtils.normalizeCzechOperatorPlmn(plmn)
            if (norm != null) {
                add(norm)
                BtsGeoUtils.mocnPartnerPlmn(norm)?.let { add(it) }
            }
        }
        val radioType = inferBeaconDbRadioType(band)

        for (targetPlmn in plmnsToTry) {
            val mnc = targetPlmn.drop(3).toIntOrNull() ?: continue
            var conn: HttpURLConnection? = null
            try {
                val towerJson = JSONObject().apply {
                    put("mobileCountryCode", 230)
                    put("mobileNetworkCode", mnc)
                    put("cellId", cellId)
                    put("radioType", radioType)
                    if (tac > 0) put("locationAreaCode", tac)
                }
                val payload = JSONObject().apply {
                    put("cellTowers", JSONArray().put(towerJson))
                }.toString()

                val url = URL("https://api.beacondb.net/v1/geolocate")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("User-Agent", "GNSSNetLogger/1.8 (VUT-FEKT)")
                }
                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseBeaconDbResponse(body)
                    if (parsed != null) return@withContext parsed
                }
            } catch (_: Exception) {
                // Try MOCN partner or continue to next tier
            } finally {
                conn?.disconnect()
            }
        }
        null
    }

    internal fun parseBeaconDbResponse(jsonBody: String, maxAccuracyMeters: Double = 5000.0): BtsGeocodeResult? {
        return try {
            val root = JSONObject(jsonBody)
            val loc = root.optJSONObject("location") ?: return null
            val lat = loc.optDouble("lat", Double.NaN)
            val lon = loc.optDouble("lng", Double.NaN)
            val acc = root.optDouble("accuracy", 9999.0)
            if (!BtsGeoUtils.isValidCzechCoord(lat, lon)) return null
            if (acc > maxAccuracyMeters) return null
            BtsGeocodeResult(
                latitude = lat,
                longitude = lon,
                source = "BEACONDB",
                detail = "±${acc.toInt()}m"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun inferBeaconDbRadioType(bandRaw: String): String {
        val low = bandRaw.lowercase(Locale.ROOT)
        return when {
            low.contains("5g") || low.contains("nr") || low.contains("3500") ||
                low.contains("3700") || low.contains("n78") -> "nr"
            low.contains("umts") || low.contains("3g") || low.contains("wcdma") -> "wcdma"
            low.contains("gsm") || low.contains("2g") -> "gsm"
            else -> "lte"
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun tryPlatformGeocoder(context: Context, query: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale("cs", "CZ"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    try {
                        geocoder.getFromLocationName(
                            query,
                            3,
                            48.4, 12.0, 51.1, 18.95,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    val valid = addresses.firstOrNull {
                                        BtsGeoUtils.isValidCzechCoord(it.latitude, it.longitude)
                                    }
                                    cont.resume(valid?.let { it.latitude to it.longitude })
                                }

                                override fun onError(errorMessage: String?) {
                                    cont.resume(null)
                                }
                            }
                        )
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    val list = geocoder.getFromLocationName(query, 3, 48.4, 12.0, 51.1, 18.95)
                    val valid = list?.firstOrNull { BtsGeoUtils.isValidCzechCoord(it.latitude, it.longitude) }
                    valid?.let { it.latitude to it.longitude }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryNominatimGeocoder(packageName: String, query: String): Pair<Double, Double>? {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=cz&q=$encoded")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "$packageName/1.8 (VUT-FEKT-SignalLogger)")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val lat = obj.optString("lat").toDoubleOrNull()
                        val lon = obj.optString("lon").toDoubleOrNull()
                        if (BtsGeoUtils.isValidCzechCoord(lat, lon)) {
                            return@withContext Pair(lat!!, lon!!)
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}
