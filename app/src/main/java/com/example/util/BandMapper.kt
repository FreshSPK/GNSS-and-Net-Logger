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
            in 158200..164200 -> "n20 (800 MHz FDD)"
            in 140000..158199 -> "n28 (700 MHz FDD)"
            in 185000..192000 -> "n8 (900 MHz FDD)"
            in 361000..376000 -> "n3 (1800 MHz FDD)"
            in 422000..434000 -> "n1 (2100 MHz FDD)"
            in 514000..524000 -> "n38 (2600 MHz TDD)"
            in 499200..538000 -> "n7 (2600 MHz FDD)"
            in 620000..653333 -> "n78 (3500 MHz C-Band)"
            in 653334..693333 -> "n77 (3700 MHz C-Band)"
            else -> if (nrarfcn > 0) "NR-ARFCN $nrarfcn" else "Unknown NR Band"
        }
    }

    fun lteBandToFreqLabel(band: Int): String {
        return when (band) {
            1 -> "2100 MHz"
            2 -> "1900 MHz"
            3 -> "1800 MHz"
            4 -> "1700/2100 MHz"
            5 -> "850 MHz"
            7 -> "2600 MHz"
            8 -> "900 MHz"
            20 -> "800 MHz"
            28 -> "700 MHz"
            32 -> "1500 MHz SDL"
            38 -> "2600 MHz TDD"
            40 -> "2300 MHz TDD"
            41 -> "2500 MHz TDD"
            42 -> "3500 MHz TDD"
            43 -> "3700 MHz TDD"
            else -> if (band > 0) "Band $band" else ""
        }
    }

    fun nrBandToFreqLabel(band: Int): String {
        return when (band) {
            1 -> "2100 MHz"
            3 -> "1800 MHz"
            7 -> "2600 MHz"
            8 -> "900 MHz"
            20 -> "800 MHz"
            28 -> "700 MHz"
            38 -> "2600 MHz TDD"
            40 -> "2300 MHz TDD"
            41 -> "2500 MHz TDD"
            77 -> "3700 MHz C-Band"
            78 -> "3500 MHz C-Band"
            else -> if (band > 0) "n$band" else ""
        }
    }

    /**
     * Calculate Downlink and Uplink carrier frequencies from EARFCN (LTE).
     */
    fun lteEarfcnToFrequencies(earfcn: Int): Pair<Double, Double> {
        return when (earfcn) {
            in 0..599 -> Pair(2110.0 + 0.1 * (earfcn - 0), 1920.0 + 0.1 * (earfcn - 0)) // B1
            in 600..1199 -> Pair(1930.0 + 0.1 * (earfcn - 600), 1850.0 + 0.1 * (earfcn - 600)) // B2
            in 1200..1949 -> Pair(1805.0 + 0.1 * (earfcn - 1200), 1710.0 + 0.1 * (earfcn - 1200)) // B3
            in 2750..3449 -> Pair(2620.0 + 0.1 * (earfcn - 2750), 2500.0 + 0.1 * (earfcn - 2750)) // B7
            in 3450..3799 -> Pair(925.0 + 0.1 * (earfcn - 3450), 880.0 + 0.1 * (earfcn - 3450)) // B8
            in 6150..6449 -> Pair(791.0 + 0.1 * (earfcn - 6150), 832.0 + 0.1 * (earfcn - 6150)) // B20
            in 8650..9039 -> Pair(758.0 + 0.1 * (earfcn - 8650), 703.0 + 0.1 * (earfcn - 8650)) // B28
            in 9210..9659 -> Pair(1452.0 + 0.1 * (earfcn - 9210), 0.0) // B32 SDL
            in 37750..38249 -> {
                val f = 2570.0 + 0.1 * (earfcn - 37750)
                Pair(f, f) // B38 TDD
            }
            in 38650..39649 -> {
                val f = 2300.0 + 0.1 * (earfcn - 38650)
                Pair(f, f) // B40 TDD
            }
            in 39650..41589 -> {
                val f = 2496.0 + 0.1 * (earfcn - 39650)
                Pair(f, f) // B41 TDD
            }
            in 41590..43589 -> {
                val f = 3400.0 + 0.1 * (earfcn - 41590)
                Pair(f, f) // B42 TDD
            }
            in 43590..45589 -> {
                val f = 3600.0 + 0.1 * (earfcn - 43590)
                Pair(f, f) // B43 TDD
            }
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

    /**
     * Detect cellular RF anomalies, fake base stations (IMSI Catchers), 2G downgrades, and interference.
     */
    fun detectCellularAnomalies(
        networkGen: String,
        tac: String,
        enbOrGnbId: String,
        sectorId: String,
        rsrp: Int?,
        sinr: Float?,
        neighborsCount: Int,
        isServing: Boolean,
        lang: String = "CS"
    ): List<com.example.model.CellularAnomaly> {
        val isCs = lang != "EN"
        val list = mutableListOf<com.example.model.CellularAnomaly>()
        if (!isServing || networkGen == "Searching" || networkGen == "Unknown") return list

        // 1. 2G Downgrade Attack Check
        if (networkGen.contains("2G") || networkGen.contains("GSM") || networkGen.contains("GPRS") || networkGen.contains("EDGE")) {
            list.add(
                com.example.model.CellularAnomaly(
                    type = com.example.model.AnomalyType.DOWNGRADE_ATTACK_2G,
                    severity = com.example.model.AnomalySeverity.CRITICAL,
                    title = if (isCs) "Varování: Vynucený downgrade na 2G GSM" else "Warning: Forced 2G GSM Downgrade",
                    description = if (isCs) 
                        "Telefon byl přepnut do zastaralé sítě 2G bez vzájemné autentizace. Typický projev podvržené BTS / IMSI Catcheru."
                        else "Phone forced to legacy 2G GSM with no mutual authentication. Typical rogue BTS / IMSI Catcher signature."
                )
            )
        }

        // 2. Isolated Rogue Cell Check (High power but zero neighbor cells)
        if (rsrp != null && rsrp > -85 && neighborsCount == 0) {
            list.add(
                com.example.model.CellularAnomaly(
                    type = com.example.model.AnomalyType.ROGUE_BTS_ISOLATED,
                    severity = com.example.model.AnomalySeverity.WARNING,
                    title = if (isCs) "Podezření: Izolovaná buňka bez sousedů" else "Suspicious: Isolated Cell (No Neighbors)",
                    description = if (isCs)
                        "Silný signál ($rsrp dBm), avšak vysílač nevysílá žádné sousední buňky v SIB tabulce. Běžný znak testovací či podvržené BTS."
                        else "Strong signal ($rsrp dBm) without any neighbor cell relations in SIB. Common fake BTS / femtocell signature."
                )
            )
        }

        // 3. RF Interference & Jamming (Strong RSRP but low/negative SINR)
        if (rsrp != null && rsrp > -90 && sinr != null && sinr < -3f) {
            list.add(
                com.example.model.CellularAnomaly(
                    type = com.example.model.AnomalyType.RF_INTERFERENCE_JAMMING,
                    severity = com.example.model.AnomalySeverity.WARNING,
                    title = if (isCs) "Kritický RF šum / Rušení kanálu" else "High RF Noise / Channel Interference",
                    description = if (isCs)
                        "Signál je silný ($rsrp dBm), ale poměr SINR (${String.format(java.util.Locale.US, "%.1f", sinr)} dB) indikuje silné soukanálové rušení."
                        else "High signal power ($rsrp dBm) but degraded SINR (${String.format(java.util.Locale.US, "%.1f", sinr)} dB) indicates severe co-channel interference."
                )
            )
        }

        // 4. Invalid Cell ID / Area code check
        if (enbOrGnbId == "0" || sectorId == "0" || tac == "0" || tac == "65534") {
            list.add(
                com.example.model.CellularAnomaly(
                    type = com.example.model.AnomalyType.INVALID_CELL_ID,
                    severity = com.example.model.AnomalySeverity.INFO,
                    title = if (isCs) "Nekonzistentní identifikátor buňky" else "Inconsistent Cell Identity",
                    description = if (isCs)
                        "Buňka vysílá nestandardní TAC ($tac) nebo nulové ID vysílače."
                        else "Cell is broadcasting non-standard TAC ($tac) or zero transmitter ID."
                )
            )
        }

        return list
    }
}
