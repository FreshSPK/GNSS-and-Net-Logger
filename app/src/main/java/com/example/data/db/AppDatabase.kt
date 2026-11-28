package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [LogSession::class, TelemetryRecord::class, BtsTowerEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao
    abstract fun btsDao(): BtsDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN uniqueSatellitesInView INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN uniqueSatellitesUsed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN usedSatellitesDetail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN visibleSatellitesDetail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1CaSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2CaSummary TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1BtsLocation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1BtsDistanceM REAL")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1BtsAzimuthDeg REAL")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2BtsLocation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2BtsDistanceM REAL")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim2BtsAzimuthDeg REAL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityRiskScore INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityGnssRisk INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityRfRisk INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityTimeRisk INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN gnssMinusNtpMs INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1TaVsBtsDeltaM REAL")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1RsrpResidualDb INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN sim1RssiRsrpGapDb INTEGER")
                db.execSQL("ALTER TABLE telemetry_records ADD COLUMN securityAlertsSummary TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_operatorPlmn_locationName` ON `bts_towers` (`operatorPlmn`, `locationName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bts_towers_locationName` ON `bts_towers` (`locationName`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "signal_gnss_logger.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
