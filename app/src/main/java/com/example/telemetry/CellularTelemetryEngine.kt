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
import java.util.concurrent.ConcurrentHashMap

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
    @Volatile
    private var isRunning = false
    @Volatile
    private var pingEnabled = true
    @Volatile
    private var taMultiplierMode = 2

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rfDispatcher = Dispatchers.IO.limitedParallelism(1)
    @Volatile
    private var lastReadMs = 0L

    private data class ActiveCarrierChannel(
        val networkType: Int,
        val band: Int,
        val bandwidthKhz: Int,
        val dlFreqKhz: Int = 0,
        val dlChannelNumber: Int = 0,
        val isPrimary: Boolean = false
    )

    private val callbacksBySubId = ConcurrentHashMap<Int, Any>()
    private val overrideNetworkTypeBySubId = ConcurrentHashMap<Int, Int>()
    private val activeCarrierChannelsBySubId = ConcurrentHashMap<Int, List<ActiveCarrierChannel>>()

    fun setPingEnabled(enabled: Boolean) {
        if (pingEnabled == enabled) return
        pingEnabled = enabled
        if (enabled && isRunning && pingJob?.isActive != true) {
            startPingMonitor()
        } else if (!enabled) {
            pingJob?.cancel()
            pingJob = null
            _cellularState.value = _cellularState.value.copy(pingRttMs = null, pingPacketLoss = false)
        }
    }

    fun setTaMultiplierMode(mode: Int) {
        taMultiplierMode = mode
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        if (pingEnabled) {
            startPingMonitor()
        }
        registerTelephonyListeners()
        startCellScanning()
    }

    fun stop() {
        isRunning = false
        pingJob?.cancel()
        pingJob = null
        scanJob?.cancel()
        scanJob = null
        unregisterTelephonyListeners()
    }

    private fun startPingMonitor() {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning && pingEnabled) {
                val pingResult = measureSocketPing(byteArrayOf(8, 8, 8, 8), 53, 2000)
                _cellularState.value = _cellularState.value.copy(
                    pingRttMs = pingResult.first,
                    pingPacketLoss = pingResult.second
                )
                delay(2500)
            }
        }
    }

    private fun measureSocketPing(ipBytes: ByteArray, port: Int, timeoutMs: Int): Pair<Long?, Boolean> {
        return try {
            val addr = InetAddress.getByAddress(ipBytes)
            val startTime = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(addr, port), timeoutMs)
            }
            val rtt = SystemClock.elapsedRealtime() - startTime
            Pair(rtt, false)
        } catch (e: Exception) {
            Pair(null, true)
        }
    }

    private fun registerTelephonyListeners() {
        val tm = telephonyManager ?: return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val hasPhoneState = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val targets = mutableListOf<Pair<Int, TelephonyManager>>(-1 to tm)
        if (hasPhoneState) {
            val subInfos = try {
                subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            for (sub in subInfos) {
                try {
                    targets.add(sub.subscriptionId to tm.createForSubscriptionId(sub.subscriptionId))
                } catch (_: Exception) {
                    // Safe catch
                }
            }
        }

        for ((subKey, targetTm) in targets) {
            if (callbacksBySubId.containsKey(subKey)) continue
            registerListenerForManager(subKey, targetTm, hasPhoneState)
        }
    }

    private fun registerListenerForManager(subKey: Int, targetTm: TelephonyManager, hasPhoneState: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb: TelephonyCallback = if (hasPhoneState) {
                    object : TelephonyCallback(),
                        TelephonyCallback.CellInfoListener,
                        TelephonyCallback.SignalStrengthsListener,
                        TelephonyCallback.DisplayInfoListener,
                        TelephonyCallback.PhysicalChannelConfigListener {
                        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                            scheduleRead(cellInfo, force = false)
                        }

                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            scheduleRead(null, force = false)
                        }

                        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                            overrideNetworkTypeBySubId[subKey] = telephonyDisplayInfo.overrideNetworkType
                            scheduleRead(null, force = false)
                        }

                        override fun onPhysicalChannelConfigChanged(configs: MutableList<PhysicalChannelConfig>) {
                            activeCarrierChannelsBySubId[subKey] = configs.mapNotNull { cfg ->
                                val bw = cfg.cellBandwidthDownlinkKhz
                                val dlFreq = try { cfg.downlinkFrequencyKhz } catch (_: Exception) { 0 }
                                val dlCh = try { cfg.downlinkChannelNumber } catch (_: Exception) { 0 }
                                val isPrim = try { cfg.connectionStatus == PhysicalChannelConfig.CONNECTION_PRIMARY_SERVING } catch (_: Exception) { false }
                                if (bw > 0) {
                                    ActiveCarrierChannel(
                                        networkType = cfg.networkType,
                                        band = cfg.band,
                                        bandwidthKhz = bw,
                                        dlFreqKhz = if (dlFreq > 0) dlFreq else 0,
                                        dlChannelNumber = if (dlCh > 0 && dlCh != Int.MAX_VALUE) dlCh else 0,
                                        isPrimary = isPrim
                                    )
                                } else null
                            }
                            scheduleRead(null, force = false)
                        }
                    }
                } else {
                    object : TelephonyCallback(),
                        TelephonyCallback.CellInfoListener,
                        TelephonyCallback.SignalStrengthsListener {
                        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                            scheduleRead(cellInfo, force = false)
                        }

                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                            scheduleRead(null, force = false)
                        }
                    }
                }
                targetTm.registerTelephonyCallback(context.mainExecutor, cb)
                callbacksBySubId[subKey] = Pair(targetTm, cb)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                        scheduleRead(cellInfo, force = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        scheduleRead(null, force = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            overrideNetworkTypeBySubId[subKey] = telephonyDisplayInfo.overrideNetworkType
                            scheduleRead(null, force = false)
                        }
                    }
                }
                @Suppress("DEPRECATION")
                val events = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasPhoneState) {
                    PhoneStateListener.LISTEN_CELL_INFO or
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
                } else {
                    PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                }
                @Suppress("DEPRECATION")
                targetTm.listen(listener, events)
                callbacksBySubId[subKey] = Pair(targetTm, listener)
            }
        } catch (_: Exception) {
            // Safe listener catch
        }
    }

    private fun unregisterTelephonyListeners() {
        for ((_, entry) in callbacksBySubId) {
            try {
                @Suppress("UNCHECKED_CAST")
                val pair = entry as? Pair<TelephonyManager, Any> ?: continue
                val targetTm = pair.first
                val cb = pair.second
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (cb as? TelephonyCallback)?.let { targetTm.unregisterTelephonyCallback(it) }
                } else {
                    @Suppress("DEPRECATION")
                    (cb as? PhoneStateListener)?.let { targetTm.listen(it, PhoneStateListener.LISTEN_NONE) }
                }
            } catch (_: Exception) {
                // Safe catch
            }
        }
        callbacksBySubId.clear()
        overrideNetworkTypeBySubId.clear()
        activeCarrierChannelsBySubId.clear()
    }

    private fun startCellScanning() {
        scanJob?.cancel()
        scanJob = scope.launch(rfDispatcher) {
            while (isActive && isRunning) {
                registerTelephonyListeners()
                triggerFreshCellUpdate()
                readCellularTelemetry(providedCells = null, force = true)
                delay(1000)
            }
        }
    }

    private fun scheduleRead(providedCells: List<CellInfo>? = null, force: Boolean = false) {
        if (!isRunning) return
        val nowMs = SystemClock.elapsedRealtime()
        if (!force && nowMs - lastReadMs < 450L) return
        scope.launch(rfDispatcher) {
            readCellularTelemetry(providedCells, force)
        }
    }

    private fun triggerFreshCellUpdate() {
        val tm = telephonyManager ?: return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasPhoneState = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val subInfos = if (hasPhoneState) {
                try {
                    subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val managersToPoll = mutableListOf(tm)
            for (sub in subInfos) {
                try {
                    val subTm = tm.createForSubscriptionId(sub.subscriptionId)
                    managersToPoll.add(subTm)
                } catch (e: Exception) {
                    // Safe catch
                }
            }

            for (mgr in managersToPoll) {
                try {
                    mgr.requestCellInfoUpdate(context.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                            scheduleRead(cellInfo, force = false)
                        }
                    })
                } catch (e: Exception) {
                    // Device driver may throttle requestCellInfoUpdate
                }
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
    private fun readCellularTelemetry(providedCells: List<CellInfo>? = null, force: Boolean = false) {
        val tm = telephonyManager ?: return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        if (!force && nowMs - lastReadMs < 450L) {
            return
        }
        lastReadMs = nowMs

        try {
            val allCellInfo = providedCells ?: tm.allCellInfo ?: emptyList()

            // Subscriptions detection
            val hasPhoneState = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            val subInfos = if (hasPhoneState) {
                try {
                    subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()

            val defaultDataSubId = try {
                SubscriptionManager.getDefaultDataSubscriptionId()
            } catch (e: Exception) {
                -1
            }

            val activeDataSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    SubscriptionManager.getActiveDataSubscriptionId()
                } catch (e: Exception) {
                    -1
                }
            } else -1

            var sim1Info = SimCellInfo(slotIndex = 0, displayName = "SIM 1")
            var sim2Info = SimCellInfo(slotIndex = 1, displayName = "SIM 2")

            if (subInfos.isNotEmpty()) {
                val sub1 = subInfos.firstOrNull { it.simSlotIndex == 0 }
                val sub2 = subInfos.firstOrNull { it.simSlotIndex == 1 }

                val sub1Tm = sub1?.let { tm.createForSubscriptionId(it.subscriptionId) }
                val sub2Tm = sub2?.let { tm.createForSubscriptionId(it.subscriptionId) }

                val sub1Connected = try { sub1Tm?.dataState == TelephonyManager.DATA_CONNECTED } catch (e: Exception) { false }
                val sub2Connected = try { sub2Tm?.dataState == TelephonyManager.DATA_CONNECTED } catch (e: Exception) { false }

                // Check which SIM carries active data
                val isSim2Data = sub2Connected || (activeDataSubId != -1 && sub2?.subscriptionId == activeDataSubId) ||
                    (!sub1Connected && defaultDataSubId != -1 && sub2?.subscriptionId == defaultDataSubId)
                val isSim1Data = !isSim2Data && (sub1Connected || (activeDataSubId != -1 && sub1?.subscriptionId == activeDataSubId) ||
                    (defaultDataSubId != -1 && sub1?.subscriptionId == defaultDataSubId) || sub2 == null)

                val assignedCells = mutableSetOf<CellInfo>()
                val isSingleSim = subInfos.size <= 1

                // Process SIM 1 and SIM 2 with strict PLMN & operator isolation
                if (sub1 != null && sub1Tm != null) {
                    val carrier1 = sub1.displayName?.toString()?.ifEmpty { null }
                        ?: sub1.carrierName?.toString()?.ifEmpty { null }
                        ?: sub1Tm.networkOperatorName.ifEmpty { "SIM 1" }
                    val cells1 = sub1Tm.allCellInfo ?: allCellInfo
                    sim1Info = parseCellInfoForSubscription(cells1, sub1Tm, 0, sub1.subscriptionId, carrier1, isSim1Data, sub1, assignedCells, isSingleSim)
                }

                if (sub2 != null && sub2Tm != null) {
                    val carrier2 = sub2.displayName?.toString()?.ifEmpty { null }
                        ?: sub2.carrierName?.toString()?.ifEmpty { null }
                        ?: sub2Tm.networkOperatorName.ifEmpty { "SIM 2" }
                    val cells2 = sub2Tm.allCellInfo ?: allCellInfo
                    sim2Info = parseCellInfoForSubscription(cells2, sub2Tm, 1, sub2.subscriptionId, carrier2, isSim2Data, sub2, assignedCells, isSingleSim)
                }
            } else {
                // Single SIM fallback
                val opName = tm.networkOperatorName.ifEmpty { "Cellular" }
                val assigned = mutableSetOf<CellInfo>()
                sim1Info = parseCellInfoForSubscription(allCellInfo, tm, 0, -1, opName, isDataActive = true, subInfo = null, assignedCells = assigned, isSingleSim = true)
            }

            _cellularState.value = _cellularState.value.copy(
                sim1 = sim1Info,
                sim2 = sim2Info,
                lastUpdateEpochMs = System.currentTimeMillis()
            )
        } catch (e: SecurityException) {
            // Permission missing
        } catch (e: Exception) {
            // Driver variance
        }
    }

    private fun normalizePlmn(mcc: String?, mnc: String?): String {
        if (mcc.isNullOrBlank() || mnc.isNullOrBlank()) return ""
        val cMcc = mcc.trim().padStart(3, '0')
        val cMnc = mnc.trim().padStart(2, '0')
        return "$cMcc$cMnc"
    }

    private fun isCellCompatible(cell: CellInfo, targetPlmn: String, knownMnc: String?, targetCarrier: String): Boolean {
        val cellPlmn = getCellPlmn(cell)
        if (cellPlmn.isEmpty()) return false
        val cellMnc = if (cellPlmn.length >= 5) cellPlmn.substring(3) else ""

        // Strict Czech operator network isolation
        if (targetCarrier.contains("vodafone")) {
            // Vodafone CZ operates on MNC 03 (PLMN 23003) exclusively
            return cellMnc == "03" || cellPlmn == "23003"
        }
        if (targetCarrier.contains("t-mobile") || targetCarrier.contains("tmobile")) {
            // T-Mobile CZ operates on MNC 01 (PLMN 23001) and MOCN network sharing with CETIN/O2 (MNC 02)
            // It MUST NOT match Vodafone (03)
            return (cellMnc == "01" || cellMnc == "02" || cellPlmn == "23001" || cellPlmn == "23002") && cellMnc != "03"
        }
        if (targetCarrier.contains("o2")) {
            // O2 CZ operates on MNC 02 (PLMN 23002) and MOCN network sharing with T-Mobile (MNC 01)
            // It MUST NOT match Vodafone (03)
            return (cellMnc == "02" || cellMnc == "01" || cellPlmn == "23002" || cellPlmn == "23001") && cellMnc != "03"
        }

        // Generic global fallback
        if (targetPlmn.isNotEmpty() && cellPlmn == targetPlmn) return true
        if (knownMnc != null && cellMnc == knownMnc) return true
        return false
    }

    private fun parseCellInfoForSubscription(
        cellList: List<CellInfo>,
        tm: TelephonyManager,
        slotIndex: Int,
        subId: Int,
        displayName: String,
        isDataActive: Boolean,
        subInfo: SubscriptionInfo?,
        assignedCells: MutableSet<CellInfo>,
        isSingleSim: Boolean = false
    ): SimCellInfo {
        // PLMN matching for dual SIM
        val subMcc = subInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.mccString else it.mcc.toString()
        }
        val subMnc = subInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.mncString else it.mnc.toString()
        }
        val subPlmn = normalizePlmn(subMcc, subMnc)
        val netOperator = tm.networkOperator.takeIf { it.isNotBlank() } ?: ""
        val targetPlmn = if (netOperator.isNotBlank()) netOperator else subPlmn

        val targetCarrier = (subInfo?.displayName?.toString() ?: displayName).lowercase(java.util.Locale.ROOT)
        val knownTargetMnc = when {
            targetCarrier.contains("t-mobile") || targetCarrier.contains("tmobile") -> "01"
            targetCarrier.contains("o2") -> "02"
            targetCarrier.contains("vodafone") -> "03"
            else -> subMnc?.padStart(2, '0')
        }

        // Search for a serving cell from cellList that hasn't been assigned yet.
        // In 5G NSA (EN-DC), both an LTE anchor (PCell) and an NR cell (SCG) can report isRegistered=true.
        // Prefer CellInfoLte as the PCell when both exist; only pick CellInfoNr as servingCell in 5G SA!
        val unassignedCells = cellList.filter { it !in assignedCells }

        fun findBestServing(preferLte: Boolean): CellInfo? {
            val lteFilter: (CellInfo) -> Boolean = { !preferLte || it is CellInfoLte }
            if (targetPlmn.isNotEmpty()) {
                unassignedCells.firstOrNull { cell ->
                    lteFilter(cell) && cell.isRegistered && isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier) && getCellPlmn(cell) == targetPlmn
                }?.let { return it }
            }
            unassignedCells.firstOrNull { cell ->
                lteFilter(cell) && cell.isRegistered && isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier)
            }?.let { return it }
            return null
        }

        var servingCell: CellInfo? = findBestServing(preferLte = true)
            ?: findBestServing(preferLte = false)
            ?: unassignedCells.firstOrNull { cell ->
                isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier)
            }

        // Mark cell as assigned if found
        if (servingCell != null) {
            assignedCells.add(servingCell)
        }

        // Capture any co-registered 5G NR Secondary Cell (EN-DC SCG) when PCell is LTE
        val registeredNrSecondaryCell: CellInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && servingCell is CellInfoLte) {
            unassignedCells.firstOrNull { cell ->
                cell != servingCell &&
                    cell is CellInfoNr &&
                    cell.isRegistered &&
                    (isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier) ||
                        (getCellPlmn(cell).isEmpty() && (isDataActive || isSingleSim)))
            }?.also { assignedCells.add(it) }
        } else null

        val neighbors = mutableListOf<CellNeighbor>()
        var servingRsrp: Int? = null

        var tech = "Searching"
        var plmn = if (targetPlmn.isNotEmpty()) targetPlmn else if (subPlmn.isNotEmpty()) subPlmn else "N/A"
        var tac = "N/A"
        var ci = "N/A"
        var enbOrGnb = "N/A"
        var sector = "N/A"
        var pci = 0
        var arfcn = 0
        var band = "N/A"
        var bandwidthKhz: Int? = null
        var dlFreq: Double? = null
        var ulFreq: Double? = null
        var rsrp: Int? = null
        var rsrq: Int? = null
        var rssi: Int? = null
        var sinr: Float? = null
        var cqi: Int? = null
        var ssRsrp: Int? = null
        var ssRsrq: Int? = null
        var ssSinr: Float? = null
        var csiRsrp: Int? = null
        var csiRsrq: Int? = null
        var csiSinr: Float? = null
        var ta: Int? = null
        var nrBand: String? = null
        var nrArfcn: Int? = null
        var nrPci: Int? = null
        var nrDlFreq: Double? = null

        if (servingCell != null) {
            when (servingCell) {
                is CellInfoLte -> {
                    tech = "4G LTE"
                    val id = servingCell.cellIdentity
                    val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else if (id.mcc != Int.MAX_VALUE) id.mcc.toString() else null
                    val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else if (id.mnc != Int.MAX_VALUE) id.mnc.toString() else null
                    if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) {
                        plmn = "$mcc$mnc"
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && id.bandwidth != Int.MAX_VALUE && id.bandwidth > 0) {
                        bandwidthKhz = id.bandwidth
                    }

                    tac = if (id.tac != Int.MAX_VALUE && id.tac > 0) id.tac.toString() else "N/A"
                    ci = if (id.ci != Int.MAX_VALUE && id.ci > 0) id.ci.toString() else "N/A"
                    pci = if (id.pci != Int.MAX_VALUE) id.pci else 0
                    arfcn = if (id.earfcn != Int.MAX_VALUE) id.earfcn else 0
                    band = if (arfcn > 0) {
                        BandMapper.lteEarfcnToBand(arfcn)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        id.bands.firstOrNull()?.let { b ->
                            "B$b (${BandMapper.lteBandToFreqLabel(b)})"
                        } ?: "Unknown Band"
                    } else "Unknown Band"
                    val freqs = BandMapper.lteEarfcnToFrequencies(arfcn)
                    if (freqs.first > 0) {
                        dlFreq = freqs.first
                        ulFreq = freqs.second
                    }

                    if (id.ci > 0 && id.ci != Int.MAX_VALUE) {
                        val decoded = BandMapper.decodeLteCellId(id.ci.toLong())
                        enbOrGnb = decoded.first.toString()
                        sector = decoded.second.toString()
                    }

                    val ss = servingCell.cellSignalStrength
                    rsrp = BandMapper.sanitizeMetric(ss.rsrp)
                    rsrq = BandMapper.sanitizeMetric(ss.rsrq)
                    rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) BandMapper.sanitizeMetric(ss.rssi) else null
                    sinr = BandMapper.sanitizeSinr(ss.rssnr.toDouble())
                    cqi = ss.cqi.takeIf { it in 1..15 }
                    ta = if (ss.timingAdvance in 0..1282) ss.timingAdvance else null
                    servingRsrp = rsrp
                }
                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && servingCell is CellInfoNr) {
                        tech = "5G SA"
                        val id = servingCell.cellIdentity as? CellIdentityNr
                        if (id != null) {
                            val mcc = id.mccString
                            val mnc = id.mncString
                            if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) plmn = "$mcc$mnc"
                            tac = if (id.tac != Int.MAX_VALUE && id.tac > 0) id.tac.toString() else "N/A"
                            ci = if (id.nci != Long.MAX_VALUE && id.nci > 0L) id.nci.toString() else "N/A"
                            pci = if (id.pci != Int.MAX_VALUE) id.pci else 0
                            arfcn = if (id.nrarfcn != Int.MAX_VALUE && id.nrarfcn > 0) id.nrarfcn else 0
                            band = if (arfcn > 0) {
                                BandMapper.nrArfcnToBand(arfcn)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                id.bands.firstOrNull()?.let { b ->
                                    "n$b (${BandMapper.nrBandToFreqLabel(b)})"
                                } ?: "Unknown NR Band"
                            } else "Unknown NR Band"
                            val freqs = BandMapper.nrArfcnToFrequencies(arfcn)
                            if (freqs.first > 0) {
                                dlFreq = freqs.first
                                ulFreq = freqs.second
                            }

                            if (id.nci > 0L && id.nci != Long.MAX_VALUE) {
                                val decoded = BandMapper.decodeNrCellId(id.nci)
                                enbOrGnb = decoded.first.toString()
                                sector = decoded.second.toString()
                            }
                        }

                        val ss = servingCell.cellSignalStrength as? CellSignalStrengthNr
                        if (ss != null) {
                            ssRsrp = BandMapper.sanitizeMetric(ss.ssRsrp)
                            ssRsrq = BandMapper.sanitizeMetric(ss.ssRsrq)
                            ssSinr = BandMapper.sanitizeSinr(ss.ssSinr.toDouble())
                            csiRsrp = BandMapper.sanitizeMetric(ss.csiRsrp)
                            csiRsrq = BandMapper.sanitizeMetric(ss.csiRsrq)
                            csiSinr = BandMapper.sanitizeSinr(ss.csiSinr.toDouble())

                            rsrp = ssRsrp ?: csiRsrp
                            rsrq = ssRsrq ?: csiRsrq
                            sinr = ssSinr ?: csiSinr
                            servingRsrp = rsrp
                        }
                    } else if (servingCell is CellInfoGsm) {
                        tech = "2G GSM"
                        val id = servingCell.cellIdentity
                        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else if (id.mcc != Int.MAX_VALUE) id.mcc.toString() else null
                        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else if (id.mnc != Int.MAX_VALUE) id.mnc.toString() else null
                        if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) plmn = "$mcc$mnc"
                        tac = if (id.lac != Int.MAX_VALUE && id.lac > 0) id.lac.toString() else "N/A"
                        ci = if (id.cid != Int.MAX_VALUE && id.cid > 0) id.cid.toString() else "N/A"
                        arfcn = if (id.arfcn != Int.MAX_VALUE) id.arfcn else 0
                        band = "GSM 900/1800"
                        val ss = servingCell.cellSignalStrength
                        rsrp = BandMapper.sanitizeMetric(ss.dbm)
                        rssi = BandMapper.sanitizeMetric(ss.dbm)
                        servingRsrp = rsrp
                    } else if (servingCell is CellInfoWcdma) {
                        tech = "3G UMTS"
                        val id = servingCell.cellIdentity
                        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else if (id.mcc != Int.MAX_VALUE) id.mcc.toString() else null
                        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else if (id.mnc != Int.MAX_VALUE) id.mnc.toString() else null
                        if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) plmn = "$mcc$mnc"
                        tac = if (id.lac != Int.MAX_VALUE && id.lac > 0) id.lac.toString() else "N/A"
                        ci = if (id.cid != Int.MAX_VALUE && id.cid > 0) id.cid.toString() else "N/A"
                        arfcn = if (id.uarfcn != Int.MAX_VALUE) id.uarfcn else 0
                        band = "Band 1 (2100)"
                        val ss = servingCell.cellSignalStrength
                        rsrp = BandMapper.sanitizeMetric(ss.dbm)
                        servingRsrp = rsrp
                    }
                }
            }
        } else {
            // Secondary SIM or active data SIM where modem hasn't reported CellInfo in allCellInfo:
            val canReadPhoneState = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            // 1. Try resolving CellIdentity from ServiceState (API 30+ / Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && canReadPhoneState) {
                try {
                    val sState = tm.serviceState
                    val regList = sState?.networkRegistrationInfoList ?: emptyList()
                    for (nri in regList) {
                        val cid = nri.cellIdentity ?: continue
                        if (cid is CellIdentityLte) {
                            val ciVal = if (cid.ci != Int.MAX_VALUE && cid.ci > 0) cid.ci else 0
                            if (ciVal > 0) {
                                ci = ciVal.toString()
                                val decoded = BandMapper.decodeLteCellId(ciVal.toLong())
                                if (decoded.first > 0) enbOrGnb = decoded.first.toString()
                                if (decoded.second > 0) sector = decoded.second.toString()
                            }
                            if (cid.tac != Int.MAX_VALUE && cid.tac > 0) tac = cid.tac.toString()
                            if (cid.pci != Int.MAX_VALUE && cid.pci > 0) pci = cid.pci
                            if (cid.earfcn != Int.MAX_VALUE && cid.earfcn > 0) {
                                arfcn = cid.earfcn
                                band = BandMapper.lteEarfcnToBand(arfcn)
                                val freqs = BandMapper.lteEarfcnToFrequencies(arfcn)
                                if (freqs.first > 0) {
                                    dlFreq = freqs.first
                                    ulFreq = freqs.second
                                }
                            }
                            val mcc = cid.mccString
                            val mnc = cid.mncString
                            if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) plmn = "$mcc$mnc"
                            tech = "4G LTE"
                            break
                        } else if (cid is CellIdentityNr) {
                            val nciVal = if (cid.nci != Long.MAX_VALUE && cid.nci > 0L) cid.nci else 0L
                            if (nciVal > 0L) {
                                ci = nciVal.toString()
                                val decoded = BandMapper.decodeNrCellId(nciVal)
                                if (decoded.first > 0) enbOrGnb = decoded.first.toString()
                                if (decoded.second > 0) sector = decoded.second.toString()
                            }
                            if (cid.tac != Int.MAX_VALUE && cid.tac > 0) tac = cid.tac.toString()
                            if (cid.pci != Int.MAX_VALUE && cid.pci > 0) pci = cid.pci
                            val nrBandRaw = if (cid.nrarfcn != Int.MAX_VALUE && cid.nrarfcn > 0) {
                                arfcn = cid.nrarfcn
                                BandMapper.nrArfcnToBand(arfcn)
                            } else {
                                cid.bands.firstOrNull()?.let { b -> "n$b (${BandMapper.nrBandToFreqLabel(b)})" } ?: "Unknown NR Band"
                            }
                            band = nrBandRaw
                            if (arfcn > 0) {
                                val freqs = BandMapper.nrArfcnToFrequencies(arfcn)
                                if (freqs.first > 0) {
                                    dlFreq = freqs.first
                                    ulFreq = freqs.second
                                }
                            }
                            val mcc = cid.mccString
                            val mnc = cid.mncString
                            if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) plmn = "$mcc$mnc"
                            tech = "5G SA"
                            break
                        }
                    }
                } catch (e: Exception) {
                    // ServiceState query fallback
                }
            }

            // 2. Extract hardware SignalStrength directly from subTm (without early break so LTE + NR are both captured)
            try {
                val ss = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tm.signalStrength else null
                if (ss != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        for (css in ss.cellSignalStrengths) {
                            if (css is CellSignalStrengthLte) {
                                if (tech == "Searching") tech = "4G LTE"
                                if (rsrp == null) rsrp = BandMapper.sanitizeMetric(css.rsrp)
                                if (rsrq == null) rsrq = BandMapper.sanitizeMetric(css.rsrq)
                                if (rssi == null) rssi = BandMapper.sanitizeMetric(css.rssi)
                                if (sinr == null) sinr = BandMapper.sanitizeSinr(css.rssnr.toDouble())
                            } else if (css is CellSignalStrengthNr) {
                                if (ssRsrp == null) ssRsrp = BandMapper.sanitizeMetric(css.ssRsrp)
                                if (ssRsrq == null) ssRsrq = BandMapper.sanitizeMetric(css.ssRsrq)
                                if (ssSinr == null) ssSinr = BandMapper.sanitizeSinr(css.ssSinr.toDouble())
                                if (csiRsrp == null) csiRsrp = BandMapper.sanitizeMetric(css.csiRsrp)
                                if (csiRsrq == null) csiRsrq = BandMapper.sanitizeMetric(css.csiRsrq)
                                if (csiSinr == null) csiSinr = BandMapper.sanitizeSinr(css.csiSinr.toDouble())
                                if (tech == "Searching") {
                                    tech = "5G SA"
                                    if (rsrp == null) rsrp = ssRsrp ?: csiRsrp
                                    if (rsrq == null) rsrq = ssRsrq ?: csiRsrq
                                    if (sinr == null) sinr = ssSinr ?: csiSinr
                                }
                            } else if (css is CellSignalStrengthGsm) {
                                if (tech == "Searching") tech = "2G GSM"
                                if (rsrp == null) rsrp = BandMapper.sanitizeMetric(css.dbm)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val gsmLevel = ss.gsmSignalStrength
                        if (gsmLevel in 0..31 && rsrp == null) {
                            rsrp = -113 + (2 * gsmLevel)
                            tech = "Cellular"
                        }
                    }
                }
                if (tech == "Searching" && canReadPhoneState) {
                    tech = getNetworkTypeDescription(tm.dataNetworkType.takeIf { it != TelephonyManager.NETWORK_TYPE_UNKNOWN } ?: tm.voiceNetworkType)
                }
                servingRsrp = rsrp

                if (tac == "N/A") {
                    tac = if (isDataActive) "RRC Connected" else "Standby (DSDS)"
                }
                if (enbOrGnb == "N/A") enbOrGnb = "Na příjmu"
                if (sector == "N/A") sector = "Na příjmu"
                if (ci == "N/A") ci = "Na příjmu"
                if (band == "N/A") {
                    band = if (isDataActive) "$tech (Datový přenos)" else "$tech (Pohotovost)"
                }
                if (plmn.isEmpty() || plmn == "N/A") {
                    plmn = when {
                        targetPlmn.isNotBlank() -> targetPlmn
                        subPlmn.isNotBlank() -> subPlmn
                        knownTargetMnc != null -> "230$knownTargetMnc"
                        targetCarrier.contains("t-mobile") || targetCarrier.contains("tmobile") -> "23001"
                        targetCarrier.contains("vodafone") -> "23003"
                        targetCarrier.contains("o2") -> "23002"
                        else -> "23001"
                    }
                }
            } catch (e: Exception) {
                // Secondary SIM fallback
            }
        }

        // Extract 5G NR secondary cell info when registered alongside LTE anchor in 5G NSA EN-DC
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && registeredNrSecondaryCell is CellInfoNr) {
            val idNr = registeredNrSecondaryCell.cellIdentity as? CellIdentityNr
            if (idNr != null) {
                nrPci = idNr.pci.takeIf { it != Int.MAX_VALUE && it >= 0 }
                nrArfcn = idNr.nrarfcn.takeIf { it != Int.MAX_VALUE && it > 0 }
                nrBand = nrArfcn?.let { BandMapper.nrArfcnToBand(it) }
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        idNr.bands.firstOrNull()?.let { b -> "n$b (${BandMapper.nrBandToFreqLabel(b)})" }
                    } else null
                nrDlFreq = nrArfcn?.let { BandMapper.nrArfcnToFrequencies(it).first.takeIf { f -> f > 0.0 } }
            }
            val ssNr = registeredNrSecondaryCell.cellSignalStrength as? CellSignalStrengthNr
            if (ssNr != null) {
                if (ssRsrp == null) ssRsrp = BandMapper.sanitizeMetric(ssNr.ssRsrp)
                if (ssRsrq == null) ssRsrq = BandMapper.sanitizeMetric(ssNr.ssRsrq)
                if (ssSinr == null) ssSinr = BandMapper.sanitizeSinr(ssNr.ssSinr.toDouble())
                if (csiRsrp == null) csiRsrp = BandMapper.sanitizeMetric(ssNr.csiRsrp)
                if (csiRsrq == null) csiRsrq = BandMapper.sanitizeMetric(ssNr.csiRsrq)
                if (csiSinr == null) csiSinr = BandMapper.sanitizeSinr(ssNr.csiSinr.toDouble())
            }
        }

        // Instant 1 Hz hardware SignalStrength enrichment from subTm — capture BOTH LTE and NR without early break!
        try {
            val instantSs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tm.signalStrength else null
            if (instantSs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                for (css in instantSs.cellSignalStrengths) {
                    if (css is CellSignalStrengthLte && !tech.contains("5G SA")) {
                        val freshRsrp = BandMapper.sanitizeMetric(css.rsrp)
                        val freshRsrq = BandMapper.sanitizeMetric(css.rsrq)
                        val freshRssi = BandMapper.sanitizeMetric(css.rssi)
                        val freshSinr = BandMapper.sanitizeSinr(css.rssnr.toDouble())
                        val freshCqi = css.cqi.takeIf { it in 1..15 }
                        if (freshRsrp != null) rsrp = freshRsrp
                        if (freshRsrq != null) rsrq = freshRsrq
                        if (freshRssi != null) rssi = freshRssi
                        if (freshSinr != null) sinr = freshSinr
                        if (freshCqi != null && cqi == null) cqi = freshCqi
                    } else if (css is CellSignalStrengthNr) {
                        val freshSsRsrp = BandMapper.sanitizeMetric(css.ssRsrp)
                        val freshSsRsrq = BandMapper.sanitizeMetric(css.ssRsrq)
                        val freshSsSinr = BandMapper.sanitizeSinr(css.ssSinr.toDouble())
                        val freshCsiRsrp = BandMapper.sanitizeMetric(css.csiRsrp)
                        val freshCsiRsrq = BandMapper.sanitizeMetric(css.csiRsrq)
                        val freshCsiSinr = BandMapper.sanitizeSinr(css.csiSinr.toDouble())
                        if (freshSsRsrp != null) ssRsrp = freshSsRsrp
                        if (freshSsRsrq != null) ssRsrq = freshSsRsrq
                        if (freshSsSinr != null) ssSinr = freshSsSinr
                        if (freshCsiRsrp != null) csiRsrp = freshCsiRsrp
                        if (freshCsiRsrq != null) csiRsrq = freshCsiRsrq
                        if (freshCsiSinr != null) csiSinr = freshCsiSinr
                        if (tech.contains("5G SA")) {
                            (freshSsRsrp ?: freshCsiRsrp)?.let { rsrp = it }
                            (freshSsRsrq ?: freshCsiRsrq)?.let { rsrq = it }
                            (freshSsSinr ?: freshCsiSinr)?.let { sinr = it }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Signal strength query fallback
        }

        // Parse neighbors strictly avoiding cells assigned to serving/SCG slots or belonging to the other operator's PLMN
        for (cell in cellList) {
            if (cell == servingCell || cell == registeredNrSecondaryCell || cell.isRegistered || cell in assignedCells) continue
            val cellPlmn = getCellPlmn(cell)
            if (cellPlmn.isNotEmpty() && !isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier)) {
                continue
            }
            var nTech = "LTE"
            var nPci = -1
            var nArfcn = 0
            var nBand = "N/A"
            var nRsrp = -120
            var nDl: Double? = null
            var nUl: Double? = null

            if (cell is CellInfoLte) {
                nTech = "LTE"
                nPci = if (cell.cellIdentity.pci != Int.MAX_VALUE) cell.cellIdentity.pci else -1
                nArfcn = if (cell.cellIdentity.earfcn != Int.MAX_VALUE && cell.cellIdentity.earfcn > 0) cell.cellIdentity.earfcn else 0
                nBand = if (nArfcn > 0) {
                    BandMapper.lteEarfcnToBand(nArfcn)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cell.cellIdentity.bands.firstOrNull()?.let { b -> "B$b (${BandMapper.lteBandToFreqLabel(b)})" } ?: "Unknown Band"
                } else "Unknown Band"
                val freqs = BandMapper.lteEarfcnToFrequencies(nArfcn)
                nDl = freqs.first
                nUl = freqs.second
                nRsrp = BandMapper.sanitizeMetric(cell.cellSignalStrength.rsrp)
                    ?: BandMapper.sanitizeMetric(cell.cellSignalStrength.dbm)
                    ?: -120
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
                // If modem omitted MCC/MNC on an NR neighbor, only attribute it to the data-active (or sole) SIM
                if (cellPlmn.isEmpty() && !isDataActive && !isSingleSim) continue
                nTech = "5G NR"
                val id = cell.cellIdentity as? CellIdentityNr
                nPci = id?.pci?.takeIf { it != Int.MAX_VALUE && it >= 0 } ?: -1
                nArfcn = id?.nrarfcn?.takeIf { it != Int.MAX_VALUE && it > 0 } ?: 0
                nBand = if (nArfcn > 0) {
                    BandMapper.nrArfcnToBand(nArfcn)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    id?.bands?.firstOrNull()?.let { b -> "n$b (${BandMapper.nrBandToFreqLabel(b)})" } ?: "Unknown NR Band"
                } else "Unknown NR Band"
                val freqs = BandMapper.nrArfcnToFrequencies(nArfcn)
                nDl = freqs.first
                nUl = freqs.second
                val ss = cell.cellSignalStrength as? CellSignalStrengthNr
                nRsrp = BandMapper.sanitizeMetric(ss?.ssRsrp)
                    ?: BandMapper.sanitizeMetric(ss?.csiRsrp)
                    ?: BandMapper.sanitizeMetric(ss?.dbm)
                    ?: -120
            } else {
                continue
            }

            if (nPci >= 0 && nRsrp > -140) {
                val delta = (servingRsrp ?: -95) - nRsrp
                neighbors.add(
                    CellNeighbor(
                        pci = nPci,
                        tech = nTech,
                        earfcn = nArfcn,
                        band = nBand,
                        rsrp = nRsrp,
                        deltaFromServingDb = delta,
                        dlFreqMhz = nDl,
                        ulFreqMhz = nUl
                    )
                )
            }
        }

        // Distance from Timing Advance
        val estimatedDist = ta?.let { BandMapper.calculateLteDistanceMeters(it, multiplierMode = taMultiplierMode) }

        // Bandwidth & Aggregated CA Bandwidth (5G NR + LTE-A via ServiceState.cellBandwidths & PhysicalChannelConfig)
        val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val serviceStateObj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && hasPhoneStatePerm) {
            try { tm.serviceState } catch (_: Exception) { null }
        } else null
        val serviceStateBandwidthsKhz = serviceStateObj?.let {
            try { it.cellBandwidths.filter { bw -> bw > 0 } } catch (_: Exception) { emptyList() }
        } ?: emptyList()

        val channelsSnapshot = if (subId != -1) {
            activeCarrierChannelsBySubId[subId]
                ?: if (isDataActive) activeCarrierChannelsBySubId[-1].orEmpty() else emptyList()
        } else {
            activeCarrierChannelsBySubId[-1].orEmpty()
        }

        val nrPhysChannel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            channelsSnapshot.firstOrNull { it.networkType == TelephonyManager.NETWORK_TYPE_NR }
        } else null
        if (nrPhysChannel != null) {
            if (nrBand == null && nrPhysChannel.band > 0) {
                val freqLbl = BandMapper.nrBandToFreqLabel(nrPhysChannel.band)
                nrBand = "n${nrPhysChannel.band} ($freqLbl)"
            }
            if (nrArfcn == null && nrPhysChannel.dlChannelNumber > 0) {
                nrArfcn = nrPhysChannel.dlChannelNumber
            }
            if (nrDlFreq == null) {
                nrDlFreq = when {
                    nrPhysChannel.dlFreqKhz > 0 -> nrPhysChannel.dlFreqKhz / 1000.0
                    nrArfcn != null -> BandMapper.nrArfcnToFrequencies(nrArfcn!!).first.takeIf { it > 0.0 }
                    else -> null
                }
            }
        }

        val subOverrideType = overrideNetworkTypeBySubId[subId]
            ?: if (isDataActive || isSingleSim) overrideNetworkTypeBySubId[-1] ?: 0 else 0

        val isNrConnectedBySs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceStateObj != null) {
            try {
                serviceStateObj.networkRegistrationInfoList.any {
                    it.accessNetworkTechnology == TelephonyManager.NETWORK_TYPE_NR
                } || serviceStateObj.toString().contains("nrState=CONNECTED")
            } catch (_: Throwable) { false }
        } else false

        val isNrNotRestrictedBySs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && serviceStateObj != null) {
            try {
                serviceStateObj.toString().contains("nrState=NOT_RESTRICTED")
            } catch (_: Throwable) { false }
        } else false

        val hasNrSignalData = (ssRsrp != null || csiRsrp != null || ssSinr != null || csiSinr != null)
        val isDataNr = try {
            hasPhoneStatePerm && tm.dataNetworkType == TelephonyManager.NETWORK_TYPE_NR
        } catch (_: Exception) { false }

        val (resolvedTech, nrStateLabel) = classifyCellularGeneration(
            baseTech = tech,
            overrideNetworkType = subOverrideType,
            isNrConnectedByServiceState = isNrConnectedBySs,
            isNrNotRestrictedByServiceState = isNrNotRestrictedBySs,
            hasNrSecondaryCell = registeredNrSecondaryCell != null,
            hasNrPhysicalChannel = nrPhysChannel != null,
            hasNrSignalStrength = hasNrSignalData,
            isDataNetworkNr = isDataNr
        )
        tech = resolvedTech

        if (bandwidthKhz == null) {
            bandwidthKhz = when {
                tech == "5G SA" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    nrPhysChannel?.bandwidthKhz ?: serviceStateBandwidthsKhz.maxOrNull()
                else -> channelsSnapshot.firstOrNull()?.bandwidthKhz ?: serviceStateBandwidthsKhz.firstOrNull()
            }
        }

        val totalAggregatedBwKhz: Int? = when {
            channelsSnapshot.size > 1 -> channelsSnapshot.sumOf { it.bandwidthKhz }
            serviceStateBandwidthsKhz.size > 1 -> serviceStateBandwidthsKhz.sum()
            else -> null
        }

        // Carrier Aggregation / 5G EN-DC summary + explicit list of all aggregated frequencies
        val registeredCompatibleCells = cellList.filter { cell ->
            cell.isRegistered && (
                isCellCompatible(cell, targetPlmn, knownTargetMnc, targetCarrier) ||
                    (cell == registeredNrSecondaryCell)
                )
        }

        val registeredCompatibleBands = registeredCompatibleCells
            .mapNotNull { c ->
                when (c) {
                    is CellInfoLte -> BandMapper.lteEarfcnToBand(c.cellIdentity.earfcn).substringBefore(" ").takeIf { it.startsWith("B") }
                    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c is CellInfoNr) {
                        val id = c.cellIdentity as? CellIdentityNr
                        id?.let { BandMapper.nrArfcnToBand(it.nrarfcn).substringBefore(" ") }
                    } else null
                }
            }.distinct()

        val caPhysicalDetails = channelsSnapshot.mapIndexedNotNull { idx, ch ->
            val isNrChannel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ch.networkType == TelephonyManager.NETWORK_TYPE_NR
            val prefix = if (isNrChannel) "n" else "B"
            val bandTag = if (ch.band > 0) "$prefix${ch.band}" else null

            val exactDlMhz: Double? = when {
                ch.dlFreqKhz > 0 -> ch.dlFreqKhz / 1000.0
                ch.dlChannelNumber > 0 -> {
                    val f = if (isNrChannel) BandMapper.nrArfcnToFrequencies(ch.dlChannelNumber).first
                    else BandMapper.lteEarfcnToFrequencies(ch.dlChannelNumber).first
                    if (f > 0.0) f else null
                }
                else -> null
            }
            val bandRangeLabel = if (ch.band > 0) {
                if (isNrChannel) BandMapper.nrBandToFreqLabel(ch.band) else BandMapper.lteBandToFreqLabel(ch.band)
            } else ""

            val roleTag = if (ch.isPrimary || idx == 0) "PCC" else "SCC$idx"
            val freqPart = when {
                exactDlMhz != null && bandRangeLabel.isNotBlank() ->
                    String.format(java.util.Locale.US, "%.1f MHz (%s)", exactDlMhz, bandRangeLabel)
                exactDlMhz != null ->
                    String.format(java.util.Locale.US, "%.1f MHz", exactDlMhz)
                bandRangeLabel.isNotBlank() -> bandRangeLabel
                else -> null
            }

            if (bandTag != null || freqPart != null) {
                val head = listOfNotNull(bandTag, freqPart).joinToString(" • ")
                "$roleTag: $head • ${ch.bandwidthKhz / 1000} MHz"
            } else {
                "$roleTag: ${ch.bandwidthKhz / 1000} MHz"
            }
        }

        val registeredCarrierDetails = registeredCompatibleCells.mapNotNull { c ->
            when (c) {
                is CellInfoLte -> {
                    val earfcn = c.cellIdentity.earfcn
                    if (earfcn != Int.MAX_VALUE && earfcn > 0) {
                        val bShort = BandMapper.lteEarfcnToBand(earfcn).substringBefore(" ")
                        val bFull = BandMapper.lteEarfcnToBand(earfcn)
                        val dlF = BandMapper.lteEarfcnToFrequencies(earfcn).first
                        val bwPart = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && c.cellIdentity.bandwidth in 1..200000) {
                            " • ${c.cellIdentity.bandwidth / 1000} MHz"
                        } else ""
                        if (dlF > 0.0) String.format(java.util.Locale.US, "%s • %.1f MHz%s", bShort, dlF, bwPart)
                        else "$bFull$bwPart"
                    } else null
                }
                else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c is CellInfoNr) {
                    val id = c.cellIdentity as? CellIdentityNr
                    val nrarfcn = id?.nrarfcn ?: Int.MAX_VALUE
                    if (nrarfcn != Int.MAX_VALUE && nrarfcn > 0) {
                        val bShort = BandMapper.nrArfcnToBand(nrarfcn).substringBefore(" ")
                        val bFull = BandMapper.nrArfcnToBand(nrarfcn)
                        val dlF = BandMapper.nrArfcnToFrequencies(nrarfcn).first
                        if (dlF > 0.0) String.format(java.util.Locale.US, "%s • %.1f MHz", bShort, dlF)
                        else bFull
                    } else null
                } else null
            }
        }.distinct()

        val aggregatedCarriersList: List<String> = when {
            caPhysicalDetails.size > 1 -> caPhysicalDetails
            caPhysicalDetails.size == 1 && registeredCarrierDetails.size > 1 ->
                (caPhysicalDetails + registeredCarrierDetails).distinct()
            registeredCarrierDetails.size > 1 -> registeredCarrierDetails
            caPhysicalDetails.isNotEmpty() && totalAggregatedBwKhz != null -> caPhysicalDetails
            else -> emptyList()
        }

        val caPhysicalLabels = channelsSnapshot.mapNotNull { ch ->
            if (ch.band > 0) {
                val isNrChannel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ch.networkType == TelephonyManager.NETWORK_TYPE_NR
                val prefix = if (isNrChannel) "n" else "B"
                val bandRange = if (isNrChannel) BandMapper.nrBandToFreqLabel(ch.band) else BandMapper.lteBandToFreqLabel(ch.band)
                val rangeShort = bandRange.substringBefore(" ")
                if (rangeShort.isNotBlank() && rangeShort.all { it.isDigit() }) {
                    "$prefix${ch.band}/${rangeShort}M (${ch.bandwidthKhz / 1000}M)"
                } else {
                    "$prefix${ch.band} (${ch.bandwidthKhz / 1000}M)"
                }
            } else null
        }.distinct()

        val combinedCaBands = if (caPhysicalLabels.size > 1) caPhysicalLabels else registeredCompatibleBands
        val totalBwSuffix = totalAggregatedBwKhz?.let { " • ${it / 1000} MHz" } ?: ""

        val isNsaOverride = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            (subOverrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA ||
                subOverrideType == 4 /* OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE */)
        val isNrAdvOverride = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            subOverrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED
        val isLteCaOverride = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            subOverrideType == TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA

        val caSummary: String? = when {
            tech == "5G NSA" && (isNsaOverride || registeredNrSecondaryCell != null || nrPhysChannel != null) -> {
                val anchor = band.substringBefore(" ")
                val nrTag = nrBand?.substringBefore(" ")
                val nsaBands = if (combinedCaBands.size > 1) combinedCaBands
                else listOfNotNull(anchor.takeIf { it.isNotBlank() && it != "N/A" }, nrTag).distinct()
                if (nsaBands.size > 1) "5G NSA EN-DC (${nsaBands.joinToString("+")}$totalBwSuffix)"
                else "5G NSA EN-DC ($anchor$totalBwSuffix)"
            }
            tech == "5G SA" && (isNrAdvOverride || combinedCaBands.size > 1 || totalAggregatedBwKhz != null) -> {
                if (combinedCaBands.size > 1) "5G SA NR CA (${combinedCaBands.joinToString("+")}$totalBwSuffix)"
                else "5G SA Standalone$totalBwSuffix"
            }
            isNrAdvOverride -> {
                if (combinedCaBands.size > 1) "5G+ NR CA (${combinedCaBands.joinToString("+")}$totalBwSuffix)"
                else "5G+ NR Carrier Aggregation$totalBwSuffix"
            }
            isLteCaOverride -> {
                if (combinedCaBands.size > 1) "LTE-A ${combinedCaBands.size}CA (${combinedCaBands.joinToString("+")}$totalBwSuffix)"
                else "LTE-A Carrier Aggregation$totalBwSuffix"
            }
            combinedCaBands.size > 1 -> {
                "${combinedCaBands.size}CA (${combinedCaBands.joinToString("+")}$totalBwSuffix)"
            }
            totalAggregatedBwKhz != null -> "CA Aggregated (${totalAggregatedBwKhz / 1000} MHz)"
            else -> null
        }

        // Anomaly & Rogue BTS detector
        val detectedAnomalies = BandMapper.detectCellularAnomalies(
            networkGen = tech,
            tac = tac,
            enbOrGnbId = enbOrGnb,
            sectorId = sector,
            rsrp = rsrp,
            sinr = sinr,
            neighborsCount = neighbors.size,
            isServing = servingCell != null || (rsrp != null && rsrp > -140)
        )

        val operatorName = if (displayName.isNotBlank()) displayName else tm.networkOperatorName.ifEmpty { "Cellular" }

        return SimCellInfo(
            slotIndex = slotIndex,
            subscriptionId = subId,
            displayName = operatorName,
            operatorAlpha = operatorName,
            networkGen = tech,
            carrierAggregationSummary = caSummary,
            totalAggregatedBandwidthKhz = totalAggregatedBwKhz,
            aggregatedCarriers = aggregatedCarriersList,
            nrStateLabel = nrStateLabel,
            nrBand = nrBand,
            nrArfcn = nrArfcn,
            nrPci = nrPci,
            nrDlFreqMhz = nrDlFreq,
            isServing = servingCell != null || (rsrp != null && rsrp > -140),
            isDataActive = isDataActive,
            plmn = plmn,
            tac = tac,
            ci = ci,
            enbOrGnbId = enbOrGnb,
            sectorId = sector,
            pci = pci,
            earfcnOrArfcn = arfcn,
            band = band,
            channelBandwidthKhz = bandwidthKhz,
            dlFreqMhz = dlFreq,
            ulFreqMhz = ulFreq,
            rsrp = rsrp,
            rsrq = rsrq,
            rssi = rssi,
            sinr = sinr,
            cqi = cqi,
            ssRsrp = ssRsrp,
            ssRsrq = ssRsrq,
            ssSinr = ssSinr,
            csiRsrp = csiRsrp,
            csiRsrq = csiRsrq,
            csiSinr = csiSinr,
            timingAdvance = ta,
            estimatedDistanceM = estimatedDist,
            neighbors = neighbors.sortedByDescending { it.rsrp },
            anomalies = detectedAnomalies
        )
    }

    private fun getCellPlmn(cell: CellInfo): String {
        return when (cell) {
            is CellInfoLte -> {
                val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cell.cellIdentity.mccString else if (cell.cellIdentity.mcc != Int.MAX_VALUE) cell.cellIdentity.mcc.toString() else null
                val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cell.cellIdentity.mncString else if (cell.cellIdentity.mnc != Int.MAX_VALUE) cell.cellIdentity.mnc.toString() else null
                normalizePlmn(mcc, mnc)
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr) {
                    val id = cell.cellIdentity as? CellIdentityNr
                    val mcc = id?.mccString
                    val mnc = id?.mncString
                    normalizePlmn(mcc, mnc)
                } else if (cell is CellInfoGsm) {
                    val id = cell.cellIdentity
                    val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else if (id.mcc != Int.MAX_VALUE) id.mcc.toString() else null
                    val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else if (id.mnc != Int.MAX_VALUE) id.mnc.toString() else null
                    normalizePlmn(mcc, mnc)
                } else if (cell is CellInfoWcdma) {
                    val id = cell.cellIdentity
                    val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString else if (id.mcc != Int.MAX_VALUE) id.mcc.toString() else null
                    val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mncString else if (id.mnc != Int.MAX_VALUE) id.mnc.toString() else null
                    normalizePlmn(mcc, mnc)
                } else ""
            }
        }
    }

    private fun getNetworkTypeDescription(type: Int): String {
        return when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G SA"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_GSM -> "2G GSM"
            else -> "4G LTE"
        }
    }

    companion object {
        /**
         * Resolves cellular RAT generation cleanly distinguishing 4G LTE, 5G NSA (EN-DC active NR vs LTE anchor),
         * and 5G SA (Standalone gNodeB).
         */
        fun classifyCellularGeneration(
            baseTech: String,
            overrideNetworkType: Int,
            isNrConnectedByServiceState: Boolean,
            isNrNotRestrictedByServiceState: Boolean,
            hasNrSecondaryCell: Boolean,
            hasNrPhysicalChannel: Boolean,
            hasNrSignalStrength: Boolean,
            isDataNetworkNr: Boolean
        ): Pair<String, String?> {
            val isOverride5g = overrideNetworkType == 3 /* OVERRIDE_NETWORK_TYPE_NR_NSA */ ||
                overrideNetworkType == 4 /* OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE */ ||
                overrideNetworkType == 5 /* OVERRIDE_NETWORK_TYPE_NR_ADVANCED */

            if (baseTech == "5G SA" || baseTech == "5G NR") {
                return if (overrideNetworkType == 3 /* OVERRIDE_NETWORK_TYPE_NR_NSA */) {
                    "5G NSA" to "EN-DC NR Aktivní"
                } else {
                    "5G SA" to "5G Standalone (SA)"
                }
            }

            if (baseTech == "4G LTE") {
                val activeNrLeg = isNrConnectedByServiceState ||
                    hasNrSecondaryCell ||
                    hasNrPhysicalChannel ||
                    hasNrSignalStrength
                if (activeNrLeg) {
                    return "5G NSA" to "EN-DC NR Aktivní"
                }
                if (isOverride5g || isNrNotRestrictedByServiceState) {
                    return "5G NSA" to "EN-DC LTE Kotva (Idle)"
                }
                if (isDataNetworkNr) {
                    return "5G NSA" to "EN-DC NR Aktivní"
                }
            }

            return baseTech to null
        }
    }
}
