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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun AboutAndHelpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, tint = CyanTertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nápověda a Informace o autorovi",
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
                                text = "Autor projektu",
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
                            text = "Ústav radioelektroniky (UREL)\nFakulta elektrotechniky a komunikačních technologií (FEKT)\nVysoké učení technické v Brně (VUT)",
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
                    text = "Struktura aplikace a popis funkcí",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanTertiary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                HelpSection(
                    title = "1. Měření (Přehled)",
                    body = "Centrální přístrojový štít (HUD). Umožňuje spustit, pozastavit a zastavit ukládání telemetrických vzorků. Tlačítkem „Položky“ můžete zvolit přesnou matici parametrů, které se mají ukládat (GNSS, RF, Sousedé, Senzory, Ping). Zobrazuje okamžitou rychlost, přesnost, výšku, azimut, DOP a stav obou SIM karet s barevnými bargrafy síly signálu."
                )

                HelpSection(
                    title = "2. Mobily (RF parametry)",
                    body = "Detailní analýza obsluhujících a sousedních buněk pro SIM 1 i SIM 2. Rozpad Cell ID na eNodeB ID a Sektor ID pro LTE i 5G NR. Zobrazení fyzických frekvencí Downlink / Uplink v MHz, šířky kanálu (Channel Bandwidth) a odhadu vzdálenosti k BTS věži přes Timing Advance (TA) s možností 1× (78m) i 2× (156m) kalibrace. Tabulka sousedů navíc detekuje kandidáty na Handover."
                )

                HelpSection(
                    title = "3. Satelity (GNSS parametry)",
                    body = "Podpora konstelací GPS, Galileo, GLONASS a BeiDou. Zobrazuje celkový počet i počet satelitů využitých ve fixu. Zahrnuje filtr pro zobrazení samotného GPS, Galilea či dvoufrekvenčního příjmu (L5/E5a). K dispozici jsou tři podzáložky: Skyplot radar (polární pohled na oblohu 0–360°), Signály C/N0 (bargrafy síly signálu) a Podrobná tabulka se všemi parametry (SVID, pásmo, azimut, elevace, fix)."
                )

                HelpSection(
                    title = "4. Mapa (OSM)",
                    body = "Zobrazuje trasu na offline podkladu OpenStreetMap bez nutnosti jakýchkoliv API klíčů. Nahoře umožňuje přepínat barevné zobrazení bodů podle: RSRP síly signálu, Rychlosti v km/h, Počtu satelitů nebo hodnoty PDOP. Obsahuje plovoucí legendu s barevnými prahy a tlačítka pro zoom a vycentrování polohy."
                )

                HelpSection(
                    title = "5. Grafy",
                    body = "Časové průběhy v reálném čase: Výkon signálu RSRP v čase, Rychlost, Počet satelitů, Latence socket pingu na 8.8.8.8 a environmentální data z pohybových senzorů (Akcelerometr, Gyroskop a Magnetický kompas)."
                )

                HelpSection(
                    title = "6. Historie",
                    body = "Správce relací uložených v lokální SQLite databázi. Umožňuje jednotlivé relace přejmenovat, otevřít detailní prohlížeč a procházet bod po bodu (včetně smazání chybného vzorku), zobrazit celou trasu na mapě nebo exportovat do formátu CSV, 3D KML (pro Google Earth) a GeoJSON (pro GIS aplikace)."
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
                                text = "Bezpečnost & Google Play Protect",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSignal
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aplikace nevyžaduje ani nevyužívá invazivní oprávnění READ_PHONE_STATE (žádný přístup k hovorům, kontaktům ani IMEI). Všechna data o BTS buňkách jsou v Androidu navázána na geolokaci (ACCESS_FINE_LOCATION) a ukládají se výhradně na Vašem zařízení.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimaryDark
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Zavřít")
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
