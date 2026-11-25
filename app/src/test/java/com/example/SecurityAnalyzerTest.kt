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
}
