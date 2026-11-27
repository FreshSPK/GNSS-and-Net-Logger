package com.example

import com.example.model.*
import com.example.security.SecurityAnalyzer
import com.example.security.SecurityContextSnapshot
import com.example.security.SntpTimeClient
import org.junit.Assert.*
import org.junit.Test

class SecurityAnalyzerTest {

    private fun cleanGnss(): GnssState {
        val sats = listOf(
            SatelliteInfo(1, 1, "GPS", 41f, 70f, 30f, true, 1575420000f, "L1 (1575.42 MHz)", true),
            SatelliteInfo(2, 1, "GPS", 39f, 55f, 120f, true, 1176450000f, "L5 (1176.45 MHz)", true),
            SatelliteInfo(3, 6, "Galileo", 35f, 48f, 210f, true, 1176450000f, "E5a (1176.45 MHz)", true),
            SatelliteInfo(4, 6, "Galileo", 25f, 12f, 300f, true, 1575420000f, "E1 (1575.42 MHz)", true),
            SatelliteInfo(5, 3, "GLONASS", 27f, 15f, 60f, true, 1602000000f, "G1 (~1602 MHz)", true),
            SatelliteInfo(6, 5, "BeiDou", 37f, 60f, 170f, true, 1176450000f, "B2a (1176.45 MHz)", true)
        )
        return GnssState(
            hasFix = true,
            latitude = 49.2266,
            longitude = 16.5746,
            altitudeMsl = 285.0,
            speedKmh = 0.0f,
            satellitesInView = 6,
            satellitesUsedInFix = 6,
            satellites = sats,
            l1Count = 3,
            l5Count = 3,
            l5InViewCount = 3,
            hasEverSeenDualFreq = true,
            agcInterferenceScore = 5f,
            dopplerSpreadMps = 320f,
            fixTimestampMs = 1700000000000L,
            gnssHardwareUtcMs = 1700000000010L
        )
    }

    private fun cleanCellular(): CellularState {
        val sim1 = SimCellInfo(
            slotIndex = 0,
            operatorAlpha = "T-Mobile CZ",
            networkGen = "4G LTE",
            isServing = true,
            isDataActive = true,
            plmn = "23001",
            tac = "24656",
            ci = "568853",
            pci = 21,
            dlFreqMhz = 1845.0,
            rsrp = -88,
            rsrq = -9,
            rssi = -60,
            sinr = 14f,
            timingAdvance = 4,
            estimatedDistanceM = 625.0,
            neighbors = listOf(CellNeighbor("LTE", 428, 1650, "B3", -95, 7)),
            btsMatch = BtsMatchInfo(
                operatorPlmn = "23001",
                operatorName = "T-Mobile CZ",
                cellId = 568853L,
                enodebId = 2222L,
                sectorId = 21,
                dbPci = 21,
                dbTac = 24656,
                dbBand = "1800",
                distanceMeters = 580.0
            )
        )
        return CellularState(sim1 = sim1, pingRttMs = 24L, pingPacketLoss = false)
    }

