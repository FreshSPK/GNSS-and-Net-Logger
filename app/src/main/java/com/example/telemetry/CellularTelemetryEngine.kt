package com.example.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.*
import androidx.core.content.ContextCompat
import com.example.model.CellNeighbor
import com.example.model.CellularState
import com.example.model.SimCellInfo
import com.example.util.BandMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class CellularTelemetryEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

    private val _cellularState = MutableStateFlow(CellularState())
    val cellularState: StateFlow<CellularState> = _cellularState.asStateFlow()

    private var pingJob: Job? = null
    private var scanJob: Job? = null

    fun start() {
        startPingMonitor()
        startCellScanning()
    }

    fun stop() {
        pingJob?.cancel()
        scanJob?.cancel()
    }

    private fun startPingMonitor() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val pingResult = measureSocketPing("8.8.8.8", 53, 2000)
                _cellularState.value = _cellularState.value.copy(
                    pingRttMs = pingResult.first,
                    pingPacketLoss = pingResult.second
                )
                delay(2500)
            }
        }
    }

    private fun measureSocketPing(host: String, port: Int, timeoutMs: Int): Pair<Long?, Boolean> {
        return try {
            val startTime = SystemClock.elapsedRealtime()
            val socket = Socket()
            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
            val rtt = SystemClock.elapsedRealtime() - startTime
            socket.close()
            Pair(rtt, false)
        } catch (e: Exception) {
            Pair(null, true)
        }
    }

    private fun startCellScanning() {
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                readCellularTelemetry()
                delay(1000)
            }
        }
    }

    fun updateDemoData(sim1: SimCellInfo, sim2: SimCellInfo, pingMs: Long?) {
        _cellularState.value = _cellularState.value.copy(
            sim1 = sim1,
            sim2 = sim2,
            pingRttMs = pingMs,
            pingPacketLoss = pingMs == null
        )
    }

    @Suppress("DEPRECATION")
    private fun readCellularTelemetry() {
        val tm = telephonyManager ?: return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            // Collect Subscriptions for Dual-SIM
            val subInfos = if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } else emptyList()

            var sim1Info = SimCellInfo(slotIndex = 0, displayName = "SIM 1")
            var sim2Info = SimCellInfo(slotIndex = 1, displayName = "SIM 2")

            val allCellInfo = tm.allCellInfo ?: emptyList()

            // If we have subscription info
            if (subInfos.isNotEmpty()) {
                subInfos.forEach { subInfo ->
                    val slot = subInfo.simSlotIndex
                    val carrierName = subInfo.displayName?.toString() ?: subInfo.carrierName?.toString() ?: "SIM ${slot + 1}"
                    val subTm = tm.createForSubscriptionId(subInfo.subscriptionId)
                    val cells = subTm.allCellInfo ?: allCellInfo
                    val parsed = parseCellInfoList(cells, slot, subInfo.subscriptionId, carrierName)
                    if (slot == 0) sim1Info = parsed
                    else if (slot == 1) sim2Info = parsed
                }
            } else {
                // Single SIM or fallback
                sim1Info = parseCellInfoList(allCellInfo, 0, -1, tm.networkOperatorName.ifEmpty { "Cellular" })
            }

            _cellularState.value = _cellularState.value.copy(
                sim1 = sim1Info,
                sim2 = sim2Info
            )
        } catch (e: SecurityException) {
            // Permission revoked
        } catch (e: Exception) {
            // Telephony driver variance
        }
    }

    private fun parseCellInfoList(
        cellList: List<CellInfo>,
        slotIndex: Int,
        subId: Int,
        displayName: String
    ): SimCellInfo {
        if (cellList.isEmpty()) {
            return SimCellInfo(slotIndex = slotIndex, subscriptionId = subId, displayName = displayName)
        }

        var servingCell: CellInfo? = cellList.firstOrNull { it.isRegistered } ?: cellList.firstOrNull()
        val neighbors = mutableListOf<CellNeighbor>()

        var servingRsrp: Int? = null

        // Parse Serving Cell
        var tech = "Unknown"
        var tac = "N/A"
        var ci = "N/A"
        var enbOrGnb = "N/A"
        var sector = "N/A"
        var pci = 0
        var arfcn = 0
        var band = "N/A"
        var rsrp: Int? = null
        var rsrq: Int? = null
        var rssi: Int? = null
        var sinr: Float? = null
        var ssRsrp: Int? = null
        var ssRsrq: Int? = null
        var ssSinr: Float? = null
        var csiRsrp: Int? = null
        var csiRsrq: Int? = null
        var csiSinr: Float? = null
        var ta: Int? = null

        servingCell?.let { cell ->
            when (cell) {
                is CellInfoLte -> {
                    tech = "4G LTE"
                    val id = cell.cellIdentity
                    tac = if (id.tac != Int.MAX_VALUE && id.tac > 0) id.tac.toString() else "N/A"
                    ci = if (id.ci != Int.MAX_VALUE && id.ci > 0) id.ci.toString() else "N/A"
                    pci = if (id.pci != Int.MAX_VALUE) id.pci else 0
                    arfcn = if (id.earfcn != Int.MAX_VALUE) id.earfcn else 0
                    band = BandMapper.lteEarfcnToBand(arfcn)

                    if (id.ci > 0 && id.ci != Int.MAX_VALUE) {
                        val decoded = BandMapper.decodeLteCellId(id.ci.toLong())
                        enbOrGnb = decoded.first.toString()
                        sector = decoded.second.toString()
                    }

                    val ss = cell.cellSignalStrength
                    rsrp = BandMapper.sanitizeMetric(ss.rsrp)
                    rsrq = BandMapper.sanitizeMetric(ss.rsrq)
                    rssi = BandMapper.sanitizeMetric(ss.rssi)
                    sinr = BandMapper.sanitizeSinr(ss.rssnr.toDouble())
                    ta = if (ss.timingAdvance in 0..1282) ss.timingAdvance else null
                    servingRsrp = rsrp
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
                        tech = "5G NR"
                        val id = cell.cellIdentity as? CellIdentityNr
                        if (id != null) {
                            tac = if (id.tac != Int.MAX_VALUE && id.tac > 0) id.tac.toString() else "N/A"
                            pci = if (id.pci != Int.MAX_VALUE) id.pci else 0
                            arfcn = if (id.nrarfcn != Int.MAX_VALUE) id.nrarfcn else 0
                            band = BandMapper.nrArfcnToBand(arfcn)
                            val nci = id.nci
                            if (nci > 0 && nci != Long.MAX_VALUE) {
                                ci = nci.toString()
                                val decoded = BandMapper.decodeNrCellId(nci)
                                enbOrGnb = decoded.first.toString()
                                sector = decoded.second.toString()
                            }
                        }
                        val ss = cell.cellSignalStrength as? CellSignalStrengthNr
                        if (ss != null) {
                            ssRsrp = BandMapper.sanitizeMetric(ss.ssRsrp)
                            ssRsrq = BandMapper.sanitizeMetric(ss.ssRsrq)
                            ssSinr = BandMapper.sanitizeSinr(ss.ssSinr.toDouble())
                            csiRsrp = BandMapper.sanitizeMetric(ss.csiRsrp)
                            csiRsrq = BandMapper.sanitizeMetric(ss.csiRsrq)
                            csiSinr = BandMapper.sanitizeSinr(ss.csiSinr.toDouble())
                            rsrp = ssRsrp ?: csiRsrp
                            sinr = ssSinr ?: csiSinr
                            servingRsrp = rsrp
                        }
                    } else if (cell is CellInfoWcdma) {
                        tech = "3G UMTS"
                        val id = cell.cellIdentity
                        tac = if (id.lac != Int.MAX_VALUE) id.lac.toString() else "N/A"
                        ci = if (id.cid != Int.MAX_VALUE) id.cid.toString() else "N/A"
                        pci = if (id.psc != Int.MAX_VALUE) id.psc else 0
                        arfcn = if (id.uarfcn != Int.MAX_VALUE) id.uarfcn else 0
                        band = "UARFCN $arfcn"
                        rssi = BandMapper.sanitizeMetric(cell.cellSignalStrength.dbm)
                        rsrp = rssi
                        servingRsrp = rssi
                    } else if (cell is CellInfoGsm) {
                        tech = "2G GSM"
                        val id = cell.cellIdentity
                        tac = if (id.lac != Int.MAX_VALUE) id.lac.toString() else "N/A"
                        ci = if (id.cid != Int.MAX_VALUE) id.cid.toString() else "N/A"
                        arfcn = if (id.arfcn != Int.MAX_VALUE) id.arfcn else 0
                        band = "GSM ARFCN $arfcn"
                        rssi = BandMapper.sanitizeMetric(cell.cellSignalStrength.dbm)
                        rsrp = rssi
                        servingRsrp = rssi
                    }
                }
            }
        }

        // Neighbors
        cellList.filter { !it.isRegistered }.forEach { neighbor ->
            when (neighbor) {
                is CellInfoLte -> {
                    val nRsrp = BandMapper.sanitizeMetric(neighbor.cellSignalStrength.rsrp)
                    if (nRsrp != null) {
                        val nArfcn = neighbor.cellIdentity.earfcn
                        val delta = if (servingRsrp != null) servingRsrp!! - nRsrp else 0
                        neighbors.add(
                            CellNeighbor(
                                tech = "LTE",
                                pci = neighbor.cellIdentity.pci,
                                earfcn = nArfcn,
                                band = BandMapper.lteEarfcnToBand(nArfcn),
                                rsrp = nRsrp,
                                deltaFromServingDb = delta
                            )
                        )
                    }
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && neighbor is CellInfoNr) {
                        val ss = neighbor.cellSignalStrength as? CellSignalStrengthNr
                        val nRsrp = BandMapper.sanitizeMetric(ss?.ssRsrp)
                        if (nRsrp != null) {
                            val id = neighbor.cellIdentity as? CellIdentityNr
                            val nArfcn = id?.nrarfcn ?: 0
                            val delta = if (servingRsrp != null) servingRsrp!! - nRsrp else 0
                            neighbors.add(
                                CellNeighbor(
                                    tech = "5G NR",
                                    pci = id?.pci ?: 0,
                                    earfcn = nArfcn,
                                    band = BandMapper.nrArfcnToBand(nArfcn),
                                    rsrp = nRsrp,
                                    deltaFromServingDb = delta
                                )
                            )
                        }
                    }
                }
            }
        }

        val bestNeighbor = neighbors.maxByOrNull { it.rsrp }
        val distance = BandMapper.calculateLteDistanceMeters(ta)

        return SimCellInfo(
            slotIndex = slotIndex,
            subscriptionId = subId,
            displayName = displayName,
            operatorAlpha = displayName,
            networkGen = tech,
            isServing = servingCell != null,
            tac = tac,
            ci = ci,
            enbOrGnbId = enbOrGnb,
            sectorId = sector,
            pci = pci,
            earfcnOrArfcn = arfcn,
            band = band,
            rsrp = rsrp,
            rsrq = rsrq,
            rssi = rssi,
            sinr = sinr,
            ssRsrp = ssRsrp,
            ssRsrq = ssRsrq,
            ssSinr = ssSinr,
            csiRsrp = csiRsrp,
            csiRsrq = csiRsrq,
            csiSinr = csiSinr,
            timingAdvance = ta,
            estimatedDistanceM = distance,
            neighbors = neighbors.sortedByDescending { it.rsrp }
        )
    }
}
