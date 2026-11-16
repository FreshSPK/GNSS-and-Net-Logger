package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun AboutAndHelpDialog(
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isCs = lang != "EN"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, tint = CyanTertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCs) "Nápověda a Informace o autorovi" else "Help & Developer Credits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Author Attribution Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlate700.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.School, contentDescription = null, tint = CyanTertiary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCs) "Autor projektu" else "Project Author",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CyanTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ing. Jan Kufa, Ph.D.",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(EmeraldSignal.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Release v1.4",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSignal
                                )
                            }
                        }
                        Text(
                            text = if (isCs) {
                                "Ústav radioelektroniky (UREL)\nFakulta elektrotechniky a komunikačních technologií (FEKT)\nVysoké učení technické v Brně (VUT)"
                            } else {
                                "Department of Radio Electronics (UREL)\nFaculty of Electrical Engineering and Communication (FEKT)\nBrno University of Technology (VUT Brno)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.vut.cz/lide/jan-kufa-125512"))
                                context.startActivity(browserIntent)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = CyanSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Profil: https://www.vut.cz/lide/jan-kufa-125512",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = CyanSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .testTag("author_github_link")
                                .clickable {
                                    val ghIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/FreshSPK/GNSS-and-Net-Logger"))
                                    context.startActivity(ghIntent)
                                }
                        ) {
                            Icon(imageVector = Icons.Default.Code, contentDescription = "GitHub Repository", tint = CyanTertiary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GitHub: https://github.com/FreshSPK/GNSS-and-Net-Logger",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = CyanTertiary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Kontakt: kufa@vut.cz / kufa@vutbr.cz",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_vut_logo),
                                contentDescription = "VUT Logo",
                                modifier = Modifier.height(26.dp).width(85.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release v1.4 New Features Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CyanTertiary.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCs) "✨ Novinky verze 1.4 (Release v1.4)" else "✨ What's New in Release v1.4",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "• Přesná detekce 5G NSA (EN-DC) vs. 5G SA + 5G NR sousedé: rozpoznává 5G NSA kotevní buňku i v RRC Idle (ikona 5G v liště) a aktivní 5G NR SCG větev (n1/n3/n28/n78) s odděleným filtrem 4G LTE / 5G NR\n" +
                                "• Agregované kmitočty v Carrier Aggregation: zobrazení všech agregovaných nosných (PCC/SCC) včetně konkrétního pásma a frekvence v MHz (např. B3 1800 MHz + B1 2100 MHz + n78 3500 MHz)\n" +
                                "• Shoda s GPS Test & SkyPlot: zobrazení fyzických unikátních družic i počtu L1+L5/E5a/B2a signálů, synchronizace legendy Skyplotu (plná barva = ve fixu, barevný kroužek = mimo fix, zelená záře okolo = L5/E5a/B2a)\n" +
                                "• Oddělené handovery SIM 1 / SIM 2 & Indoor Fix Guard: samostatné HO počítadlo pro každou SIM, viditelný banner při zapnutém Demo režimu a nulování zastaralého fixu/rychlosti při ztrátě signálu v budově\n" +
                                "• Upravené prahy DOP & Satelitů + Logování: nová škála PDOP (< 0,7 nejlepší, 0,7–1,5, 1,5–2,5, 2,5–3,5, > 3,5 nejhorší) a satelitů (< 5, 5–20, 21–30, 31–50, > 50); databáze (Room v4), grafy i CSV/GPX/GeoJSON nyní logují jak použité, tak viditelné satelity\n" +
                                "• Vylepšené profily měření: nezalamující se předvolby Pěší (5s/10m), Auto (10s/50m) a Statické (10s)"
                            } else {
                                "• Accurate 5G NSA (EN-DC) vs. 5G SA & 5G NR Neighbors: distinguishes 5G NSA LTE anchor (even when RRC Idle matches the status-bar 5G badge) from active 5G NR SCG legs (n1/n3/n28/n78) with dedicated 4G LTE / 5G NR neighbor filtering\n" +
                                "• Component Carrier Frequencies in CA: displays all aggregated PCC/SCC carriers with exact band labels and center frequencies in MHz (e.g., B3 1800 MHz + B1 2100 MHz + n78 3500 MHz)\n" +
                                "• GPS Test Alignment & SkyPlot Legend: reports both unique physical satellites and total L1+L5/E5a/B2a signals; Skyplot legend matches canvas 1:1 (solid fill = in fix, colored ring = unused, green glow = L5/E5a/B2a)\n" +
                                "• Per-SIM Handovers, Demo Banner & Indoor Fix Guard: independent HO counters per SIM, clear Demo Mode top banner across all tabs, and automatic fix/speed timeout reset when moving indoors\n" +
                                "• Recalibrated DOP & Satellite Scales + Full Sat Logging: updated PDOP (< 0.7 best to > 3.5 worst) and Satellite (< 5, 5–20, 21–30, 31–50, > 50) map/chart thresholds; Room v4, charts & CSV/GPX/GeoJSON now record both used and visible satellites\n" +
                                "• Clean Settings Presets: non-wrapping chips for Walking (5s/10m), Driving (10s/50m), and Static (10s)"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isCs) "Struktura aplikace a popis funkcí" else "Application Features & Architecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                HelpSection(
                    title = if (isCs) "1. Měření (Přehled HUD & Waypointy)" else "1. Measurement (Overview HUD & Waypoints)",
                    body = if (isCs) {
                        "Centrální přístrojový štít. Spouští, pozastavuje a zastavuje záznam na pozadí, zobrazuje stopky relace, ujetou vzdálenost a handovery zvlášť pro SIM 1 a SIM 2. Během měření umožňuje tlačítkem „Značka“ vložit bod zájmu (tunel, rušení, BTS)."
                    } else {
                        "Main telemetry cockpit. Starts, pauses, and stops foreground recording with session chronometer, distance counter, and separate SIM 1 / SIM 2 handover tracking. Insert custom terrain waypoints on the fly and configure exact recorded telemetry fields."
                    }
                )

                HelpSection(
                    title = if (isCs) "2. Mobily (5G NSA/SA, CA kmitočty & Spoofing)" else "2. Cellular RF (5G NSA/SA, CA Frequencies & Security)",
                    body = if (isCs) {
                        "Detailní analýza SIM 1 / SIM 2 / Obě SIM. Rozlišuje 4G LTE, 5G NSA (EN-DC) a 5G SA, zobrazuje konkrétní agregované kmitočty CA v MHz, reálné CQI, rozpad ECI/NCI na eNodeB/gNodeB + sektor, Timing Advance vzdálenost k BTS (1× nebo 2×) a detekuje 2G downgrade či anomálie IMSI Catcheru."
                    } else {
                        "Dual-SIM radio analyzer distinguishing 4G LTE, 5G NSA (EN-DC), and 5G SA, displaying exact aggregated CA MHz component frequencies, real modem CQI, decoded eNodeB/gNodeB + Sector IDs, Timing Advance BTS distance, and IMSI Catcher / 2G downgrade anomaly alerts."
                    }
                )

                HelpSection(
                    title = if (isCs) "3. Satelity (SkyPlot, Dual-Band & AGC Monitor)" else "3. Satellites (SkyPlot, Dual-Band & AGC Monitor)",
                    body = if (isCs) {
                        "Multi-konstelační příjem (GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, NavIC) s pásmy L1/E1 i L5/E5a/B2a a zobrazením unikátních družic i jednotlivých signálů. Klepnutím na satelit na radaru zobrazíte detail C/N0, azimutu a elevace. Měří TTFF, geoid N, DOP i rušení AGC."
                    } else {
                        "Multi-constellation L1+L5/E5a/B2a monitor showing both unique physical satellites and tracked signals. Tap any satellite dot on the radar for detailed C/N0, elevation, and azimuth. Tracks TTFF, Geoid separation, DOP factors, NMEA/Doppler raw data, and AGC jamming index."
                    }
                )

                HelpSection(
                    title = if (isCs) "4. Mapa (OpenStreetMap & Živá barevná trasa)" else "4. Map (OpenStreetMap & Live Heatmap)",
                    body = if (isCs) {
                        "OpenStreetMap bez API klíčů s tmavým/světlým režimem a auto-sledováním. Živá trasa se sbírá průběžně na pozadí od spuštění aplikace a barevně se vykresluje podle RSRP (S1/S2), rychlosti, satelitů (< 5 až > 50) i PDOP (< 0,7 až > 3,5) stejně jako záznamy z Historie."
                    } else {
                        "OpenStreetMap view with dark night-mode filter and auto-follow. Records live trail continuously from startup across tabs and renders color-coded tracks by RSRP (S1/S2), Speed, Satellites (< 5 to > 50), or PDOP (< 0.7 to > 3.5) both live and in replay."
                    }
                )

                HelpSection(
                    title = if (isCs) "5. Grafy (Živé i Historické křivky)" else "5. Charts (Live & Historical Waveforms)",
                    body = if (isCs) {
                        "Interaktivní grafy s dotykovým odečtem hodnot a dynamickou osou Y: RSRP (SIM 1 vs SIM 2), SINR (SIM 1 vs SIM 2), rychlost, použité i viditelné satelity, PDOP (< 0,7 až > 3,5), AGC rušení, Ping RTT, IMU senzory a teplota baterie. Lze otevřít i pro libovolnou uloženou relaci z Historie."
                    } else {
                        "Interactive touch-scrubbing charts with dynamic Y-axis scaling: dual-SIM RSRP & SINR, speed, used & visible satellites, PDOP (< 0.7 to > 3.5), AGC interference, ping latency, IMU motion, and battery temperature. Available both live and for past sessions."
                    }
                )

                HelpSection(
                    title = if (isCs) "6. Historie (Analytika, Skok na bod & Export)" else "6. History (Analytics, Point Jump & Export)",
                    body = if (isCs) {
                        "Lokální databáze relací se souhrnnou statistikou (min/prům/max RSRP, podíl 5G/4G, průměrný PDOP), bodovým prohlížečem (zobrazuje použité/viditelné satelity, CA pásma a skok na Mapu) a streamovaným exportem do CSV, 3D KML, GPX 1.1 a GeoJSON do Downloads."
                    } else {
                        "Local session manager with analytics summary, point-by-point inspector (displaying used/visible satellites, CA frequencies, and jump-to-Map), and memory-safe streaming export to CSV, 3D KML, GPX 1.1, and GeoJSON directly to Downloads."
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Security & Privacy Note
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldSignal.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = EmeraldSignal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCs) "Ochrana soukromí & Lokální uložení" else "Privacy & Local-First Design",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "Veškerá naměřená data zůstávají výhradně v lokální databázi zařízení bez cloudu a analytik. Oprávnění READ_PHONE_STATE se využívá pouze ke čtení názvu operátora u Dual SIM slotů (žádný přístup k hovorům, SMS ani kontaktům); RF buňky jsou vázány na polohu (ACCESS_FINE_LOCATION)."
                            } else {
                                "All telemetry stays strictly in your local SQLite database with zero cloud uploads or analytics. READ_PHONE_STATE is used solely to resolve Dual-SIM carrier labels (zero access to calls, SMS, or contacts); cell tower metrics rely on ACCESS_FINE_LOCATION."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isCs) "Zavřít" else "Close")
            }
        }
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
        Text(text = body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
