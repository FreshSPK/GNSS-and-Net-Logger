package com.example.util

object BandMapper {
    /**
     * Map LTE EARFCN to 3GPP Band name.
     */
    fun lteEarfcnToBand(earfcn: Int): String {
        return when (earfcn) {
            in 0..599 -> "B1 (2100 MHz FDD)"
            in 600..1199 -> "B2 (1900 MHz FDD)"
            in 1200..1949 -> "B3 (1800 MHz FDD)"
            in 1950..2399 -> "B4 (AWS-1)"
            in 2400..2649 -> "B5 (850 MHz FDD)"
            in 2650..2749 -> "B6 (800 MHz)"
            in 2750..3449 -> "B7 (2600 MHz FDD)"
            in 3450..3799 -> "B8 (900 MHz FDD)"
            in 6150..6449 -> "B20 (800 MHz FDD)"
            in 8650..9039 -> "B28 (700 MHz FDD)"
            in 9210..9659 -> "B32 (1500 MHz SDL)"
            in 37750..38249 -> "B38 (2600 MHz TDD)"
            in 38650..39649 -> "B40 (2300 MHz TDD)"
            in 39650..41589 -> "B41 (2500 MHz TDD)"
            in 41590..43589 -> "B42 (3500 MHz TDD)"
            in 43590..45589 -> "B43 (3700 MHz TDD)"
            else -> if (earfcn > 0) "EARFCN $earfcn" else "Unknown Band"
        }
    }

    /**
     * Map 5G NR-ARFCN to 3GPP Band name.
     */
    fun nrArfcnToBand(nrarfcn: Int): String {
        return when (nrarfcn) {
            in 140000..170000 -> "n28 (700 MHz FDD)"
            in 151600..160600 -> "n20 (800 MHz FDD)"
            in 185000..192000 -> "n8 (900 MHz FDD)"
            in 361000..376000 -> "n3 (1800 MHz FDD)"
            in 422000..434000 -> "n1 (2100 MHz FDD)"
            in 499200..537999 -> "n7 (2600 MHz FDD)"
            in 620000..653333 -> "n78 (3500 MHz C-Band)"
            in 636667..672000 -> "n77 (3700 MHz C-Band)"
            in 499200..538000 -> "n38 (2600 MHz TDD)"
            else -> if (nrarfcn > 0) "NR-ARFCN $nrarfcn" else "Unknown NR Band"
        }
    }

    /**
     * Calculate Downlink and Uplink carrier frequencies from EARFCN (LTE).
     */
    fun lteEarfcnToFrequencies(earfcn: Int): Pair<Double, Double> {
        return when (earfcn) {
            in 0..599 -> Pair(2110.0 + 0.1 * (earfcn - 0), 1920.0 + 0.1 * (earfcn - 0)) // B1
            in 1200..1949 -> Pair(1805.0 + 0.1 * (earfcn - 1200), 1710.0 + 0.1 * (earfcn - 1200)) // B3
            in 2750..3449 -> Pair(2620.0 + 0.1 * (earfcn - 2750), 2500.0 + 0.1 * (earfcn - 2750)) // B7
            in 3450..3799 -> Pair(925.0 + 0.1 * (earfcn - 3450), 880.0 + 0.1 * (earfcn - 3450)) // B8
            in 6150..6449 -> Pair(791.0 + 0.1 * (earfcn - 6150), 832.0 + 0.1 * (earfcn - 6150)) // B20
            in 8650..9039 -> Pair(758.0 + 0.1 * (earfcn - 8650), 703.0 + 0.1 * (earfcn - 8650)) // B28
            else -> Pair(0.0, 0.0)
        }
    }

    /**
     * Calculate Downlink frequency from 5G NR-ARFCN.
     */
    fun nrArfcnToFrequencies(arfcn: Int): Pair<Double, Double> {
        val freqMhz = when {
            arfcn in 0..599999 -> 0.0 + 0.005 * arfcn
            arfcn in 600000..2016666 -> 3000.0 + 0.015 * (arfcn - 600000)
            else -> 0.0
        }
        return Pair(freqMhz, freqMhz) // TDD / FDD approx
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
        val gnodebId = nci ushr 8
        val sectorId = (nci and 0xFF).toInt()
        return Pair(gnodebId, sectorId)
    }

    /**
     * LTE Timing Advance to meters distance.
     * @param multiplierMode 1 for theoretical 78.12 m, 2 for user calibrated ~156 m
     */
    fun calculateLteDistanceMeters(ta: Int?, multiplierMode: Int = 2): Double? {
        if (ta == null || ta < 0 || ta > 1282) return null
        val baseStep = if (multiplierMode == 2) 156.24 else 78.12
        return ta * baseStep
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
