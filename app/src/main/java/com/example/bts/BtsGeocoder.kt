package com.example.bts

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

object BtsGeocoder {

    /**
     * Resolves a Czech BTS Umisteni description to WGS84 (latitude, longitude).
     * Uses Android platform Geocoder first, with OpenStreetMap Nominatim fallback.
     */
    suspend fun geocodeCzechLocation(context: Context, rawUmisteni: String): Pair<Double, Double>? {
        if (rawUmisteni.isBlank()) return null
        val query = BtsGeoUtils.cleanLocationForGeocoding(rawUmisteni)

        // 1. Try Android platform Geocoder
        val platformResult = tryPlatformGeocoder(context, query)
        if (platformResult != null) return platformResult

        // 2. Fallback to OpenStreetMap Nominatim (no API key required)
        return tryNominatimGeocoder(context.packageName, query)
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
                    setRequestProperty("User-Agent", "$packageName/1.6 (VUT-FEKT-SignalLogger)")
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
