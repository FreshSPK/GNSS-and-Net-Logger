package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
                        Text(
                            text = "Ing. Jan Kufa, Ph.D.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
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
                        "Centrální přístrojový štít (HUD). Umožňuje spustit, pozastavit a zastavit ukládání telemetrických vzorků. Tlačítkem „Položky“ můžete zvolit přesnou matici parametrů, které se mají ukládat (GNSS, RF, Sousedé, Senzory, Ping). Zobrazuje okamžitou rychlost, přesnost, výšku, azimut, DOP a stav obou SIM karet s barevnými bargrafy síly signálu."
                    } else {
                        "Main instrument telemetry HUD. Allows you to Start, Pause, Resume, and Stop logging telemetry samples. The 'Fields' button lets you configure which parameter groups are recorded (GNSS, RF, Neighbors, Sensors, Ping). Shows speed, accuracy, altitude, azimuth, DOP, and stacked Dual-SIM cards with live signal bargraphs."
                    }
                )

                HelpSection(
                    title = if (isCs) "2. Mobily (RF parametry & 5G/LTE)" else "2. Cellular RF (LTE & 5G NR)",
                    body = if (isCs) {
                        "Detailní analýza obsluhujících a sousedních buněk pro SIM 1 i SIM 2. Rozpad Cell ID na eNodeB ID a Sektor ID (LTE) a gNodeB ID a Sektor ID (5G). Zobrazení Downlink/Uplink nosných v MHz, šířky kanálu (Channel Bandwidth) a rozpadu 5G NR metrik na SS (Synchronization Signal Block) vs. CSI (Channel State Information). Výpočet vzdálenosti k BTS věži přes Timing Advance (TA) s volitelnou 1× (78m) i 2× (156m) kalibrací. Tabulka sousedů navíc detekuje kandidáty na Handover."
                    } else {
                        "In-depth serving and neighbor cell monitor for SIM 1 & SIM 2. Decodes Cell IDs into eNodeB/gNodeB and Sector IDs. Calculates Downlink/Uplink carrier frequencies in MHz, channel bandwidth, and split 5G NR metrology (SS-RSRP/SS-SINR broadcast vs. CSI-RSRP/CSI-SINR datapath). Timing Advance (TA) BTS distance calculation (78m or 156m calibration), and neighbor handover candidates table."
                    }
                )

                HelpSection(
                    title = if (isCs) "3. Satelity (GNSS, SkyPlot & AGC Jamming)" else "3. Satellites (GNSS, SkyPlot & AGC Jamming)",
                    body = if (isCs) {
                        "Multi-systémový příjem (GPS, Galileo, GLONASS, BeiDou). Tři podzáložky: Skyplot radar (polární pohled na oblohu 0–360° s odlišením L1/L5 a záře fixu), Signály C/N0 (sloupcové grafy v dB-Hz) a Podrobná tabulka se všemi satelity. Zahrnuje filtry (Všechny, Pouze L5, GPS, Galileo) a AGC Jamming Monitor detekující rušení a propad zisku předzesilovače."
                    } else {
                        "Multi-constellation tracking (GPS, Galileo, GLONASS, BeiDou). Three sub-tabs: Skyplot radar (polar view 0–360° with L1/L5 and fix highlights), C/N0 signals (dB-Hz bar charts), and Detailed satellite table. Includes constellation filters and AGC Jamming Monitor detecting sudden gain drops from RF interference."
                    }
                )

                HelpSection(
                    title = if (isCs) "4. Mapa (OpenStreetMap)" else "4. Map (OpenStreetMap)",
                    body = if (isCs) {
                        "Offline OpenStreetMap zobrazení bez jakýchkoliv externích API klíčů. Horní přepínač umožňuje filtrovat a obarvovat trasu podle: RSRP signálu, Rychlosti, Počtu satelitů nebo PDOP. Obsahuje plovoucí legendu, zoom tlačítka (+/-) a azurové tlačítko vycentrování."
                    } else {
                        "Offline OpenStreetMap view without any proprietary API keys. Top switcher allows track color-coding by: RSRP Signal, Speed, Satellite count, or PDOP. Includes dynamic floating legend, zoom buttons (+/-), and recenter FAB."
                    }
                )

                HelpSection(
                    title = if (isCs) "5. Grafy (Časové řady & IMU)" else "5. Charts (Time Series & IMU)",
                    body = if (isCs) {
                        "Průběhy v reálném čase: RSRP signál základnové stanice, Rychlost, Počet satelitů v fixu, Socket Ping odezva na 8.8.8.8 a pohybové senzory (Akcelerometr v m/s², Gyroskop v rad/s a Magnetický kompas 0–360°)."
                    } else {
                        "Real-time waveforms: Base station RSRP in time, Speed, Satellites in fix, Socket ping latency to 8.8.8.8, and hardware motion sensors (Accelerometer m/s², Gyroscope rad/s, and Magnetic compass 0–360°)."
                    }
                )

                HelpSection(
                    title = if (isCs) "6. Historie (Přejmenování, Inspekce & Export)" else "6. History (Rename, Point Inspector & Export)",
                    body = if (isCs) {
                        "Správce relací v lokální SQLite databázi. Umožňuje přejmenovat měření, otevřít detailní prohlížeč pro procházení a mazání bod po bodu, zobrazit celou stopu na mapě nebo exportovat do CSV, 3D KML (pro Google Earth) a GeoJSON (pro QGIS)."
                    } else {
                        "Session manager in local SQLite Room database. Rename sessions, open the point-by-point sample inspector to view or delete samples, replay tracks on the map, or export to CSV, 3D KML (Google Earth), and GeoJSON (QGIS)."
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
