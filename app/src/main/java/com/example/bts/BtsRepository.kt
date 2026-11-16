package com.example.bts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.db.BtsDao
import com.example.data.db.BtsTowerEntity
import com.example.data.db.OperatorBtsStats
import com.example.model.BtsMatchInfo
import com.example.model.SimCellInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos

data class BtsImportProgress(
    val isImporting: Boolean = false,
    val fileName: String = "",
    val processedLines: Int = 0,
    val importedCount: Int = 0
)

class BtsRepository(
    private val context: Context,
    private val btsDao: BtsDao
) {
    private val prefs = context.getSharedPreferences("signal_logger_prefs", Context.MODE_PRIVATE)

    val operatorStats: Flow<List<OperatorBtsStats>> = btsDao.observeOperatorStats()
    val districtCodes: Flow<List<String>> = btsDao.observeDistrictCodes()

    private val _importProgress = MutableStateFlow(BtsImportProgress())
    val importProgress: StateFlow<BtsImportProgress> = _importProgress.asStateFlow()

    private val _isGeocoding = MutableStateFlow(false)
    val isGeocoding: StateFlow<Boolean> = _isGeocoding.asStateFlow()

    private val _geocodeProgress = MutableStateFlow(0 to 0)
    val geocodeProgress: StateFlow<Pair<Int, Int>> = _geocodeProgress.asStateFlow()

    private val attemptedGeocodeMs = ConcurrentHashMap<String, Long>()
    private val geocodeRetryCooldownMs = 10 * 60 * 1000L

    suspend fun seedBundledSampleIfNeeded() = withContext(Dispatchers.IO) {
        val alreadySeeded = prefs.getBoolean("bts_samples_seeded_v15", false)
        if (alreadySeeded) return@withContext
        val existingCount = btsDao.getTotalCount()
        if (existingCount == 0) {
            runCatching { importBundledSamples() }
        }
        prefs.edit().putBoolean("bts_samples_seeded_v15", true).apply()
    }

    suspend fun importBundledSamples(): BtsImportResult = withContext(Dispatchers.IO) {
        _importProgress.value = BtsImportProgress(isImporting = true, fileName = "tlte_sample.csv + o2lte_sample.csv")
        try {
            var totalLines = 0
            var imported = 0
            var coords = 0
            var skipped = 0
            val ops = mutableSetOf<String>()
            val start = System.currentTimeMillis()

            val assets = listOf(
                Triple("bts_samples/tlte_sample.csv", "23001", "T-Mobile CZ Sample"),
                Triple("bts_samples/o2lte_sample.csv", "23002", "O2 CZ Sample")
            )
            for ((assetPath, plmn, label) in assets) {
                runCatching {
                    context.assets.open(assetPath).use { stream ->
                        val res = BtsCsvImporter.importStream(
                            inputStream = stream,
                            btsDao = btsDao,
                            defaultOperatorPlmn = plmn,
                            fileNameHint = assetPath,
                            replaceOperator = false
                        ) { proc, imp ->
                            _importProgress.value = BtsImportProgress(true, label, totalLines + proc, imported + imp)
                        }
                        totalLines += res.totalLines
                        imported += res.importedCount
                        coords += res.withCoordsCount
                        skipped += res.skippedCount
                        ops.addAll(res.operatorsFound)
                    }
                }
            }
            attemptedGeocodeMs.clear()
            BtsImportResult(
                totalLines = totalLines,
                importedCount = imported,
                withCoordsCount = coords,
                skippedCount = skipped,
                detectedFormat = "GSMweb CZ Bundle (23001 + 23002)",
                operatorsFound = ops,
                durationMs = System.currentTimeMillis() - start
            )
        } finally {
            _importProgress.value = BtsImportProgress()
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        operatorPlmnOverride: String? = null,
        replaceOperator: Boolean = false
    ): BtsImportResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "bts_import.csv"
        val resolvedPlmn = operatorPlmnOverride ?: BtsGeoUtils.inferOperatorFromFilename(displayName)
        _importProgress.value = BtsImportProgress(isImporting = true, fileName = displayName)
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open file: $displayName")
            stream.use { input ->
                val result = BtsCsvImporter.importStream(
                    inputStream = input,
                    btsDao = btsDao,
                    defaultOperatorPlmn = resolvedPlmn,
                    fileNameHint = displayName,
                    replaceOperator = replaceOperator
                ) { processed, imported ->
                    _importProgress.value = BtsImportProgress(
                        isImporting = true,
                        fileName = displayName,
                        processedLines = processed,
                        importedCount = imported
                    )
                }
                attemptedGeocodeMs.clear()
                result
            }
        } finally {
            _importProgress.value = BtsImportProgress()
        }
    }

    suspend fun matchSimCell(
        sim: SimCellInfo,
        ueLat: Double,
        ueLon: Double,
        hasGnssFix: Boolean,
        isDemoMode: Boolean
    ): BtsMatchInfo? = withContext(Dispatchers.IO) {
        if (!sim.isServing || sim.networkGen == "Searching") return@withContext null

        val plmn = BtsGeoUtils.normalizeCzechOperatorPlmn(sim.plmn)
            ?: BtsGeoUtils.normalizeCzechOperatorPlmn(sim.operatorAlpha)
            ?: return@withContext null

        val ciLong = sim.ci.toLongOrNull()?.takeIf { it > 0L }
        var matched: BtsTowerEntity? = null
        var matchType = "CI"
        var isMocn = false

        if (ciLong != null) {
            matched = btsDao.findByPlmnAndCellId(plmn, ciLong)
            if (matched == null) {
                val partner = BtsGeoUtils.mocnPartnerPlmn(plmn)
                if (partner != null) {
                    matched = btsDao.findByPlmnAndCellId(partner, ciLong)
                    if (matched != null) {
                        matchType = "MOCN"
                        isMocn = true
                    }
                }
            }
        } else if (sim.pci > 0) {
            val tacInt = sim.tac.toIntOrNull()?.takeIf { it > 0 }
            if (tacInt != null) {
                val candidates = btsDao.findByPlmnPciTac(plmn, sim.pci, tacInt)
                if (candidates.size == 1 || (candidates.isNotEmpty() && candidates.map { it.locationName }.distinct().size == 1)) {
                    matched = candidates.first()
                    matchType = "PCI+TAC"
                }
            }
        }

        val tower = matched ?: return@withContext null

        // Lookup co-located bands/sectors on the same mast (same eNodeB ID or same locationName)
        val siteTowers = when {
            tower.enodebId > 0L -> btsDao.findCoLocatedByEnodeb(tower.operatorPlmn, tower.enodebId)
            tower.locationName.isNotBlank() -> btsDao.findCoLocatedByLocation(tower.operatorPlmn, tower.locationName)
            else -> listOf(tower)
        }
        val coLocatedBands = siteTowers
            .mapNotNull { it.band.trim().takeIf { b -> b.isNotEmpty() } }
            .distinct()
            .sortedWith(compareBy { it.toIntOrNull() ?: 99999 })

        var btsLat = tower.latitude
        var btsLon = tower.longitude
        var coordSrc = tower.coordSource

        if (!BtsGeoUtils.isValidCzechCoord(btsLat, btsLon)) {
            val siblingWithCoords = siteTowers.firstOrNull { BtsGeoUtils.isValidCzechCoord(it.latitude, it.longitude) }
            if (siblingWithCoords != null) {
                btsLat = siblingWithCoords.latitude
                btsLon = siblingWithCoords.longitude
                coordSrc = siblingWithCoords.coordSource
            }
        }

        // Known VUT Brno FEKT UREL indoor site fallback during Demo Mode
        if (isDemoMode && !BtsGeoUtils.isValidCzechCoord(btsLat, btsLon) &&
            (tower.cellId == 568853L || tower.enodebId == 2222L || tower.locationName.contains("FEKT", ignoreCase = true))
        ) {
            btsLat = 49.22695
            btsLon = 16.57545
            coordSrc = "DEMO_FEKT"
        }

        val hasValidUe = hasGnssFix && (ueLat != 0.0 || ueLon != 0.0)
        val hasValidBts = BtsGeoUtils.isValidCzechCoord(btsLat, btsLon)

        val distanceMeters = if (hasValidUe && hasValidBts) {
            BtsGeoUtils.haversineDistanceMeters(ueLat, ueLon, btsLat!!, btsLon!!)
        } else null

        val azimuthDeg = if (hasValidUe && hasValidBts) {
            BtsGeoUtils.bearingDegrees(ueLat, ueLon, btsLat!!, btsLon!!)
        } else null

        val taDeltaMeters = if (distanceMeters != null && sim.estimatedDistanceM != null) {
            sim.estimatedDistanceM - distanceMeters
        } else null

        val freqMhz = sim.dlFreqMhz?.takeIf { it > 0.0 } ?: BtsGeoUtils.bandToApproxFreqMhz(tower.band)
        val fsplDb = BtsGeoUtils.calculateFsplDb(freqMhz, distanceMeters)

        val pciMismatch = sim.pci > 0 && tower.pci > 0 && sim.pci != tower.pci
        val simTacInt = sim.tac.toIntOrNull()
        val tacMismatch = simTacInt != null && simTacInt > 0 && tower.tac > 0 && simTacInt != tower.tac

        BtsMatchInfo(
            operatorPlmn = tower.operatorPlmn,
            operatorName = BtsGeoUtils.operatorDisplayName(tower.operatorPlmn),
            cellId = tower.cellId,
            enodebId = tower.enodebId,
            sectorId = tower.sectorId,
            dbPci = tower.pci,
            dbTac = tower.tac,
            dbBand = tower.band,
            gsmCid = tower.gsmCid,
            updatedDate = tower.updatedDate,
            districtCode = tower.districtCode,
            locationName = tower.locationName,
            latitude = if (hasValidBts) btsLat else null,
            longitude = if (hasValidBts) btsLon else null,
            coordSource = coordSrc,
            distanceMeters = distanceMeters,
            azimuthDeg = azimuthDeg,
            taDeltaMeters = taDeltaMeters,
            fsplDb = fsplDb,
            coLocatedBands = coLocatedBands,
            coLocatedCount = siteTowers.size.coerceAtLeast(1),
            matchType = matchType,
            isMocnShared = isMocn,
            pciMismatch = pciMismatch,
            tacMismatch = tacMismatch
        )
    }

    suspend fun geocodeSiteIfNotAttempted(
        plmn: String,
        cellId: Long,
        enodebId: Long,
        locationName: String
    ): Pair<Double, Double>? {
        if (locationName.isBlank()) return null
        val key = if (enodebId > 0L) "$plmn:enb:$enodebId" else "$plmn:loc:$locationName"
        val now = System.currentTimeMillis()
        val lastAttempt = attemptedGeocodeMs[key]
        if (lastAttempt != null && now - lastAttempt < geocodeRetryCooldownMs) {
            return null
        }
        attemptedGeocodeMs[key] = now
        return geocodeAndSaveSite(plmn, cellId, enodebId, locationName)
    }

    suspend fun geocodeAndSaveSite(
        plmn: String,
        cellId: Long,
        enodebId: Long,
        locationName: String
    ): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (locationName.isBlank()) return@withContext null
        val coords = BtsGeocoder.geocodeCzechLocation(context, locationName) ?: return@withContext null
        saveSiteCoordinates(
            plmn = plmn,
            cellId = cellId,
            enodebId = enodebId,
            locationName = locationName,
            lat = coords.first,
            lon = coords.second,
            source = "GEOCODER"
        )
        coords
    }

    suspend fun batchGeocodeDistrict(
        districtCode: String,
        plmnFilter: String? = null,
        limit: Int = 25
    ): Int = withContext(Dispatchers.IO) {
        if (_isGeocoding.value) return@withContext 0
        _isGeocoding.value = true
        var resolved = 0
        try {
            val pending = btsDao.getUnlocatedSitesInDistrict(districtCode, plmnFilter, limit)
            _geocodeProgress.value = 0 to pending.size
            pending.forEachIndexed { idx, tower ->
                val coords = BtsGeocoder.geocodeCzechLocation(context, tower.locationName)
                if (coords != null) {
                    saveSiteCoordinates(
                        plmn = tower.operatorPlmn,
                        cellId = tower.cellId,
                        enodebId = tower.enodebId,
                        locationName = tower.locationName,
                        lat = coords.first,
                        lon = coords.second,
                        source = "GEOCODER"
                    )
                    resolved++
                }
                _geocodeProgress.value = (idx + 1) to pending.size
                delay(200L)
            }
            resolved
        } finally {
            _isGeocoding.value = false
            _geocodeProgress.value = 0 to 0
        }
    }

    suspend fun saveSiteCoordinates(
        plmn: String,
        cellId: Long,
        enodebId: Long,
        locationName: String,
        lat: Double,
        lon: Double,
        source: String = "MANUAL",
        applyToEntireSite: Boolean = true
    ) = withContext(Dispatchers.IO) {
        btsDao.updateTowerCoordinates(plmn, cellId, lat, lon, source)
        if (applyToEntireSite) {
            if (enodebId > 0L) {
                btsDao.updateSiteCoordinatesByEnodeb(plmn, enodebId, lat, lon, source)
            }
            if (locationName.isNotBlank()) {
                btsDao.updateSiteCoordinatesByLocation(plmn, locationName, lat, lon, source)
            }
        }
    }

    suspend fun findNearbyTowers(
        lat: Double,
        lon: Double,
        radiusKm: Double = 6.0,
        limit: Int = 220
    ): List<BtsTowerEntity> = withContext(Dispatchers.IO) {
        if (!BtsGeoUtils.isValidCzechCoord(lat, lon)) return@withContext emptyList()
        val latDelta = radiusKm / 111.32
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(0.2)
        val lonDelta = radiusKm / (111.32 * cosLat)
        btsDao.findNearbyWithCoords(
            centerLat = lat,
            centerLon = lon,
            minLat = lat - latDelta,
            maxLat = lat + latDelta,
            minLon = lon - lonDelta,
            maxLon = lon + lonDelta,
            limit = limit
        )
    }

    suspend fun searchTowers(query: String, plmnFilter: String? = null, limit: Int = 120): List<BtsTowerEntity> =
        withContext(Dispatchers.IO) {
            btsDao.searchTowers(query.trim(), plmnFilter, limit)
        }

    suspend fun getSiteCells(plmn: String, enodebId: Long, cellId: Long): List<BtsTowerEntity> =
        withContext(Dispatchers.IO) {
            btsDao.findSiteCells(plmn, enodebId, cellId)
        }

    suspend fun deleteOperator(plmn: String) = withContext(Dispatchers.IO) {
        btsDao.deleteByOperator(plmn)
        attemptedGeocodeMs.clear()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        btsDao.deleteAll()
        attemptedGeocodeMs.clear()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
