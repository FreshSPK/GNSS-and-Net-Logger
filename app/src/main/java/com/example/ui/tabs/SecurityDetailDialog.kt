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
                "Nezávisle porovnává tři hodinové zdroje: 1) atomový čas GNSS satelitů, 2) síťový/systémový čas NITZ a 3) internetový SNTP server (UDP:123). Mezi pravidelnými 60s dotazy běží NTP referenční čas přes monotónní čítač elapsedRealtime() (hodiny OS nemění), zatímco NITZ se ze sítě stahuje jen při událostech (Attach / změna TA či 4G↔5G) s krokem 1 s."
            } else {
                "Cross-audits three independent clocks: 1) GNSS UTC, 2) Cellular NITZ/System clock, and 3) Internet SNTP UTC (UDP:123). Between automatic 60s SNTP polls, the NTP clock runs on monotonic elapsedRealtime() (without changing OS time), while NITZ updates event-driven (Attach / TA / RAT change) at 1s steps."
            },
            howItIsCalculated = if (isCs) {
                "GNSS−NTP = GNSS UTC − NTP (UDP:123 s kompenzací poloviny RTT)\n" +
                    "NITZ−NTP = Systémový/NITZ čas − NTP\n" +
                    "GNSS−NITZ = GNSS UTC − Systémový/NITZ čas\n" +
                    "Pozn.: NITZ v mobilní síti má granulaci celých sekund a Android jej synchronizuje jen občas (proto má NITZ−NTP vyšší toleranci)."
            } else {
                "GNSS−NTP = GNSS UTC − NTP (UDP:123 with RTT/2 correction)\n" +
                    "NITZ−NTP = System/NITZ clock − NTP\n" +
                    "GNSS−NITZ = GNSS UTC − System/NITZ clock\n" +
                    "Note: Carrier NITZ updates have 1 s granularity (higher NITZ−NTP tolerance)."
            },
            thresholds = if (isCs) {
                "OK: |GNSS−NTP| ≤ 2 000 ms, |GNSS−NITZ| ≤ 2 500 ms, |NITZ−NTP| ≤ 3 500 ms · WARNING (+20 b.): GNSS−NTP > 2 000 ms, GNSS−NITZ > 2 500 ms nebo NITZ−NTP > 3 500 ms · CRITICAL (+70 b.): > 5 000 ms / 6 000 ms / 8 000 ms."
            } else {
                "OK: |GNSS−NTP| ≤ 2,000 ms, |GNSS−NITZ| ≤ 2,500 ms, |NITZ−NTP| ≤ 3,500 ms · WARNING (+20 pts): > 2,000 / 2,500 / 3,500 ms · CRITICAL (+70 pts): > 5,000 / 6,000 / 8,000 ms."
            }
        )

        checkId == "gnss_hw_clock" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Sleduje plynulost chodu vnitřního křemenného oscilátoru (TCXO) GNSS přijímače. Počítá pouze nové skoky vzniklé během aktivního 3D fixu (≥ 4 satelity) – běžné úsporné resetování čipu bez fixu v interiéru a počítadlo před startem relace jsou ignorovány a po restartu aplikace se nulují."
            } else {
                "Monitors stability of the internal GNSS TCXO oscillator via clock discontinuity jumps and drift rate. Counts only new discontinuities occurring during active 3D fix (≥ 4 sats), ignoring boot HAL totals, indoor power cycling without fix, and resetting cleanly on restart."
            },
            howItIsCalculated = if (isCs) {
                "Skoky = součet nových inkrementů hardwareClockDiscontinuityCount od prvního GnssMeasurementsEvent během aktivního fixu (při startu relace vždy 0).\n" +
                    "Drift = GnssClock.driftNanosPerSecond [ns/s]."
            } else {
                "Jumps = cumulative increments of hardwareClockDiscontinuityCount during active 3D fix after initial GnssMeasurementsEvent (resets to 0 per session).\n" +
                    "Drift = GnssClock.driftNanosPerSecond [ns/s]."
            },
            thresholds = if (isCs) {
                "OK: < 2 nové skoky za fixu a |drift| ≤ 50 000 ns/s · WARNING (+25 b.): skoky ≥ 2 nebo |drift| > 50 000 ns/s."
            } else {
                "OK: < 2 discontinuities in fix and |drift| ≤ 50,000 ns/s · WARNING (+25 pts): discontinuities ≥ 2 or |drift| > 50,000 ns/s."
            }
        )

        checkId == "gnss_constellation_drop" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Skutečný přijímač pod oblohou sleduje současně více nezávislých systémů (GPS, Galileo, GLONASS, BeiDou). Levnější jednokonstelační SDR spoofer generuje pouze americké GPS L1 – po jeho spuštění ostatní konstelace náhle zmizí."
            } else {
                "An authentic open-sky receiver tracks multiple GNSS constellations concurrently (GPS, Galileo, GLONASS, BeiDou). Low-cost SDR spoofers emit GPS L1 only, causing non-GPS constellations to drop out."
            },
            howItIsCalculated = if (isCs) {
                "Sleduje příznak hasEverSeenMultiConstellation v relaci a porovnává počet GPS satelitů proti součtu Galileo + GLONASS + BeiDou."
            } else {
                "Monitors hasEverSeenMultiConstellation across the session and compares GPS count against combined Galileo + GLONASS + BeiDou count."
            },
            thresholds = if (isCs) {
                "OK: Gal/GLO/BDS ≥ 1 · CRITICAL (+65 b.): Po předchozím multi-GNSS příjmu spadne Gal/GLO/BDS na 0 při ≥ 7 GPS a Ø C/N0 > 33 dB-Hz."
            } else {
                "OK: Gal/GLO/BDS ≥ 1 · CRITICAL (+65 pts): Non-GPS count hits 0 despite ≥ 7 GPS sats and mean C/N0 > 33 dB-Hz after prior multi-GNSS reception."
            }
        )

        checkId == "gnss_nmea_vs_location" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Porovnává souřadnice vypočtené přímo GNSS modemem v textových větách NMEA (\$GNGGA / \$GNRMC) proti souřadnicím z Android Location API. Odhalí hookování a přepisování polohy v operačním systému."
            } else {
                "Compares raw modem NMEA sentence coordinates (\$GNGGA / \$GNRMC) against fused Android Location API coordinates to catch OS-level location hooking or spoofing."
            },
            howItIsCalculated = if (isCs) {
                "Parsuje formát ddmm.mmmm z příchozí věty \$GNGGA / \$GNRMC do WGS84 a počítá Haversine odchylku ΔNMEA [m] od Location."
            } else {
                "Parses ddmm.mmmm fields from live \$GNGGA / \$GNRMC sentences into WGS84 and computes Haversine offset ΔNMEA [m] vs. Location."
            },
            thresholds = if (isCs) {
                "OK: ΔNMEA ≤ 35 m · CRITICAL (+75 b.): ΔNMEA > 35 m při platném fixu."
            } else {
                "OK: ΔNMEA ≤ 35 m · CRITICAL (+75 pts): ΔNMEA > 35 m on an active fix."
            }
        )

        checkId == "gnss_adr_doppler_freeze" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Sdružuje hloubkové fyzikální kontroly GNSS: 1) zamrznutí polohy družic na obloze po ≥ 60 s (statický replay), 2) rozpor znaménka Dopplerova posuvu proti změně elevace družice, 3) výpadek nosné fáze ADR (0 %) při silném signálu, 4) skokový nárůst C/N0 (≥ +9 dB) v klidu a 5) zamrzlou přesnost při vysokém HDOP."
            } else {
                "Combines deep physical GNSS checks: 1) sky constellation freeze over ≥ 60s (static replay), 2) Doppler sign contradicting satellite elevation slope, 3) 0% carrier phase ADR lock at strong C/N0, 4) abrupt stationary C/N0 takeover jump (≥ +9 dB), and 5) frozen accuracy under high HDOP."
            },
            howItIsCalculated = if (isCs) {
                "Sleduje 60s změnu azimutu/elevace (|ΔAz|+|ΔEl|), korelaci znaménka pseudorangeRateMps s dEl/dt, poměr platných ADR_STATE_VALID ku použitým družicím a 4,5s krok Ø C/N0."
            } else {
                "Tracks 60s azimuth/elevation drift (|ΔAz|+|ΔEl|), sign correlation between pseudorangeRateMps and dEl/dt, ADR_STATE_VALID ratio, and 4.5s mean C/N0 step."
            },
            thresholds = if (isCs) {
                "WARNING (+35–40 b.): OppDop ≥ 3, ΔCN0 ≥ +9 dB v klidu, ADR = 0 % při Ø C/N0 > 38 dB-Hz nebo zamrzlá přesnost · CRITICAL (+70 b.): Zamrzlé nebe ≥ 60 s (< 0,05°)."
            } else {
                "WARNING (+35–40 pts): OppDop ≥ 3, stationary ΔCN0 ≥ +9 dB, 0% ADR above 38 dB-Hz, or frozen accuracy · CRITICAL (+70 pts): Sky frozen ≥ 60s (< 0.05°)."
            }
        )

        checkId == "gnss_gyro_heading" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Integruje projekci úhlové rychlosti gyroskopu do svislé osy zemského tíhového pole (∫ω_vert dt) během jízdy (> 14 km/h) a porovnává ji se změnou kurzu (bearing) z GNSS. Doplňuje o kontrolu celkové intenzity magnetického pole |B| [µT]."
            } else {
                "Integrates gravity-projected vertical gyroscope angular velocity (∫ω_vert dt) while moving (> 14 km/h) and compares turn delta against GNSS bearing change, alongside ambient magnetic flux |B| [µT]."
            },
            howItIsCalculated = if (isCs) {
                "ω_vert = (ω⃗ · g⃗) / |g⃗|, ΔYaw_Gyro = ∫ω_vert dt za 4,5 s vs. ΔBearing_GNSS; magnetometr |B| = √(Bx² + By² + Bz²)."
            } else {
                "ω_vert = (ω⃗ · g⃗) / |g⃗|, ΔYaw_Gyro = ∫ω_vert dt over 4.5s vs. ΔBearing_GNSS; magnetometer |B| = √(Bx² + By² + Bz²)."
            },
            thresholds = if (isCs) {
                "OK: |ΔYaw| ≤ 28°, |B| 18–95 µT · WARNING (+25–35 b.): |ΔYaw| > 28° nebo anomálie |B| · CRITICAL (+65 b.): |ΔYaw| > 40°."
            } else {
                "OK: |ΔYaw| ≤ 28°, |B| 18–95 µT · WARNING (+25–35 pts): |ΔYaw| > 28° or |B| anomaly · CRITICAL (+65 pts): |ΔYaw| > 40°."
            }
        )

        checkId == "imu_step_speed" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Bez dalších systémových oprávnění detekuje z dynamických rázů akcelerometru rytmické kroky chůze (posledních 6 s) a porovnává je s rychlostí z GNSS."
            } else {
                "Detects rhythmic walking step cadence over the last 6 seconds from accelerometer peaks (zero extra permissions) and cross-checks against GNSS speed."
            },
            howItIsCalculated = if (isCs) {
                "Počítá kroky v okně 6 s (impulz 1,45–8,5 m/s² s odstupem 280–1200 ms) a kontroluje rozpor s GNSS rychlostí (> 35 km/h nebo zamrznutí < 0,5 km/h při ≥ 8 krocích)."
            } else {
                "Counts steps in a 6s sliding window (1.45–8.5 m/s² dynamic peaks spaced 280–1200 ms) and flags mismatch with GNSS speed (> 35 km/h or frozen < 0.5 km/h at ≥ 8 steps)."
            },
            thresholds = if (isCs) {
                "OK: Shoda kadence chůze s rychlostí · WARNING (+35 b.): Detekovaná chůze při GNSS > 35 km/h nebo ≥ 8 kroků při rychlosti < 0,5 km/h."
            } else {
                "OK: Walking cadence matches velocity · WARNING (+35 pts): Walking with GNSS > 35 km/h or ≥ 8 steps at < 0.5 km/h."
            }
        )

        checkId == "net_integrity" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Kontroluje validitu datového připojení, přidělené DNS servery čistě mobilní sítě (bez Wi-Fi a VPN), stav DATA_SUSPENDED, Captive Portal a přítomnost VPN tunelu."
            } else {
                "Inspects internet validation, DNS servers assigned on pure cellular transport (excluding Wi-Fi and VPN), DATA_SUSPENDED state, Captive Portal, and VPN status."
            },
            howItIsCalculated = if (isCs) {
                "Čte ConnectivityManager NetworkCapabilities (VALIDATED, CAPTIVE_PORTAL, TRANSPORT_VPN) a LinkProperties.dnsServers (detekce privátních RFC1918/ULA DNS adres 192.168/10/fc00 na mobilních datech)."
            } else {
                "Inspects NetworkCapabilities (VALIDATED, CAPTIVE_PORTAL, TRANSPORT_VPN) and LinkProperties.dnsServers (flagging private RFC1918/ULA DNS on cellular-only links)."
            },
            thresholds = if (isCs) {
                "OK: Internet ověřen, veřejné DNS · INFO: VPN · WARNING (+35 b.): Výpadek internetu, Captive Portal nebo DATA_SUSPENDED · CRITICAL (+65 b.): Privátní lokální DNS na mobilních datech."
            } else {
                "OK: Validated public DNS · INFO: VPN · WARNING (+35 pts): Unvalidated net, Captive Portal, or DATA_SUSPENDED · CRITICAL (+65 pts): Private/local DNS on cellular data."
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

        checkId.endsWith("_fspl_and_cqi") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Ověřuje fyzikální strop přijatého výkonu podle útlumu volného prostoru (FSPL) k BTS, hlídá podezřelé testovací identifikátory (PLMN 001-01/999, TAC 0/1, eNB 0/1), úzké SDR kanály (1,4 / 3 MHz) a rozpor mezi silným RSRP a kriticky nízkým CQI."
            } else {
                "Validates measured RSRP against the Free-Space Path Loss (FSPL) physical ceiling to the BTS, flags test/reserved IDs (PLMN 00101/999, TAC 0/1, eNB 0/1), ultra-narrow SDR bandwidths (1.4/3 MHz), and low CQI despite strong RSRP."
            },
            howItIsCalculated = if (isCs) {
                "FSPL_max = 40 − FSPL_dB [dBm]; ΔFSPL = RSRP − FSPL_max.\nKontrola PLMN (00101/999*), TAC (0,1,65534,65535), eNB (0,1), šířky kanálu (1400/3000 kHz) a CQI ∈ 1..3 při RSRP > −85 dBm."
            } else {
                "FSPL_max = 40 − FSPL_dB [dBm]; ΔFSPL = RSRP − FSPL_max.\nChecks PLMN (00101/999*), TAC (0,1,65534,65535), eNB (0,1), LTE BW (1.4/3 MHz), and CQI ∈ 1..3 at RSRP > −85 dBm."
            },
            thresholds = if (isCs) {
                "WARNING (+35 b.): úzké SDR pásmo (1,4/3 MHz) nebo nízké CQI při silném RSRP · CRITICAL (+65–75 b.): ΔFSPL > +12 dB nebo testovací PLMN/TAC/eNB."
            } else {
                "WARNING (+35 pts): narrow SDR BW (1.4/3 MHz) or poor CQI at strong RSRP · CRITICAL (+65–75 pts): ΔFSPL > +12 dB or test PLMN/TAC/eNB."
            }
        )

        checkId.endsWith("_imu_ta_drop") -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Kombinuje inerciální senzory telefonu s radiovým parametrem Timing Advance. Pokud telefon nehybně leží na stole (IMU klid ≥ 10 s), vzdálenost ke skutečné BTS se nemůže měnit – náhlý pád TA směrem k nule znamená přiblížení mobilní falešné BTS."
            } else {
                "Correlates physical IMU stillness with cellular Timing Advance. When the handset is stationary (≥ 10s), tower distance cannot shrink – an abrupt TA drop toward zero indicates a rogue base station approaching or capturing the link."
            },
            howItIsCalculated = if (isCs) {
                "Ukládá klidovou bázi TA při IMU klidu ≥ 10 s a sleduje pokles ΔTA ≥ 4 na TA ≤ 2 při změně buňky (CI/PCI) nebo bez sousedů (alarm drží 90 s)."
            } else {
                "Captures a stationary TA baseline after ≥ 10s IMU stillness and triggers on TA drop ≥ 4 down to TA ≤ 2 on cell switch (CI/PCI) or zero neighbors (90s hold)."
            },
            thresholds = if (isCs) {
                "WARNING (+35 b.): skokový pokles TA ≥ 4 na TA ≤ 2 v klidu · CRITICAL (+55 b.): pokles na TA ≤ 1 na izolované buňce."
            } else {
                "WARNING (+35 pts): stationary TA drop ≥ 4 to TA ≤ 2 · CRITICAL (+55 pts): drop to TA ≤ 1 on an isolated cell."
            }
        )

        checkId == "dual_sim_cross_check" -> SecurityCheckGuide(
            whatItMeans = if (isCs) {
                "Křížově srovnává obě aktivní SIM karty (SIM 1 vs. SIM 2) jako vzájemnou referenci. Odhalí cílený útok na jednoho operátora (jedna SIM sražena na 2G s TA=0, zatímco druhá drží 4G/5G), geografický nesoulad BTS obou SIM (> 40 km) či souběžné rušení."
            } else {
                "Cross-correlates SIM 1 and SIM 2 as independent hardware probes, exposing targeted single-SIM 2G/TA=0 downgrades, > 40 km BTS geolocation divergence, or simultaneous multi-carrier jamming."
            },
            howItIsCalculated = if (isCs) {
                "Porovnává RAT a TA mezi SIM 1 a SIM 2, Haversine vzdálenost mezi známými BTS obou SIM a současný pokles SINR < −1 dB při RSRP > −95 dBm."
            } else {
                "Compares RAT & TA divergence across SIM 1 and SIM 2, Haversine distance between matched BTS sites, and simultaneous SINR < −1 dB under strong RSRP."
            },
            thresholds = if (isCs) {
                "OK: Obě SIM konzistentní na 4G/5G, ΔBTS < 40 km · CRITICAL (+65–70 b.): Jednostranný 2G TA=0 downgrade, ΔBTS > 40 km nebo duální RF rušení."
            } else {
                "OK: Both SIMs consistent on 4G/5G, ΔBTS < 40 km · CRITICAL (+65–70 pts): Single-SIM 2G TA=0 downgrade, ΔBTS > 40 km, or dual-SIM jamming."
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
