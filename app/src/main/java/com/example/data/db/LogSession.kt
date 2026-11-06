package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_sessions")
data class LogSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val pointCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val operatorSummary: String = "",
    val primaryBand: String = "",
    val primaryTech: String = "",
    val avgRsrp: Double? = null,
    val maxSpeedKmh: Double = 0.0,
    val notes: String = ""
)
