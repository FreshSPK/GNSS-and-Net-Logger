package com.example.telemetry

import com.example.model.*
import kotlin.math.cos
import kotlin.math.sin

class DemoSimulationEngine {
    private var tick = 0
    // Centered around Brno University of Technology FEKT UREL (approx. 49.2266 N, 16.5746 E)
    private var centerLat = 49.2266
    private var centerLon = 16.5746

    fun nextSimulationStep(): Triple<GnssState, CellularState, EnvironmentState> {
        tick++
        val angle = (tick * 2.5) * (Math.PI / 180.0)
        val radius = 0.0035 + 0.001 * sin(tick * 0.05)

        val curLat = centerLat + radius * cos(angle)
        val curLon = centerLon + radius * sin(angle)
        val speedKmh = 42.0f + 12f * sin(tick * 0.1f).toFloat()
        val speedMs = speedKmh / 3.6f
        val bearing = ((tick * 2.5 + 90) % 360).toFloat()

        // Satellites cycle: 18 - 28 satellites
        val satTotal = 24 + (4 * sin(tick * 0.1)).toInt()
        val satUsed = (satTotal * 0.85).toInt()
        val hdop = 0.8f + 0.2f * sin(tick * 0.2f).toFloat()
        val vdop = hdop * 1.3f
        val pdop = 1.4f

        val dualRatio = 72f + 15f * sin(tick * 0.15f).toFloat()

        // Generate dynamic satellites list for Polar SkyPlot
        val satList = mutableListOf<SatelliteInfo>()
        val constellations = listOf(
            Triple(1, "GPS", "L1/L5"),
            Triple(3, "GLONASS", "G1/G2"),
            Triple(6, "Galileo", "E1/E5a"),
            Triple(5, "BeiDou", "B1/B2a")
        )

        for (i in 1..satTotal) {
            val constInfo = constellations[i % constellations.size]
            val az = ((i * 360f / satTotal) + (tick * 1.2f)) % 360f
            val el = (20f + ((i * 47) % 65)).toFloat()
            val cn0 = 34f + (15f * sin((i + tick) * 0.3)).toFloat().coerceIn(-4f, 16f)
            satList.add(
                SatelliteInfo(
                    svid = i,
                    constellationType = constInfo.first,
                    constellationName = constInfo.second,
                    cn0DbHz = cn0,
                    elevationDegrees = el,
                    azimuthDegrees = az,
                    hasCarrierFrequency = true,
                    carrierFrequencyHz = 1575420000f,
                    bandLabel = constInfo.third,
                    usedInFix = i <= satUsed
                )
            )
        }

        val constCounts = mapOf(
            "GPS" to Pair(7, 8),
            "Galileo" to Pair(6, 6),
            "GLONASS" to Pair(4, 5),
            "BeiDou" to Pair(4, 5)
        )

        // AGC Jamming test (periodic slight perturbation, normal: ~12%)
        val agcScore = (12f + 5f * sin(tick * 0.2f).toFloat()).coerceIn(0f, 100f)

        val gnss = GnssState(
            hasFix = true,
            latitude = curLat,
            longitude = curLon,
            altitudeMsl = 295.0 + 15.0 * sin(tick * 0.08),
            altitudeWgs84 = 340.0 + 15.0 * sin(tick * 0.08),
            speedKmh = speedKmh,
            speedMs = speedMs,
            bearingDeg = bearing,
            horizontalAccuracyM = 1.8f + 0.4f * sin(tick * 0.3f).toFloat(),
            verticalAccuracyM = 2.9f,
            satellitesInView = satTotal,
            satellitesUsedInFix = satUsed,
            pdop = pdop,
            hdop = hdop,
            vdop = vdop,
            dualFrequencyRatioPct = dualRatio,
            satellites = satList,
            agcInterferenceScore = agcScore,
            jammingAlert = false,
            constellationCountMap = constCounts
        )

        // RF Dual-SIM
        val is5gSector = (tick / 20) % 2 == 0
        val lteRsrp = -82 + (12 * sin(tick * 0.1)).toInt()
        val lteSinr = 16f + 6f * sin(tick * 0.12f).toFloat()

        val sim1 = SimCellInfo(
            slotIndex = 0,
            subscriptionId = 1,
            displayName = "O2-CZ (SIM 1)",
            operatorAlpha = "O2-CZ",
            networkGen = if (is5gSector) "5G NSA" else "4G LTE",
            isServing = true,
            tac = "24201",
            ci = "1849201",
            enbOrGnbId = "7223",
            sectorId = if (is5gSector) "12" else "3",
            pci = 184,
            earfcnOrArfcn = if (is5gSector) 641666 else 1650,
            band = if (is5gSector) "n78 (3500)" else "B3 (1800)",
            rsrp = lteRsrp,
            rsrq = -10,
            rssi = -65,
            sinr = lteSinr,
            ssRsrp = if (is5gSector) lteRsrp - 4 else null,
            ssRsrq = if (is5gSector) -11 else null,
            ssSinr = if (is5gSector) lteSinr - 1.5f else null,
            timingAdvance = 4,
            estimatedDistanceM = 4 * 78.12,
            neighbors = listOf(
                CellNeighbor("LTE", 185, 1650, "B3 (1800)", lteRsrp - 7, 7),
                CellNeighbor("LTE", 92, 6300, "B20 (800 DD)", lteRsrp - 11, 11),
                CellNeighbor("5G NR", 310, 641666, "n78 (3500)", lteRsrp - 14, 14)
            )
        )

        val sim2Rsrp = -89 + (8 * cos(tick * 0.09)).toInt()
        val sim2 = SimCellInfo(
            slotIndex = 1,
            subscriptionId = 2,
            displayName = "T-Mobile CZ (SIM 2)",
            operatorAlpha = "T-Mobile CZ",
            networkGen = "4G LTE",
            isServing = true,
            tac = "24110",
            ci = "294819",
            enbOrGnbId = "1151",
            sectorId = "1",
            pci = 220,
            earfcnOrArfcn = 6300,
            band = "B20 (800 DD)",
            rsrp = sim2Rsrp,
            rsrq = -12,
            rssi = -71,
            sinr = 11.5f,
            timingAdvance = 7,
            estimatedDistanceM = 7 * 78.12,
            neighbors = listOf(
                CellNeighbor("LTE", 221, 6300, "B20 (800 DD)", sim2Rsrp - 6, 6)
            )
        )

        val pingMs = (28L + (14 * sin(tick * 0.2)).toLong()).coerceAtLeast(18L)

        val cell = CellularState(
            sim1 = sim1,
            sim2 = sim2,
            activeSimSlot = 0,
            pingRttMs = pingMs,
            pingPacketLoss = false
        )

        // Environment pressure
        val baroPressure = 988.5f + 1.2f * sin(tick * 0.05f).toFloat()
        val env = EnvironmentState(
            pressureHpa = baroPressure,
            baseQnhHpa = 1013.25f,
            relativeAltitudeM = 295f
        )

        return Triple(gnss, cell, env)
    }
}
