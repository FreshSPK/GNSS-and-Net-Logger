package com.example.ui.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*
import com.example.ui.theme.*

enum class SecuritySubTab {
    CHECKS,
    CLOCKS,
    PHYSICS,
    INCIDENTS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityTab(
    security: SecurityAnalysisState,
    isDemoMode: Boolean,
    lang: String,
    onSyncNtpNow: () -> Unit,
    onRunDeepScan: () -> Unit,
    onClearIncidents: () -> Unit,
    onSelectSimulatedAttack: (SimulatedAttackType) -> Unit
) {
    val isCs = lang != "EN"
    var activeSubTab by remember { mutableStateOf(SecuritySubTab.CHECKS) }
    var checkFilter by remember { mutableStateOf("ALL") }
    var showMethodologyDialog by remember { mutableStateOf(false) }
    var selectedCheckForDetail by remember { mutableStateOf<SecurityCheckResult?>(null) }

    val statusColor = when (security.overallStatus) {
        ThreatSeverity.CRITICAL -> RoseCritical
        ThreatSeverity.WARNING -> AmberWarning
        ThreatSeverity.INFO -> CyanTertiary
        ThreatSeverity.OK -> EmeraldSignal
    }

    val statusTitle = when (security.overallStatus) {
        ThreatSeverity.CRITICAL -> if (isCs) "KRITICKÝ POPLACH" else "CRITICAL THREAT"
        ThreatSeverity.WARNING -> if (isCs) "DETEKOVÁNA ANOMÁLIE" else "ANOMALY DETECTED"
        ThreatSeverity.INFO -> if (isCs) "INFORMAČNÍ STAV" else "INFORMATIONAL"
        ThreatSeverity.OK -> if (isCs) "ČISTÉ RF & GNSS PROSTŘEDÍ" else "CLEAN RF & GNSS SPECTRUM"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCs) "● DETEKCE PODVODŮ & RUŠENÍ" else "● SPOOFING, JAMMING & SECURITY",
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isCs) "Anti-spoofing GNSS, IMSI Catcher, fyzika TA & NTP čas" else "GNSS anti-spoofing, IMSI Catcher, TA physics & NTP clock",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }
            IconButton(
                onClick = { showMethodologyDialog = true },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("security_info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = if (isCs) "Metodika detekce" else "Detection Methodology",
                    tint = CyanTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Threat Gauge Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSlate800),
            border = BorderStroke(1.5.dp, statusColor.copy(alpha = 0.65f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(statusColor.copy(alpha = 0.14f), Color.Transparent)
                        )
                    )
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThreatRiskGauge(
                            riskScore = security.overallRiskScore,
                            accentColor = statusColor,
                            modifier = Modifier.size(92.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = statusTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val alertsCount = security.checks.count { it.severity == ThreatSeverity.WARNING || it.severity == ThreatSeverity.CRITICAL }
                            Text(
                                text = if (alertsCount == 0) {
                                    if (isCs) "Všech ${security.checks.size} bezpečnostních kontrol bez nálezu."
                                    else "All ${security.checks.size} security checks nominal."
                                } else {
                                    if (isCs) "Aktivní varování: $alertsCount z ${security.checks.size} kontrol."
                                    else "Active alerts: $alertsCount of ${security.checks.size} checks."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextPrimaryDark
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DomainScoreChip(
                                    label = "GNSS",
                                    score = security.gnssRiskScore,
                                    modifier = Modifier.weight(1f)
                                )
                                DomainScoreChip(
                                    label = if (isCs) "RF Síť" else "Cell RF",
                                    score = security.rfRiskScore,
                                    modifier = Modifier.weight(1f)
                                )
                                DomainScoreChip(
                                    label = if (isCs) "Čas" else "Clock",
                                    score = security.timeRiskScore,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRunDeepScan,
                            enabled = !security.isDeepScanning,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("security_deep_scan_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (security.isDeepScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = DarkSlate900
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = DarkSlate900,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (security.isDeepScanning) {
                                    if (isCs) "Skenuji..." else "Scanning..."
                                } else {
                                    if (isCs) "Hloubkový sken" else "Deep Scan"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkSlate900
                            )
                        }
                        OutlinedButton(
                            onClick = onSyncNtpNow,
                            enabled = !security.timeDiagnostics.isNtpSyncing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("security_ntp_top_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanSecondary),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            if (security.timeDiagnostics.isNtpSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanTertiary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = CyanTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (security.timeDiagnostics.isNtpSyncing) {
                                    if (isCs) "Ověřuji NTP..." else "Syncing NTP..."
                                } else {
                                    if (isCs) "Ověřit NTP čas" else "Sync NTP Time"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanTertiary
                            )
                        }
                    }

                    if (security.isDeepScanning || security.lastDeepScanTimeMs != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val scanFmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US) }
                        val alertsNow = security.checks.count { it.severity == ThreatSeverity.WARNING || it.severity == ThreatSeverity.CRITICAL }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("security_deep_scan_status"),
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSlate900.copy(alpha = 0.8f),
                            border = BorderStroke(
                                1.dp,
                                if (security.isDeepScanning) CyanPrimary.copy(alpha = 0.6f)
                                else statusColor.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (security.isDeepScanning) Icons.Default.Radar else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (security.isDeepScanning) CyanTertiary else statusColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val statusMsg = if (security.isDeepScanning) {
                                    if (isCs) {
                                        "Probíhá aktivní hloubkový sken: vyžaduji čerstvé buňky modemu, SNTP UDP:123 a kontrolu ${security.checks.size} detektorů..."
                                    } else {
                                        "Active deep scan: polling fresh modem cells, SNTP UDP:123 & auditing ${security.checks.size} detectors..."
                                    }
                                } else {
                                    val timeStr = security.lastDeepScanTimeMs?.let { scanFmt.format(java.util.Date(it)) } ?: "—"
                                    val rttPart = security.timeDiagnostics.ntpRttMs?.let { " • NTP RTT ${it}ms" } ?: ""
                                    if (isCs) {
                                        "Hloubkový sken dokončen ($timeStr): ${security.checks.size} kontrol, $alertsNow varování$rttPart"
                                    } else {
                                        "Deep scan finished ($timeStr): ${security.checks.size} checks, $alertsNow alerts$rttPart"
                                    }
                                }
                                Text(
                                    text = statusMsg,
                                    fontSize = 10.sp,
                                    color = if (security.isDeepScanning) CyanTertiary else TextSecondaryDark,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Demo Attack Simulator (only visible when Demo Mode is active)
        if (isDemoMode) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Science, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCs) "DEMO SIMULÁTOR ÚTOKŮ (TEST DETEKTORŮ)" else "DEMO ATTACK SIMULATOR (TEST DETECTORS)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val scenarios = listOf(
                            SimulatedAttackType.NONE to (if (isCs) "Normální" else "Normal"),
                            SimulatedAttackType.GNSS_SPOOFING to (if (isCs) "GNSS Spoof" else "GNSS Spoof"),
                            SimulatedAttackType.GNSS_JAMMING to (if (isCs) "GNSS Jamming" else "GNSS Jamming"),
                            SimulatedAttackType.IMSI_CATCHER_2G to "2G IMSI Catcher",
                            SimulatedAttackType.TA_POWER_SPOOF to (if (isCs) "TA + Výkon" else "TA + Power"),
                            SimulatedAttackType.TIME_DRIFT_ATTACK to (if (isCs) "Časový útok" else "Time Drift")
                        )
                        scenarios.forEach { (type, label) ->
                            FilterChip(
                                selected = security.activeSimulatedAttack == type,
                                onClick = { onSelectSimulatedAttack(type) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sub-Tab Switcher
        TabRow(
            selectedTabIndex = activeSubTab.ordinal,
            containerColor = DarkSlate800,
            contentColor = CyanTertiary
        ) {
            Tab(
                selected = activeSubTab == SecuritySubTab.CHECKS,
                onClick = { activeSubTab = SecuritySubTab.CHECKS },
                text = { Text(if (isCs) "Kontroly" else "Checks", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            )
            Tab(
                selected = activeSubTab == SecuritySubTab.CLOCKS,
                onClick = { activeSubTab = SecuritySubTab.CLOCKS },
                text = { Text(if (isCs) "Čas UTC" else "Clocks", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            )
            Tab(
                selected = activeSubTab == SecuritySubTab.PHYSICS,
                onClick = { activeSubTab = SecuritySubTab.PHYSICS },
                text = { Text(if (isCs) "Fyzika TA" else "Physics", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            )
            Tab(
                selected = activeSubTab == SecuritySubTab.INCIDENTS,
                onClick = { activeSubTab = SecuritySubTab.INCIDENTS },
                text = {
                    val cnt = security.recentIncidents.size
                    Text(
                        text = if (cnt > 0) "${if (isCs) "Deník" else "Log"} ($cnt)" else (if (isCs) "Deník" else "Log"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (activeSubTab) {
            SecuritySubTab.CHECKS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val alertCount = security.checks.count { it.severity == ThreatSeverity.WARNING || it.severity == ThreatSeverity.CRITICAL }
                    FilterChip(
                        selected = checkFilter == "ALL",
                        onClick = { checkFilter = "ALL" },
                        label = { Text(if (isCs) "Vše (${security.checks.size})" else "All (${security.checks.size})", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = checkFilter == "ALERTS",
                        onClick = { checkFilter = "ALERTS" },
                        label = { Text(if (isCs) "Anomálie ($alertCount)" else "Alerts ($alertCount)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = checkFilter == "GNSS",
                        onClick = { checkFilter = "GNSS" },
                        label = { Text("GNSS", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = checkFilter == "RF",
                        onClick = { checkFilter = "RF" },
                        label = { Text(if (isCs) "RF Sítě" else "Cell RF", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = checkFilter == "TIME",
                        onClick = { checkFilter = "TIME" },
                        label = { Text(if (isCs) "Čas" else "Time", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filtered = remember(security.checks, checkFilter) {
                    val base = when (checkFilter) {
                        "ALERTS" -> security.checks.filter { it.severity == ThreatSeverity.WARNING || it.severity == ThreatSeverity.CRITICAL }
                        "GNSS" -> security.checks.filter { it.category == ThreatCategory.GNSS_SPOOFING || it.category == ThreatCategory.GNSS_JAMMING }
                        "RF" -> security.checks.filter { it.category == ThreatCategory.CELLULAR_SPOOFING || it.category == ThreatCategory.CELLULAR_JAMMING }
                        "TIME" -> security.checks.filter { it.category == ThreatCategory.TIME_ANOMALY }
                        else -> security.checks
                    }
                    base.sortedByDescending { it.severity.ordinal }
                }

                if (filtered.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSignal)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isCs) "Žádné aktivní anomálie v této kategorii." else "No active anomalies in this category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EmeraldSignal
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filtered.forEach { chk ->
                            SecurityCheckCard(
                                check = chk,
                                isCs = isCs,
                                onClick = { selectedCheckForDetail = chk }
                            )
                        }
                    }
                }
            }

            SecuritySubTab.CLOCKS -> {
                TimeForensicsSubView(
                    timeDiag = security.timeDiagnostics,
                    isCs = isCs,
                    onSyncNtpNow = onSyncNtpNow
                )
            }

            SecuritySubTab.PHYSICS -> {
                PhysicsWorkbenchSubView(
                    gnssMetrics = security.gnssMetrics,
                    sim1 = security.sim1RfMetrics,
                    sim2 = security.sim2RfMetrics,
                    isCs = isCs
                )
            }

            SecuritySubTab.INCIDENTS -> {
                IncidentTimelineSubView(
                    incidents = security.recentIncidents,
                    isCs = isCs,
                    onClearIncidents = onClearIncidents
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_vut_logo),
                contentDescription = "VUT FEKT Brno Logo",
                modifier = Modifier
                    .height(28.dp)
                    .width(90.dp)
            )
        }
    }

    if (showMethodologyDialog) {
        SecurityMethodologyDialog(
            isCs = isCs,
            onDismiss = { showMethodologyDialog = false }
        )
    }

    selectedCheckForDetail?.let { detailCheck ->
        SecurityCheckDetailDialog(
            check = detailCheck,
            isCs = isCs,
            onDismiss = { selectedCheckForDetail = null }
        )
    }
}

@Composable
private fun ThreatRiskGauge(
    riskScore: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedSweep by animateFloatAsState(
        targetValue = (riskScore.coerceIn(0, 100) / 100f) * 260f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 220f),
        label = "riskSweep"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = DarkSlate700,
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animatedSweep > 1f) {
                drawArc(
                    color = accentColor,
                    startAngle = 140f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$riskScore%",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = accentColor
            )
            Text(
                text = "RISK",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun DomainScoreChip(
    label: String,
    score: Int,
    modifier: Modifier = Modifier
) {
    val c = when {
        score >= 60 -> RoseCritical
        score >= 25 -> AmberWarning
        else -> EmeraldSignal
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, color = TextSecondaryDark, maxLines = 1)
            Text(
                text = "$score%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = c
            )
        }
    }
}

@Composable
private fun SecurityCheckCard(
    check: SecurityCheckResult,
    isCs: Boolean,
    onClick: () -> Unit
) {
    val color = when (check.severity) {
        ThreatSeverity.CRITICAL -> RoseCritical
        ThreatSeverity.WARNING -> AmberWarning
        ThreatSeverity.INFO -> CyanTertiary
        ThreatSeverity.OK -> EmeraldSignal
    }
    val icon = when (check.severity) {
        ThreatSeverity.CRITICAL -> Icons.Default.GppBad
        ThreatSeverity.WARNING -> Icons.Default.Warning
        ThreatSeverity.INFO -> Icons.Default.Info
        ThreatSeverity.OK -> Icons.Default.VerifiedUser
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("security_check_card_${check.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (check.severity == ThreatSeverity.CRITICAL || check.severity == ThreatSeverity.WARNING) {
                color.copy(alpha = 0.13f)
            } else {
                DarkSlate800
            }
        ),
        border = BorderStroke(1.dp, color.copy(alpha = if (check.severity == ThreatSeverity.OK) 0.3f else 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCs) check.titleCs else check.titleEn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.22f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = check.severity.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isCs) check.summaryCs else check.summaryEn,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlate900, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = check.measuredValue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (isCs) "Norma" else "Norm"}: ${check.expectedRange}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondaryDark,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = if (isCs) "Zobrazit detail a výpočet" else "Show detail & calculation",
                    tint = CyanTertiary.copy(alpha = 0.8f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun SecurityMethodologyDialog(
    isCs: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSlate800,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CyanTertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCs) "Co vše dokáže mobil detekovat?" else "Smartphone Detection Capabilities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MethodSection(
                    title = if (isCs) "1. GNSS Anti-Spoofing & Fúze senzorů" else "1. GNSS Anti-Spoofing & Sensor Fusion",
                    body = if (isCs) {
                        "• Mock Location & ΔNMEA: kontrola isMock i shody surových vět \$GNGGA/\$GNRMC vs. Location API (> 35 m).\n" +
                            "• AGC, C/N0, Konstelace & L5: rušení AGC, uniformní C/N0 (σ < 2,3 dB-Hz), inverze elevace, skok C/N0 (≥ +9 dB v klidu), zmizení L5 či výpadek Galileo/GLONASS/BeiDou.\n" +
                            "• Doppler, ADR & Zamrznutí: kolaps Dopplera (σ_v < 45 m/s), opačné znaménko Dopplera vůči elevaci, ztráta fáze nosné ADR a zamrzlé nebe (≥ 60 s).\n" +
                            "• Fúze IMU, Magnetometru, Kroků & BTS: klid IMU vs. rychlost, zatáčení Gyroskop Δyaw vs. GNSS Δbearing, intenzita pole |B| (µT), kadence kroků (6 s), barometr Δh a GNSS vs. BTS (> 45 km)."
                    } else {
                        "• Mock Location & ΔNMEA: audits isMock plus raw \$GNGGA/\$GNRMC vs. Location API divergence (> 35 m).\n" +
                            "• AGC, C/N0, Constellations & L5: AGC jamming, flat C/N0 (σ < 2.3 dB-Hz), elevation inversion, stationary C/N0 takeover jump (≥ +9 dB), L5 drop, and GPS-only constellation drops.\n" +
                            "• Doppler, ADR & Freeze: Doppler spread collapse (σ_v < 45 m/s), opposite Doppler vs. elevation slope, lost ADR carrier phase, and frozen sky constellations (≥ 60s).\n" +
                            "• IMU, Mag, Cadence & BTS Fusion: stationary IMU vs. speed, Gyro Δyaw vs. GNSS bearing turn delta, magnetometer |B| (µT), walking cadence (6s), barometer Δh, and GNSS vs. BTS (> 45 km)."
                    }
                )
                MethodSection(
                    title = if (isCs) "2. Srovnání času (GNSS · Mobilní síť · NTP)" else "2. Multi-Clock Audit (GNSS · Network · NTP)",
                    body = if (isCs) {
                        "Porovnává v reálném čase přesný UTC čas ze satelitů (GnssClock i Fix), síťový/systémový čas NITZ a internetový NTP dotaz (UDP:123 s kompenzací RTT, limit 2 000 ms). Odhaluje replay útoky (meaconing), manipulaci NITZ i skoky krystalu GnssClock během aktivního fixu."
                    } else {
                        "Continuously compares GNSS UTC (GnssClock & Fix), carrier NITZ/System time, and live SNTP UTC (UDP:123 with RTT compensation, 2,000 ms green window) to catch GNSS replay, NITZ spoofing, and oscillator jumps during active fix."
                    }
                )
                MethodSection(
                    title = if (isCs) "3. RF fyzika, Dual-SIM & Síťový stack" else "3. Cellular Physics, Dual-SIM & Network Stack",
                    body = if (isCs) {
                        "• TA vs. BTS, RSRP & FSPL strop: kontrola ΔTA proti poloze BTS, path-loss model, strop volného prostoru FSPL (+12 dB) i náhlý pád TA při nehybném IMU.\n" +
                            "• IMSI Catcher, Test ID & Jamming: 2G downgrade, testovací PLMN/TAC/eNB, úzký LTE kanál (1,4/3 MHz), nízké CQI při silném RSRP, mezera RSSI−RSRP > 38 dB a privátní DNS na mobilních datech.\n" +
                            "• Dual-SIM korelace: porovnání SIM 1 vs. SIM 2 (jednostranný 2G TA=0 útok, odlišná lokalita BTS > 40 km či souběžné rušení)."
                    } else {
                        "• TA vs. BTS, RSRP & FSPL Ceiling: checks ΔTA against BTS position, path-loss corridor, FSPL ceiling (+12 dB), and abrupt TA drops while IMU-stationary.\n" +
                            "• Stingray, Test IDs & Jamming: 2G downgrade, test PLMN/TAC/eNB, narrow SDR BW (1.4/3 MHz), low CQI at strong RSRP, RSSI−RSRP > 38 dB, and private cellular DNS.\n" +
                            "• Dual-SIM Correlation: SIM 1 vs. SIM 2 audit for targeted single-SIM 2G/TA=0 attacks, > 40 km BTS divergence, or dual-band jamming."
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isCs) "Rozumím" else "Close")
            }
        }
    )
}

@Composable
private fun MethodSection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = CyanTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextPrimaryDark
        )
    }
}
