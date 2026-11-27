package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.tabs.SecurityTab
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bts.BtsCsvImporter
import com.example.bts.BtsGeoUtils
import com.example.bts.BtsGeocoder
import com.example.bts.BtsRepository
import com.example.data.db.AppDatabase
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.export.Exporter
import com.example.ui.components.BtsDatabaseDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.model.AnomalyType
import com.example.model.BatteryState
import com.example.model.CellularState
import com.example.model.EnvironmentState
import com.example.model.GnssState
import com.example.model.LoggedFieldsSelection
import com.example.model.MapTrailPoint
import com.example.model.SatelliteInfo
import com.example.model.SimCellInfo
import com.example.service.TelemetryLoggingService
import com.example.telemetry.DemoSimulationEngine
import com.example.telemetry.GnssTelemetryEngine
import com.example.ui.components.AboutAndHelpDialog
import com.example.ui.components.SkyPlotView
import com.example.ui.tabs.CellularRfTab
import com.example.ui.tabs.OverviewTab
import com.example.ui.tabs.SessionDetailInspector
import com.example.ui.tabs.SettingsDialog
import com.example.ui.theme.SignalLoggerTheme
import com.example.util.BandMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCalculateLteDistanceMeters() {
        val dist1x = BandMapper.calculateLteDistanceMeters(ta = 2, multiplierMode = 1)
        assertEquals(156.24, dist1x ?: 0.0, 0.1)

        val dist2x = BandMapper.calculateLteDistanceMeters(ta = 2, multiplierMode = 2)
        assertEquals(312.48, dist2x ?: 0.0, 0.1)
    }

    @Test
    fun testAnomalyDetection2gDowngrade() {
        val anomalies = BandMapper.detectCellularAnomalies(
            networkGen = "2G GSM",
            tac = "1234",
            enbOrGnbId = "100",
            sectorId = "1",
            rsrp = -75,
            sinr = 15f,
            neighborsCount = 2,
            isServing = true,
            lang = "CS"
        )
        assertTrue(anomalies.any { it.type == AnomalyType.DOWNGRADE_ATTACK_2G })
    }

    @Test
    fun testAnomalyDetectionIsolatedCell() {
        val anomalies = BandMapper.detectCellularAnomalies(
            networkGen = "4G LTE",
            tac = "1234",
            enbOrGnbId = "100",
            sectorId = "1",
            rsrp = -70,
            sinr = 15f,
            neighborsCount = 0,
            isServing = true,
            lang = "CS"
        )
        assertTrue(anomalies.any { it.type == AnomalyType.ROGUE_BTS_ISOLATED })
    }

    private fun sampleSession() = LogSession(
        id = 1L,
        title = "VUT Test Drive",
        startTimeMs = 1700000000000L
    )

    private fun sampleRecord(
        id: Long = 1L,
        lat: Double = 49.2268,
        lon: Double = 16.5754,
        sim1Rsrp: Int? = -82,
        satsUsed: Int = 12,
        pdop: Float = 1.2f,
        waypoint: String? = null
    ) = TelemetryRecord(
        id = id,
        sessionId = 1L,
        timestampMs = 1700000001000L + id * 1000L,
        latitude = lat,
        longitude = lon,
        altitudeMsl = 265.0,
        altitudeWgs84 = 310.0,
        speedKmh = 45.0f,
        speedMs = 12.5f,
        bearingDeg = 90.0f,
        horizontalAccuracyM = 3.2f,
        verticalAccuracyM = 4.5f,
        satellitesInView = 24,
        satellitesUsedInFix = satsUsed,
        pdop = pdop,
        hdop = 0.8f,
        vdop = 0.9f,
        dualFrequencyRatioPct = 50.0f,
        agcInterferenceScore = 5.0f,
        constellationBreakdown = "GPS:6,GAL:6",
        sim1Operator = "Vodafone CZ",
        sim1NetworkGen = "4G LTE",
        sim1TacLac = "38003",
        sim1CellId = "827147",
        sim1EnbOrGnb = "3231",
        sim1SectorId = "11",
        sim1Pci = 260,
        sim1Arfcn = 6400,
        sim1Band = "B20",
        sim1Rsrp = sim1Rsrp,
        sim1Rsrq = -10,
        sim1Rssi = -72,
        sim1Sinr = 12.5f,
        sim1SsRsrp = null,
        sim1SsRsrq = null,
        sim1SsSinr = null,
        sim1CsiRsrp = null,
        sim1CsiRsrq = null,
        sim1CsiSinr = null,
        sim1TimingAdvance = 2,
        sim1EstimatedDistanceM = 312.5,
        sim2Operator = "T-Mobile CZ",
        sim2NetworkGen = "5G NSA",
        sim2Band = "n78",
        sim2Rsrp = -91,
        sim2Sinr = 9.0f,
        bestNeighborRsrp = -95,
        neighborServingDeltaDb = 13,
        pressureHpa = 985.2f,
        baroRelativeAltM = 235.0f,
        seaLevelPressureHpa = 1013.25f,
        pingRttMs = 24L,
        pingPacketLoss = false,
        sim1Plmn = "23003",
        sim2Plmn = "23001",
        batteryTempC = 31.5f,
        batteryLevelPct = 88,
        nmeaSentence = "\$GNGGA,120000,4913.6,N,01634.5,E,1,12,0.8,265.0,M,45.0,M,,*00",
        rawDopplerHz = 120.0f,
        sim2TacLac = "25400",
        sim2CellId = "99011",
        sim2EnbOrGnb = "386",
        sim2SectorId = "195",
        sim2Pci = 412,
        sim2Arfcn = 640512,
        sim2Rsrq = -11,
        sim2Rssi = -78,
        sim2TimingAdvance = 3,
        sim2EstimatedDistanceM = 468.7,
        waypointNote = waypoint,
        uniqueSatellitesInView = 18,
        uniqueSatellitesUsed = satsUsed,
        usedSatellitesDetail = "G10(L1/L5@34); E03(E1/E5a@29)",
        visibleSatellitesDetail = "G02(L1@33); G10(L1/L5@34); E03(E1/E5a@29)",
        sim1CaSummary = "LTE-A 2CA (B20+B3 • 30 MHz) | PCC: B20 • 806.0 MHz • 10 MHz + SCC 1: B3 • 1845.0 MHz • 20 MHz",
        sim2CaSummary = "5G NSA EN-DC (n78 • 40 MHz)",
        sim1BtsLocation = "Brno - Královo Pole, Technická 3082/12",
        sim1BtsDistanceM = 185.4,
        sim1BtsAzimuthDeg = 42.0f,
        sim2BtsLocation = "Brno - Kolejní, VUT",
        sim2BtsDistanceM = 410.0,
        sim2BtsAzimuthDeg = 120.0f
    )

    @Test
    fun testExporterCsvIncludesSim2FieldsWaypointsAndNoFixDash() {
        val session = sampleSession()
        val records = listOf(
            sampleRecord(id = 1L, satsUsed = 12, pdop = 1.2f, waypoint = "Bazar/Bridge"),
            sampleRecord(id = 2L, satsUsed = 2, pdop = 9.9f, waypoint = null)
        )
        val csv = Exporter.exportToCsv(session, records)
        assertTrue(csv.contains("Sim2TAC,Sim2CellId,Sim2eNodeB_gNodeB,Sim2Sector,Sim2PCI,Sim2ARFCN,Sim2Band"))
        assertTrue(csv.contains("WaypointNote"))
        assertTrue(csv.contains("\"Bazar/Bridge\""))
        assertTrue(csv.contains("\"25400\""))
        assertTrue(csv.contains(",-,-,-,")) // no-fix DOP turns into '-'
        assertTrue(csv.contains("UniqueSatsInView,UniqueSatsUsed,UsedSatellites,VisibleSatellites,Sim1CA,Sim2CA"))
        assertTrue(csv.contains("G10(L1/L5@34); E03(E1/E5a@29)"))
        assertTrue(csv.contains("G02(L1@33); G10(L1/L5@34); E03(E1/E5a@29)"))
        assertTrue(csv.contains("PCC: B20 • 806.0 MHz • 10 MHz + SCC 1: B3 • 1845.0 MHz • 20 MHz"))
    }

    @Test
    fun testExporterKmlGpxAndGeoJson() {
        val session = sampleSession()
        val records = listOf(
            sampleRecord(id = 1L, lat = 49.2268, lon = 16.5754, sim1Rsrp = -80, waypoint = "Start Gate"),
            sampleRecord(id = 2L, lat = 49.2275, lon = 16.5762, sim1Rsrp = -112, waypoint = null)
        )

        val kml = Exporter.exportToKml(session, records)
        assertTrue(kml.contains("<Style id=\"rsrpStrong\">"))
        assertTrue(kml.contains("<Style id=\"rsrpWeak\">"))
        assertTrue(kml.contains("<name>Start Gate</name>"))

        val gpx = Exporter.exportToGpx(session, records)
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("<wpt lat=\"49.2268\" lon=\"16.5754\">"))
        assertTrue(gpx.contains("<sim2_rsrp>-91</sim2_rsrp>"))
        assertTrue(gpx.contains("<sats_in_view>24</sats_in_view>"))
        assertTrue(gpx.contains("<used_satellites>G10(L1/L5@34); E03(E1/E5a@29)</used_satellites>"))
        assertTrue(gpx.contains("<visible_satellites>G02(L1@33); G10(L1/L5@34); E03(E1/E5a@29)</visible_satellites>"))

        val geoJson = Exporter.exportToGeoJson(session, records)
        assertTrue(geoJson.contains("\"type\": \"FeatureCollection\""))
        assertTrue(geoJson.contains("\"waypointNote\": \"Start Gate\""))
        assertTrue(geoJson.contains("\"sim2Rsrp\": -91"))
        assertTrue(geoJson.contains("\"satellitesInView\": 24"))
        assertTrue(geoJson.contains("\"uniqueSatellitesInView\": 18"))
        assertTrue(geoJson.contains("\"usedSatellites\": \"G10(L1/L5@34); E03(E1/E5a@29)\""))
        assertTrue(geoJson.contains("\"visibleSatellites\": \"G02(L1@33); G10(L1/L5@34); E03(E1/E5a@29)\""))
    }

    @Test
    fun testRoomDatabaseInsertAndSessionProgress() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.telemetryDao()
            val sessionId = dao.insertSession(sampleSession().copy(id = 0L))
            dao.insertRecord(sampleRecord(id = 0L, waypoint = "Check 1").copy(sessionId = sessionId))
            dao.updateSessionProgress(
                sessionId = sessionId,
                endTimeMs = 1700000010000L,
                pointCount = 1,
                distanceMeters = 123.4,
                maxSpeedKmh = 45.0
            )

            val loadedSession = dao.getLatestSession()
            assertNotNull(loadedSession)
            assertEquals(1, loadedSession!!.pointCount)
            assertEquals(123.4, loadedSession.distanceMeters, 0.01)

            val loadedRecords = dao.getRecordsListForSession(sessionId)
            assertEquals(1, loadedRecords.size)
            assertEquals("Check 1", loadedRecords[0].waypointNote)
            assertEquals(412, loadedRecords[0].sim2Pci)
            assertEquals(18, loadedRecords[0].uniqueSatellitesInView)
            assertEquals("G10(L1/L5@34); E03(E1/E5a@29)", loadedRecords[0].usedSatellitesDetail)
            assertEquals("G02(L1@33); G10(L1/L5@34); E03(E1/E5a@29)", loadedRecords[0].visibleSatellitesDetail)
        } finally {
            db.close()
        }
    }

    @Test
    fun testLiveMapTrailDeduplicationAndSimCaBandwidth() {
        TelemetryLoggingService.clearLiveMapTrail()
        assertEquals(0, TelemetryLoggingService.liveMapTrail.value.size)

        // Add first point (Brno VUT)
        TelemetryLoggingService.addMapTrailPoint(
            MapTrailPoint(49.226800, 16.575400, -85, -92, 30f, 18, 30, 1.1f, 1000L)
        )
        // Add jitter point < 1.5m away (should be deduplicated)
        TelemetryLoggingService.addMapTrailPoint(
            MapTrailPoint(49.226803, 16.575403, -86, -92, 30f, 18, 30, 1.1f, 2000L)
        )
        assertEquals(1, TelemetryLoggingService.liveMapTrail.value.size)

        // Add point ~15m away (should be added)
        TelemetryLoggingService.addMapTrailPoint(
            MapTrailPoint(49.226950, 16.575400, -98, -89, 32f, 16, 28, 1.3f, 3000L)
        )
        assertEquals(2, TelemetryLoggingService.liveMapTrail.value.size)

        val sim = SimCellInfo(
            channelBandwidthKhz = 20_000,
            totalAggregatedBandwidthKhz = 60_000,
            carrierAggregationSummary = "CA 3CC (60 MHz)",
            aggregatedCarriers = listOf("PCC: B20 • 806.0 MHz • 10 MHz", "SCC 1: B3 • 1845.0 MHz • 20 MHz")
        )
        assertEquals(20_000, sim.channelBandwidthKhz)
        assertEquals(60_000, sim.totalAggregatedBandwidthKhz)
        assertEquals(2, sim.aggregatedCarriers.size)
        assertEquals("800 MHz", BandMapper.lteBandToFreqLabel(20))
        assertEquals("3500 MHz C-Band", BandMapper.nrBandToFreqLabel(78))
        TelemetryLoggingService.clearLiveMapTrail()
    }

    @Test
    fun test5gNsaVsSaClassificationAndNrBands() {
        // 1. Plain 4G LTE without any NR indication
        val plainLte = com.example.telemetry.CellularTelemetryEngine.classifyCellularGeneration(
            baseTech = "4G LTE",
            overrideNetworkType = 0,
            isNrConnectedByServiceState = false,
            isNrNotRestrictedByServiceState = false,
            hasNrSecondaryCell = false,
            hasNrPhysicalChannel = false,
            hasNrSignalStrength = false,
            isDataNetworkNr = false
        )
        assertEquals("4G LTE", plainLte.first)
        assertEquals(null, plainLte.second)

        // 2. 5G NSA in idle on LTE Anchor (status bar shows 5G via TelephonyDisplayInfo or NR_STATE_NOT_RESTRICTED)
        val nsaAnchor = com.example.telemetry.CellularTelemetryEngine.classifyCellularGeneration(
            baseTech = "4G LTE",
            overrideNetworkType = 3, // OVERRIDE_NETWORK_TYPE_NR_NSA
            isNrConnectedByServiceState = false,
            isNrNotRestrictedByServiceState = true,
            hasNrSecondaryCell = false,
            hasNrPhysicalChannel = false,
            hasNrSignalStrength = false,
            isDataNetworkNr = false
        )
        assertEquals("5G NSA", nsaAnchor.first)
        assertEquals("EN-DC LTE Kotva (Idle)", nsaAnchor.second)

        // 3. 5G NSA with active NR SCG (EN-DC connected / CellSignalStrengthNr / PhysicalChannelConfig NR)
        val nsaActive = com.example.telemetry.CellularTelemetryEngine.classifyCellularGeneration(
            baseTech = "4G LTE",
            overrideNetworkType = 3,
            isNrConnectedByServiceState = true,
            isNrNotRestrictedByServiceState = true,
            hasNrSecondaryCell = true,
            hasNrPhysicalChannel = true,
            hasNrSignalStrength = true,
            isDataNetworkNr = false
        )
        assertEquals("5G NSA", nsaActive.first)
        assertEquals("EN-DC NR Aktivní", nsaActive.second)

        // 4. Future 5G SA (primary serving CellInfoNr without NR_NSA override)
        val sa5g = com.example.telemetry.CellularTelemetryEngine.classifyCellularGeneration(
            baseTech = "5G SA",
            overrideNetworkType = 0,
            isNrConnectedByServiceState = true,
            isNrNotRestrictedByServiceState = false,
            hasNrSecondaryCell = false,
            hasNrPhysicalChannel = true,
            hasNrSignalStrength = true,
            isDataNetworkNr = true
        )
        assertEquals("5G SA", sa5g.first)
        assertEquals("5G Standalone (SA)", sa5g.second)

        // 5. Verify European 5G NR bands don't shadow each other
        assertTrue(BandMapper.nrArfcnToBand(155000).startsWith("n28"))
        assertTrue(BandMapper.nrArfcnToBand(160000).startsWith("n20"))
        assertTrue(BandMapper.nrArfcnToBand(426000).startsWith("n1"))
        assertTrue(BandMapper.nrArfcnToBand(636000).startsWith("n78"))
    }

    @Test
    fun testGnssEngineResetAndFixTimeoutClearsStaleDemoOrOutdoorSpeed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engine = GnssTelemetryEngine(context, scope)

        // Simulate stale state (e.g. Demo Mode 44 km/h fix)
        engine.updateDemoState(
            GnssState(
                hasFix = true,
                latitude = 49.226756,
                longitude = 16.571020,
                speedKmh = 44.0f,
                speedMs = 12.22f,
                satellitesInView = 44,
                satellitesUsedInFix = 0
            )
        )
        assertTrue(engine.gnssState.value.hasFix)
        assertEquals(44.0f, engine.gnssState.value.speedKmh, 0.01f)

        // Check fix timeout immediately clears stale hasFix and speedKmh when lastFixElapsedMs == 0
        engine.checkFixTimeout()
        assertFalse(engine.gnssState.value.hasFix)
        assertEquals(0f, engine.gnssState.value.speedKmh, 0.01f)

        // Reset state completely clears coordinates and counts when leaving Demo mode
        engine.updateDemoState(GnssState(hasFix = true, latitude = 49.226756, longitude = 16.571020, speedKmh = 44.0f))
        engine.resetState()
        assertFalse(engine.gnssState.value.hasFix)
        assertEquals(0.0, engine.gnssState.value.latitude, 0.0001)
        assertEquals(0.0, engine.gnssState.value.longitude, 0.0001)
        assertEquals(0f, engine.gnssState.value.speedKmh, 0.01f)
    }

    @Test
    fun testDemoSimulationGeneratesCaCarriersAndValidTelemetry() {
        val demo = DemoSimulationEngine()
        val (gnss, cellular, _) = demo.nextSimulationStep(taMultiplierMode = 2)
        assertTrue(gnss.hasFix)
        assertTrue(gnss.latitude in 49.0..49.5)
        assertTrue(gnss.uniqueSatellitesInView > 0)
        assertTrue(gnss.satellitesInView >= gnss.uniqueSatellitesInView)
        assertTrue(cellular.sim1.aggregatedCarriers.isNotEmpty())
        assertTrue(cellular.sim2.aggregatedCarriers.any { it.contains("n78") })
        assertEquals("5G NSA", cellular.sim2.networkGen)
    }

    @Test
    fun testSkyPlotGreenGlowAndAboutGithubLinkUi() {
        val sampleSats = listOf(
            SatelliteInfo(
                svid = 10,
                constellationType = 1,
                constellationName = "GPS",
                cn0DbHz = 38.0f,
                elevationDegrees = 45f,
                azimuthDegrees = 120f,
                hasCarrierFrequency = true,
                carrierFrequencyHz = 1176450000f,
                bandLabel = "L5 (1176.45 MHz)",
                usedInFix = true
            )
        )

        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SkyPlotView(satellites = sampleSats, lang = "CS")
            }
        }
        composeTestRule.onNodeWithText("Plná barva = Ve fixu").assertIsDisplayed()
        composeTestRule.onNodeWithText("Barevný kroužek = Mimo fix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zelená záře okolo = Pásmo L5/E5a/B2a").assertIsDisplayed()
    }

    @Test
    fun testAboutDialogShowsRelease18AndGithubRepoLink() {
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                AboutAndHelpDialog(lang = "CS", onDismiss = {})
            }
        }
        composeTestRule.onNodeWithText("Release v1.8").assertIsDisplayed()
        composeTestRule.onNodeWithTag("author_github_link").assertIsDisplayed()
        composeTestRule.onNodeWithText("GitHub: https://github.com/FreshSPK/GNSS-and-Net-Logger").assertIsDisplayed()
    }

    @Test
    fun testOverviewTabNoFixDoesNotShowGnssFixOrStaleSpeed() {
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                OverviewTab(
                    gnss = GnssState(
                        hasFix = false,
                        speedKmh = 44.0f,
                        satellitesInView = 44,
                        satellitesUsedInFix = 0,
                        uniqueSatellitesInView = 44,
                        uniqueSatellitesUsedInFix = 0
                    ),
                    cellular = CellularState(),
                    env = EnvironmentState(battery = BatteryState()),
                    isLogging = false,
                    isPaused = false,
                    loggedPoints = 0,
                    fieldsSelection = LoggedFieldsSelection(),
                    lang = "CS",
                    onStartLogging = {},
                    onPauseLogging = {},
                    onResumeLogging = {},
                    onStopLogging = {},
                    onUpdateFieldsSelection = {}
                )
            }
        }
        composeTestRule.onNodeWithText("Bez GNSS Fixu (Vyhledávání...)").assertIsDisplayed()
        composeTestRule.onNodeWithText("44.0 km/h").assertDoesNotExist()
    }

    @Test
    fun testHistoryInspectorNumbersFromOneAndHidesSatDetails() {
        val records = listOf(sampleRecord(id = 15247L, satsUsed = 12, pdop = 0.65f))
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SessionDetailInspector(
                    session = sampleSession(),
                    records = records,
                    lang = "CS",
                    onBack = {},
                    onExportCsv = {},
                    onOpenCharts = {},
                    onDeletePoint = {}
                )
            }
        }
        composeTestRule.onNodeWithText("#1 •", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("#15247", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Sats: 12/24 (12/18 sat.)").assertIsDisplayed()
        composeTestRule.onNodeWithText("S1: Vodafone CZ (4G LTE) -82 dBm", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("S2: T-Mobile CZ (5G NSA) -91 dBm", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Použité:", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Viditelné:", substring = true).assertDoesNotExist()
    }

    @Test
    fun testBtsGeoUtilsCalculationsAndNormalization() {
        val dist = BtsGeoUtils.haversineDistanceMeters(49.2266, 16.5746, 49.22695, 16.57545)
        assertTrue(dist in 55.0..90.0)

        val bearing = BtsGeoUtils.bearingDegrees(49.2266, 16.5746, 49.22695, 16.57545)
        assertTrue(bearing in 30f..85f)
        assertEquals("SV", BtsGeoUtils.bearingToCardinal(45f, isCs = true))
        assertEquals("NE", BtsGeoUtils.bearingToCardinal(45f, isCs = false))

        val fspl = BtsGeoUtils.calculateFsplDb(2655.0, 1000.0)
        assertNotNull(fspl)
        assertEquals(100.93, fspl!!, 0.2)

        assertEquals("23001", BtsGeoUtils.normalizeCzechOperatorPlmn("T-Mobile CZ"))
        assertEquals("23002", BtsGeoUtils.normalizeCzechOperatorPlmn("23002"))
        assertEquals("23003", BtsGeoUtils.normalizeCzechOperatorPlmn("Vodafone"))
        assertEquals("23002", BtsGeoUtils.mocnPartnerPlmn("23001"))
        assertEquals("23001", BtsGeoUtils.mocnPartnerPlmn("23002"))

        val cleaned = BtsGeoUtils.cleanLocationForGeocoding("Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL")
        assertEquals("Brno - Královo Pole, Technická 3082/12, Czechia", cleaned)
    }

    @Test
    fun testBtsCsvImporterGsmwebCp1250AndPreservesCoords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val btsDao = db.btsDao()
            val cp1250 = Charset.forName("windows-1250")
            val gsmwebSample = """
                CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni
                568853;21;24656;2600;3391;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL
                568854;22;24656;1800;3392;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL
                ;;;;;;;
            """.trimIndent()

            val res1 = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(gsmwebSample.toByteArray(cp1250)),
                btsDao = btsDao,
                defaultOperatorPlmn = "23001",
                fileNameHint = "tlte.csv",
                replaceOperator = false
            )
            assertEquals(2, res1.importedCount)

            val saved = btsDao.findByPlmnAndCellId("23001", 568853L)
            assertNotNull(saved)
            assertEquals(2222L, saved!!.enodebId)
            assertEquals(21, saved.sectorId)
            assertEquals("BM", saved.districtCode)
            assertTrue(saved.locationName.contains("Královo Pole"))

            // Save GPS coordinates manually and verify subsequent GSMweb re-import preserves them
            btsDao.updateTowerCoordinates("23001", 568853L, 49.22695, 16.57545, "MANUAL")
            BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(gsmwebSample.toByteArray(cp1250)),
                btsDao = btsDao,
                defaultOperatorPlmn = "23001",
                fileNameHint = "tlte.csv",
                replaceOperator = false
            )
            val reimported = btsDao.findByPlmnAndCellId("23001", 568853L)
            assertEquals(49.22695, reimported?.latitude ?: 0.0, 0.0001)
            assertEquals(16.57545, reimported?.longitude ?: 0.0, 0.0001)
            assertEquals("MANUAL", reimported?.coordSource)

            // Also verify OpenCelliD MCC 230 filtering & CLF format
            val openCellCsv = """
                radio,mcc,net,area,cell,unit,lon,lat,range
                LTE,230,3,38003,827147,260,16.5712,49.2281,1000
                LTE,262,1,12345,999999,10,13.4000,52.5000,1000
            """.trimIndent()
            val ocRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(openCellCsv.toByteArray()),
                btsDao = btsDao
            )
            assertEquals(1, ocRes.importedCount)
            assertEquals(1, ocRes.skippedCount)
            val vfTower = btsDao.findByPlmnAndCellId("23003", 827147L)
            assertNotNull(vfTower)
            assertEquals(49.2281, vfTower?.latitude ?: 0.0, 0.0001)
        } finally {
            db.close()
        }
    }

    @Test
    fun testBtsRepositoryMatchingMocnColocatedAndDemoFekt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.btsDao()
            val repo = BtsRepository(context, dao)
            val gsmweb = """
                CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni
                568853;21;24656;2600;3391;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL
                568833;21;24656;1800;3391;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL
            """.trimIndent()
            BtsCsvImporter.importStream(ByteArrayInputStream(gsmweb.toByteArray()), dao, "23001", "tlte.csv")

            val sim = SimCellInfo(
                isServing = true,
                networkGen = "4G LTE",
                plmn = "23001",
                operatorAlpha = "T-Mobile CZ",
                ci = "568853",
                enbOrGnbId = "2222",
                sectorId = "21",
                pci = 99, // intentional mismatch to test pciMismatch detection
                tac = "24656",
                dlFreqMhz = 2655.0,
                estimatedDistanceM = 312.5
            )

            val match = repo.matchSimCell(
                sim = sim,
                ueLat = 49.2266,
                ueLon = 16.5746,
                hasGnssFix = true,
                isDemoMode = true
            )
            assertNotNull(match)
            assertEquals("DEMO_FEKT", match!!.coordSource)
            assertTrue(match.pciMismatch)
            assertFalse(match.tacMismatch)
            assertTrue(match.coLocatedBands.contains("1800"))
            assertTrue(match.coLocatedBands.contains("2600"))
            assertTrue((match.distanceMeters ?: 0.0) in 50.0..100.0)
            assertNotNull(match.azimuthDeg)
            assertNotNull(match.taDeltaMeters)
            assertNotNull(match.fsplDb)

            // MOCN lookup: O2 SIM ("23002") matching T-Mobile cell ("23001")
            val mocnMatch = repo.matchSimCell(
                sim = sim.copy(plmn = "23002", operatorAlpha = "O2 - CZ", pci = 21),
                ueLat = 49.2266,
                ueLon = 16.5746,
                hasGnssFix = true,
                isDemoMode = true
            )
            assertNotNull(mocnMatch)
            assertTrue(mocnMatch!!.isMocnShared)
            assertEquals("MOCN", mocnMatch.matchType)
            assertFalse(mocnMatch.pciMismatch)
        } finally {
            db.close()
        }
    }

    @Test
    fun testExporterIncludesV15BtsFields() {
        val session = sampleSession()
        val records = listOf(sampleRecord(id = 1L))
        val csv = Exporter.exportToCsv(session, records)
        assertTrue(csv.contains("Sim1BtsLocation,Sim1BtsDistance_m,Sim1BtsAzimuth_deg,Sim2BtsLocation,Sim2BtsDistance_m,Sim2BtsAzimuth_deg"))
        assertTrue(csv.contains("Brno - Královo Pole, Technická 3082/12"))

        val gpx = Exporter.exportToGpx(session, records)
        assertTrue(gpx.contains("<sim1_bts_location>Brno - Královo Pole, Technická 3082/12</sim1_bts_location>"))
        assertTrue(gpx.contains("<sim1_bts_distance_m>185.4</sim1_bts_distance_m>"))

        val geoJson = Exporter.exportToGeoJson(session, records)
        assertTrue(geoJson.contains("\"sim1BtsLocation\": \"Brno - Královo Pole, Technická 3082/12\""))
        assertTrue(geoJson.contains("\"sim1BtsDistanceM\": 185.4"))
    }

    @Test
    fun testOpenCellIdGzipAndBidirectionalGsmwebMerge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.btsDao()

            // Headerless OpenCelliD .csv.gz (MCC 230 Czech operators + foreign row skipped)
            val openCellRows = """
                LTE,230,1,24656,568853,0,16.57545,49.22695,800,12,1,1600000000,1700000000,-85
                NR,230,2,38002,990110,120,16.57120,49.22810,1200,5,1,1600000000,1700000000,-90
                LTE,262,1,1000,123456,1,13.4000,52.5000,500,1,1,1600000000,1700000000,-80
            """.trimIndent()
            val gzOut = ByteArrayOutputStream()
            GZIPOutputStream(gzOut).use { it.write(openCellRows.toByteArray()) }

            val gzRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(gzOut.toByteArray()),
                btsDao = dao,
                fileNameHint = "230.csv.gz"
            )
            assertEquals(2, gzRes.importedCount)
            assertEquals(2, gzRes.withCoordsCount)
            assertEquals(1, gzRes.skippedCount)

            // Order 1: OpenCelliD imported first (has GPS), then GSMweb imported (no GPS, rich address + band + PCI)
            val gsmweb = """
                CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni
                568853;21;24656;2600;3391;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12
                777111;55;25400;1800;1122;01.05.2020;BM;Brno - Komín, Hlavní 10
            """.trimIndent()
            BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(gsmweb.toByteArray()),
                btsDao = dao,
                defaultOperatorPlmn = "23001",
                fileNameHint = "tlte.csv"
            )

            val mergedOrder1 = dao.findByPlmnAndCellId("23001", 568853L)!!
            assertEquals(49.22695, mergedOrder1.latitude!!, 0.0001)
            assertEquals("OPENCELLID", mergedOrder1.coordSource)
            assertEquals("Brno - Královo Pole, Technická 3082/12", mergedOrder1.locationName)
            assertEquals("2600", mergedOrder1.band)
            assertEquals(21, mergedOrder1.pci)
            assertEquals("BM", mergedOrder1.districtCode)

            // Order 2: GSMweb row 777111 existed WITHOUT GPS first; now OpenCelliD arrives WITH GPS
            val openCellPatch = """
                radio,mcc,net,area,cell,unit,lon,lat,range
                LTE,230,1,25400,777111,0,16.55555,49.22111,900
            """.trimIndent()
            BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(openCellPatch.toByteArray()),
                btsDao = dao,
                fileNameHint = "230.csv"
            )

            val mergedOrder2 = dao.findByPlmnAndCellId("23001", 777111L)!!
            assertEquals(49.22111, mergedOrder2.latitude!!, 0.0001)
            assertEquals(16.55555, mergedOrder2.longitude!!, 0.0001)
            assertEquals("OPENCELLID", mergedOrder2.coordSource)
            // GSMweb rich metadata MUST survive OpenCelliD upsert
            assertEquals("Brno - Komín, Hlavní 10", mergedOrder2.locationName)
            assertEquals("1800", mergedOrder2.band)
            assertEquals(55, mergedOrder2.pci)
            assertEquals("BM", mergedOrder2.districtCode)
        } finally {
            db.close()
        }
    }

    @Test
    fun testFindSiteCellsSettingsSlidersAndOperatorLabels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.btsDao()
            val csv = """
                CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni
                155161857;287;24656;1800;3297;13.12.2018;BM;Brno - Purkyňova 2850/105
                155161858;288;24656;800;3298;13.12.2018;BM;Brno - Purkyňova 2850/105
                155161859;289;24656;2600;3299;13.12.2018;BM;Brno - Purkyňova 2850/105
            """.trimIndent()
            BtsCsvImporter.importStream(ByteArrayInputStream(csv.toByteArray()), dao, "23001", "tlte.csv")

            val enb = 155161857L / 256L
            val siteCells = dao.findSiteCells("23001", enb, 155161857L)
            assertEquals(3, siteCells.size)
            assertTrue(siteCells.any { it.band == "1800" })
            assertTrue(siteCells.any { it.band == "800" })
            assertTrue(siteCells.any { it.band == "2600" })
        } finally {
            db.close()
        }

        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                CellularRfTab(
                    cellular = CellularState(
                        sim1 = SimCellInfo(slotIndex = 0, displayName = "Vodafone CZ", plmn = "23003"),
                        sim2 = SimCellInfo(slotIndex = 1, displayName = "T-Mobile CZ", plmn = "23001", isDataActive = true)
                    ),
                    lang = "CS"
                )
            }
        }
        composeTestRule.onNodeWithText("S1: Vodafone").assertIsDisplayed()
        composeTestRule.onNodeWithText("S2: T-Mobile").assertIsDisplayed()
        composeTestRule.onNodeWithText("Vodafone C", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("T-Mobile C", substring = true).assertDoesNotExist()
    }

    @Test
    fun testSettingsDialogShowsTimeAndDistanceSlidersWithAutoPreset() {
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SettingsDialog(
                    settings = com.example.model.LoggingSettings(
                        triggerMode = com.example.model.LogTriggerMode.HYBRID,
                        timeIntervalSeconds = 10,
                        distanceThresholdMeters = 50f
                    ),
                    lang = "CS",
                    onDismiss = {},
                    onSaveSettings = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("time_interval_slider").assertExists()
        composeTestRule.onNodeWithTag("distance_step_slider").assertExists()
        assertTrue(composeTestRule.onAllNodesWithText("10s").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("50m").fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun testSecurityTabDisplaysChecksClocksAndPhysicsSubTabs() {
        val sampleState = com.example.security.SecurityAnalyzer.evaluate(
            gnssInput = GnssState(hasFix = true, satellitesInView = 8, satellitesUsedInFix = 6),
            cellularInput = CellularState(),
            timeDiagInput = com.example.model.TimeSyncDiagnostics(
                ntpTimeMs = 1700000000000L,
                ntpServer = "time.google.com",
                ntpRttMs = 18L,
                ntpStratum = 1,
                systemTimeMs = 1700000000010L,
                gnssFixTimeMs = 1700000000005L
            ),
            ctx = com.example.security.SecurityContextSnapshot()
        )
        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                SecurityTab(
                    security = sampleState,
                    isDemoMode = true,
                    lang = "CS",
                    onSyncNtpNow = {},
                    onRunDeepScan = {},
                    onClearIncidents = {},
                    onSelectSimulatedAttack = {}
                )
            }
        }
        composeTestRule.onNodeWithText("● DETEKCE PODVODŮ & RUŠENÍ").assertIsDisplayed()
        composeTestRule.onNodeWithTag("security_deep_scan_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Čas UTC").performScrollTo().performClick()
        composeTestRule.onNodeWithText("SROVNÁNÍ PŘESNÉHO ČASU (UTC)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Fyzika TA").performScrollTo().performClick()
        composeTestRule.onNodeWithText("FYZIKA SIGNÁLU GNSS & FÚZE SENZORŮ").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testAboutDialogShowsPdfManualButtonsAndLocalizedFilenames() {
        assertEquals(
            "GNSS_Net_Logger_v1.8_Manual_CS.pdf",
            com.example.export.UserManualPdfGenerator.manualFilename(isCs = true)
        )
        assertEquals(
            "GNSS_Net_Logger_v1.8_Manual_EN.pdf",
            com.example.export.UserManualPdfGenerator.manualFilename(isCs = false)
        )

        composeTestRule.setContent {
            SignalLoggerTheme(darkTheme = true) {
                AboutAndHelpDialog(lang = "CS", onDismiss = {})
            }
        }
        composeTestRule.onNodeWithTag("download_pdf_manual_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_pdf_manual_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Uživatelský & technický manuál (PDF)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testV18RuianAndBeaconDbParsersAndAddressCleaning() {
        val queries = BtsGeoUtils.buildRuianAddressQueries(
            "Brno - Královo Pole, Technická 3082/12, indoor v laboratoři FEKT VUT UREL"
        )
        assertTrue(queries.any { it == "Technická 3082/12, Brno" })
        assertTrue(queries.any { it == "Brno - Královo Pole, Technická 3082/12" })
        assertTrue(BtsGeoUtils.hasStreetOrNumberHint("Brno - Královo Pole, Technická 3082/12, indoor FEKT"))
        assertFalse(BtsGeoUtils.hasStreetOrNumberHint("Mikulov, k.ú. Mušlov, příhradový stožár v lese"))

        val ruianJson = """
            {
              "spatialReference": { "wkid": 4326 },
              "candidates": [
                { "address": "Technická 3082/12, Královo Pole, 616 00 Brno", "location": { "x": 16.57545, "y": 49.22695 }, "score": 100.0 },
                { "address": "Technická, Brno", "location": { "x": 16.58000, "y": 49.22500 }, "score": 60.0 }
              ]
            }
        """.trimIndent()
        val ruian = BtsGeocoder.parseCuZkRuianResponse(ruianJson)
        assertNotNull(ruian)
        assertEquals("RUIAN", ruian!!.source)
        assertEquals(49.22695, ruian.latitude, 0.0001)
        assertEquals(16.57545, ruian.longitude, 0.0001)

        val beaconJson = """{ "location": { "lat": 49.2268, "lng": 16.5752 }, "accuracy": 420.0 }"""
        val beacon = BtsGeocoder.parseBeaconDbResponse(beaconJson)
        assertNotNull(beacon)
        assertEquals("BEACONDB", beacon!!.source)
        assertEquals("±420m", beacon.detail)

        // Coarse > 5km country-level accuracy must be rejected
        val tooCoarse = """{ "location": { "lat": 49.8, "lng": 15.5 }, "accuracy": 25000.0 }"""
        assertEquals(null, BtsGeocoder.parseBeaconDbResponse(tooCoarse))
    }

    @Test
    fun testV18MultiFormatImportPriorityNtmClfCellMapperKmlZipAndEnodebMatch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.btsDao()
            val repo = BtsRepository(context, dao)

            // 1. Multi-file ZIP archive: GSMweb CSV + CLF v3.0 with // comment header
            val gsmCsv = """
                CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni
                568853;21;24656;2600;3391;25.04.2019;BM;Brno - Královo Pole, Technická 3082/12
            """.trimIndent()
            val clfWithComment = """
                //CLF v3.0 exported from GSMweb
                23001;568853;24656;2222;49.22695;16.57545;0;Brno FEKT mast
            """.trimIndent()

            val zipOut = ByteArrayOutputStream()
            ZipOutputStream(zipOut).use { zos ->
                zos.putNextEntry(ZipEntry("tlte.csv"))
                zos.write(gsmCsv.toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("23001.clf"))
                zos.write(clfWithComment.toByteArray())
                zos.closeEntry()
            }
            val zipRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(zipOut.toByteArray()),
                btsDao = dao,
                fileNameHint = "cz_bts_bundle.zip"
            )
            assertEquals(2, zipRes.importedCount)
            assertTrue(zipRes.detectedFormat.startsWith("ZIP"))

            val clfMerged = dao.findByPlmnAndCellId("23001", 568853L)!!
            assertEquals("CLF", clfMerged.coordSource)
            assertEquals(49.22695, clfMerged.latitude!!, 0.0001)
            assertEquals(21, clfMerged.pci)

            // 2. Coarse OpenCelliD centroid MUST NOT overwrite exact CLF mast coordinates!
            val coarseOpenCell = """
                LTE,230,1,24656,568853,0,16.59999,49.24999,1500,3,1,1600000000,1700000000,-92
            """.trimIndent()
            BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(coarseOpenCell.toByteArray()),
                btsDao = dao,
                fileNameHint = "230.csv"
            )
            val afterOpenCell = dao.findByPlmnAndCellId("23001", 568853L)!!
            assertEquals("CLF", afterOpenCell.coordSource)
            assertEquals(49.22695, afterOpenCell.latitude!!, 0.0001)

            // 3. NetMonster .ntm import (both 9-column and 10-column forms)
            val ntmData = """
                4G;230;02;990209;38002;115;49.22810;16.57120;Brno Kolejni O2
                5G;230;03;827147;38003;3231;260;49.22450;16.57890;Brno Pod Palackeho vrchem
            """.trimIndent()
            val ntmRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(ntmData.toByteArray()),
                btsDao = dao,
                fileNameHint = "export.ntm"
            )
            assertEquals(2, ntmRes.importedCount)
            assertEquals(2, ntmRes.withCoordsCount)
            val o2Ntm = dao.findByPlmnAndCellId("23002", 990209L)!!
            assertEquals("NTM", o2Ntm.coordSource)
            assertEquals(115, o2Ntm.pci)
            val vfNtm = dao.findByPlmnAndCellId("23003", 827147L)!!
            assertEquals("NTM", vfNtm.coordSource)
            assertEquals(260, vfNtm.pci)

            // 4. CellMapper eNodeB-only Tower CSV + sibling sector matching in BtsRepository
            val cellMapperEnbCsv = """
                eNB,Lat,Lon,MCC,MNC,TAC,System,Name
                4444,49.21000,16.61000,230,03,38003,LTE,Brno Ponava CellMapper Pin
            """.trimIndent()
            val cmRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(cellMapperEnbCsv.toByteArray()),
                btsDao = dao,
                fileNameHint = "cellmapper_towers.csv"
            )
            assertEquals(1, cmRes.importedCount)

            // Connect to sector 12 of eNB 4444 (CI = 4444 * 256 + 12 = 1137676), which was NOT explicitly in CSV
            val enbSiblingMatch = repo.matchSimCell(
                sim = SimCellInfo(
                    isServing = true,
                    networkGen = "4G LTE",
                    plmn = "23003",
                    ci = ((4444L shl 8) + 12L).toString(),
                    pci = 333,
                    tac = "38003"
                ),
                ueLat = 49.212,
                ueLon = 16.612,
                hasGnssFix = true,
                isDemoMode = false
            )
            assertNotNull(enbSiblingMatch)
            assertEquals("eNB", enbSiblingMatch!!.matchType)
            assertEquals("CELLMAPPER", enbSiblingMatch.coordSource)
            assertEquals(49.21000, enbSiblingMatch.latitude!!, 0.0001)
            assertFalse(enbSiblingMatch.pciMismatch)

            // 5. KML Placemark import
            val kml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml><Document>
                  <Placemark>
                    <name>O2 eNB 7777 CellID 1990913</name>
                    <description>23002 Brno Kralovo Pole TAC 38010</description>
                    <Point><coordinates>16.58888,49.23333,0</coordinates></Point>
                  </Placemark>
                </Document></kml>
            """.trimIndent()
            val kmlRes = BtsCsvImporter.importStream(
                inputStream = ByteArrayInputStream(kml.toByteArray()),
                btsDao = dao,
                fileNameHint = "towers.kml"
            )
            assertEquals(1, kmlRes.importedCount)
            val kmlTower = dao.findByPlmnAndCellId("23002", 1990913L)!!
            assertEquals("KML", kmlTower.coordSource)
            assertEquals(49.23333, kmlTower.latitude!!, 0.0001)
        } finally {
            db.close()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun testBtsDatabaseDialogShowsMultiSourceImportAndRuianBeaconDbControls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = BtsRepository(context, db.btsDao())
            runBlocking { repo.importBundledSamples() }

            composeTestRule.setContent {
                SignalLoggerTheme(darkTheme = true) {
                    BtsDatabaseDialog(
                        btsRepository = repo,
                        lang = "CS",
                        onDismiss = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("bts_dialog_list")
                .performScrollToNode(hasTestTag("open_gsmweb_portal_button"))
            composeTestRule.onNodeWithTag("open_gsmweb_portal_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_opencellid_downloads_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("open_cellmapper_portal_button").assertIsDisplayed()
            composeTestRule.onNodeWithTag("bts_dialog_list")
                .performScrollToNode(hasTestTag("bts_import_file_button"))
            composeTestRule.onNodeWithTag("bts_import_file_button").assertIsDisplayed()
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                runCatching {
                    composeTestRule.onNodeWithTag("bts_dialog_list")
                        .performScrollToNode(hasTestTag("bts_batch_geocode_button"))
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithTag("bts_batch_geocode_button").assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
