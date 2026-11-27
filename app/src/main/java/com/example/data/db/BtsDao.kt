package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.bts.BtsGeoUtils
import kotlinx.coroutines.flow.Flow

data class OperatorBtsStats(
    val operatorPlmn: String,
    val totalCount: Int,
    val locatedCount: Int
)

data class ExistingCoordRow(
    val cellId: Long,
    val pci: Int,
    val tac: Int,
    val band: String,
    val gsmCid: String,
    val updatedDate: String,
    val districtCode: String,
    val locationName: String,
    val latitude: Double?,
    val longitude: Double?,
    val coordSource: String
)

@Dao
interface BtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(towers: List<BtsTowerEntity>)

    @Query(
        "SELECT cellId, pci, tac, band, gsmCid, updatedDate, districtCode, locationName, latitude, longitude, coordSource " +
            "FROM bts_towers WHERE operatorPlmn = :plmn AND cellId IN (:cellIds)"
    )
    suspend fun getExistingMetaForBatch(plmn: String, cellIds: List<Long>): List<ExistingCoordRow>

    @Transaction
    suspend fun upsertPreservingCoords(plmn: String, towers: List<BtsTowerEntity>) {
        if (towers.isEmpty()) return
        val ids = towers.map { it.cellId }
        val existingMap = mutableMapOf<Long, ExistingCoordRow>()
        for (chunk in ids.chunked(450)) {
            getExistingMetaForBatch(plmn, chunk).forEach { row ->
                existingMap[row.cellId] = row
            }
        }
        val merged = towers.map { item ->
            val prev = existingMap[item.cellId]
            if (prev == null) {
                item
            } else {
                val itemHasCoords = BtsGeoUtils.isValidCzechCoord(item.latitude, item.longitude)
                val prevHasCoords = BtsGeoUtils.isValidCzechCoord(prev.latitude, prev.longitude)
                val useNewCoords = when {
                    !itemHasCoords -> false
                    !prevHasCoords -> true
                    else -> BtsGeoUtils.coordSourcePriority(item.coordSource) >= BtsGeoUtils.coordSourcePriority(prev.coordSource)
                }
                val lat = if (useNewCoords) item.latitude else prev.latitude
                val lon = if (useNewCoords) item.longitude else prev.longitude
                val coordSrc = if (useNewCoords) item.coordSource else prev.coordSource

                val isItemLocSynthetic = item.locationName.isBlank() ||
                    item.locationName.startsWith("OpenCelliD") ||
                    item.locationName.startsWith("CellMapper") ||
                    item.locationName.startsWith("BeaconDB")
                val isPrevLocRich = prev.locationName.isNotBlank() &&
                    !prev.locationName.startsWith("OpenCelliD") &&
                    !prev.locationName.startsWith("CellMapper") &&
                    !prev.locationName.startsWith("BeaconDB")
                val mergedLocation = when {
                    isItemLocSynthetic && isPrevLocRich -> prev.locationName
                    item.locationName.isBlank() -> prev.locationName
                    else -> item.locationName
                }

                val isItemBandNumeric = item.band.isNotEmpty() && item.band.all { it.isDigit() }
                val isPrevBandNumeric = prev.band.isNotEmpty() && prev.band.all { it.isDigit() }
                val mergedBand = when {
                    !isItemBandNumeric && isPrevBandNumeric -> prev.band
                    item.band.isBlank() -> prev.band
                    else -> item.band
                }

                item.copy(
                    pci = if (item.pci > 0) item.pci else prev.pci,
                    tac = if (item.tac > 0) item.tac else prev.tac,
                    band = mergedBand,
                    gsmCid = item.gsmCid.ifBlank { prev.gsmCid },
                    updatedDate = item.updatedDate.ifBlank { prev.updatedDate },
                    districtCode = item.districtCode.ifBlank { prev.districtCode },
                    locationName = mergedLocation,
                    latitude = lat,
                    longitude = lon,
                    coordSource = coordSrc
                )
            }
        }
        upsertBatch(merged)
    }

    /**
     * Applies eNodeB/gNodeB-level tower coordinates (e.g. from CellMapper eNB CSV exports).
     * Updates existing co-located cells only if the incoming source has higher/equal precision,
     * or inserts a site-anchor entry when no cells for that eNodeB exist yet.
     */
    @Transaction
    suspend fun applyEnodebTowersPreservingPriority(plmn: String, anchors: List<BtsTowerEntity>) {
        if (anchors.isEmpty()) return
        val toInsert = mutableListOf<BtsTowerEntity>()
        for (anchor in anchors) {
            if (anchor.enodebId <= 0L) continue
            val existing = findCoLocatedByEnodeb(plmn, anchor.enodebId)
            if (existing.isEmpty()) {
                toInsert.add(anchor)
            } else {
                val newPrio = BtsGeoUtils.coordSourcePriority(anchor.coordSource)
                for (row in existing) {
                    val prevHas = BtsGeoUtils.isValidCzechCoord(row.latitude, row.longitude)
                    val prevPrio = BtsGeoUtils.coordSourcePriority(row.coordSource)
                    if (!prevHas || newPrio >= prevPrio) {
                        toInsert.add(
                            row.copy(
                                latitude = anchor.latitude,
                                longitude = anchor.longitude,
                                coordSource = anchor.coordSource,
                                locationName = if (row.locationName.isBlank()) anchor.locationName else row.locationName,
                                band = if (row.band.isBlank()) anchor.band else row.band,
                                tac = if (row.tac <= 0 && anchor.tac > 0) anchor.tac else row.tac
                            )
                        )
                    }
                }
            }
        }
        if (toInsert.isNotEmpty()) {
            upsertBatch(toInsert)
        }
    }

    @Query("SELECT * FROM bts_towers WHERE operatorPlmn = :plmn AND cellId = :cellId LIMIT 1")
    suspend fun findByPlmnAndCellId(plmn: String, cellId: Long): BtsTowerEntity?

    @Query("SELECT * FROM bts_towers WHERE cellId = :cellId")
    suspend fun findByCellId(cellId: Long): List<BtsTowerEntity>

    @Query("SELECT * FROM bts_towers WHERE operatorPlmn = :plmn AND pci = :pci AND tac = :tac LIMIT 10")
    suspend fun findByPlmnPciTac(plmn: String, pci: Int, tac: Int): List<BtsTowerEntity>

    @Query("SELECT * FROM bts_towers WHERE operatorPlmn = :plmn AND enodebId = :enodebId ORDER BY band, sectorId")
    suspend fun findCoLocatedByEnodeb(plmn: String, enodebId: Long): List<BtsTowerEntity>

    @Query("SELECT * FROM bts_towers WHERE operatorPlmn = :plmn AND locationName = :locationName AND locationName != '' ORDER BY band, sectorId")
    suspend fun findCoLocatedByLocation(plmn: String, locationName: String): List<BtsTowerEntity>

    @Query(
        "SELECT * FROM bts_towers WHERE operatorPlmn = :plmn " +
            "AND ((:enodebId > 0 AND enodebId = :enodebId) OR cellId = :cellId) " +
            "ORDER BY band, sectorId"
    )
    suspend fun findSiteCells(plmn: String, enodebId: Long, cellId: Long): List<BtsTowerEntity>

    @Query(
        "SELECT * FROM bts_towers " +
            "WHERE latitude IS NOT NULL AND longitude IS NOT NULL " +
            "AND latitude BETWEEN :minLat AND :maxLat " +
            "AND longitude BETWEEN :minLon AND :maxLon " +
            "GROUP BY operatorPlmn, CASE WHEN enodebId > 0 THEN enodebId ELSE cellId END " +
            "ORDER BY ((latitude - :centerLat) * (latitude - :centerLat) + 0.42 * (longitude - :centerLon) * (longitude - :centerLon)) ASC " +
            "LIMIT :limit"
    )
    suspend fun findNearbyWithCoords(
        centerLat: Double,
        centerLon: Double,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        limit: Int = 220
    ): List<BtsTowerEntity>

    @Query(
        "SELECT operatorPlmn, COUNT(*) AS totalCount, " +
            "SUM(CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 1 ELSE 0 END) AS locatedCount " +
            "FROM bts_towers GROUP BY operatorPlmn"
    )
    fun observeOperatorStats(): Flow<List<OperatorBtsStats>>

    @Query("SELECT COUNT(*) FROM bts_towers")
    suspend fun getTotalCount(): Int

    @Query("SELECT DISTINCT districtCode FROM bts_towers WHERE districtCode != '' ORDER BY districtCode")
    fun observeDistrictCodes(): Flow<List<String>>

    @Query(
        "SELECT * FROM bts_towers " +
            "WHERE (:plmn IS NULL OR operatorPlmn = :plmn) " +
            "AND (:query = '' OR locationName LIKE '%' || :query || '%' " +
            "OR districtCode LIKE '%' || :query || '%' " +
            "OR coordSource LIKE '%' || :query || '%' " +
            "OR CAST(cellId AS TEXT) LIKE '%' || :query || '%' " +
            "OR CAST(enodebId AS TEXT) LIKE '%' || :query || '%' " +
            "OR CAST(pci AS TEXT) = :query) " +
            "ORDER BY CASE WHEN latitude IS NOT NULL THEN 0 ELSE 1 END, cellId ASC " +
            "LIMIT :limit"
    )
    suspend fun searchTowers(query: String, plmn: String?, limit: Int = 120): List<BtsTowerEntity>

    @Query(
        "SELECT * FROM bts_towers " +
            "WHERE districtCode = :districtCode " +
            "AND (:plmn IS NULL OR operatorPlmn = :plmn) " +
            "AND (latitude IS NULL OR longitude IS NULL) " +
            "GROUP BY operatorPlmn, CASE WHEN enodebId > 0 THEN enodebId WHEN locationName != '' THEN locationName ELSE CAST(cellId AS TEXT) END " +
            "LIMIT :limit"
    )
    suspend fun getUnlocatedSitesInDistrict(
        districtCode: String,
        plmn: String?,
        limit: Int = 50
    ): List<BtsTowerEntity>

    @Query(
        "SELECT * FROM bts_towers " +
            "WHERE (:plmn IS NULL OR operatorPlmn = :plmn) " +
            "AND (latitude IS NULL OR longitude IS NULL) " +
            "GROUP BY operatorPlmn, CASE WHEN enodebId > 0 THEN enodebId WHEN locationName != '' THEN locationName ELSE CAST(cellId AS TEXT) END " +
            "LIMIT :limit"
    )
    suspend fun getUnlocatedSitesAll(
        plmn: String?,
        limit: Int = 50
    ): List<BtsTowerEntity>

    @Query(
        "UPDATE bts_towers SET latitude = :lat, longitude = :lon, coordSource = :source " +
            "WHERE operatorPlmn = :plmn AND cellId = :cellId"
    )
    suspend fun updateTowerCoordinates(plmn: String, cellId: Long, lat: Double, lon: Double, source: String)

    @Query(
        "UPDATE bts_towers SET latitude = :lat, longitude = :lon, coordSource = :source " +
            "WHERE operatorPlmn = :plmn AND enodebId = :enodebId AND enodebId > 0"
    )
    suspend fun updateSiteCoordinatesByEnodeb(plmn: String, enodebId: Long, lat: Double, lon: Double, source: String)

    @Query(
        "UPDATE bts_towers SET latitude = :lat, longitude = :lon, coordSource = :source " +
            "WHERE operatorPlmn = :plmn AND locationName = :locationName AND locationName != ''"
    )
    suspend fun updateSiteCoordinatesByLocation(plmn: String, locationName: String, lat: Double, lon: Double, source: String)

    @Query("DELETE FROM bts_towers WHERE operatorPlmn = :plmn")
    suspend fun deleteByOperator(plmn: String)

    @Query("DELETE FROM bts_towers")
    suspend fun deleteAll()
}
