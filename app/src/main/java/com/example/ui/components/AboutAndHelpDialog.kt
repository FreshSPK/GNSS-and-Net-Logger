package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.export.UserManualPdfGenerator
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AboutAndHelpDialog(
    lang: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isCs = lang != "EN"
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var pdfSavedStatus by remember { mutableStateOf<String?>(null) }

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
                                    text = "Release v1.8",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Illustrated PDF User & Technical Manual Download Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldSignal.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, EmeraldSignal.copy(alpha = 0.55f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = EmeraldSignal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCs) "Uživatelský & technický manuál (PDF)" else "User & Technical Manual (PDF)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "Ilustrovaná 6stránková příručka s grafy a schématy: vysvětluje modely GNSS (L1/L5, SkyPlot, DOP), 5G NSA/SA, fyziku TA vs. RSRP a BTS, kontrolu 3 časů UTC i detekci spoofingu a rušení."
                            } else {
                                "Illustrated 6-page handbook with technical diagrams explaining GNSS models (L1/L5, SkyPlot, DOP), 5G NSA/SA, TA vs. RSRP & BTS physics, 3-clock UTC auditing, and anti-spoofing detectors."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (isGeneratingPdf) return@Button
                                    isGeneratingPdf = true
                                    coroutineScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            UserManualPdfGenerator.saveManualToDownloads(context, lang)
                                        }
                                        isGeneratingPdf = false
                                        if (result.success) {
                                            pdfSavedStatus = result.displayPath
                                            Toast.makeText(
                                                context,
                                                if (isCs) "✓ PDF manuál uložen: ${result.displayPath}" else "✓ PDF manual saved: ${result.displayPath}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                if (isCs) "Chyba při ukládání PDF: ${result.errorMessage ?: ""}" else "PDF save failed: ${result.errorMessage ?: ""}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isGeneratingPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("download_pdf_manual_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSignal),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                if (isGeneratingPdf) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = DarkSlate900
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = DarkSlate900,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCs) "Stáhnout PDF" else "Download PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkSlate900,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isGeneratingPdf) return@OutlinedButton
                                    isGeneratingPdf = true
                                    coroutineScope.launch {
                                        val fileResult = withContext(Dispatchers.IO) {
                                            runCatching { UserManualPdfGenerator.createShareableOrViewableManualFile(context, lang) }
                                        }
                                        isGeneratingPdf = false
                                        fileResult.onSuccess { file ->
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    context.startActivity(viewIntent)
                                                } catch (_: Exception) {
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        putExtra(Intent.EXTRA_SUBJECT, file.name)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            shareIntent,
                                                            if (isCs) "Otevřít / Sdílet PDF manuál" else "Open / Share PDF Manual"
                                                        )
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    if (isCs) "Nepodařilo se otevřít PDF: ${e.message}" else "Could not open PDF: ${e.message}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                if (isCs) "Chyba PDF: ${err.message}" else "PDF error: ${err.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isGeneratingPdf,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("open_pdf_manual_button"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CyanTertiary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = CyanTertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCs) "Otevřít" else "Open",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanTertiary,
                                    maxLines = 1
                                )
                            }
                        }
                        pdfSavedStatus?.let { path ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isCs) "✓ Uloženo do: $path" else "✓ Saved to: $path",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release v1.8 New Features Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = CyanTertiary.copy(alpha = 0.12f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CyanTertiary))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isCs) "✨ Novinky verze 1.8 (Release v1.8)" else "✨ What's New in Release v1.8",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CyanTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCs) {
                                "• Přesná česká lokalizace BTS (ČÚZK RÚIAN + BeaconDB): adresy z GSMwebu (tlte/o2lte/vlte CSV) nyní překládá oficiální registr ČÚZK RÚIAN s přesností na budovu (~2–10 m); lesní a mimoměstské stožáry bez ulice či neznámé buňky automaticky dohledá BeaconDB (bez API klíče)\n" +
                                "• Univerzální import BTS (.ntm / .clf / .kml / .zip / CellMapper): nově podporuje exporty NetMonster (.ntm, nm2300x), věžové i logovací CSV z CellMapperu (včetně párování přes eNodeB/gNodeB pro všechny sektory stožáru), soubory KML i vícenásobné .zip archivy\n" +
                                "• Ochrana přesnosti souřadnic: hierarchie priorit (Ruční > CLF/NTM/KML > RÚIAN > CellMapper eNB > BeaconDB > OpenCelliD) zaručuje, že hrubší odhady nikdy nepřepíší přesné zaměření stožáru\n" +
                                "• Základ z v1.7 & v1.6: komplexní štít Podvody (fúze gyro/mag/krokoměru, ΔNMEA, ADR, 3× čas UTC, FSPL strop, Dual-SIM, Simulator útoků) a vektorový PDF manuál"
                            } else {
                                "• Precision BTS Geolocation (CUZK RUIAN + BeaconDB): resolves Czech GSMweb street addresses via the official CUZK RUIAN registry to ~2–10 m building accuracy, and resolves rural/streetless masts or unknown cells via BeaconDB without any API key\n" +
                                "• Universal Multi-Format BTS Import (.ntm / .clf / .kml / .zip / CellMapper): adds NetMonster .ntm / nm2300x, CellMapper eNodeB tower CSVs & signal logs (with co-located eNB sibling sector matching), KML placemarks, and multi-file .zip archives\n" +
                                "• Coordinate Precision Hierarchy: Manual > CLF/NTM/KML > RUIAN > CellMapper eNB > BeaconDB > OpenCelliD ensures coarse crowdsourced centroids never overwrite exact mast coordinates\n" +
                                "• Full v1.7 / v1.6 Foundation: Spoofing & Jamming Shield (Gyro/Mag/Step sensor fusion, ΔNMEA, ADR phase, 3-clock UTC audit, FSPL ceiling, Dual-SIM check) and vector PDF manual"
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
                    title = if (isCs) "2. Mobily (5G NSA/SA, CA & Databáze BTS ČR)" else "2. Cellular RF (5G NSA/SA, CA & CZ BTS Registry)",
                    body = if (isCs) {
                        "Detailní analýza SIM 1 / SIM 2 / Obě SIM. Páruje obslužnou buňku proti lokální databázi českých vysílačů (T-Mobile, O2, Vodafone), ukazuje lokalitu BTS, reálnou GPS vzdálenost, azimut, Δ TA, FSPL, sdílená pásma na stožáru a nesoulad PCI/TAC. Komplexní detekce spoofingu a rušení je v záložce Podvody."
                    } else {
                        "Dual-SIM analyzer matching serving cells against the local 3-operator Czech BTS registry (T-Mobile, O2, Vodafone), displaying site address, real GPS distance, azimuth, TA delta, FSPL, co-located mast bands, MOCN status, and PCI/TAC checks. Full spoofing & jamming analysis is in the Spoofing tab."
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
                    title = if (isCs) "4. Mapa (OpenStreetMap, Vrstva BTS & Živá trasa)" else "4. Map (OpenStreetMap, BTS Layer & Live Track)",
                    body = if (isCs) {
                        "OpenStreetMap s tmavým/světlým režimem, vrstvou známých BTS vysílačů (T-Mobile, O2, Vodafone) a přímou RF spojnicí k obslužným BTS. Barevně vykresluje trasu podle RSRP (S1/S2), rychlosti, satelitů (< 5 až > 50) i PDOP (< 0,7 až > 3,5)."
                    } else {
                        "OpenStreetMap view with dark night-mode filter, known Czech BTS tower markers (T-Mobile, O2, Vodafone), and serving RF link lines. Renders color-coded tracks by RSRP (S1/S2), Speed, Satellites (< 5 to > 50), or PDOP (< 0.7 to > 3.5) both live and in replay."
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
                    title = if (isCs) "6. Podvody (Detekce spoofingu, rušení & času)" else "6. Spoofing (Anti-Spoofing, Jamming & Clocks)",
                    body = if (isCs) {
                        "Bezpečnostní štít pro GNSS a mobilní sítě: porovnává čas GNSS/NITZ/NTP, hlídá fyziku TA vs. RSRP a polohu BTS, odhaluje širokopásmové rušení (RSSI−RSRP, AGC), inverzi elevace C/N0, kolaps Dopplera, fúzi s IMU/barometrem i 2G IMSI Catchery."
                    } else {
                        "GNSS & cellular security shield: audits GNSS/NITZ/NTP clocks, validates TA vs. RSRP path-loss and BTS location, detects wideband RF/AGC jamming, C/N0 elevation inversion, Doppler collapse, IMU/barometer mismatches, and 2G IMSI Catchers."
                    }
                )

                HelpSection(
                    title = if (isCs) "7. Historie (Analytika, Skok na bod & Export)" else "7. History (Analytics, Point Jump & Export)",
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
