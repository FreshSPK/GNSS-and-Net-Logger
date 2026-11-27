package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.bts.BtsGeoUtils
import com.example.data.db.AppDatabase
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.export.Exporter
import com.example.model.*
import com.example.security.SecurityAnalyzer
import com.example.security.SecurityContextSnapshot
import com.example.security.SecurityMonitorEngine
import com.example.telemetry.EnvironmentSensorEngine
import com.example.ui.tabs.*
import com.example.ui.theme.SignalLoggerTheme
import com.example.util.BandMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComprehensiveAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun cleanSatellites(): List<SatelliteInfo> = listOf(
        SatelliteInfo(1, 1, "GPS", 41f, 70f, 30f, true, 1575420000f, "L1", true),
        SatelliteInfo(2, 1, "GPS", 40f, 62f, 110f, true, 1176450000f, "L5", true),
        SatelliteInfo(3, 3, "GALILEO", 39f, 55f, 200f, true, 1575420000f, "E1", true),
        SatelliteInfo(4, 3, "GALILEO", 33f, 30f, 280f, true, 1176450000f, "E5a", true),
        SatelliteInfo(5, 6, "GLONASS", 29f, 16f, 45f, true, 1602000000f, "L1", true),
        SatelliteInfo(6, 5, "BEIDOU", 27f, 12f, 320f, true, 1561098000f, "B1", true)
    )

    private fun cleanGnss() = GnssState(
        hasFix = true,
        latitude = 49.2268,
        longitude = 16.5754,
        altitudeMsl = 265.0,
        speedKmh = 36.0f,
        speedMs = 10.0f,
        satellitesInView = 12,
        satellitesUsedInFix = 8,
        l1Count = 6,
        l5Count = 2,
        l5InViewCount = 2,
        hasEverSeenDualFreq = true,
        dopplerSpreadMps = 310f,
        agcInterferenceScore = 4f,
        avgAgcDb = -10.0,
        agcBaselineDb = -10.2,
        fixTimestampMs = 1700000000000L,
        gnssHardwareUtcMs = 1700000000000L,
        satellites = cleanSatellites()
    )

    private fun cleanSim1() = SimCellInfo(
        slotIndex = 0,
        isServing = true,
        isDataActive = true,
        operatorAlpha = "Vodafone CZ",
        displayName = "Vodafone CZ",
        plmn = "23003",
        networkGen = "4G LTE",
        band = "B3 (1800)",
        dlFreqMhz = 1845.0,
        tac = "38003",
        ci = "827147",
        pci = 260,
        rsrp = -80,
        rsrq = -9,
        rssi = -62,
        sinr = 14.5f,
        timingAdvance = 2,
        estimatedDistanceM = 312.0,
        neighbors = listOf(CellNeighbor("4G LTE", 120, 6400, "B20", -94, 14)),
        btsMatch = BtsMatchInfo(
            operatorPlmn = "23003",
            operatorName = "Vodafone CZ",
            cellId = 827147L,
            enodebId = 3231L,
            sectorId = 11,
            dbPci = 260,
            dbTac = 38003,
            dbBand = "1800",
            locationName = "Brno - FEKT VUT",
            latitude = 49.2280,
            longitude = 16.5780,
            coordSource = "OPENCELLID",
            distanceMeters = 280.0,
            azimuthDeg = 52f
        )
    )

    private fun sampleRecordWithSecurity(
        risk: Int? = 75,
        status: String = "CRITICAL"
    ) = TelemetryRecord(
        id = 1L,
        sessionId = 1L,
        timestampMs = 1700000005000L,
        latitude = 49.2268,
        longitude = 16.5754,
        altitudeMsl = 265.0,
        altitudeWgs84 = 310.0,
        speedKmh = 36f,
        speedMs = 10f,
        bearingDeg = 45f,
        horizontalAccuracyM = 3f,
        verticalAccuracyM = 4f,
        satellitesInView = 12,
        satellitesUsedInFix = 8,
        pdop = 1.2f,
        hdop = 0.8f,
        vdop = 0.9f,
        dualFrequencyRatioPct = 25f,
        agcInterferenceScore = 10f,
        constellationBreakdown = "GPS:4,GAL:4",
        sim1Operator = "Vodafone CZ",
        sim1NetworkGen = "4G LTE",
        sim1TacLac = "38003",
        sim1CellId = "827147",
        sim1EnbOrGnb = "3231",
        sim1SectorId = "11",
        sim1Pci = 260,
        sim1Arfcn = 1300,
        sim1Band = "B3",
        sim1Rsrp = -61,
        sim1Rsrq = -10,
        sim1Rssi = -18,
        sim1Sinr = -5.4f,
        sim1SsRsrp = null,
        sim1SsRsrq = null,
        sim1SsSinr = null,
        sim1CsiRsrp = null,
        sim1CsiRsrq = null,
        sim1CsiSinr = null,
        sim1TimingAdvance = 18,
        sim1EstimatedDistanceM = 2812.0,
        sim2Operator = "No SIM",
        sim2NetworkGen = "Searching",
        sim2Band = "",
        sim2Rsrp = null,
        sim2Sinr = null,
        bestNeighborRsrp = null,
        neighborServingDeltaDb = null,
        pressureHpa = 1012f,
        baroRelativeAltM = 260f,
        pingRttMs = 22L,
        pingPacketLoss = false,
        securityRiskScore = risk,
        securityStatus = status,
        securityGnssRisk = if (risk != null) 20 else null,
        securityRfRisk = if (risk != null) 85 else null,
        securityTimeRisk = if (risk != null) 0 else null,
        gnssMinusNtpMs = if (risk != null) -4820L else null,
        sim1TaVsBtsDeltaM = if (risk != null) 2450.0 else null,
        sim1RsrpResidualDb = if (risk != null) 34 else null,
        sim1RssiRsrpGapDb = if (risk != null) 43 else null,
        securityAlertsSummary = if (risk != null) "sim1_power_vs_ta:CRITICAL(-61 dBm); time_triple_sync:CRITICAL(G-N: -4820ms)" else ""
    )

    @Test
    fun testRoomMigration5To6PreservesExistingRowsAndSupportsSpoofingFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_test_v5_v6.db"
        context.deleteDatabase(dbName)

        // 1. Create a v5 telemetry_records table directly via SQLite
        val legacyHelper = object : SQLiteOpenHelper(context, dbName, null, 5) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `log_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER,
                        `pointCount` INTEGER NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `maxSpeedKmh` REAL NOT NULL,
                        `operatorSummary` TEXT NOT NULL,
                        `primaryTech` TEXT NOT NULL,
                        `primaryBand` TEXT NOT NULL,
                        `avgRsrp` REAL,
                        `notes` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `telemetry_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitudeMsl` REAL NOT NULL,
                        `altitudeWgs84` REAL NOT NULL,
                        `speedKmh` REAL NOT NULL,
                        `speedMs` REAL NOT NULL,
                        `bearingDeg` REAL NOT NULL,
                        `horizontalAccuracyM` REAL NOT NULL,
                        `verticalAccuracyM` REAL NOT NULL,
                        `satellitesInView` INTEGER NOT NULL,
                        `satellitesUsedInFix` INTEGER NOT NULL,
                        `pdop` REAL NOT NULL,
                        `hdop` REAL NOT NULL,
                        `vdop` REAL NOT NULL,
                        `dualFrequencyRatioPct` REAL NOT NULL,
                        `agcInterferenceScore` REAL NOT NULL,
                        `constellationBreakdown` TEXT NOT NULL,
                        `sim1Operator` TEXT NOT NULL,
                        `sim1NetworkGen` TEXT NOT NULL,
                        `sim1TacLac` TEXT NOT NULL,
                        `sim1CellId` TEXT NOT NULL,
                        `sim1EnbOrGnb` TEXT NOT NULL,
                        `sim1SectorId` TEXT NOT NULL,
                        `sim1Pci` INTEGER NOT NULL,
                        `sim1Arfcn` INTEGER NOT NULL,
                        `sim1Band` TEXT NOT NULL,
                        `sim1Rsrp` INTEGER,
                        `sim1Rsrq` INTEGER,
                        `sim1Rssi` INTEGER,
                        `sim1Sinr` REAL,
                        `sim1SsRsrp` INTEGER,
                        `sim1SsRsrq` INTEGER,
                        `sim1SsSinr` REAL,
                        `sim1CsiRsrp` INTEGER,
                        `sim1CsiRsrq` INTEGER,
                        `sim1CsiSinr` REAL,
                        `sim1TimingAdvance` INTEGER,
                        `sim1EstimatedDistanceM` REAL,
                        `sim2Operator` TEXT NOT NULL,
                        `sim2NetworkGen` TEXT NOT NULL,
                        `sim2Band` TEXT NOT NULL,
                        `sim2Rsrp` INTEGER,
                        `sim2Sinr` REAL,
                        `bestNeighborRsrp` INTEGER,
                        `neighborServingDeltaDb` INTEGER,
                        `pressureHpa` REAL,
                        `baroRelativeAltM` REAL,
                        `seaLevelPressureHpa` REAL,
                        `pingRttMs` INTEGER,
                        `pingPacketLoss` INTEGER NOT NULL,
                        `sim1Plmn` TEXT NOT NULL,
                        `sim2Plmn` TEXT NOT NULL,
                        `batteryTempC` REAL,
                        `batteryLevelPct` INTEGER,
                        `nmeaSentence` TEXT,
                        `rawDopplerHz` REAL,
                        `sim2TacLac` TEXT NOT NULL,
                        `sim2CellId` TEXT NOT NULL,
                        `sim2EnbOrGnb` TEXT NOT NULL,
                        `sim2SectorId` TEXT NOT NULL,
                        `sim2Pci` INTEGER NOT NULL,
                        `sim2Arfcn` INTEGER NOT NULL,
                        `sim2Rsrq` INTEGER,
                        `sim2Rssi` INTEGER,
                        `sim2TimingAdvance` INTEGER,
                        `sim2EstimatedDistanceM` REAL,
                        `waypointNote` TEXT,
                        `uniqueSatellitesInView` INTEGER NOT NULL,
                        `uniqueSatellitesUsed` INTEGER NOT NULL,
                        `usedSatellitesDetail` TEXT NOT NULL,
                        `visibleSatellitesDetail` TEXT NOT NULL,
                        `sim1CaSummary` TEXT NOT NULL,
                        `sim2CaSummary` TEXT NOT NULL,
                        `sim1BtsLocation` TEXT NOT NULL,
                        `sim1BtsDistanceM` REAL,
                        `sim1BtsAzimuthDeg` REAL,
                        `sim2BtsLocation` TEXT NOT NULL,
                        `sim2BtsDistanceM` REAL,
                        `sim2BtsAzimuthDeg` REAL,
                        FOREIGN KEY(`sessionId`) REFERENCES `log_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telemetry_records_sessionId` ON `telemetry_records` (`sessionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telemetry_records_timestampMs` ON `telemetry_records` (`timestampMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_telemetry_records_latitude_longitude` ON `telemetry_records` (`latitude`, `longitude`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bts_towers` (
                        `operatorPlmn` TEXT NOT NULL,
                        `cellId` INTEGER NOT NULL,
                        `enodebId` INTEGER NOT NULL,
                        `sectorId` INTEGER NOT NULL,
                        `pci` INTEGER NOT NULL,
                        `tac` INTEGER NOT NULL,
                        `band` TEXT NOT NULL,
                        `gsmCid` TEXT NOT NULL,
                        `updatedDate` TEXT NOT NULL,
                        `districtCode` TEXT NOT NULL,
                        `locationName` TEXT NOT NULL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `coordSource` TEXT NOT NULL,
                        `importedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`operatorPlmn`, `cellId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_cellId` ON `bts_towers` (`cellId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_operatorPlmn_enodebId` ON `bts_towers` (`operatorPlmn`, `enodebId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_operatorPlmn_pci_tac` ON `bts_towers` (`operatorPlmn`, `pci`, `tac`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_districtCode` ON `bts_towers` (`districtCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_latitude_longitude` ON `bts_towers` (`latitude`, `longitude`)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val writable = legacyHelper.writableDatabase
        writable.execSQL(
            "INSERT INTO log_sessions (id, title, startTimeMs, pointCount, distanceMeters, maxSpeedKmh, operatorSummary, primaryTech, primaryBand, notes) " +
                "VALUES (1, 'Legacy v5 Drive', 1700000000000, 1, 100.0, 40.0, 'Vodafone CZ', '4G LTE', 'B3', '')"
        )
        writable.execSQL(
            "INSERT INTO telemetry_records (id, sessionId, timestampMs, latitude, longitude, altitudeMsl, altitudeWgs84, speedKmh, speedMs, bearingDeg, " +
                "horizontalAccuracyM, verticalAccuracyM, satellitesInView, satellitesUsedInFix, pdop, hdop, vdop, dualFrequencyRatioPct, agcInterferenceScore, constellationBreakdown, " +
                "sim1Operator, sim1NetworkGen, sim1TacLac, sim1CellId, sim1EnbOrGnb, sim1SectorId, sim1Pci, sim1Arfcn, sim1Band, sim2Operator, sim2NetworkGen, sim2Band, pingPacketLoss, " +
                "sim1Plmn, sim2Plmn, sim2TacLac, sim2CellId, sim2EnbOrGnb, sim2SectorId, sim2Pci, sim2Arfcn, uniqueSatellitesInView, uniqueSatellitesUsed, usedSatellitesDetail, visibleSatellitesDetail, sim1CaSummary, sim2CaSummary, sim1BtsLocation, sim2BtsLocation) " +
                "VALUES (10, 1, 1700000001000, 49.22, 16.57, 250.0, 295.0, 30.0, 8.3, 90.0, 4.0, 5.0, 10, 7, 1.5, 1.0, 1.1, 20.0, 5.0, 'GPS:7', " +
                "'Vodafone CZ', '4G LTE', '38003', '827147', '3231', '11', 260, 1300, 'B3', 'No SIM', 'Searching', '', 0, " +
                "'23003', 'N/A', '', '', '', '', 0, 0, 10, 7, '', '', '', '', 'Brno', '')"
        )
        writable.close()
        legacyHelper.close()

        // 2. Open with Room v6 and run MIGRATION_5_6 cleanly
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = roomDb.telemetryDao()
            val legacyRows = dao.getRecordsListForSession(1L)
            assertEquals(1, legacyRows.size)
            assertNull(legacyRows[0].securityRiskScore)
            assertEquals("", legacyRows[0].securityStatus)

            // 3. Insert new v6 record containing full spoofing fields
            val newId = dao.insertRecord(sampleRecordWithSecurity(risk = 85, status = "CRITICAL").copy(id = 0L, sessionId = 1L))
            val allRows = dao.getRecordsListForSession(1L)
            assertEquals(2, allRows.size)
            val savedSec = allRows.first { it.id == newId }
            assertEquals(85, savedSec.securityRiskScore)
            assertEquals("CRITICAL", savedSec.securityStatus)
            assertEquals(-4820L, savedSec.gnssMinusNtpMs)
            assertEquals(2450.0, savedSec.sim1TaVsBtsDeltaM!!, 0.01)
            assertEquals(34, savedSec.sim1RsrpResidualDb)
            assertEquals(43, savedSec.sim1RssiRsrpGapDb)
            assertTrue(savedSec.securityAlertsSummary.contains("sim1_power_vs_ta"))
        } finally {
            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testSecurityAnalyzerDetectsAllThreatVectorsAndSimulatedAttacks() {
        val baseTime = TimeSyncDiagnostics(
            ntpTimeMs = 1700000000000L,
            ntpServer = "pool.ntp.org",
            ntpRttMs = 15L,
            ntpStratum = 2,
            systemTimeMs = 1700000000010L,
            gnssFixTimeMs = 1700000000000L,
            gnssHardwareUtcMs = 1700000000000L
        )
        val cleanCell = CellularState(sim1 = cleanSim1())

        // 1. Baseline nominal -> OK / 0 risk
        val baseline = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = cleanCell,
            timeDiagInput = baseTime,
            ctx = SecurityContextSnapshot(hasValidatedInternet = true)
        )
        assertEquals(ThreatSeverity.OK, baseline.overallStatus)
        assertEquals(0, baseline.overallRiskScore)

        // 2. Software Mock Location -> CRITICAL
        val mockRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(isMockLocation = true),
            cellularInput = cleanCell,
            timeDiagInput = baseTime,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.CRITICAL, mockRes.overallStatus)
        assertEquals(ThreatSeverity.CRITICAL, mockRes.checks.first { it.id == "gnss_mock" }.severity)

        // 3. Elevation inversion (low elevation C/N0 > zenith C/N0) -> WARNING
        val invSats = listOf(
            SatelliteInfo(1, 1, "GPS", 31f, 65f, 10f, true, 1575420000f, "L1", true),
            SatelliteInfo(2, 1, "GPS", 32f, 55f, 120f, true, 1575420000f, "L1", true),
            SatelliteInfo(3, 3, "GALILEO", 41f, 10f, 200f, true, 1575420000f, "E1", true),
            SatelliteInfo(4, 3, "GALILEO", 40f, 14f, 280f, true, 1575420000f, "E1", true),
            SatelliteInfo(5, 6, "GLONASS", 33f, 35f, 45f, true, 1602000000f, "L1", true),
            SatelliteInfo(6, 5, "BEIDOU", 34f, 30f, 320f, true, 1561098000f, "B1", true)
        )
        val invRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(satellites = invSats),
            cellularInput = cleanCell,
            timeDiagInput = baseTime,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.WARNING, invRes.checks.first { it.id == "gnss_cn0_profile" }.severity)

        // 4. IMU stationary conflict + GNSS spatial mismatch (> 45 km) -> CRITICAL
        val spoofPosRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(speedKmh = 45f),
            cellularInput = CellularState(
                sim1 = cleanSim1().copy(
                    btsMatch = cleanSim1().btsMatch!!.copy(distanceMeters = 52_000.0)
                )
            ),
            timeDiagInput = baseTime,
            ctx = SecurityContextSnapshot(isImuStationaryNow = true, imuStationarySeconds = 8)
        )
        assertEquals(ThreatSeverity.CRITICAL, spoofPosRes.checks.first { it.id == "gnss_imu_kinematics" }.severity)
        assertEquals(ThreatSeverity.CRITICAL, spoofPosRes.checks.first { it.id == "gnss_vs_bts_geo" }.severity)

        // 5. Power vs TA Overpower Near BTS (TA=0, RSRP=-48, no neighbors) -> CRITICAL
        val stingraySim = cleanSim1().copy(timingAdvance = 0, estimatedDistanceM = 0.0, rsrp = -48, neighbors = emptyList())
        val stingrayRes = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss(),
            cellularInput = CellularState(sim1 = stingraySim),
            timeDiagInput = baseTime,
            ctx = SecurityContextSnapshot()
        )
        assertEquals("OVERPOWER_NEAR_BTS", stingrayRes.sim1RfMetrics.powerVsTaAnomalyType)
        assertEquals(ThreatSeverity.CRITICAL, stingrayRes.checks.first { it.id == "sim1_power_vs_ta" }.severity)

        // 6. All 5 Demo Attack Simulator modes trigger CRITICAL threats
        for (attack in SimulatedAttackType.entries.filter { it != SimulatedAttackType.NONE }) {
            val simAttackState = SecurityAnalyzer.evaluate(
                gnssInput = cleanGnss(),
                cellularInput = cleanCell,
                timeDiagInput = baseTime,
                ctx = SecurityContextSnapshot(),
                simulatedAttack = attack
            )
            assertEquals(
                "Expected CRITICAL status for simulated attack $attack",
                ThreatSeverity.CRITICAL,
                simAttackState.overallStatus
            )
            assertTrue("Expected high risk score for $attack", simAttackState.overallRiskScore >= 65)
        }
    }

    @Test
    fun testSecurityMonitorEngineTracksTacOscillationsAndIncidentCooldown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = SecurityMonitorEngine(context, scope)

        val baseGnss = cleanGnss()
        val baseEnv = EnvironmentState(battery = BatteryState())

        // Oscillate SIM1 TAC across 4 distinct areas -> triggers tacChanges2Min >= 3 WARNING
        listOf("1001", "1002", "1003", "1004").forEach { tac ->
            engine.evaluateNow(
                gnss = baseGnss,
                cellular = CellularState(sim1 = cleanSim1().copy(tac = tac)),
                env = baseEnv,
                isDemoMode = true
            )
        }
        val state = engine.securityState.value
        assertEquals(3, state.sim1RfMetrics.tacChangesCount)
        assertEquals(ThreatSeverity.WARNING, state.checks.first { it.id == "sim1_imsi_catcher" }.severity)
        assertTrue(state.recentIncidents.isNotEmpty())

        engine.clearIncidents()
        assertTrue(engine.securityState.value.recentIncidents.isEmpty())

        engine.resetSessionBaselines()
        engine.evaluateNow(baseGnss, CellularState(sim1 = cleanSim1().copy(tac = "1004")), baseEnv, isDemoMode = true)
        assertEquals(0, engine.securityState.value.sim1RfMetrics.tacChangesCount)
    }

    @Test
    fun testExporterExportsAndEscapesSpoofingTelemetryAcrossAllFormats() {
        val session = LogSession(id = 42L, title = "Anti-Spoof \"Test\" Drive", startTimeMs = 1700000000000L)
        val recLogged = sampleRecordWithSecurity(risk = 75, status = "CRITICAL")
        val recSkipped = sampleRecordWithSecurity(risk = null, status = "").copy(id = 2L)

        val csv = Exporter.exportToCsv(session, listOf(recLogged, recSkipped))
        assertTrue(csv.contains("SecurityRiskScore,SecurityStatus,GnssRiskScore,RfRiskScore,TimeRiskScore,GnssMinusNtpMs,Sim1TaVsBtsDelta_m,Sim1RsrpResidual_dB,Sim1RssiRsrpGap_dB,SecurityAlertsSummary"))
        assertTrue(csv.contains("75,\"CRITICAL\",20,85,0,-4820,2450.0,34,43,\"sim1_power_vs_ta:CRITICAL(-61 dBm); time_triple_sync:CRITICAL(G-N: -4820ms)\""))
        assertTrue(csv.contains(",\"\",,,,,")) // null fields for skipped record

        val gpx = Exporter.exportToGpx(session, listOf(recLogged))
        assertTrue(gpx.contains("<security_risk_score>75</security_risk_score>"))
        assertTrue(gpx.contains("<security_status>CRITICAL</security_status>"))
        assertTrue(gpx.contains("<gnss_minus_ntp_ms>-4820</gnss_minus_ntp_ms>"))
        assertTrue(gpx.contains("<sim1_ta_vs_bts_delta_m>2450.0</sim1_ta_vs_bts_delta_m>"))

        val geoJson = Exporter.exportToGeoJson(session, listOf(recLogged, recSkipped))
        assertTrue(geoJson.contains("\"title\": \"Anti-Spoof \\\"Test\\\" Drive\""))
        assertTrue(geoJson.contains("\"securityRiskScore\": 75"))
        assertTrue(geoJson.contains("\"securityRiskScore\": null"))
        assertTrue(geoJson.contains("\"sim1RssiRsrpGapDb\": 43"))
    }

    @Test
    fun testBandMapperEarfcnNrArfcnAndEnvironmentBarometerMath() {
        assertEquals("B1 (2100 MHz FDD)", BandMapper.lteEarfcnToBand(300))
        assertEquals("B3 (1800 MHz FDD)", BandMapper.lteEarfcnToBand(1300))
        assertEquals("B7 (2600 MHz FDD)", BandMapper.lteEarfcnToBand(2850))
        assertEquals("B20 (800 MHz FDD)", BandMapper.lteEarfcnToBand(6400))
        assertEquals("B28 (700 MHz FDD)", BandMapper.lteEarfcnToBand(8800))

        assertTrue(BandMapper.nrArfcnToBand(365000).startsWith("n3"))
        assertTrue(BandMapper.nrArfcnToBand(636000).startsWith("n78"))
        assertEquals(Pair(2222L, 21), BandMapper.decodeLteCellId(568853L))
        assertNull(BandMapper.sanitizeMetric(Int.MAX_VALUE))
        assertEquals(-85, BandMapper.sanitizeMetric(-85))
        assertEquals(1815.0, BandMapper.lteEarfcnToFrequencies(1300).first, 0.1)
        assertEquals(3540.0, BandMapper.nrArfcnToFrequencies(636000).first, 0.5)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val envEngine = EnvironmentSensorEngine(context)
        envEngine.updateDemoPressure(pressureHpa = 1013.25f, qnhHpa = 1013.25f)
        assertEquals(0f, envEngine.envState.value.relativeAltitudeM ?: -99f, 0.2f)
        envEngine.updateDemoPressure(pressureHpa = 983.0f, qnhHpa = 1013.25f)
        assertTrue((envEngine.envState.value.relativeAltitudeM ?: 0f) in 240f..270f)
    }

    @Test
    fun testGnssTabAndChartsTabRenderCleanly() {
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                GnssTab(gnss = cleanGnss(), lang = "CS")
            }
        }
        composeTestRule.onNodeWithText("GNSS SATELITY A PÁSMA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plná barva = Ve fixu").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testOverviewFieldsDialogTogglesSpoofingAndCellularTabHasNoDuplicateSpoofingCard() {
        var latestSelection = LoggedFieldsSelection()

        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                OverviewTab(
                    gnss = cleanGnss(),
                    cellular = CellularState(sim1 = cleanSim1()),
                    env = EnvironmentState(battery = BatteryState()),
                    isLogging = false,
                    isPaused = false,
                    loggedPoints = 0,
                    fieldsSelection = latestSelection,
                    lang = "CS",
                    onStartLogging = {},
                    onPauseLogging = {},
                    onResumeLogging = {},
                    onStopLogging = {},
                    onUpdateFieldsSelection = { latestSelection = it }
                )
            }
        }
        composeTestRule.onNodeWithText("Položky").performClick()
        composeTestRule.onNodeWithTag("field_checkbox_spoofing").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("field_checkbox_spoofing").performClick()
        assertFalse(latestSelection.logSpoofing)
    }

    @Test
    fun testCellularRfTabHasRemovedDuplicateSpoofingCardAndHistoryShowsSecurityStats() {
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                CellularRfTab(
                    cellular = CellularState(sim1 = cleanSim1()),
                    lang = "CS"
                )
            }
        }
        composeTestRule.onNodeWithText("SPOOFING DETECTION", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Detekce spoofingu", substring = true).assertDoesNotExist()
    }

    @Test
    fun testHistoryInspectorDisplaysLoggedSpoofingRiskAndAlerts() {
        val records = listOf(sampleRecordWithSecurity(risk = 85, status = "CRITICAL"))
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SessionDetailInspector(
                    session = LogSession(id = 1L, title = "Security Test Session", startTimeMs = 1700000000000L),
                    records = records,
                    lang = "CS",
                    onBack = {},
                    onExportCsv = {},
                    onOpenCharts = {},
                    onDeletePoint = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Max riziko: 85% (výstrah: 1)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Bezpečnost: 85% [CRITICAL] | ΔTA 2450m | NTP -4820ms", substring = true).assertIsDisplayed()
    }

    @Test
    fun testClockOffsetUpTo800MsStaysOkAndAbove800MsTriggersWarning() {
        val now = 1700000000000L
        val gnss = cleanGnss()
        val cell = CellularState(sim1 = cleanSim1())

        // -662 ms (exact screenshot scenario) and exactly -800 ms must stay OK (green)
        for (offsetMs in listOf(-662L, -800L, 800L)) {
            val diag = TimeSyncDiagnostics(
                ntpTimeMs = now,
                systemTimeMs = now + offsetMs,
                gnssFixTimeMs = now + offsetMs,
                gnssHardwareUtcMs = now + offsetMs
            )
            val res = SecurityAnalyzer.evaluate(
                gnssInput = gnss.copy(fixTimestampMs = now + offsetMs, gnssHardwareUtcMs = now + offsetMs),
                cellularInput = cell,
                timeDiagInput = diag,
                ctx = SecurityContextSnapshot()
            )
            val timeCheck = res.checks.first { it.id == "time_triple_sync" }
            assertEquals("Offset $offsetMs ms should stay OK", ThreatSeverity.OK, timeCheck.severity)
        }

        // -850 ms should trigger WARNING, -2200 ms should trigger CRITICAL
        val warnDiag = TimeSyncDiagnostics(
            ntpTimeMs = now,
            systemTimeMs = now - 730L,
            gnssFixTimeMs = now - 850L,
            gnssHardwareUtcMs = now - 850L
        )
        val warnRes = SecurityAnalyzer.evaluate(
            gnssInput = gnss.copy(fixTimestampMs = now - 850L, gnssHardwareUtcMs = now - 850L),
            cellularInput = cell,
            timeDiagInput = warnDiag,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.WARNING, warnRes.checks.first { it.id == "time_triple_sync" }.severity)

        val critDiag = TimeSyncDiagnostics(
            ntpTimeMs = now,
            systemTimeMs = now - 730L,
            gnssFixTimeMs = now - 2200L,
            gnssHardwareUtcMs = now - 2200L
        )
        val critRes = SecurityAnalyzer.evaluate(
            gnssInput = gnss.copy(fixTimestampMs = now - 2200L, gnssHardwareUtcMs = now - 2200L),
            cellularInput = cell,
            timeDiagInput = critDiag,
            ctx = SecurityContextSnapshot()
        )
        assertEquals(ThreatSeverity.CRITICAL, critRes.checks.first { it.id == "time_triple_sync" }.severity)
    }

    @Test
    fun testSecurityTabClickingCheckCardOpensFullDetailAndExplanationPopup() {
        val now = 1700000000000L
        val evaluated = SecurityAnalyzer.evaluate(
            gnssInput = cleanGnss().copy(fixTimestampMs = now - 662L, gnssHardwareUtcMs = now - 662L),
            cellularInput = CellularState(sim1 = cleanSim1()),
            timeDiagInput = TimeSyncDiagnostics(
                ntpTimeMs = now,
                systemTimeMs = now - 734L,
                gnssFixTimeMs = now - 662L,
                gnssHardwareUtcMs = now - 662L
            ),
            ctx = SecurityContextSnapshot()
        )

        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SecurityTab(
                    security = evaluated,
                    isDemoMode = false,
                    lang = "CS",
                    onSyncNtpNow = {},
                    onRunDeepScan = {},
                    onClearIncidents = {},
                    onSelectSimulatedAttack = {}
                )
            }
        }

        // Verify -662 ms GNSS-NTP stays OK & card can be clicked to open unclipped popup
        composeTestRule.onNodeWithTag("security_check_card_time_triple_sync").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("security_check_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("NAMĚŘENÉ HODNOTY (ÚPLNÝ VÝPIS)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("security_check_detail_measured").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Co tento parametr znamená").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Jak se počítá").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("security_check_detail_close").performClick()
        composeTestRule.onNodeWithTag("security_check_detail_dialog").assertDoesNotExist()
    }
}
