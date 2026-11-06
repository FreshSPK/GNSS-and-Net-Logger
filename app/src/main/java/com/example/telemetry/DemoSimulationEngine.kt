package com.example.telemetry

import com.example.model.*
import com.example.util.BandMapper
import kotlin.math.cos
import kotlin.math.sin

class DemoSimulationEngine {
    private var tick = 0
    // Centered around Brno University of Technology FEKT UREL (approx. 49.2266 N, 16.5746 E)
    private var centerLat = 49.2266
    private var centerLon = 16.5746

    fun nextSimulationStep(taMultiplierMode: Int = 2): Triple<GnssState, CellularState, EnvironmentState> {
        tick++
        val angle = (tick * 2.5) * (Math.PI / 180.0)
        val radius = 0.0035 + 0.001 * sin(tick * 0.05)

        val curLat = centerLat + radius * cos(angle)
        val curLon = centerLon + radius * sin(angle)
        val speedKmh = 42.0f + 12f * sin(tick * 0.1f).toFloat()
        val speedMs = speedKmh / 3.6f
        val bearing = ((tick * 2.5 + 90) % 360).toFloat()

        // Satellites cycle: 16 - 24 satellites
        val satTotal = 20 + (4 * sin(tick * 0.1)).toInt()
        val satUsed = (satTotal * 0.82).toInt()
        val hdop = 0.85f + 0.15f * sin(tick * 0.2f).toFloat()
        val vdop = 1.20f + 0.10f * cos(tick * 0.18f).toFloat()
        val pdop = 1.48f

        val dualRatio = 75f

        val satList = mutableListOf<SatelliteInfo>()
        val constellations = listOf(
            Triple(1, "GPS", "L1/L5"),
            Triple(6, "Galileo", "E1/E5a"),
            Triple(3, "GLONASS", "G1"),
            Triple(5, "BeiDou", "B1/B2")
        )

        var l1Count = 0
        var l5Count = 0

        for (i in 1..satTotal) {
            val constInfo = constellations[i % constellations.size]
            val az = ((i * 360f / satTotal) + (tick * 1.2f)) % 360f
            val el = (18f + ((i * 47) % 68)).toFloat()
            val cn0 = 32f + (16f * sin((i + tick) * 0.3)).toFloat().coerceIn(-4f, 18f)
            val hasL5 = (i % 2 == 0) && (constInfo.first == 1 || constInfo.first == 6)
            val bandLabel = if (hasL5) "L5/E5a" else "L1/E1"
            if (hasL5) l5Count++ else l1Count++

            satList.add(
                SatelliteInfo(
                    svid = i,
                    constellationType = constInfo.first,
                    constellationName = constInfo.second,
                    cn0DbHz = cn0,
                    elevationDegrees = el,
                    azimuthDegrees = az,
                    hasCarrierFrequency = true,
                    carrierFrequencyHz = if (hasL5) 1176450000f else 1575420000f,
                    bandLabel = bandLabel,
                    usedInFix = i <= satUsed
                )
            )
        }

        val constCounts = mapOf(
            "GPS" to Pair(7, 8),
            "Galileo" to Pair(5, 6),
            "GLONASS" to Pair(3, 4),
            "BeiDou" to Pair(3, 4)
        )

        val agcScore = (10f + 4f * sin(tick * 0.2f).toFloat()).coerceIn(0f, 100f)

        val gnss = GnssState(
            hasFix = true,
            latitude = curLat,
            longitude = curLon,
            altitudeMsl = 286.9 + 5.0 * sin(tick * 0.08),
            altitudeWgs84 = 331.0 + 5.0 * sin(tick * 0.08),
            speedKmh = speedKmh,
            speedMs = speedMs,
            bearingDeg = bearing,
            horizontalAccuracyM = 1.4f,
            verticalAccuracyM = 2.0f,
            satellitesInView = satTotal,
            satellitesUsedInFix = satUsed,
            pdop = pdop,
            hdop = hdop,
            vdop = vdop,
            dualFrequencyRatioPct = dualRatio,
            satellites = satList,
            agcInterferenceScore = agcScore,
            jammingAlert = false,
            constellationCountMap = constCounts,
            l1Count = l1Count,
            l5Count = l5Count,
            latestNmeaSentence = "\$GNGGA,100800.00,4913.823,N,01634.731,E,1,18,0.94,286.9,M,44.1,M,,*47",
            rawDopplerHz = -1240.5f + (80f * sin(tick * 0.15f)).toFloat(),
            adrValidCount = (satUsed * 0.75).toInt()
        )

        // RF Dual-SIM
        val is5gSector = (tick / 20) % 2 == 0
        val lteRsrp = -82 + (6 * sin(tick * 0.1)).toInt()
        val lteSinr = 18f + 3f * sin(tick * 0.12f).toFloat()

        val sim1Ta = 2
        val sim1 = SimCellInfo(
            slotIndex = 0,
            subscriptionId = 1,
            displayName = "SIM 1: T-Mobile CZ",
            operatorAlpha = "T-Mobile CZ",
            networkGen = "4G LTE Advanced",
            isServing = true,
            plmn = "230-01",
            tac = "51200",
            ci = "25840897",
            enbOrGnbId = "100941",
            sectorId = "133",
            pci = 184,
            earfcnOrArfcn = 6300,
            band = "B20 (800 MHz FDD)",
            channelBandwidthKhz = 10000,
            dlFreqMhz = 796.0,
            ulFreqMhz = 837.0,
            rsrp = lteRsrp,
            rsrq = -9,
            rssi = -54,
            sinr = lteSinr,
            cqi = 13,
            timingAdvance = sim1Ta,
            estimatedDistanceM = BandMapper.calculateLteDistanceMeters(sim1Ta, taMultiplierMode),
            neighbors = listOf(
                CellNeighbor("LTE", 428, 1650, "B3 (1800 MHz FDD)", lteRsrp - 13, 13, 1805.0, 1710.0),
                CellNeighbor("LTE", 290, 3100, "B7 (2600 MHz FDD)", lteRsrp - 12, 12, 2620.0, 2500.0),
                CellNeighbor("LTE", 277, 6300, "B20 (800 MHz FDD)", lteRsrp - 18, 18, 791.0, 832.0),
                CellNeighbor("LTE", 241, 1650, "B3 (1800 MHz FDD)", lteRsrp - 24, 24, 816.0, 857.0)
            ),
            anomalies = BandMapper.detectCellularAnomalies("4G LTE Advanced", "51200", "100941", "133", lteRsrp, lteSinr, 4, true)
        )

        val sim2Ta = 4
        val sim2Rsrp = -91 + (4 * cos(tick * 0.09)).toInt()
        val sim2 = SimCellInfo(
            slotIndex = 1,
            subscriptionId = 2,
            displayName = "SIM 2: O2 - CZ",
            operatorAlpha = "O2 - CZ",
            networkGen = "5G NR NSA",
            isServing = true,
            plmn = "230-02",
            tac = "51201",
            ci = "18941952",
            enbOrGnbId = "73992",
            sectorId = "151",
            pci = 342,
            earfcnOrArfcn = 641666,
            band = "n78 (3500 MHz C-Band)",
            channelBandwidthKhz = 40000,
            dlFreqMhz = 3550.0,
            ulFreqMhz = 3550.0,
            rsrp = sim2Rsrp,
            rsrq = -11,
            rssi = -64,
            sinr = 12.0f,
            cqi = 10,
            timingAdvance = sim2Ta,
            estimatedDistanceM = BandMapper.calculateLteDistanceMeters(sim2Ta, taMultiplierMode),
            neighbors = listOf(
                CellNeighbor("LTE", 428, 6300, "B20 (800 MHz FDD)", sim2Rsrp - 4, 4),
                CellNeighbor("5G NR", 342, 641666, "n78 (3500 MHz C-Band)", sim2Rsrp - 6, 6)
            ),
            anomalies = BandMapper.detectCellularAnomalies("5G NR NSA", "51201", "73992", "151", sim2Rsrp, 12.0f, 2, true)
        )

        val pingMs = (28L + (8 * sin(tick * 0.2)).toLong()).coerceAtLeast(18L)

        val cell = CellularState(
            sim1 = sim1,
            sim2 = sim2,
            activeSimSlot = 0,
            pingRttMs = pingMs,
            pingPacketLoss = false
        )

        val baroPressure = 988.5f + 0.8f * sin(tick * 0.05f).toFloat()
        val env = EnvironmentState(
            pressureHpa = baroPressure,
            baseQnhHpa = 1013.25f,
            relativeAltitudeM = 286.9f,
            seaLevelPressureHpa = baroPressure + 33.2f,
            motion = MotionSensorState(
                accelX = 0.2f * sin(tick * 0.2f).toFloat(),
                accelY = 0.4f * cos(tick * 0.2f).toFloat(),
                accelZ = 9.8f,
                accelMagnitude = 9.82f,
                gyroZ = 0.05f * sin(tick * 0.1f).toFloat(),
                compassAzimuthDeg = bearing
            ),
            battery = BatteryState(
                temperatureC = 30.5f + (1.2f * sin(tick * 0.05f)).toFloat(),
                levelPct = 85,
                voltageMv = 4060,
                isCharging = false
            )
        )

        return Triple(gnss, cell, env)
    }
}
