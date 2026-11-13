package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.LogSession
import com.example.data.db.TelemetryRecord
import com.example.export.Exporter
import com.example.model.AnomalyType
import com.example.model.MapTrailPoint
import com.example.model.SimCellInfo
import com.example.service.TelemetryLoggingService
import com.example.util.BandMapper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
        waypointNote = waypoint
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

        val geoJson = Exporter.exportToGeoJson(session, records)
        assertTrue(geoJson.contains("\"type\": \"FeatureCollection\""))
        assertTrue(geoJson.contains("\"waypointNote\": \"Start Gate\""))
        assertTrue(geoJson.contains("\"sim2Rsrp\": -91"))
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
            MapTrailPoint(49.226800, 16.575400, -85, -92, 30f, 18, 1.1f, 1000L)
        )
        // Add jitter point < 1.5m away (should be deduplicated)
        TelemetryLoggingService.addMapTrailPoint(
            MapTrailPoint(49.226803, 16.575403, -86, -92, 30f, 18, 1.1f, 2000L)
        )
        assertEquals(1, TelemetryLoggingService.liveMapTrail.value.size)

        // Add point ~15m away (should be added)
        TelemetryLoggingService.addMapTrailPoint(
            MapTrailPoint(49.226950, 16.575400, -98, -89, 32f, 16, 1.3f, 3000L)
        )
        assertEquals(2, TelemetryLoggingService.liveMapTrail.value.size)

        val sim = SimCellInfo(
            channelBandwidthKhz = 20_000,
            totalAggregatedBandwidthKhz = 60_000,
            carrierAggregationSummary = "CA 3CC (60 MHz)"
        )
        assertEquals(20_000, sim.channelBandwidthKhz)
        assertEquals(60_000, sim.totalAggregatedBandwidthKhz)
        TelemetryLoggingService.clearLiveMapTrail()
    }
}
