package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Insert
    suspend fun insertSession(session: LogSession): Long

    @Update
    suspend fun updateSession(session: LogSession)

    @Query("UPDATE log_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun renameSession(sessionId: Long, newTitle: String)

    @Delete
    suspend fun deleteSession(session: LogSession)

    @Query("SELECT * FROM log_sessions ORDER BY startTimeMs DESC")
    fun getAllSessions(): Flow<List<LogSession>>

    @Query("SELECT * FROM log_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): LogSession?

    @Update
    suspend fun updateRecord(record: TelemetryRecord)

    @Delete
    suspend fun deleteRecord(record: TelemetryRecord)

    @Insert
    suspend fun insertRecord(record: TelemetryRecord): Long

    @Insert
    suspend fun insertRecords(records: List<TelemetryRecord>)

    @Query("SELECT * FROM telemetry_records WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getRecordsForSession(sessionId: Long): Flow<List<TelemetryRecord>>

    @Query("SELECT * FROM telemetry_records WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getRecordsListForSession(sessionId: Long): List<TelemetryRecord>

    @Query("SELECT COUNT(*) FROM telemetry_records WHERE sessionId = :sessionId")
    suspend fun getRecordCountForSession(sessionId: Long): Int

    @Query("DELETE FROM telemetry_records WHERE sessionId = :sessionId")
    suspend fun deleteRecordsForSession(sessionId: Long)

    @Query("""
        SELECT * FROM log_sessions 
        WHERE (:techFilter IS NULL OR primaryTech LIKE '%' || :techFilter || '%')
          AND (:operatorFilter IS NULL OR operatorSummary LIKE '%' || :operatorFilter || '%')
          AND (:startTime IS NULL OR startTimeMs >= :startTime)
          AND (:endTime IS NULL OR startTimeMs <= :endTime)
        ORDER BY startTimeMs DESC
    """)
    fun filterSessions(
        techFilter: String?,
        operatorFilter: String?,
        startTime: Long?,
        endTime: Long?
    ): Flow<List<LogSession>>
}
