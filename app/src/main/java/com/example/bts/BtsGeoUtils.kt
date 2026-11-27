package com.example.bts

import java.util.Locale
import kotlin.math.*

object BtsGeoUtils {
    private const val EARTH_RADIUS_M = 6371000.0

    /**
     * Haversine great-circle distance in meters between two WGS84 coordinates.
     */
    fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Initial bearing (azimuth) from point 1 (UE) to point 2 (BTS) in degrees [0, 360).
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val deg = Math.toDegrees(atan2(y, x))
        return (((deg % 360.0) + 360.0) % 360.0).toFloat()
    }

    fun bearingToCardinal(deg: Float, isCs: Boolean): String {
        val dirsCs = arrayOf("S", "SV", "V", "JV", "J", "JZ", "Z", "SZ")
        val dirsEn = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return if (isCs) dirsCs[idx] else dirsEn[idx]
    }

    /**
     * Free-Space Path Loss (FSPL) in dB: 32.45 + 20*log10(f_MHz) + 20*log10(d_km).
     */
    fun calculateFsplDb(freqMhz: Double?, distanceMeters: Double?): Double? {
        if (freqMhz == null || freqMhz <= 0.0 || distanceMeters == null || distanceMeters < 1.0) return null
        val dKm = distanceMeters / 1000.0
        return 32.45 + 20.0 * log10(freqMhz) + 20.0 * log10(dKm)
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            String.format(Locale.US, "%.0f m", meters)
        } else {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    fun normalizeCzechOperatorPlmn(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits == "23001" || digits == "2301") return "23001"
        if (digits == "23002" || digits == "2302") return "23002"
        if (digits == "23003" || digits == "2303") return "23003"

        val low = trimmed.lowercase(Locale.ROOT)
        return when {
            low.contains("t-mobile") || low.contains("tmobile") || low.contains("paegas") -> "23001"
            low.contains("o2") || low.contains("eurotel") -> "23002"
            low.contains("vodafone") || low.contains("oskar") -> "23003"
            else -> null
        }
    }

    fun inferOperatorFromFilename(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val low = fileName.lowercase(Locale.ROOT)
        return when {
            low.startsWith("tlte") || low.startsWith("t5g") || low.startsWith("tgsm") ||
                low.contains("t-mobile") || low.contains("tmobile") ||
                low.contains("23001") || low.contains("230-01") || low.contains("paegas") -> "23001"
            low.startsWith("o2") || low.contains("_o2") || low.contains("o2lte") ||
                low.contains("o25g") || low.contains("23002") || low.contains("230-02") ||
                low.contains("eurotel") -> "23002"
            low.startsWith("vlte") || low.startsWith("v5g") || low.startsWith("vgsm") ||
                low.contains("vodafone") || low.contains("23003") || low.contains("230-03") ||
                low.contains("oskar") -> "23003"
            else -> null
        }
    }

    fun operatorDisplayName(plmn: String): String = when (plmn) {
        "23001" -> "T-Mobile CZ"
        "23002" -> "O2 CZ"
        "23003" -> "Vodafone CZ"
        else -> plmn
    }

    fun mocnPartnerPlmn(plmn: String): String? = when (plmn) {
        "23001" -> "23002"
        "23002" -> "23001"
        else -> null
    }

    fun isValidCzechCoord(lat: Double?, lon: Double?): Boolean {
        if (lat == null || lon == null) return false
        return lat in 48.3..51.2 && lon in 11.9..19.0
    }

    /**
     * Precision ranking for coordinate sources so coarse UE centroids (OpenCelliD / UE logs)
     * never overwrite exact tower pins or official Czech RUIAN cadastre coordinates.
     */
    fun coordSourcePriority(source: String): Int = when (source.uppercase(Locale.ROOT)) {
        "MANUAL" -> 100
        "CLF", "NTM", "KML", "IMPORT", "DEMO_FEKT" -> 85
        "RUIAN" -> 80
        "CELLMAPPER" -> 70
        "GEOCODER", "NOMINATIM" -> 55
        "BEACONDB" -> 45
        "OPENCELLID" -> 40
        "CELLMAPPER_LOG" -> 25
        else -> 0
    }

    private val MAST_JARGON_KEYWORDS = listOf(
        "stožár", "stozar", "stož.", "příhrad", "prihrad", "betonov", "kotven",
        "komín", "komin", "kom.", "panelák", "panelak", "bytový", "bytovy",
        "indoor", "poutač", "poutac", "vodojem", "vodárn", "vodarn", "silo",
        "věž", "vez", "kostel", "střecha", "strecha", "hala", "haly", "areál",
        "areal", "oc ", "lzs", "tunel", "budova", "hotel", "nemocnice",
        "datacentrum", "škola", "skola", "úřad", "urad", "kancelář", "rozhledna",
        "k.ú.", "k. u.", "p.č.", "parc.", "parcela", "č.p.", "č.ev.", "u nádraží"
    )

    private fun stripNotesAndPrefixes(part: String): String {
        return part
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\b(ul\\.|ulice|tř\\.|třída|nám\\.|náměstí)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(č\\.\\s*p\\.|č\\.\\s*o\\.|č\\.\\s*ev\\.)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Returns true if the GSMweb Umisteni string looks like it carries a street or house number
     * (where RUIAN address matching achieves 2-10m accuracy), rather than only a forest/hill mast description.
     */
    fun hasStreetOrNumberHint(rawUmisteni: String): Boolean {
        val parts = rawUmisteni.split(",").map { stripNotesAndPrefixes(it) }.filter { it.isNotBlank() }
        if (parts.size < 2) return false
        val secondLow = parts[1].lowercase(Locale.ROOT)
        if (MAST_JARGON_KEYWORDS.any { secondLow.contains(it) }) return false
        return parts[1].any { it.isDigit() } || parts[1].length >= 4
    }

    /**
     * Generates ranked Czech address queries for CUZK RUIAN GeocodeSOE (without ", Czechia").
     * Example: "Brno - Královo Pole, Technická 3082/12, indoor FEKT" ->
     *   ["Brno - Královo Pole, Technická 3082/12", "Technická 3082/12, Brno", "Brno - Královo Pole"]
     */
    fun buildRuianAddressQueries(rawUmisteni: String): List<String> {
        val rawParts = rawUmisteni.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (rawParts.isEmpty()) return emptyList()

        val kept = mutableListOf<String>()
        for ((idx, part) in rawParts.withIndex()) {
            val cleaned = stripNotesAndPrefixes(part)
            if (cleaned.isBlank()) continue
            val low = cleaned.lowercase(Locale.ROOT)
            if (idx == 0) {
                kept.add(cleaned)
                continue
            }
            if (MAST_JARGON_KEYWORDS.any { low.contains(it) }) break
            kept.add(cleaned)
            if (kept.size >= 3) break
        }

        if (kept.isEmpty()) return emptyList()
        val results = linkedSetOf<String>()
        val cityFull = kept[0]
        val baseCity = cityFull.substringBefore(" - ").trim()

        if (kept.size >= 2) {
            val streetPart = kept[1]
            // Full form: "Technická 3082/12, Brno" works especially well in RUIAN GeocodeSOE
            results.add("$streetPart, $baseCity")
            results.add("$cityFull, $streetPart")
            // Also try without orientation number if slash notation (e.g., 3082/12 -> 3082)
            val slashMatch = Regex("^(.+?\\s+\\d+)/\\d+$").find(streetPart)
            if (slashMatch != null) {
                results.add("${slashMatch.groupValues[1]}, $baseCity")
            }
        }
        // Only add bare municipality when there is no mast-only hint ambiguity or as last candidate
        if (kept.size == 1) {
            results.add(baseCity)
            if (cityFull != baseCity) results.add(cityFull)
        }
        return results.filter { it.length >= 2 }.toList()
    }

    /**
     * Strips GSMweb tower construction jargon from Umisteni so Geocoder/Nominatim resolves street & municipality cleanly.
     * Example: "Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL"
     *   -> "Brno - Královo Pole, Technická 3082/12, Czechia"
     */
    fun cleanLocationForGeocoding(rawUmisteni: String): String {
        val parts = rawUmisteni.split(",").map { stripNotesAndPrefixes(it) }.filter { it.isNotBlank() }
        if (parts.isEmpty()) return rawUmisteni

        val kept = mutableListOf<String>()
        for ((idx, part) in parts.withIndex()) {
            val low = part.lowercase(Locale.ROOT)
            if (idx == 0) {
                kept.add(part)
                continue
            }
            val looksLikeMast = MAST_JARGON_KEYWORDS.any { low.contains(it) }
            if (!looksLikeMast) {
                kept.add(part)
            } else {
                break
            }
        }

        val joined = kept.take(2).joinToString(", ")
        return "$joined, Czechia"
    }

    fun bandToApproxFreqMhz(bandRaw: String): Double? {
        val digits = bandRaw.filter { it.isDigit() }
        return when (digits) {
            "700", "28" -> 768.0
            "800", "20" -> 806.0
            "900", "8" -> 945.0
            "1500", "32" -> 1472.0
            "1800", "3" -> 1845.0
            "2100", "1" -> 2140.0
            "2600", "7", "38" -> 2655.0
            "3500", "78", "42" -> 3550.0
            "3700", "77", "43" -> 3700.0
            else -> digits.toDoubleOrNull()?.takeIf { it in 400.0..6000.0 }
        }
    }
}
