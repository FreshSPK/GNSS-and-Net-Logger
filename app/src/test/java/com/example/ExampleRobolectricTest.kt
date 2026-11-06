package com.example

import com.example.model.AnomalyType
import com.example.util.BandMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
    // 0 neighbors with strong RSRP -> Rogue BTS signature
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
}
