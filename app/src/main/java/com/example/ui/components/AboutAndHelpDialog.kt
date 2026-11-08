package com.example.ui.components

import android.content.Context
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
                                    text = "Release v1.2",
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

                // Release v1.2 New Features Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CyanTertiary.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCs) "✨ Novinky verze 1.2 (Release v1.2)" else "✨ What's New in Release v1.2",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "• Skyplot Radar: nový přepínač režimů [Podle konstelace] (GPS, Galileo, GLONASS, BeiDou) a [Podle síly C/N₀] se synchronizovanou legendou, září fixu a L5/E5a kroužky\n• Korekce DOP: při absenci 3D fixu (< 4 satelity) se zobrazuje pomlčka „—“ (v CSV „-“) namísto degradované hodnoty 9\n• Dual SIM & DSDS: přesné rozlišení datové SIM (RRC Connected) vs. pohotovostního režimu pro hovory (DSDS Standby / RRC Idle) a přepínač zobrazení SIM 1 / SIM 2 / Obě SIM\n• Grafy: současné real-time vykreslování RSRP křivek pro SIM 1 i SIM 2 vedle sebe na společné škále\n• Historie: přímé uložení do složky Stažené (Downloads) bez cloudu i systémové sdílení\n• Android 14/15: optimalizace prediktivního gesta zpět (OnBackInvokedCallback)"
                            } else {
                                "• Skyplot Radar: mode switcher [By Constellation] (GPS, Galileo, GLONASS, BeiDou) vs [By C/N₀ Signal] with synchronized legend, fix glow, and L5/E5a ring highlights\n• DOP Correction: shows dash '—' (in CSV '-') when 3D fix is unavailable (< 4 satellites) instead of degraded value 9\n• Dual SIM & DSDS: accurate distinction of active data SIM (RRC Connected) vs standby voice SIM (DSDS Standby / RRC Idle) with dedicated SIM 1 / SIM 2 / Both selector\n• Charts: simultaneous real-time RSRP curves for both SIM 1 & SIM 2 side-by-side\n• History: direct offline download to device Downloads folder and system share sheet\n• Android 14/15: OnBackInvokedCallback predictive back optimization"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detailní nápověda co jak funguje a k čemu slouží
                Text(
                    text = if (isCs) "Struktura aplikace a popis funkcí" else "Application Features & Architecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                HelpSection(
                    title = if (isCs) "1. Měření (Přehled)" else "1. Measurement (Overview HUD)",
                    body = if (isCs) {
                        "Centrální přístrojový štít (HUD). Umožňuje spustit, pozastavit a zastavit ukládání telemetrických vzorků s WakeLock zámkem na pozadí. Tlačítkem „Položky“ můžete zvolit přesnou matici parametrů (GNSS, RF, Sousedé, Senzory, Ping). Zobrazuje okamžitou rychlost, přesnost, výšku, azimut, DOP geometrii (s pomlčkou „—“ při absenci fixu) a stav obou SIM karet s barevnými bargrafy síly signálu."
                    } else {
                        "Main instrument telemetry HUD. Allows you to Start, Pause, Resume, and Stop logging telemetry samples with background CPU WakeLock. The 'Fields' button lets you configure which parameter groups are recorded (GNSS, RF, Neighbors, Sensors, Ping). Shows speed, accuracy, altitude, azimuth, DOP geometry (with '—' dash when fix is lost), and stacked Dual-SIM cards with live signal bargraphs."
                    }
                )

                HelpSection(
                    title = if (isCs) "2. Mobily (RF parametry & 5G/LTE)" else "2. Cellular RF (LTE & 5G NR)",
                    body = if (isCs) {
                        "Detailní analýza obsluhujících a sousedních buněk s horním přepínačem [SIM 1], [SIM 2] a [Obě SIM]. Rozlišuje aktivní datové spojení od pohotovostního režimu DSDS Standby. Rozpad Cell ID na eNodeB ID a Sektor ID (LTE) a gNodeB ID a Sektor ID (5G). Zobrazení nosných v MHz, šířky kanálu a rozpadu 5G NR metrik (SS vs. CSI). Výpočet vzdálenosti k BTS věži přes Timing Advance (TA) s volitelnou 1× (78m) i 2× (156m) kalibrací a detektor anomálií / IMSI Catcheru."
                    } else {
                        "In-depth serving and neighbor cell monitor with top switcher [SIM 1], [SIM 2], and [Both SIMs]. Clearly distinguishes active data connection from DSDS Standby mode. Decodes Cell IDs into eNodeB/gNodeB and Sector IDs. Calculates Downlink/Uplink frequencies in MHz, channel bandwidth, and split 5G NR metrology (SS vs. CSI). Timing Advance (TA) BTS distance calculation and cell spoofing/anomaly detector."
                    }
                )

                HelpSection(
                    title = if (isCs) "3. Satelity (GNSS, SkyPlot & AGC Jamming)" else "3. Satellites (GNSS, SkyPlot & AGC Jamming)",
                    body = if (isCs) {
                        "Multi-systémový příjem (GPS, Galileo, GLONASS, BeiDou, QZSS). Tři podzáložky: Skyplot radar s přepínáním zobrazení [Podle konstelace] vs. [Podle síly C/N₀] se synchronizovanou legendou, Signály C/N0 (sloupcové grafy v dB-Hz) a Podrobná tabulka se všemi satelity. Zahrnuje filtry (Všechny, Pouze L5, GPS, Galileo) a AGC Jamming Monitor detekující rušení a propad zisku předzesilovače."
                    } else {
                        "Multi-constellation tracking (GPS, Galileo, GLONASS, BeiDou, QZSS). Three sub-tabs: Skyplot radar with color mode switcher [By Constellation] vs [By C/N₀ Signal] and dynamic synchronized legend, C/N0 signals (dB-Hz bar charts), and Detailed satellite table. Includes constellation filters and AGC Jamming Monitor detecting receiver gain drops."
                    }
                )

                HelpSection(
                    title = if (isCs) "4. Mapa (OpenStreetMap)" else "4. Map (OpenStreetMap)",
                    body = if (isCs) {
                        "Offline OpenStreetMap zobrazení bez jakýchkoliv externích API klíčů s optimalizovanými tlačítky zoomu (+/-). Horní přepínač umožňuje filtrovat a obarvovat trasu podle: RSRP signálu, Rychlosti, Počtu satelitů nebo PDOP. Obsahuje plovoucí legendu a azurové tlačítko vycentrování."
                    } else {
                        "Offline OpenStreetMap view without any proprietary API keys with optimized zoom controls (+/-). Top switcher allows track color-coding by: RSRP Signal, Speed, Satellite count, or PDOP. Includes dynamic floating legend and recenter FAB."
                    }
                )

                HelpSection(
                    title = if (isCs) "5. Grafy (Časové řady & IMU)" else "5. Charts (Time Series & IMU)",
                    body = if (isCs) {
                        "Průběhy v reálném čase: současné srovnání RSRP signálu obou operátorů pro SIM 1 (tyrkysová) i SIM 2 (oranžová) vedle sebe, rychlost, počet satelitů ve fixu, Socket Ping odezva na 8.8.8.8 a pohybové senzory (Akcelerometr v m/s², Gyroskop v rad/s a Magnetický kompas 0–360°)."
                    } else {
                        "Real-time waveforms: simultaneous comparative RSRP signal curves for SIM 1 (cyan) and SIM 2 (amber) side-by-side, speed, satellites in fix, Socket ping latency to 8.8.8.8, and hardware motion sensors (Accelerometer m/s², Gyroscope rad/s, and Magnetic compass 0–360°)."
                    }
                )

                HelpSection(
                    title = if (isCs) "6. Historie (Přejmenování, Inspekce & Export)" else "6. History (Rename, Point Inspector & Export)",
                    body = if (isCs) {
                        "Správce relací v lokální SQLite databázi. Umožňuje přejmenovat měření, otevřít detailní prohlížeč pro procházení a mazání bod po bodu, zobrazit stopu na mapě, uložit přímo do složky Stažené (Downloads) v telefonu bez cloudu nebo exportovat a sdílet přes CSV, 3D KML (Google Earth) a GeoJSON (QGIS)."
                    } else {
                        "Session manager in local SQLite Room database. Rename sessions, open the point-by-point sample inspector to view or delete samples, replay tracks on the map, save files directly to device Downloads folder without cloud, or export and share via CSV, 3D KML (Google Earth), and GeoJSON (QGIS)."
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Security & Play Protect Note
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
                                text = if (isCs) "Bezpečnost & Google Play Protect" else "Security & Google Play Protect",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "Aplikace nevyžaduje ani nevyužívá invazivní oprávnění READ_PHONE_STATE (žádný přístup k hovorům, kontaktům ani IMEI). Všechna data o BTS buňkách jsou v Androidu navázána na geolokaci (ACCESS_FINE_LOCATION) a ukládají se výhradně na Vašem zařízení."
                            } else {
                                "The app does not require or request sensitive READ_PHONE_STATE permission (zero access to calls, contacts, or IMEI). All cellular cell tower parameters are tied to Android geocoding (ACCESS_FINE_LOCATION) and stored locally on your device."
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
