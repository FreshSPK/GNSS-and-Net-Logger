package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SecurityCheckResult
import com.example.model.ThreatSeverity
import com.example.ui.theme.*

data class SecurityCheckGuide(
    val whatItMeans: String,
    val howItIsCalculated: String,
    val thresholds: String
)

fun getSecurityCheckGuide(checkId: String, isCs: Boolean): SecurityCheckGuide {
    return when {
        checkId == "gnss_mock" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Ověřuje, zda poloha přichází z reálného hardwarového GNSS čipu telefonu, nebo ji do systému Android injektuje softwarová aplikace (Fake GPS / vývojářský Mock Location provider)."
            } else {
                "Verifies whether fixes originate from physical GNSS hardware or are software-injected via Android's Mock Location developer interface."
            },
            howItIsCalculated = if (isCs) {
                "Čte systémový příznak Location.isMock (API 31+) / isFromMockProvider přímo z příchozího NMEA/Location objektu LocationManageru."
            } else {
                "Reads Android's Location.isMock (API 31+) / isFromMockProvider flag directly from incoming LocationManager updates."
            },
            thresholds = if (isCs) {
                "OK: Hardware GPS (isMock = false) · CRITICAL (+85 b.): isMock = true."
            } else {
                "OK: Hardware GPS (isMock = false) · CRITICAL (+85 pts): isMock = true."
            }
        )

        checkId == "gnss_agc_jam" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "AGC (Automatic Gain Control) vyjadřuje, jak moc přijímač tlumí zesílení vstupního zesilovače. Když poblíž vysílá rušička (jammer) v pásmu L1/L5, přijímač stáhne zisk AGC dolů, aby se nepřetížil."
            } else {
                "AGC (Automatic Gain Control) reflects RF front-end attenuation. When an in-band L1/L5 jammer transmits nearby, the receiver drops AGC gain to prevent ADC saturation."
            },
            howItIsCalculated = if (isCs) {
                "Z GnssMeasurementsEvent se průměruje automaticGainControlLevelDb napříč družicemi a porovnává se s referenční čistou úrovní (baseline) v relaci. Z poklesu zisku ΔAGC [dB] se počítá index rušení (0–100 %)."
            } else {
                "Averages automaticGainControlLevelDb across raw GnssMeasurementsEvent signals against the clean session baseline. AGC compression ΔAGC [dB] drives the interference index (0–100%)."
            },
            thresholds = if (isCs) {
                "OK: Index < 35 % (ΔAGC < 6 dB) · WARNING (+35 b.): ≥ 35 % · CRITICAL (+80 b.): ≥ 65 % nebo hardwarový jamming alert."
            } else {
                "OK: Index < 35% (ΔAGC < 6 dB) · WARNING (+35 pts): ≥ 35% · CRITICAL (+80 pts): ≥ 65% or active jamming alert."
            }
        )

        checkId == "gnss_cn0_profile" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Reálné družice na obloze mají různou vzdálenost i elevaci – družice vysoko v zenitu mají silnější signál než nízko nad obzorem. Pozemní RF spoofer naproti tomu vysílá všechny falešné družice z jediné antény se stejným výkonem (nízký rozptyl σ) nebo zvýhodní nízkou elevaci."
            } else {
                "Real satellites span varying slant ranges and elevations, so zenith signals naturally exceed horizon ones. Ground spoofers transmit all fake PRNs from a single antenna, collapsing C/N0 variance (σ) or inverting elevation."
            },
            howItIsCalculated = if (isCs) {
                "Vypočte se průměr Ø C/N0 [dB-Hz] a směrodatná odchylka σ napříč viditelnými družicemi (≥ 4 sat.). Dále se srovná průměr C/N0 družic v zenitu (elevace ≥ 45°) proti obzoru (5°–20°)."
            } else {
                "Computes mean C/N0 [dB-Hz] and standard deviation σ across tracked satellites (≥ 4), plus compares high-elevation (≥ 45°) vs. low-elevation (5°–20°) average C/N0."
            },
            thresholds = if (isCs) {
                "WARNING (+35 b.): LowEl ≥ HighEl + 2,5 dB-Hz (při Ø > 30 dB-Hz) · CRITICAL (+70 b.): ≥ 6 satelitů s Ø C/N0 > 43,5 dB-Hz a σ < 2,3 dB-Hz."
            } else {
                "WARNING (+35 pts): LowEl ≥ HighEl + 2.5 dB-Hz (at mean > 30) · CRITICAL (+70 pts): ≥ 6 sats with mean > 43.5 dB-Hz and σ < 2.3 dB-Hz."
            }
        )

        checkId == "gnss_doppler" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Družice GNSS obíhají rychlostí ~3,9 km/s v různých směrech, takže jejich radiální rychlost vůči telefonu (Dopplerův posuv) má široký přirozený rozptyl. Jednoduchý SDR spoofer bez přesné simulace oběžných drah má rozptyl uměle zploštělý."
            } else {
                "Orbital GNSS satellites move at ~3.9 km/s in different directions, creating wide radial Doppler dispersion. Naive or static SDR spoofers fail to reproduce realistic satellite-to-satellite Doppler spread."
            },
            howItIsCalculated = if (isCs) {
                "Ze surových měření GnssMeasurement.pseudorangeRateMetersPerSecond se vypočte výběrová směrodatná odchylka σ_v [m/s] napříč všemi sledovanými signály."
            } else {
                "Calculates standard deviation σ_v [m/s] of GnssMeasurement.pseudorangeRateMetersPerSecond across tracked satellites."
            },
            thresholds = if (isCs) {
                "OK: σ_v obvykle 150–650 m/s · WARNING (+45 b.): ≥ 5 satelitů a σ_v < 45 m/s."
            } else {
                "OK: typically σ_v = 150–650 m/s · WARNING (+45 pts): ≥ 5 satellites with σ_v < 45 m/s."
            }
        )

        checkId == "gnss_dual_band" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Moderní telefony přijímají civilní pásmo L1/E1 (1575,42 MHz) i novější L5/E5a/B2a (1176,45 MHz). Levné RF spoofery generují pouze L1 – při spuštění útoku tak silné L5 signály náhle úplně zmizí."
            } else {
                "Dual-frequency phones track both L1/E1 (1575.42 MHz) and L5/E5a/B2a (1176.45 MHz). Low-cost RF spoofers transmit only L1, causing previously visible L5/E5a signals to vanish despite strong L1."
            },
            howItIsCalculated = if (isCs) {
                "Sleduje příznak hasEverSeenDualFreq v aktuální relaci. Pokud telefon v této relaci již L5/E5a viděl a nyní vidí ≥ 8 satelitů se silným průměrem C/N0 > 32 dB-Hz, ale počet L5 klesne na 0, spustí varování."
            } else {
                "Tracks hasEverSeenDualFreq in session. Triggers if the device previously locked L5/E5a and currently sees ≥ 8 satellites at mean C/N0 > 32 dB-Hz with zero L5 signals."
            },
            thresholds = if (isCs) {
                "OK: L5/E5a ≥ 1 (nebo HW bez L5) · WARNING (+40 b.): L5 = 0 při ≥ 8 sat. L1 a Ø C/N0 > 32 dB-Hz."
            } else {
                "OK: L5/E5a ≥ 1 (or single-band HW) · WARNING (+40 pts): L5 = 0 with ≥ 8 L1 sats and mean C/N0 > 32 dB-Hz."
            }
        )

        checkId == "gnss_imu_kinematics" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Křížově ověřuje polohu a výšku GNSS proti inerciálním senzorům telefonu (akcelerometr + gyroskop) a barometru. Pokud telefon fyzicky leží v klidu, ale GPS hlásí jízdu nebo skok výšky bez změny tlaku, jde o podvržení."
            } else {
                "Cross-checks GNSS speed, coordinates, and altitude against physical IMU sensors (accelerometer + gyroscope) and barometer pressure deltas."
            },
            howItIsCalculated = if (isCs) {
                "1) Měří dobu klidu IMU (|a − g| i rotace pod prahem). 2) Počítá skoky polohy mezi po sobě jdoucími fixy (> 65 m/s). 3) Srovnává 5s změnu výšky GNSS vs. barometrickou výšku Δh."
            } else {
                "1) Counts consecutive stationary IMU seconds. 2) Detects inter-fix coordinate jumps (> 65 m/s). 3) Compares 5-second GNSS vs. barometric altitude deltas (Δh)."
            },
            thresholds = if (isCs) {
                "WARNING (+35 b.): |Δh_GNSS − Δh_Baro| > 40 m · CRITICAL (+65 b.): klid IMU ≥ 4 s & GNSS > 12 km/h nebo skoky > 65 m/s."
            } else {
                "WARNING (+35 pts): |Δh_GNSS − Δh_Baro| > 40 m · CRITICAL (+65 pts): stationary IMU ≥ 4s & GNSS > 12 km/h or jump > 65 m/s."
            }
        )

        checkId == "gnss_vs_bts_geo" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Kontroluje, zda GNSS souřadnice telefonu dávají geografický smysl vůči obsluhujícímu vysílači mobilní sítě (BTS) nalezenému v lokální databázi ČR. Odhalí podvržení polohy o desítky či stovky kilometrů (např. teleport na letiště)."
            } else {
                "Verifies whether GNSS coordinates physically align with the serving cellular base station location in the local BTS database, catching large-scale GNSS teleportation."
            },
            howItIsCalculated = if (isCs) {
                "Spočte Haversine vzdálenost mezi aktuální GNSS polohou a známými GPS souřadnicemi obsluhující BTS (bere se maximum z SIM 1 / SIM 2)."
            } else {
                "Computes Haversine ground distance between current GNSS coordinates and the matched serving BTS coordinates (max across SIM 1 / SIM 2)."
            },
            thresholds = if (isCs) {
                "OK: < 35 km (běžný dosah buňky) · CRITICAL (+75 b.): vzdálenost GNSS od známé BTS > 45 km."
            } else {
                "OK: < 35 km (normal cell radius) · CRITICAL (+75 pts): GNSS-to-BTS distance > 45 km."
            }
        )

        checkId == "time_triple_sync" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Nezávisle porovnává tři hodinové zdroje: 1) atomový čas GNSS satelitů (G), 2) čas mobilní sítě / systému NITZ (S) a 3) internetový SNTP server (N). Rozdíl odhalí GNSS replay/meaconing útok nebo podvržený síťový čas NITZ."
            } else {
                "Cross-audits three independent clocks: 1) GNSS UTC (G), 2) Cellular NITZ/System clock (S), and 3) Internet SNTP UTC (N). Divergence exposes GNSS replay/meaconing or rogue NITZ tampering."
            },
            howItIsCalculated = if (isCs) {
                "G-N = GNSS UTC − NTP (UDP:123 s kompenzací poloviny RTT)\n" +
                    "S-N = Systémový/NITZ čas − NTP\n" +
                    "G-S = GNSS UTC − Systémový/NITZ čas\n" +
                    "Pozn.: NITZ v mobilní síti bývá posílán s přesností ±1 s (proto má S-N vyšší toleranci)."
            } else {
                "G-N = GNSS UTC − NTP (UDP:123 with RTT/2 correction)\n" +
                    "S-N = System/NITZ clock − NTP\n" +
                    "G-S = GNSS UTC − System/NITZ clock\n" +
                    "Note: Carrier NITZ updates have ~1 s granularity (higher S-N tolerance)."
            },
            thresholds = if (isCs) {
                "OK: |G-N| ≤ 800 ms, |G-S| ≤ 800 ms, |S-N| ≤ 2500 ms · WARNING (+35 b.): |G-N| > 800 ms nebo |G-S| > 800 ms · CRITICAL (+75 b.): |G-N| > 2000 ms nebo |G-S| > 2500 ms."
            } else {
                "OK: |G-N| ≤ 800 ms, |G-S| ≤ 800 ms, |S-N| ≤ 2500 ms · WARNING (+35 pts): |G-N| > 800 ms or |G-S| > 800 ms · CRITICAL (+75 pts): |G-N| > 2000 ms or |G-S| > 2500 ms."
            }
        )

        checkId == "gnss_hw_clock" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Sleduje plynulost chodu vnitřního křemenného oscilátoru (TCXO) GNSS přijímače. Časté diskontinuity nebo extrémní frekvenční drift mohou značit násilné přesměrování hodin čipu při manipulaci se signálem (nebo agresivní úsporný režim GNSS čipu v interiéru)."
            } else {
                "Monitors stability of the internal GNSS TCXO oscillator via clock discontinuity jumps and drift rate. Unexpected jumps can reflect forced GNSS clock re-locking or indoor GNSS power cycling."
            },
            howItIsCalculated = if (isCs) {
                "Skoky = (aktuální hardwareClockDiscontinuityCount) − (hodnota při startu relace).\n" +
                    "Drift = GnssClock.driftNanosPerSecond [ns/s]."
            } else {
                "Jumps = current hardwareClockDiscontinuityCount minus baseline at session start.\n" +
                    "Drift = GnssClock.driftNanosPerSecond [ns/s]."
            },
            thresholds = if (isCs) {
                "OK: 0 nových skoků v relaci a |drift| ≤ 50 000 ns/s · WARNING (+30 b.): skoky > 0 nebo |drift| > 50 000 ns/s."
            } else {
                "OK: 0 new discontinuities and |drift| ≤ 50,000 ns/s · WARNING (+30 pts): discontinuities > 0 or |drift| > 50,000 ns/s."
            }
        )

        checkId == "net_integrity" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Kontroluje, zda aktivní datové připojení má ověřený přístup k internetu (Validated Internet) a zda přes něj neběží VPN tunel (který může navyšovat Ping a NTP RTT)."
            } else {
                "Checks whether the active cellular connection has validated internet routing and whether a VPN tunnel is active (which increases Ping/NTP latency)."
            },
            howItIsCalculated = if (isCs) {
                "Čte ConnectivityManager NetworkCapabilities: NET_CAPABILITY_VALIDATED a TRANSPORT_VPN."
            } else {
                "Inspects Android ConnectivityManager NetworkCapabilities for NET_CAPABILITY_VALIDATED and TRANSPORT_VPN."
            },
            thresholds = if (isCs) {
                "OK: Internet ověřen · INFO: Aktivní VPN · WARNING (+30 b.): Silný mobilní signál (> −95 dBm) na datové SIM bez validovaného internetu."
            } else {
                "OK: Validated internet · INFO: VPN active · WARNING (+30 pts): Strong data SIM (> −95 dBm) without validated internet."
            }
        )

        checkId.endsWith("_ta_vs_bts") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Timing Advance (TA) určuje přesnou dobu šíření rádiového signálu mezi mobilem a vysílačem. Porovnání vzdálenosti z TA se skutečnou GPS polohou BTS v databázi odhalí podvržený vysílač (IMSI Catcher), který je mnohem blíže než oficiální BTS."
            } else {
                "Timing Advance (TA) measures actual radio propagation time between UE and serving tower. Comparing TA distance against the database BTS range exposes rogue transmitters operating much closer than the real mast."
            },
            howItIsCalculated = if (isCs) {
                "TA_vzdálenost = TA × krok (1× = 78,12 m LTE, 2× = 156,24 m).\n" +
                    "ΔTA = TA_vzdálenost − Haversine_vzdálenost_BTS [m]."
            } else {
                "TA_range = TA × step (1× = 78.12 m, 2× = 156.24 m).\n" +
                    "ΔTA = TA_range − Haversine_BTS_distance [m]."
            },
            thresholds = if (isCs) {
                "OK: |ΔTA| ≤ 1 500 m · WARNING (+35 b.): |ΔTA| > 1 500 m · CRITICAL (+65 b.): |ΔTA| > 3 500 m (při BTS < 30 km)."
            } else {
                "OK: |ΔTA| ≤ 1,500 m · WARNING (+35 pts): |ΔTA| > 1,500 m · CRITICAL (+65 pts): |ΔTA| > 3,500 m (when BTS < 30 km)."
            }
        )

        checkId.endsWith("_power_vs_ta") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Fyzika šíření elektromagnetických vln svazuje vzdálenost (TA) a přijatý výkon (RSRP). Extrémně silný signál při velkém TA značí nelegální opakovač nebo manipulaci s TA; naopak extrémně silný signál těsně u buňky (TA=0–1) bez sousedů ukazuje na blízký IMSI Catcher."
            } else {
                "Propagation physics couples TA range with received RSRP. Overly strong RSRP at high TA indicates an illegal repeater/TA manipulation; overpowering RSRP at TA ≤ 1 without neighbors fits a near-field Stingray."
            },
            howItIsCalculated = if (isCs) {
                "Empirický model útlumu pro ČR:\n" +
                    "RSRP_oček = −72 − 34·log10(max(d,50)/150) − 20·log10(f/1800) [dBm]\n" +
                    "Reziduum Δ = RSRP_změřený − RSRP_oček [dB]."
            } else {
                "Empirical path-loss model:\n" +
                    "RSRP_exp = −72 − 34·log10(max(d,50)/150) − 20·log10(f/1800) [dBm]\n" +
                    "Residual Δ = RSRP_measured − RSRP_exp [dB]."
            },
            thresholds = if (isCs) {
                "OK: Δ = −22 až +25 dB · WARNING (+30 b.): silný útlum při TA ≤ 2 (RSRP < −114 dBm) · CRITICAL (+65 b.): Δ > +28 dB nebo TA ≥ 12 & RSRP > −68 dBm nebo TA ≤ 1 & RSRP > −55 dBm bez sousedů/špatný SINR."
            } else {
                "OK: Δ = −22 to +25 dB · WARNING (+30 pts): severe attenuation at TA ≤ 2 (< −114 dBm) · CRITICAL (+65 pts): Δ > +28 dB or TA ≥ 12 & RSRP > −68 dBm or isolated TA ≤ 1 & RSRP > −55 dBm."
            }
        )

        checkId.endsWith("_rf_jamming") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "RSRP měří výkon užitečného referenčního signálu jednoho prvku (15 kHz), zatímco RSSI měří celkový širokopásmový výkon v celém LTE kanálu včetně šumu a cizích rušiček. Pokud RSSI prudce roste vůči RSRP a klesá SINR, kanál někdo ruší."
            } else {
                "RSRP measures useful reference-element power while RSSI captures total wideband energy in the channel. A large RSSI−RSRP gap paired with low SINR/RSRQ reveals in-band interference or RF jamming."
            },
            howItIsCalculated = if (isCs) {
                "Δ_wideband = RSSI [dBm] − RSRP [dBm] (běžně kolem 18–30 dB podle počtu RB).\n" +
                    "Sleduje také odstup signálu od šumu SINR [dB] a kvalitu RSRQ [dB]."
            } else {
                "Wideband gap = RSSI [dBm] − RSRP [dBm], evaluated alongside SINR [dB] and RSRQ [dB]."
            },
            thresholds = if (isCs) {
                "OK: SINR ≥ −2 dB, Δ < 36 dB · WARNING (+30 b.): Δ > 35 dB · CRITICAL (+70 b.): Δ > 38 dB (s SINR < 5 dB či RSRQ < −15 dB) nebo RSRP > −90 dBm při SINR < −3 dB."
            } else {
                "OK: SINR ≥ −2 dB, Δ < 36 dB · WARNING (+30 pts): Δ > 35 dB · CRITICAL (+70 pts): Δ > 38 dB (with SINR < 5 dB or RSRQ < −15 dB) or RSRP > −90 dBm with SINR < −3 dB."
            }
        )

        checkId.endsWith("_imsi_catcher") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Sdružuje typické podpisy aktivního i pasivního IMSI Catcheru (Stingray): nucený pád do nezabezpečené sítě 2G GSM, falešné parametry PCI/TAC oproti databázi BTS, chybějící sousední buňky v SIB zprávách, rychlé změny TAC či výpadek dat při silném RF."
            } else {
                "Aggregates classic active/passive IMSI Catcher indicators: forced downgrade to unauthenticated 2G GSM, PCI/TAC mismatch against the BTS registry, zero SIB neighbors, rapid TAC oscillation, and data blackholing."
            },
            howItIsCalculated = if (isCs) {
                "Vyhodnocuje současně: typ sítě (2G GSM/GPRS/EDGE), počet sousedů v SIB při RSRP > −85 dBm, počet unikátních změn TAC za poslední 2 minuty, porovnání PCI/TAC s databází BTS a 100% ztrátu pingu 8.8.8.8."
            } else {
                "Evaluates network generation (2G GSM), neighbor count at RSRP > −85 dBm, TAC change count over a rolling 2-minute window, local BTS DB PCI/TAC match, and 100% socket ping loss."
            },
            thresholds = if (isCs) {
                "OK: 4G/5G, sousedé ≥ 1, shoda DB · WARNING (+45 b.): DB nesoulad, izolovaná buňka, TAC změny ≥ 3 za 2 min nebo ping loss · CRITICAL (+85 b.): vynucený 2G GSM downgrade."
            } else {
                "OK: 4G/5G, neighbors ≥ 1, DB match · WARNING (+45 pts): DB mismatch, isolated cell, ≥ 3 TAC shifts in 2m, or data blackhole · CRITICAL (+85 pts): forced 2G GSM downgrade."
            }
        )

        else -> SecurityCheckGuide(
            whatItMeans = if (isCs) "Bezpečnostní diagnostika rádiového či polohového subsystému." else "Security diagnostic check for radio or positioning telemetry.",
            howItIsCalculated = if (isCs) "Porovnává živou telemetrii s fyzikálními a síťovými limity." else "Compares live telemetry against physical and protocol bounds.",
            thresholds = checkId
        )
    }
}

