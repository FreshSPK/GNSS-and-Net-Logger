package com.example.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "bts_towers",
    primaryKeys = ["operatorPlmn", "cellId"],
    indices = [
        Index("cellId"),
        Index("operatorPlmn", "enodebId"),
        Index("operatorPlmn", "pci", "tac"),
        Index("districtCode"),
        Index("latitude", "longitude"),
        Index("operatorPlmn", "locationName"),
        Index("locationName")
    ]
)
data class BtsTowerEntity(
    val operatorPlmn: String, // "23001" T-Mobile, "23002" O2, "23003" Vodafone
    val cellId: Long,
    val enodebId: Long = 0L,
    val sectorId: Int = 0,
    val pci: Int = -1,
    val tac: Int = 0,
    val band: String = "",
    val gsmCid: String = "",
    val updatedDate: String = "",
    val districtCode: String = "",
    val locationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val coordSource: String = "", // "", "IMPORT", "GEOCODED", "MANUAL"
    val importedAtMs: Long = System.currentTimeMillis()
)
