package com.example.util

object BandMapper {
    /**
     * Map LTE EARFCN to 3GPP Band name.
     */
    fun lteEarfcnToBand(earfcn: Int): String {
        return when (earfcn) {
            in 0..599 -> "B1 (2100)"
            in 600..1199 -> "B2 (1900)"
            in 1200..1949 -> "B3 (1800)"
            in 1950..2399 -> "B4 (AWS-1)"
            in 2400..2649 -> "B5 (850)"
            in 2650..2749 -> "B6 (800)"
            in 2750..3449 -> "B7 (2600)"
            in 3450..3799 -> "B8 (900)"
            in 6150..6449 -> "B20 (800 DD)"
            in 8650..9039 -> "B28 (700 APT)"
            in 9210..9659 -> "B32 (1500 SDL)"
            in 37750..38249 -> "B38 (2600 TDD)"
            in 38650..39649 -> "B40 (2300 TDD)"
            in 39650..41589 -> "B41 (2500 TDD)"
            in 41590..43589 -> "B42 (3500 TDD)"
            in 43590..45589 -> "B43 (3700 TDD)"
            else -> if (earfcn > 0) "EARFCN $earfcn" else "Unknown Band"
        }
    }

    /**
     * Map 5G NR-ARFCN to 3GPP Band name.
     */
    fun nrArfcnToBand(nrarfcn: Int): String {
        return when (nrarfcn) {
            in 140000..170000 -> "n28 (700 APT)"
            in 151600..160600 -> "n20 (800)"
            in 185000..192000 -> "n8 (900)"
            in 361000..376000 -> "n3 (1800)"
            in 422000..434000 -> "n1 (2100)"
            in 499200..537999 -> "n7 (2600)"
            in 620000..653333 -> "n78 (3500)"
            in 636667..672000 -> "n77 (3700)"
            in 499200..538000 -> "n38 (2600 TDD)"
            else -> if (nrarfcn > 0) "NR-ARFCN $nrarfcn" else "Unknown NR Band"
        }
    }

    /**
     * Decode eNodeB ID and Sector ID from 28-bit LTE ECI (E-UTRAN Cell Identifier).
     */
    fun decodeLteCellId(eci: Long): Pair<Long, Int> {
        if (eci <= 0 || eci == 2147483647L) return Pair(0L, 0)
        val enodebId = eci ushr 8
        val sectorId = (eci and 0xFF).toInt()
        return Pair(enodebId, sectorId)
    }

    /**
     * Decode gNodeB ID and Sector ID from 36-bit NR NCI.
     */
    fun decodeNrCellId(nci: Long): Pair<Long, Int> {
        if (nci <= 0 || nci == 2147483647L) return Pair(0L, 0)
        // Typically 22..32 bits for gNodeB ID, remaining for sector (commonly 14 or 8 bits)
        val gnodebId = nci ushr 8
        val sectorId = (nci and 0xFF).toInt()
        return Pair(gnodebId, sectorId)
    }

    /**
     * LTE Timing Advance to meters distance.
     * Each TA step = 16 * Ts = 0.52 microseconds round-trip = ~78.12 m one-way.
     */
    fun calculateLteDistanceMeters(ta: Int?): Double? {
        if (ta == null || ta < 0 || ta > 1282) return null
        return ta * 78.12
    }

    /**
     * Sanitize RF numbers (avoid Android's Integer.MAX_VALUE or unavailable placeholders).
     */
    fun sanitizeMetric(value: Int?): Int? {
        if (value == null || value == Int.MAX_VALUE || value == Int.MIN_VALUE || value == 2147483647 || value == -1) return null
        return value
    }

    fun sanitizeSinr(value: Double?): Float? {
        if (value == null || value.isNaN() || value == Double.MAX_VALUE || value > 100.0 || value < -50.0) return null
        return value.toFloat()
    }
}
