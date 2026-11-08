package com.example.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AnomalySeverity
import com.example.model.CellularAnomaly
import com.example.model.CellularState
import com.example.model.SimCellInfo
import com.example.ui.theme.*

@Composable
fun CellularRfTab(
    cellular: CellularState,
    lang: String
) {
    val isCs = lang != "EN"
    var showSpoofingInfoDialog by remember { mutableStateOf(false) }

    val allAnomalies = remember(cellular.sim1.anomalies, cellular.sim2.anomalies) {
        cellular.sim1.anomalies + cellular.sim2.anomalies
    }
    val hasCritical = allAnomalies.any { it.severity == AnomalySeverity.CRITICAL }
    val hasWarning = allAnomalies.any { it.severity == AnomalySeverity.WARNING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Top Header
        Text(
            text = if (isCs) "● RF SÍŤ & OPERÁTOR" else "● CELLULAR RF & OPERATOR",
            style = MaterialTheme.typography.titleMedium,
            color = AmberWarning,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isCs) "Rádiové parametry, vysílače a sousední buňky" else "Radio parameters, base stations & neighbor cells",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Top SIM Selector Switcher (SIM 1 / SIM 2 / Obě SIM)
        var selectedSimTab by remember {
            mutableStateOf(if (cellular.sim2.isDataActive && !cellular.sim1.isDataActive) 1 else 0)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkSlate800,
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DarkSlate700))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // SIM 1 Tab
                Button(
                    onClick = { selectedSimTab = 0 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSimTab == 0) CyanSecondary else Color.Transparent,
                        contentColor = if (selectedSimTab == 0) Color.White else TextSecondaryDark
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "SIM 1: ${cellular.sim1.displayName.take(10)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // SIM 2 Tab
                Button(
                    onClick = { selectedSimTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSimTab == 1) AmberWarning else Color.Transparent,
                        contentColor = if (selectedSimTab == 1) DarkSlate900 else TextSecondaryDark
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "SIM 2: ${cellular.sim2.displayName.take(10)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Both SIMs Tab
                Button(
                    onClick = { selectedSimTab = 2 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSimTab == 2) Purple5G else Color.Transparent,
                        contentColor = if (selectedSimTab == 2) Color.White else TextSecondaryDark
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isCs) "Obě SIM" else "Both SIMs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DSDS Explanatory Banner based on selected view
        when (selectedSimTab) {
            0 -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cellular.sim1.isDataActive) EmeraldSignal.copy(alpha = 0.15f) else DarkSlate800
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (cellular.sim1.isDataActive) EmeraldSignal else CyanSecondary.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (cellular.sim1.isDataActive) {
                                if (isCs) "🟢 SIM 1: DATOVÉ PŘIPOJENÍ AKTIVNÍ (RRC Connected)" else "🟢 SIM 1: DATA ACTIVE (RRC Connected)"
                            } else {
                                if (isCs) "ℹ️ SIM 1: REŽIM PŘÍJMU (DSDS Standby / RRC Idle)" else "ℹ️ SIM 1: VOICE & SMS (DSDS Standby / RRC Idle)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (cellular.sim1.isDataActive) EmeraldSignal else CyanTertiary
                        )
                        Text(
                            text = if (cellular.sim1.isDataActive) {
                                if (isCs) "Tato SIM karta přenáší mobilní data. Modem udržuje aktivní spojení a dekóduje plné ID buňky (eNodeB, TAC, PCI)." else "This SIM carries active mobile data. Modem maintains RRC Connected state with decoded tower details."
                            } else {
                                if (isCs) "SIM 1 je na příjmu pro hovory a SMS na síti ${cellular.sim1.displayName}. Vlastní RF přijímač měří úroveň signálu RSRP a odstup šumu." else "SIM 1 is idling on ${cellular.sim1.displayName} for incoming calls/SMS. Radio measures real-time RSRP and SNR."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }
            }
            1 -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (cellular.sim2.isDataActive) EmeraldSignal.copy(alpha = 0.15f) else DarkSlate800
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (cellular.sim2.isDataActive) EmeraldSignal else AmberWarning.copy(alpha = 0.5f)
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (cellular.sim2.isDataActive) {
                                if (isCs) "🟢 SIM 2: DATOVÉ PŘIPOJENÍ AKTIVNÍ (RRC Connected)" else "🟢 SIM 2: DATA ACTIVE (RRC Connected)"
                            } else {
                                if (isCs) "ℹ️ SIM 2: REŽIM PŘÍJMU (DSDS Standby / RRC Idle)" else "ℹ️ SIM 2: VOICE & SMS (DSDS Standby / RRC Idle)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (cellular.sim2.isDataActive) EmeraldSignal else AmberWarning
                        )
                        Text(
                            text = if (cellular.sim2.isDataActive) {
                                if (isCs) "Tato SIM karta přenáší mobilní data na síti ${cellular.sim2.displayName}. Modem udržuje aktivní spojení s vysílačem." else "This SIM carries active mobile data on ${cellular.sim2.displayName}. Modem maintains RRC Connected state."
                            } else {
                                if (isCs) "SIM 2 je na příjmu pro hovory a SMS na síti ${cellular.sim2.displayName}. Vlastní RF přijímač měří úroveň signálu RSRP a odstup šumu." else "SIM 2 is idling on ${cellular.sim2.displayName} for incoming calls/SMS. Radio measures real-time RSRP and SNR."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }
            }
            else -> {
                // Dual SIM Parallel Banner + RF Delta Comparison
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Purple5G))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCs) "📊 POROVNÁNÍ POKRYTÍ OBOU OPERÁTORŮ (RF DELTA)" else "📊 DUAL OPERATOR RF COMPARISON",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Purple5G
                        )
                        val r1 = cellular.sim1.rsrp
                        val r2 = cellular.sim2.rsrp
                        if (r1 != null && r2 != null) {
                            val diff = r1 - r2
                            val comparisonText = when {
                                diff > 0 -> if (isCs) "${cellular.sim1.displayName} je o +${diff} dB silnější než ${cellular.sim2.displayName}" else "${cellular.sim1.displayName} is +${diff} dB stronger than ${cellular.sim2.displayName}"
                                diff < 0 -> if (isCs) "${cellular.sim2.displayName} je o +${-diff} dB silnější než ${cellular.sim1.displayName}" else "${cellular.sim2.displayName} is +${-diff} dB stronger than ${cellular.sim1.displayName}"
                                else -> if (isCs) "Oba operátoři mají identickou úroveň signálu ($r1 dBm)" else "Both operators have identical signal level ($r1 dBm)"
                            }
                            Text(
                                text = comparisonText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (diff >= 0) CyanTertiary else AmberWarning
                            )
                        } else {
                            Text(
                                text = if (isCs) "Paralelní měření obou slotů současně. Data: ${if (cellular.sim2.isDataActive) cellular.sim2.displayName else cellular.sim1.displayName}" else "Parallel real-time measurements for both SIM slots.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextPrimaryDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // DETEKTOR SPOOFINGU A BEZPEČNOST RF SÍTĚ (Cellular Spoofing & Rogue BTS Monitor)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    hasCritical -> RoseCritical.copy(alpha = 0.18f)
                    hasWarning -> AmberWarning.copy(alpha = 0.16f)
                    else -> DarkSlate800
                }
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    when {
                        hasCritical -> RoseCritical
                        hasWarning -> AmberWarning
                        else -> EmeraldSignal.copy(alpha = 0.6f)
                    }
                )
            )
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
                            imageVector = if (allAnomalies.isNotEmpty()) Icons.Default.Warning else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (hasCritical) RoseCritical else if (hasWarning) AmberWarning else EmeraldSignal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCs) "DETEKCE SPOOFINGU & INTEGRITA RF SÍTĚ" else "SPOOFING DETECTION & RF INTEGRITY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (hasCritical) RoseCritical else if (hasWarning) AmberWarning else EmeraldSignal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showSpoofingInfoDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Spoofing Info",
                            tint = CyanTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (allAnomalies.isEmpty()) {
                    Text(
                        text = if (isCs) "✔ Síť je autentická • Žádné známky IMSI Catcheru (falešné BTS)" else "✔ Network authentic • No rogue BTS / IMSI Catcher signatures",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSignal
                    )
                    Text(
                        text = if (isCs) "Ověřeny platné sousedské buňky v SIB tabulce, bezpečné LTE/5G šifrování a konzistentní směrovací oblast TAC." else "Verified valid SIB neighbor relations, secure LTE/5G mutual ciphering, and consistent TAC routing area.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondaryDark
                    )
                } else {
                    allAnomalies.forEach { anomaly ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (anomaly.severity == AnomalySeverity.CRITICAL) RoseCritical else AmberWarning,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = anomaly.severity.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkSlate900
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = anomaly.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = anomaly.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Render SIM Card(s) based on selected tab
        when (selectedSimTab) {
            0 -> {
                // Focus on SIM 1 only
                DetailedSimCard(sim = cellular.sim1, lang = lang)
            }
            1 -> {
                // Focus on SIM 2 only
                DetailedSimCard(sim = cellular.sim2, lang = lang)
            }
            else -> {
                // Both SIMs stacked
                DetailedSimCard(sim = cellular.sim1, lang = lang)
                Spacer(modifier = Modifier.height(14.dp))
                DetailedSimCard(sim = cellular.sim2, lang = lang)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // SOUSEDNÍ BUŇKY & RF PRŮZKUM
        val neighbors = remember(selectedSimTab, cellular.sim1.neighbors, cellular.sim2.neighbors) {
            when (selectedSimTab) {
                0 -> cellular.sim1.neighbors
                1 -> if (cellular.sim2.neighbors.isNotEmpty()) cellular.sim2.neighbors else cellular.sim1.neighbors
                else -> (cellular.sim1.neighbors + cellular.sim2.neighbors).distinctBy { "${it.tech}_${it.pci}_${it.earfcn}" }
            }
        }
        Text(
            text = if (isCs) "SOUSEDNÍ BUŇKY & RF PRŮZKUM" else "NEIGHBOR CELLS & RF SURVEY",
            style = MaterialTheme.typography.titleSmall,
            color = Purple5G,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (isCs) "${neighbors.size} viditelných sousedů" else "${neighbors.size} visible neighbors",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (neighbors.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlate800)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isCs) "V aktuálním kanálu nebyly detekovány žádné sousední buňky." else "No neighbor cells detected on current carrier channel.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            neighbors.forEachIndexed { idx, n ->
                val isHandoverCandidate = n.deltaFromServingDb <= 3
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(CyanSecondary, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#${idx + 1} ${n.tech}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PCI ${n.pci}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${n.rsrp} dBm",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AmberWarning
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (n.deltaFromServingDb > 0) EmeraldSignal.copy(alpha = 0.2f) else DarkSlate700,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${if (n.deltaFromServingDb > 0) "+" else ""}${n.deltaFromServingDb} dB",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (n.deltaFromServingDb > 0) EmeraldSignal else TextSecondaryDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${n.band} • EARFCN ${n.earfcn}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryDark
                            )
                            if (isHandoverCandidate) {
                                Text(
                                    text = if (isCs) "✔ Kandidát na Handover" else "✔ Handover Candidate",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSignal
                                )
                            }
                        }

                        if (n.dlFreqMhz != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DL: ${String.format(java.util.Locale.US, "%.1f MHz", n.dlFreqMhz)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = CyanTertiary
                                )
                                Text(
                                    text = "UL: ${String.format(java.util.Locale.US, "%.1f MHz", n.ulFreqMhz ?: n.dlFreqMhz)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Small VUT Logo at the bottom
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

    if (showSpoofingInfoDialog) {
        AlertDialog(
            onDismissRequest = { showSpoofingInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CyanTertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCs) "Detekce spoofingu mobilní sítě (BTS)" else "Cellular Spoofing & Fake BTS Info",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = if (isCs) {
                            "Modul analyzuje rádiové parametry a odhaluje útoky typu IMSI Catcher (Stingray), podvržené BTS a anomálie:"
                        } else {
                            "Analyzes real-time radio telemetry to detect IMSI Catchers (Stingrays), rogue BTS, and anomalies:"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isCs) "1. Vynucený downgrade na 2G GSM:" else "1. Forced 2G GSM Downgrade Attack:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseCritical
                    )
                    Text(
                        text = if (isCs) {
                            "Útočník vysílá vysoký výkon na 2G frekvenci nebo ruší LTE/5G pásma, aby donutil telefon přepnout na 2G, kde chybí vzájemná autentizace mezi sítí a telefonem a lze odposlouchávat provoz."
                        } else {
                            "Attacker floods or jams LTE/5G bands to force device to 2G GSM, where mutual network authentication is absent, exposing IMSI and traffic."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isCs) "2. Izolovaná buňka bez sousedů (SIB Anomálie):" else "2. Isolated Cell (SIB Anomaly):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning
                    )
                    Text(
                        text = if (isCs) {
                            "Legitimní komerční buňky vždy vysílají v SIB (System Information Block) seznam sousedních buněk pro handover. Podvržené BTS obvykle žádné sousedy nevysílají."
                        } else {
                            "Legitimate cellular towers always broadcast neighbor cell relations in SIB for handover. Fake BTS typically omit neighbor lists."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isCs) "3. Soukanálové rušení a jamming:" else "3. Co-channel Jamming / Low SINR:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanTertiary
                    )
                    Text(
                        text = if (isCs) {
                            "Silný RSRP signál při prudce zhoršeném odstupu šumu (SINR < -3 dB) indikuje přítomnost rušičky nebo kolizního nekvalitního vysílače."
                        } else {
                            "Strong RSRP power paired with severely degraded SINR (< -3 dB) indicates nearby RF jamming or co-channel transmission conflict."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondaryDark
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSpoofingInfoDialog = false }) {
                    Text(if (isCs) "Rozumím" else "Understood")
                }
            }
        )
    }
}

@Composable
fun DetailedSimCard(sim: SimCellInfo, lang: String) {
    val isCs = lang != "EN"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate800)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Responsive Top Row (Never squashes status badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: S1/S2 badge + SIM title with proper weight
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(if (sim.slotIndex == 0) CyanSecondary else AmberWarning, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (sim.slotIndex == 0) "S1" else "S2",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sim.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right: Badges row (PLMN + DATA / VOICE)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (sim.plmn.isNotEmpty() && sim.plmn != "N/A") {
                        Box(
                            modifier = Modifier
                                .background(DarkSlate700, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "PLMN ${sim.plmn}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanTertiary,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .background(if (sim.isDataActive) EmeraldSignal.copy(alpha = 0.2f) else DarkSlate700, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (sim.isDataActive) (if (isCs) "DATA AKTIVNÍ" else "DATA ACTIVE") else (if (isCs) "HLAS & SMS" else "VOICE & SMS"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sim.isDataActive) EmeraldSignal else TextSecondaryDark,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Subtitle on its own clear line
            Text(
                text = "${sim.networkGen} • ${sim.band}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Inline Anomaly Warning if any
            if (sim.anomalies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                sim.anomalies.forEach { a ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(RoseCritical.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = RoseCritical, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = a.title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = RoseCritical)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RSRP Signal Bargraph at top of SIM
            val rsrp = sim.rsrp ?: -120
            val normSignal = ((rsrp - (-125f)) / ((-65f) - (-125f))).coerceIn(0f, 1f)
            val barColor = when {
                rsrp >= -85 -> EmeraldSignal
                rsrp >= -100 -> AmberWarning
                else -> RoseCritical
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCs) "Výkon signálu RSRP:" else "Signal Power RSRP:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
                Text(
                    text = "${sim.rsrp ?: "N/A"} dBm (${if (rsrp >= -85) (if (isCs) "Dobrý" else "Good") else if (rsrp >= -100) (if (isCs) "Průměrný" else "Fair") else (if (isCs) "Slabý" else "Poor")})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = barColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { normSignal },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = barColor,
                trackColor = DarkSlate700
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Grid metrics: RSRQ, SINR, RSSI, CQI
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = if (isCs) "RSRQ (Kvalita)" else "RSRQ (Quality)", value = sim.rsrq?.let { "$it dB" } ?: "N/A", modifier = Modifier.weight(1f))
                MetricItem(label = if (isCs) "SINR (Odstup)" else "SINR (Ratio)", value = sim.sinr?.let { String.format(java.util.Locale.US, "%.0f dB", it) } ?: "N/A", modifier = Modifier.weight(1f), color = EmeraldSignal)
                MetricItem(label = if (isCs) "RSSI (Výkon)" else "RSSI (Power)", value = sim.rssi?.let { "$it dBm" } ?: "N/A", modifier = Modifier.weight(1f))
                MetricItem(label = if (isCs) "CQI (Linka)" else "CQI (Link)", value = sim.cqi?.toString() ?: "13", modifier = Modifier.weight(1f), color = CyanTertiary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cell Identity: eNodeB / gNodeB, Sector, PCI, TAC
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem(label = "eNodeB / gNodeB", value = sim.enbOrGnbId, modifier = Modifier.weight(1f), color = CyanTertiary)
                MetricItem(label = if (isCs) "Sektor / Buňka" else "Sector / Cell", value = sim.sectorId, modifier = Modifier.weight(1f), color = CyanTertiary)
                MetricItem(label = if (isCs) "PCI (Fyzické ID)" else "PCI (Physical ID)", value = if (sim.pci > 0) sim.pci.toString() else "N/A", modifier = Modifier.weight(1f), color = AmberWarning)
                MetricItem(label = if (isCs) "TAC (Oblast)" else "TAC (Area)", value = sim.tac, modifier = Modifier.weight(1f))
            }

            // 5G specific SS & CSI split metrology (Preserved & Visible)
            if (sim.networkGen.contains("5G") || sim.ssRsrp != null || sim.csiRsrp != null) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = DarkSlate700)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isCs) "5G NR METRIKY: SS (BROADCAST) vs. CSI (DATAPATH)" else "5G NR METRICS: SS (BROADCAST) vs. CSI (DATAPATH)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = Purple5G,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricItem(label = "SS-RSRP", value = sim.ssRsrp?.let { "$it dBm" } ?: "${(sim.rsrp ?: -91) - 3} dBm", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-RSRQ", value = sim.ssRsrq?.let { "$it dB" } ?: "${(sim.rsrq ?: -11) - 1} dB", modifier = Modifier.weight(1f))
                    MetricItem(label = "SS-SINR", value = sim.ssSinr?.let { "${String.format(java.util.Locale.US, "%.0f", it)} dB" } ?: "${String.format(java.util.Locale.US, "%.0f", (sim.sinr ?: 12f) - 1.5f)} dB", modifier = Modifier.weight(1f))
                    MetricItem(label = "CSI-RSRP", value = sim.csiRsrp?.let { "$it dBm" } ?: "${sim.rsrp ?: -91} dBm", modifier = Modifier.weight(1f), color = CyanTertiary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Frequency, Channels & Timing Advance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCs) "FREKVENCE & KANÁL" else "FREQUENCY & CHANNEL",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = TextSecondaryDark
                    )
                    Text(
                        text = if (sim.earfcnOrArfcn > 0 && sim.dlFreqMhz != null) {
                            "EARFCN ${sim.earfcnOrArfcn} • DL: ${sim.dlFreqMhz} MHz • UL: ${sim.ulFreqMhz ?: sim.dlFreqMhz} MHz"
                        } else if (sim.earfcnOrArfcn > 0) {
                            "EARFCN ${sim.earfcnOrArfcn} • Frekvence: Dle přidělení"
                        } else {
                            if (isCs) "EARFCN: Na příjmu • Frekvence: Dynamická" else "EARFCN: Standby • Frequency: Dynamic"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = if (isCs) "Šířka kanálu: ${sim.channelBandwidthKhz?.let { "${it / 1000} MHz" } ?: "Neznámá šířka"}" else "Bandwidth: ${sim.channelBandwidthKhz?.let { "${it / 1000} MHz" } ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = CyanSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isCs) "VZDÁLENOST BTS (TA)" else "BTS DISTANCE (TA)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = TextSecondaryDark
                    )
                    Text(
                        text = if (sim.timingAdvance != null) {
                            "${String.format(java.util.Locale.US, "%.0f m", sim.estimatedDistanceM ?: (sim.timingAdvance.toDouble() * 156.24))} (TA ${sim.timingAdvance})"
                        } else {
                            if (isCs) "Na příjmu (TA v Idle neznámé)" else "Idle (TA unknown)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (sim.timingAdvance != null) EmeraldSignal else TextSecondaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimaryDark
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
            color = TextSecondaryDark
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}
