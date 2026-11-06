package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_records",
    foreignKeys = [
        ForeignKey(
            entity = LogSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("timestampMs"),
        Index("latitude", "longitude")
    ]
)
data class TelemetryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,

    // GNSS Fields
    val latitude: Double,
    val longitude: Double,
    val altitudeMsl: Double,
    val altitudeWgs84: Double,
    val speedKmh: Float,
    val speedMs: Float,
    val bearingDeg: Float,
    val horizontalAccuracyM: Float,
    val verticalAccuracyM: Float,
    val satellitesInView: Int,
    val satellitesUsedInFix: Int,
    val pdop: Float,
    val hdop: Float,
    val vdop: Float,
    val dualFrequencyRatioPct: Float,
    val agcInterferenceScore: Float, // 0..100% relative or nominal
    val constellationBreakdown: String, // e.g. "GPS:8,GLO:5,GAL:6,BDS:7"

    // Cellular Slot 1
    val sim1Operator: String,
    val sim1NetworkGen: String, // 2G, 3G, 4G, 5G NSA, 5G SA
    val sim1TacLac: String,
    val sim1CellId: String,
    val sim1EnbOrGnb: String,
    val sim1SectorId: String,
    val sim1Pci: Int,
    val sim1Arfcn: Int,
    val sim1Band: String,
    val sim1Rsrp: Int?, // dBm
    val sim1Rsrq: Int?, // dB
    val sim1Rssi: Int?, // dBm
    val sim1Sinr: Float?, // dB
    val sim1SsRsrp: Int?,
    val sim1SsRsrq: Int?,
    val sim1SsSinr: Float?,
    val sim1CsiRsrp: Int?,
    val sim1CsiRsrq: Int?,
    val sim1CsiSinr: Float?,
    val sim1TimingAdvance: Int?,
    val sim1EstimatedDistanceM: Double?,

    // Cellular Slot 2
    val sim2Operator: String,
    val sim2NetworkGen: String,
    val sim2Band: String,
    val sim2Rsrp: Int?,
    val sim2Sinr: Float?,

    // Best Neighbor Cell & Delta
    val bestNeighborRsrp: Int?,
    val neighborServingDeltaDb: Int?,

    // Environmental / Network / Hardware
    val pressureHpa: Float?,
    val baroRelativeAltM: Float?,
    val seaLevelPressureHpa: Float? = null,
    val pingRttMs: Long?,
    val pingPacketLoss: Boolean,

    // Advanced Telemetry (v1.1)
    val sim1Plmn: String = "N/A",
    val sim2Plmn: String = "N/A",
    val batteryTempC: Float? = null,
    val batteryLevelPct: Int? = null,
    val nmeaSentence: String? = null,
    val rawDopplerHz: Float? = null
)