    @Test
    fun cleanEnvironmentProducesZeroCriticalOrWarning() {
        val now = 1700000000000L
        val timeDiag = TimeSyncDiagnostics(
            ntpTimeMs = now,
            systemTimeMs = now + 20L,
            gnssFixTimeMs = now,
            gnssHardwareUtcMs = now + 10L
        )
        val result = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.OK, result.overallStatus)
        assertTrue(result.overallRiskScore < 20)
    }

    @Test
    fun detectsGnssVsNtpClockSkewReplayAttack() {
        val now = 1700000000000L
        val timeDiag = TimeSyncDiagnostics(
            ntpTimeMs = now,
            systemTimeMs = now + 15L,
            gnssFixTimeMs = now - 5200L,
            gnssHardwareUtcMs = now - 5200L
        )
        val result = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(fixTimestampMs = now - 5200L, gnssHardwareUtcMs = now - 5200L),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot()
        )
        val timeCheck = result.checks.first { it.id == "time_triple_sync" }
        assertEquals(ThreatSeverity.CRITICAL, timeCheck.severity)
        assertTrue(result.timeRiskScore >= 70)
    }

    @Test
    fun detectsTaVsPowerAndWidebandJamming() {
        val spoofedCell = cleanCellular().let { c ->
            c.copy(
                sim1 = c.sim1.copy(
                    timingAdvance = 16, // ~2500m
                    estimatedDistanceM = 2500.0,
                    rsrp = -60, // impossibly strong for 2.5km
                    rssi = -15, // gap = 45 dB (> 38 dB wideband jamming)
                    sinr = -5.5f
                )
            )
        }
        val now = 1700000000000L
        val result = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = spoofedCell,
            timeDiagInput = TimeSyncDiagnostics(ntpTimeMs = now, systemTimeMs = now),
            ctx = SecurityContextSnapshot()
        )
        assertEquals("IMPOSSIBLE_STRONG_FAR_TA", result.sim1RfMetrics.powerVsTaAnomalyType)
        assertTrue(result.sim1RfMetrics.widebandJammingDetected)
        assertEquals(ThreatSeverity.CRITICAL, result.overallStatus)
    }

    @Test
    fun detectsImuStationaryKinematicSpoofAndElevationInversion() {
        val invertedSats = listOf(
            SatelliteInfo(1, 1, "GPS", 30f, 65f, 10f, true, 1575420000f, "L1", true),
            SatelliteInfo(2, 1, "GPS", 29f, 55f, 90f, true, 1575420000f, "L1", true),
            SatelliteInfo(3, 6, "Galileo", 41f, 10f, 180f, true, 1575420000f, "E1", true),
            SatelliteInfo(4, 6, "Galileo", 42f, 14f, 270f, true, 1575420000f, "E1", true),
            SatelliteInfo(5, 3, "GLONASS", 39f, 12f, 45f, true, 1602000000f, "G1", true),
            SatelliteInfo(6, 5, "BeiDou", 38f, 16f, 220f, true, 1575420000f, "B1C", true)
        )
        val spoofedGnss = cleanGnss().copy(
            satellites = invertedSats,
            speedKmh = 48.0f,
            dopplerSpreadMps = 20.0f
        )
        val result = SecurityAnalyzer.evaluate(
            gnssInput = spoofedGnss,
            cellularInput = cleanCellular(),
            timeDiagInput = TimeSyncDiagnostics(),
            ctx = SecurityContextSnapshot(imuStationarySeconds = 8, isImuStationaryNow = true)
        )
        assertTrue(result.gnssMetrics.elevationCn0Inversion)
        assertTrue(result.gnssMetrics.gnssVsImuMismatch)
        assertEquals(ThreatSeverity.CRITICAL, result.overallStatus)
    }

    @Test
    fun sntpTimestampParsingMatchesUnixEpoch() {
        // 2023-11-14 22:13:20.500 UTC -> unix sec = 1700000000, ntp sec = 1700000000 + 2208988800 = 3908988800
        val ntpSec = 1700000000L + 2208988800L
        val buf = ByteArray(48)
        buf[0] = ((ntpSec ushr 24) and 0xFF).toByte()
        buf[1] = ((ntpSec ushr 16) and 0xFF).toByte()
        buf[2] = ((ntpSec ushr 8) and 0xFF).toByte()
        buf[3] = (ntpSec and 0xFF).toByte()
        // 0.5 sec = 0x80000000
        buf[4] = 0x80.toByte()

        val parsed = SntpTimeClient.readTimestampMs(buf, 0)
        assertEquals(1700000000500L, parsed)
    }

    @Test
    fun detectsConstellationDropAndNmeaMismatch() {
        val gpsOnlySats = (1..8).map { id ->
            SatelliteInfo(id, 1, "GPS", 38f, 35f + id * 3f, (id * 40f) % 360f, true, 1575420000f, "L1", true)
        }
        val gnssSpoof = cleanGnss().copy(
            satellites = gpsOnlySats,
            satellitesInView = 8,
            satellitesUsedInFix = 8,
            hasEverSeenMultiConstellation = true,
            nmeaVsLocationDistM = 142.5
        )
        val res = SecurityAnalyzer.evaluate(
            gnssInput = gnssSpoof,
            cellularInput = cleanCellular(),
            timeDiagInput = TimeSyncDiagnostics(ntpTimeMs = 1700000000000L, systemTimeMs = 1700000000000L),
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.CRITICAL, res.checks.first { it.id == "gnss_constellation_drop" }.severity)
        assertTrue(res.gnssMetrics.constellationDrop)
        assertEquals(ThreatSeverity.CRITICAL, res.checks.first { it.id == "gnss_nmea_vs_location" }.severity)
    }

    @Test
    fun detectsAdrDropOppositeDopplerCn0StepAndFrozenSky() {
        val now = 1700000000000L
        val timeDiag = TimeSyncDiagnostics(ntpTimeMs = now, systemTimeMs = now)

        val adrAndDopplerRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(
                adrValidCount = 0,
                hasEverSeenValidAdr = true,
                satellitesUsedInFix = 6
            ),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot(
                dopplerElevationMismatchCount = 4,
                cn0TakeoverJumpDb = 11.4f
            )
        )
        val adrWarn = adrAndDopplerRes.checks.first { it.id == "gnss_adr_doppler_freeze" }
        assertEquals(ThreatSeverity.WARNING, adrWarn.severity)
        assertEquals(4, adrAndDopplerRes.gnssMetrics.dopplerElevMismatchCount)

        val frozenRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot(frozenConstellationDetected = true)
        )
        assertEquals(ThreatSeverity.CRITICAL, frozenRes.checks.first { it.id == "gnss_adr_doppler_freeze" }.severity)
        assertTrue(frozenRes.gnssMetrics.frozenConstellation)
    }

    @Test
    fun detectsGyroHeadingMismatchMagnetometerAnomalyAndWalkingSpeedConflict() {
        val now = 1700000000000L
        val timeDiag = TimeSyncDiagnostics(ntpTimeMs = now, systemTimeMs = now)

        val res = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(speedKmh = 48.0f, horizontalAccuracyM = 3.0f),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot(
                gnssBearingDelta4sDeg = 60.0f,
                gyroYawDelta4sDeg = 2.0f,
                gyroTurnMismatchDetected = true,
                magneticFieldUt = 12.0f,
                stepsLast6s = 12,
                isWalkingDetected = true
            )
        )
        assertEquals(ThreatSeverity.CRITICAL, res.checks.first { it.id == "gnss_gyro_heading" }.severity)
        assertTrue(res.gnssMetrics.gyroTurnMismatch)
        assertEquals(ThreatSeverity.WARNING, res.checks.first { it.id == "imu_step_speed" }.severity)
        assertTrue(res.gnssMetrics.walkingVsGnssMismatch)

        // Walking while GNSS stays frozen near zero km/h
        val frozenWalkRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(speedKmh = 0.1f, horizontalAccuracyM = 3.0f),
            cellularInput = cleanCellular(),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot(stepsLast6s = 10, isWalkingDetected = true)
        )
        assertEquals(ThreatSeverity.WARNING, frozenWalkRes.checks.first { it.id == "imu_step_speed" }.severity)
    }

    @Test
    fun detectsFsplExcessTestIdsCqiMismatchStationaryTaDropDualSimDivergenceAndPrivateDns() {
        val now = 1700000000000L
        val timeDiag = TimeSyncDiagnostics(ntpTimeMs = now, systemTimeMs = now)

        // 1. FSPL ceiling excess + test TAC + stationary IMU TA drop + private DNS
        val clean = cleanCellular()
        val s1Test = clean.sim1.copy(
            plmn = "00101",
            tac = "1",
            rsrp = -50,
            timingAdvance = 0,
            estimatedDistanceM = 0.0,
            neighbors = emptyList(),
            btsMatch = clean.sim1.btsMatch!!.copy(fsplDb = 110.0) // maxExpected = 40 - 110 = -70 dBm -> excess +20 dB
        )
        val res1 = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = clean.copy(sim1 = s1Test),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot(
                stationaryTaDropDetectedSim1 = true,
                stationaryTaDropDetailSim1 = "TA 10 -> 0 (PCI 21->21)",
                dnsServers = listOf("192.168.8.1"),
                hasPrivateCellularDns = true
            )
        )
        assertEquals(ThreatSeverity.CRITICAL, res1.checks.first { it.id == "sim1_fspl_and_cqi" }.severity)
        assertEquals(ThreatSeverity.CRITICAL, res1.checks.first { it.id == "sim1_imu_ta_drop" }.severity)
        assertEquals(ThreatSeverity.CRITICAL, res1.checks.first { it.id == "net_integrity" }.severity)

        // 2. Narrow LTE bandwidth / poor CQI at strong RSRP
        val s1NarrowCqi = clean.sim1.copy(channelBandwidthKhz = 1400, cqi = 2, rsrp = -75, rsrq = -17)
        val resNarrow = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = clean.copy(sim1 = s1NarrowCqi),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.WARNING, resNarrow.checks.first { it.id == "sim1_fspl_and_cqi" }.severity)
        assertTrue(resNarrow.sim1RfMetrics.cqiPoorWithStrongRf)

        // 3. Dual-SIM divergence: SIM1 forced to 2G + TA=0 while SIM2 healthy 4G LTE + >40km BTS divergence
        val s1Rogue = clean.sim1.copy(
            networkGen = "2G GSM",
            timingAdvance = 0,
            btsMatch = clean.sim1.btsMatch!!.copy(latitude = 49.2266, longitude = 16.5746)
        )
        val s2Healthy = clean.sim1.copy(
            slotIndex = 1,
            operatorAlpha = "O2 - CZ",
            plmn = "23002",
            networkGen = "4G LTE",
            timingAdvance = 5,
            btsMatch = clean.sim1.btsMatch!!.copy(latitude = 50.0755, longitude = 14.4378) // Prague vs Brno (~185 km)
        )
        val resDual = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = CellularState(sim1 = s1Rogue, sim2 = s2Healthy),
            timeDiagInput = timeDiag,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.CRITICAL, resDual.checks.first { it.id == "dual_sim_cross_check" }.severity)
    }
}
