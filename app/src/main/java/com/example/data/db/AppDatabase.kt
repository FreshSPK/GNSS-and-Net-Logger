package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LogSession::class, TelemetryRecord::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2TacLac TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2CellId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2EnbOrGnb TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2SectorId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2Pci INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2Arfcn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2Rsrq INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2Rssi INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2TimingAdvance INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2EstimatedDistanceM REAL")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN waypointNote TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "signal_gnss_logger.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