@Composable
fun SecurityCheckDetailDialog(
    check: SecurityCheckResult,
    isCs: Boolean,
    onDismiss: () -> Unit
) {
    val accent = when (check.severity) {
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
    val guide = getSecurityCheckGuide(check.id, isCs)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("security_check_detail_dialog"),
        containerColor = DarkSlate800,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isCs) check.titleCs else check.titleEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = check.severity.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accent
                        )
                    }
                    Text(
                        text = if (check.scoreImpact > 0) {
                            if (isCs) "Vliv na riziko: +${check.scoreImpact} b." else "Risk impact: +${check.scoreImpact} pts"
                        } else {
                            if (isCs) "Vliv na riziko: 0 b. (v normě)" else "Risk impact: 0 pts (nominal)"
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (check.scoreImpact > 0) accent else TextSecondaryDark
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Full unclipped measured vs expected values
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSlate900,
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isCs) "NAMĚŘENÉ HODNOTY (ÚPLNÝ VÝPIS)" else "MEASURED VALUES (FULL OUTPUT)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = check.measuredValue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = accent,
                            modifier = Modifier.testTag("security_check_detail_measured")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = DarkSlate700)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${if (isCs) "Očekávaná norma" else "Expected norm"}: ${check.expectedRange}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimaryDark
                        )
                    }
                }

                // Current status evaluation
                DetailInfoBlock(
                    title = if (isCs) "Aktuální vyhodnocení" else "Current Assessment",
                    body = if (isCs) check.summaryCs else check.summaryEn,
                    titleColor = accent
                )

                // What the metric means
                DetailInfoBlock(
                    title = if (isCs) "Co tento parametr znamená" else "What This Parameter Means",
                    body = guide.whatItMeans,
                    titleColor = CyanTertiary
                )

                // How it is computed
                DetailInfoBlock(
                    title = if (isCs) "Jak se počítá" else "How It Is Calculated",
                    body = guide.howItIsCalculated,
                    titleColor = Purple5G
                )

                // Exact thresholds
                DetailInfoBlock(
                    title = if (isCs) "Prahové limity hodnocení" else "Severity Thresholds",
                    body = guide.thresholds,
                    titleColor = EmeraldSignal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("security_check_detail_close")
            ) {
                Text(if (isCs) "Zavřít" else "Close")
            }
        }
    )
}

@Composable
private fun DetailInfoBlock(
    title: String,
    body: String,
    titleColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = TextPrimaryDark
        )
    }
}
