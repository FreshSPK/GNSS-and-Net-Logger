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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                                    text = "Release v1.3",
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

                // Release v1.3 New Features Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CyanTertiary.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCs) "✨ Novinky verze 1.3 (Release v1.3)" else "✨ What's New in Release v1.3",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "• Stabilita & Výdrž baterie: chytré pozastavení GPS/senzorů na pozadí bez aktivního záznamu, průběžné ukládání relace proti pádu, neomezený WakeLock, filtr GPS driftu v režimu Vzdálenost a živá změna nastavení za běhu\n• Waypointy & Stopky: tlačítko [+ Značka] pro označení bodu zájmu v terénu + stopky, ujetá vzdálenost a počítadlo handoverů (HO)\n• Měřič RF bez falešných hodnot: reálné CQI z modemu, detekce Carrier Aggregation (CA / 5G EN-DC), TTFF (doba prvního fixu), geoid N (WGS84 vs MSL) a kalibrace AGC\n• Interaktivní SkyPlot & Grafy: klepnutím na satelit zobrazíte detail, grafy s dotykovým odečtem hodnot, automatickou osou Y (60+ satelitů) a novými grafy SINR, PDOP a AGC\n• Profily & Tmavá mapa: tmavý noční filtr OpenStreetMap, opravený zoom při přehrávání, klikatelné značky, souhrnná statistika relace, GPX 1.1 a vícebarevné RSRP KML streamované do Downloads\n• Tlačítko Ukončit: v horní liště pro kompletní bezpečné ukončení aplikace i služeb"
                            } else {
                                "• Stability & Battery Endurance: smart lifecycle pause when backgrounded without logging, crash-safe progress checkpoints, continuous WakeLock, distance static drift filter, and live settings reload\n• Field Waypoints & HUD Timer: [+ Mark] waypoint tagging button, live chronometer, distance counter, and handover (HO) tracking\n• Honest RF/GNSS Telemetry: real modem LTE CQI, Carrier Aggregation (CA / 5G EN-DC) badges, GNSS TTFF timer, Geoid separation N (WGS84 vs MSL), and Reset AGC baseline\n• Interactive SkyPlot & Charts: tap satellites on radar to inspect, touch-drag crosshair scrubbing, dynamic Y-scaling (60+ sats), plus SINR, PDOP, and AGC waveforms\n• Night Map & Streaming Export: Dark OSM tile filter, fixed zoom stability during replay, clickable markers, session analytics card, GPX 1.1 & multi-color RSRP KML\n• Power Exit Button: clean shutdown action in the top app bar"
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
                        "Centrální přístrojový štít. Spouští, pozastavuje a zastavuje záznam na pozadí, zobrazuje stopky relace, ujetou vzdálenost a počet handoverů (HO). Během měření umožňuje tlačítkem „Značka“ vložit bod zájmu (tunel, rušení, BTS). Upozorní na systémovou optimalizaci baterie."
                    } else {
                        "Main telemetry cockpit. Starts, pauses, and stops foreground recording with session chronometer, distance counter, and handover counter. Insert custom terrain waypoints on the fly and configure exact recorded telemetry fields."
                    }
                )

                HelpSection(
                    title = if (isCs) "2. Mobily (RF parametry, CA & Spoofing)" else "2. Cellular RF (LTE, 5G & CA)",
                    body = if (isCs) {
                        "Detailní analýza SIM 1 / SIM 2 / Obě SIM. Rozpoznává Carrier Aggregation (LTE-A CA, 5G NSA EN-DC), reálné CQI, rozpad ECI/NCI na eNodeB/gNodeB + sektor, Timing Advance vzdálenost k BTS (kalibrace 1× nebo 2×) a detekuje 2G downgrade či anomálie IMSI Catcheru."
                    } else {
                        "Dual-SIM radio analyzer displaying Carrier Aggregation (LTE-A CA, 5G NSA EN-DC), real modem CQI, decoded eNodeB/gNodeB + Sector IDs, Timing Advance BTS distance, and IMSI Catcher / 2G downgrade anomaly alerts."
                    }
                )

                HelpSection(
                    title = if (isCs) "3. Satelity (SkyPlot, TTFF & AGC Monitor)" else "3. Satellites (SkyPlot, TTFF & AGC Monitor)",
                    body = if (isCs) {
                        "Multi-konstelační příjem (GPS, Galileo, GLONASS, BeiDou) s pásmy L1/E1 i L5/E5a. Klepnutím na satelit na radaru zobrazíte detail C/N0 a azimutu/elevace. Měří dobu prvního fixu (TTFF), geoidní separaci N a hladinu rušení AGC s tlačítkem Kalibrovat."
                    } else {
                        "Multi-constellation L1+L5 receiver monitor. Tap any satellite dot on the radar for detailed C/N0, elevation, and azimuth. Tracks TTFF, Geoid separation, DOP factors, NMEA/Doppler raw data, and AGC jamming index with baseline calibration."
                    }
                )

                HelpSection(
                    title = if (isCs) "4. Mapa (OpenStreetMap & Živá barevná trasa)" else "4. Map (OpenStreetMap & Live Heatmap)",
                    body = if (isCs) {
                        "OpenStreetMap bez API klíčů s tmavým/světlým režimem a auto-sledováním. Živá trasa se sbírá průběžně na pozadí i při prohlížení jiných karet a barevně se vykresluje podle RSRP (S1/S2), rychlosti, satelitů i PDOP stejně jako záznamy z Historie."
                    } else {
                        "OpenStreetMap view with dark night-mode filter and auto-follow. Records live trail continuously in background across tabs and renders color-coded tracks by RSRP (S1/S2), Speed, Satellites, or PDOP both live and in replay."
                    }
                )

                HelpSection(
                    title = if (isCs) "5. Grafy (Živé i Historické křivky)" else "5. Charts (Live & Historical Waveforms)",
                    body = if (isCs) {
                        "Interaktivní grafy s dotykovým odečtem hodnot a dynamickou osou Y: RSRP (SIM 1 vs SIM 2), SINR (SIM 1 vs SIM 2), rychlost, satelity, PDOP, AGC rušení, Ping RTT, IMU senzory a teplota baterie. Lze otevřít i pro libovolnou uloženou relaci z Historie."
                    } else {
                        "Interactive touch-scrubbing charts with dynamic Y-axis scaling: dual-SIM RSRP & SINR, speed, satellites, PDOP, AGC interference, ping latency, IMU motion, and battery temperature. Available both live and for past sessions."
                    }
                )

                HelpSection(
                    title = if (isCs) "6. Historie (Analytika, Skok na bod & Export)" else "6. History (Analytics, Point Jump & Export)",
                    body = if (isCs) {
                        "Lokální databáze relací se souhrnnou statistikou (min/prům/max RSRP, podíl 5G/4G, průměrný PDOP), bodovým prohlížečem (klepnutím na konkrétní bod jej ihned vycentrujete na Mapě) a streamovaným exportem do CSV (plné sloupce SIM 2), 3D KML, GPX 1.1 a GeoJSON do Downloads."
                    } else {
                        "Local session manager with analytics summary, point-by-point inspector (tap any point to jump directly to its location on the Map), and memory-safe streaming export to CSV (full SIM 2 & waypoint columns), 3D KML, GPX 1.1, and GeoJSON directly to Downloads."
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
