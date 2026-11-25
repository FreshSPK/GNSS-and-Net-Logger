# 📡 GNSS & Net Logger

[English](#english-documentation) | [Česky](#česká-dokumentace)

[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026--36)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20M3-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Room](https://img.shields.io/badge/Database-Room%20v6%20SQLite-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Release](https://img.shields.io/badge/Release-v1.6-10B981.svg?style=flat)](RELEASE_v1.6.md)
[![VUT FEKT](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Standalone native Android application for field RF drive-testing, multi-constellation GNSS satellite diagnostics, Dual-SIM (4G LTE / 5G NSA / 5G SA) network telemetry logging, offline Czech 3-operator BTS registry matching, and real-time GNSS/RF anti-spoofing & jamming detection. Runs **100% locally** with zero external API keys, cloud accounts, or tracking SDKs.

* **Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)
* **Links:** [VUT Profile](https://www.vut.cz/lide/jan-kufa-125512) · [Latest Release (v1.6)](RELEASE_v1.6.md) · [All Releases](https://github.com/FreshSPK/GNSS-and-Net-Logger/releases)
* **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`

---

## Architecture & Screen Map

```text
┌────────────────────────────────────────────────────────────────────────────────┐
│                        GNSS & Net Logger (100% On-Device)                      │
├─────────────────────┬───────────────────────────┬──────────────────────────────┤
│   Hardware & Radio  │     Analysis & Engine     │     Persistence & Output     │
│  • LocationManager  │  • Dual-SIM Cell Engine   │  • Room SQLite (v6, MIG 5→6) │
│  • GnssStatus / Raw │  • GNSS & SkyPlot Engine  │  • Foreground Service + Mark │
│  • TelephonyManager │  • BtsRepository (MCC230) │  • CSV / 3D KML / GPX / JSON │
│  • IMU & Barometer  │  • SecurityAnalyzer (v1.6)│  • OSMDroid Map + BTS Layer  │
│  • SNTP UDP:123     │  • 3-Clock NTP/NITZ/GNSS  │  • 6-Page Vector PDF Manual  │
└─────────────────────┴───────────────────────────┴──────────────────────────────┘
```

| # | English Tab | Česká záložka | Core Purpose / Hlavní účel |
| :-: | :--- | :--- | :--- |
| 1 | **Overview** | **Přehled** | Foreground recording, waypoints (`+ Mark`), selective `Fields` (incl. `logSpoofing`), GNSS kinematics, Dual-SIM & sensor HUD |
| 2 | **Cellular RF** | **RF parametry** | Dual-SIM DSDS isolation, 5G NSA/SA & CA decoder, Czech BTS DB (`23001/02/03`), ΔTA, FSPL, neighbor cells & DB PCI/TAC check |
| 3 | **GNSS** | **GNSS** | Multi-constellation L1+L5/E5a/B2a SkyPlot radar, C/N₀ bars, satellite table, `GnssMeasurementsEvent` AGC & NMEA stream |
| 4 | **Map** | **Mapa** | OpenStreetMap + BTS Tower layer, BTS-only browser, RF serving links, live breadcrumbs & clickable historical replay |
| 5 | **Charts** | **Grafy** | Interactive touch-scrubbing waveforms (RSRP, SINR, Speed, Sats, PDOP, AGC, Ping, IMU, Battery) |
| 6 | **Spoofing** *(v1.6)* | **Podvody** *(v1.6)* | Threat Risk Index (`0–100%`), 3-clock NTP/NITZ/GNSS audit, TA-vs-RSRP & BTS physics, GNSS/IMU anti-spoofing, Demo Attack Simulator |
| 7 | **History** | **Historie** | Search/rename sessions, Dual-SIM + Security analytics, numbered point inspector (`#1`…) with jump-to-Map & CSV/KML/GPX/GeoJSON exports |

---

<a id="english-documentation"></a>
## 🌐 English Documentation

### 1. Overview (Live Telemetry HUD)
* **Foreground Recording:** Start, Pause, Resume, and Stop background logging with a persistent notification, timed `WakeLock`, live chronometer, odometer, and separate **`HO SIM1` / `SIM2`** handover counters.
* **Field Waypoints (`+ Mark`) & Selective Logging (`Fields`):** Tag terrain checkpoints (*Tunnel*, *Interference*, *BTS*) on the fly and toggle individual logged groups: **GNSS/DOP**, **Cellular RF**, **Neighbors**, **Sensors**, **Battery**, **NMEA**, **Ping**, and **Spoofing & Jamming Telemetry (`logSpoofing`)**.
* **GNSS Kinematics & Filtered DOP:** 6-decimal coordinates, speed, bearing, 68% error bounds, and live HDOP / VDOP / PDOP (shows `"—"` instead of stale values when fewer than 4 satellites are in fix, with automatic indoor fix-loss timeout).
* **Satellites & Dual-SIM Summary:** Unique physical satellites vs. tracked L1+L5/E5a/B2a signals, constellation breakdown, stacked SIM 1 / SIM 2 bars with Carrier Aggregation (`CA:`) frequencies, and barometer/battery/ping readouts.

### 2. Cellular RF (Serving BTS, CZ BTS Registry & 5G NSA/SA)
* **SIM Switcher (`SIM 1` | `SIM 2` | `Both SIMs`):** Strict per-subscription DSDS isolation showing `DATA ACTIVE (RRC Connected)` vs. `VOICE & SMS (DSDS Standby)` and live ΔRSRP operator delta.
* **Czech 3-Operator BTS Database (`23001` T-Mobile CZ, `23002` O2 CZ, `23003` Vodafone CZ):**
  * **Streaming OpenCelliD.org (`.csv` / `.csv.gz`) & GSMweb CSV Import:** Streams full OpenCelliD Czechia dumps (`230.csv` or `230.csv.gz` with WGS84 GPS, auto-filtering `mcc=230`), GSMweb (`CP1250`/`UTF-8`, street names/bands/PCI), and CLF v3/v4 in 2,000-row batches — bidirectionally merging OpenCelliD GPS with GSMweb metadata in either import order.
  * **Real BTS Distance & Azimuth:** Computes Haversine ground distance, cardinal bearing, ΔTA (Timing Advance vs. real GPS distance), Free-Space Path Loss (`FSPL dB`), co-located bands on the same eNodeB mast, and CETIN **MOCN** sharing fallback.
  * **Coordinate Editor & Optional Geocoding:** Manually edit exact mast coordinates or optionally geocode GPS-less GSMweb addresses.
* **5G NSA (EN-DC) vs. 5G SA Detection:** Per-SIM `TelephonyCallback` distinguishes `4G LTE`, `5G NSA` (`EN-DC LTE Anchor (Idle)` vs. `EN-DC NR Active`), and `5G SA`, including `SS-RSRP/RSRQ/SINR` and `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Cell Decoding:** Exact DL/UL MHz frequencies, `Total CA` bandwidth, per-carrier `PCC/SCC/SCG` badges, PLMN, TAC, PCI, `eNodeB/gNodeB + Sector ID`, modem CQI, and Timing Advance BTS distance (`1×` / `2×`).
* **Neighbors & DB PCI/TAC Check:** Per-SIM neighbor tables with **`All` | `4G LTE` | `5G NR`** filter chips, handover candidate highlighting (`Δ ≤ 3 dB`), and BTS DB `PCI/TAC` mismatch warnings (full anti-spoofing and jamming diagnostics live in **Spoofing**).

### 3. GNSS (Satellites & SkyPlot)
* **Constellations & Dual-Band:** Tracks GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, and NavIC across L1/E1/B1/G1 and L5/E5a/B2a, reporting both unique physical satellites and total signals (aligned with *GPS Test*), with live filter chips.
* **SkyPlot Radar:** Groups multi-band signals per physical satellite with **By Constellation** / **By C/N₀** modes, tap-to-inspect details, and 1:1 legend (**Solid fill** = In fix, **Colored ring** = Unused, **Green glow** = Dual-band **L5 / E5a / B2a**).
* **C/N₀ Bars, Table, AGC & NMEA:** Signal strength bars, full satellite table, `GnssMeasurementsEvent` AGC jamming monitor, Doppler shift, and live `$GNGGA` / `$GNRMC` stream.

### 4. Map (OpenStreetMap, BTS Layer & Live/Replay Tracks)
* **BTS Layer, BTS-Only Mode & Serving RF Links:** Toggleable OpenStreetMap overlay plus dedicated **BTS Only** mode showing operator-colored towers (`TM`, `O2`, `VF`), serving SIM 1 / SIM 2 masts, clickable tower details (co-located cells/bands/PCIs/TACs + DB shortcut), and dashed UE-to-BTS RF link lines.
* **Live & Replay Tracks:** Continuously collects background breadcrumbs from startup (`since start`) and renders identical color-coded tracks in Live and History Replay modes.
* **Color Layers (`S1` / `S2` toggle):**
  * **RSRP:** Green (`> -80 dBm`) to Red (`< -105 dBm`)
  * **Speed:** Blue (`< 10 km/h`) to Red (`> 90 km/h`)
  * **Satellites:** Red (`< 5`), Orange (`5–20`), Cyan (`21–30`), Emerald (`31–50`), Blue (`> 50`)
  * **PDOP:** Emerald (`< 0.7`), Cyan (`0.7–1.5`), Amber (`1.5–2.5`), Orange (`2.5–3.5`), Red (`> 3.5`)

### 5. Charts
Interactive touch-scrubbing Canvas waveforms (live & historical): **Dual-SIM RSRP & SINR**, **Speed**, **Satellites** (**Used** vs. **Visible**), **PDOP**, **AGC Interference**, **Ping RTT**, **Accelerometer**, **Gyroscope**, **Compass**, and **Battery Temperature**.

### 6. Spoofing & Jamming Shield *(v1.6)*
* **Threat Risk Index (`0–100%`) & Sub-Scores:** Fused risk score across **GNSS**, **Cell RF**, and **Clock**, with bottom-bar warning badge, **Deep Scan**, timestamped **Incident Log**, and **acoustic warning tone** on critical events during active recording.
* **Multi-Source UTC Time Audit (`GNSS · NITZ · NTP`):** Built-in RFC 4330 SNTP UDP:123 client (`time.google.com`, `tik.cesnet.cz`, `pool.ntp.org`, `time.cloudflare.com`) with RTT compensation compared against hardware `GnssClock` UTC (`timeNanos − fullBiasNanos`) and cellular NITZ time, plus hardware clock discontinuity & oscillator drift tracking.
* **RF Propagation Physics (`TA vs. RSRP` & `BTS Distance`):** Validates Timing Advance against known BTS Haversine distance (`ΔTA`), checks measured RSRP against empirical path-loss (`-72 dBm @ 150 m / 1800 MHz`, `34 dB/dec`), flags wideband LTE jamming (`RSSI − RSRP > 38 dB` + SINR/RSRQ), 2G GSM downgrades, isolated SIB cells, TAC oscillations, and strong-RF data blackholes.
* **Deep GNSS Anti-Spoofing & Sensor Fusion:** Evaluates `Location.isMock`, AGC vs. C/N₀ compression, uniform C/N₀ (`Ø > 43.5 dB-Hz`, `σ < 2.3 dB-Hz`) & zenith-vs-horizon elevation inversion, orbital Doppler spread collapse (`σ_v < 45 m/s`), L1-only spoofing (vanishing L5/E5a), IMU stationary kinematics, barometer `Δh > 40 m`, teleport jumps (`> 65 m/s`), and GNSS-vs-BTS distance (`> 45 km`).
* **Demo Attack Simulator:** One-tap Demo Mode verification scenarios (*GNSS Spoof*, *GNSS Jamming*, *2G IMSI Catcher*, *TA + Power*, *Time Drift*).

### 7. History & Exports (Room DB v6)
* **Session Management & Analytics:** Search, rename, view Dual-SIM analytics (`S1` + `S2` RSRP ranges, `5G/4G` share, avg PDOP, **Max Security Risk** & **Incident Count**), open waveforms in **Charts**, or inspect 1-based samples (`#1`, `#2`, …) with per-point **Security Status & Alert summary**, **jump-to-Map**, and single-point deletion.
* **Streaming Exports (`Download/GNSS_Net_Logger/` & Share Sheet):**
  * **CSV** (complete SIM 1 + SIM 2 RF, `Sim1Bts*` / `Sim2Bts*`, `UniqueSats*`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA` / `Sim2CA`, `WaypointNote`, NMEA, Doppler, sensors, plus 10 v1.6 security columns: `SecurityRiskScore`, `SecurityStatus`, `GnssRiskScore`, `RfRiskScore`, `TimeRiskScore`, `GnssMinusNtpMs`, `Sim1TaVsBtsDelta_m`, `Sim1RsrpResidual_dB`, `Sim1RssiRsrpGap_dB`, `SecurityAlertsSummary`)
  * **3D KML** (color-coded path + waypoint `<Placemark>` pins)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF, BTS, satellite, and `<security_*>` `<extensions>`)
  * **GeoJSON** (QGIS/ArcGIS-ready features with full BTS, satellite, CA, and security properties)

---

<a id="česká-dokumentace"></a>
## 🇨🇿 Česká dokumentace

### 1. Přehled (Živý telemetrický HUD)
* **Záznam na pozadí:** Spuštění, pozastavení, obnovení a zastavení měření se stálou notifikací, časovaným `WakeLock`, stopkami, počítadlem vzdálenosti a oddělenými **handovery `HO SIM1` / `SIM2`**.
* **Značky (`+ Značka`) & Výběr dat (`Položky`):** Vkládání bodů zájmu (*Tunel*, *Rušení*, *BTS*) a nezávislé zapínání logovaných skupin: **GNSS/DOP**, **Mobilní RF**, **Sousedé**, **Senzory**, **Baterie**, **NMEA**, **Ping** a **Detekce spoofingu & rušení (`logSpoofing`)**.
* **GNSS poloha & filtrované DOP:** Souřadnice na 6 desetinných míst, rychlost, azimut, přesnost (68 %) a HDOP / VDOP / PDOP (při méně než 4 satelitech nebo ztrátě fixu v budově zobrazuje `"—"` a automaticky nuluje zastaralou rychlost).
* **Satelity & Dual-SIM přehled:** Unikátní družice i L1+L5/E5a/B2a signály, rozpad podle konstelací, grafy SIM 1 / SIM 2 s agregovanými pásmy (`CA:`) a údaje z barometru, baterie a pingu.

### 2. RF parametry (Obslužná BTS, Databáze BTS ČR & 5G NSA/SA)
* **Přepínač (`SIM 1` | `SIM 2` | `Obě SIM`):** Striktní DSDS oddělení karet s indikací `DATA AKTIVNÍ (RRC Connected)` vs. `HLAS & SMS (DSDS Standby)` a živým rozdílem signálu ΔRSRP.
* **Databáze BTS 3 českých operátorů (`23001` T-Mobile CZ, `23002` O2 CZ, `23003` Vodafone CZ):**
  * **Streamovaný import OpenCelliD.org (`.csv` / `.csv.gz`) & GSMweb CSV:** Zpracuje celý český export OpenCelliD (`230.csv` i přímo zabalený `230.csv.gz` pro `mcc=230`) i CSV z GSMweb (`CP1250`/`UTF-8`) a CLF v3/v4 po dávkách 2 000 řádků — a obousměrně sloučí GPS z OpenCelliD s metadaty z GSMweb v libovolném pořadí importu.
  * **Skutečná vzdálenost k BTS & azimut:** Počítá Haversine vzdálenost k vysílači, kompasový směr, odchylku Timing Advance (ΔTA), teoretický útlum volného prostoru (`FSPL dB`), souběžná pásma na stožáru i sdílení **MOCN** (T-Mobile/O2).
  * **Editor souřadnic & volitelné geokódování:** Ruční úprava GPS souřadnic nebo volitelné dohledání GPS z adres GSMweb bez souřadnic.
* **Detekce 5G NSA (EN-DC) vs. 5G SA:** Per-SIM `TelephonyCallback` rozlišuje `4G LTE`, `5G NSA` (`EN-DC LTE Kotva (Idle)` vs. `EN-DC NR Aktivní`) a `5G SA`, včetně metrik `SS-RSRP/RSRQ/SINR` a `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Dekódování buněk:** Přesné DL/UL frekvence v MHz, celková šířka pásma `Celkem CA`, štítky `PCC/SCC/SCG`, PLMN, TAC, PCI, `eNodeB/gNodeB + Sektor`, reálné CQI z modemu a vzdálenost BTS dle Timing Advance (`1×` / `2×`).
* **Sousední buňky & kontrola PCI/TAC:** Oddělené seznamy sousedů pro každou SIM s filtrem **`Vše` | `4G LTE` | `5G NR`**, zvýrazněním kandidátů na handover (`Δ ≤ 3 dB`) a kontrolou **nesouladu PCI/TAC proti databázi BTS** (kompletní detekce spoofingu a rušení je na záložce **Podvody**).

### 3. GNSS (Satelity & SkyPlot)
* **Konstelace & Dual-Band:** Podpora GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS a NavIC v pásmech L1/E1/B1/G1 a L5/E5a/B2a se zobrazením unikátních družic i celkových signálů (*GPS Test*) a filtračními čipy.
* **SkyPlot Radar:** Sdružuje vícepásmové signály do jedné družice (**Podle konstelace** / **Podle C/N₀**), detail po klepnutí a 1:1 legenda (**Plná barva** = Ve fixu, **Barevný kroužek** = Mimo fix, **Zelená záře** = Dual-band **L5 / E5a / B2a**).
* **C/N₀ grafy, Tabulka, AGC & NMEA:** Sloupcové grafy síly signálu, kompletní tabulka, AGC monitor rušení z `GnssMeasurementsEvent`, Dopplerův posuv a živý tok `$GNGGA` / `$GNRMC`.

### 4. Mapa (OpenStreetMap, Vrstva BTS & Živá trasa)
* **Vrstva BTS, režim „Jen BTS“ & RF spojnice:** Přepínatelná vrstva OpenStreetMap i samostatný režim **Jen BTS** s barevnými vysílači (`TM`, `O2`, `VF`), klikatelným detailem stožáru (všechny buňky, pásma, PCI, TAC + odkaz do DB) a čárkovanou přímou RF linkou k obslužné BTS.
* **Živá trasa i přehrávání historie:** Průběžně sbírá trasu od spuštění aplikace (`od startu`) a vykresluje ji živě i při zpětném přehrávání s možností kliknout na libovolný bod trasy.
* **Barevné vrstvy (přepínač `S1` / `S2`):**
  * **RSRP:** Zelená (`> -80 dBm`) až Červená (`< -105 dBm`)
  * **Rychlost:** Modrá (`< 10 km/h`) až Červená (`> 90 km/h`)
  * **Satelity:** Červená (`< 5`), Oranžová (`5–20`), Azurová (`21–30`), Smaragdová (`31–50`), Modrá (`> 50`)
  * **PDOP:** Smaragdová (`< 0,7`), Azurová (`0,7–1,5`), Oranžová (`1,5–2,5`), Oranžovočervená (`2,5–3,5`), Červená (`> 3,5`)

### 5. Grafy
Interaktivní dotykové křivky s min/prům/max statistikou (živě i pro historické relace): **Duální RSRP & SINR**, **Rychlost**, **Satelity** (**Ve fixu** i **Viditelné**), **PDOP**, **AGC rušení**, **Ping RTT**, **Akcelerometr**, **Gyroskop**, **Kompas** a **Teplota baterie**.

### 6. Podvody (Detekce spoofingu, rušení & přesného času) *(v1.6)*
* **Threat Risk Index (`0–100 %`) & dílčí skóre:** Souhrnný ukazatel rizika pro **GNSS**, **RF sítě** a **Čas**, varovný štítek ve spodní liště, **Hloubkový sken**, **Deník incidentů** a **akustický tón** při kritickém incidentu během záznamu.
* **Srovnání přesného času (`GNSS · NITZ · NTP`):** Vestavěný RFC 4330 SNTP UDP:123 klient (`time.google.com`, `tik.cesnet.cz`, `pool.ntp.org`, `time.cloudflare.com`) s kompenzací RTT oproti `GnssClock` UTC (`timeNanos − fullBiasNanos`) a síťovému času NITZ + sledování diskontinuit a driftu oscilátoru.
* **RF Fyzika (`TA vs. RSRP` & `Poloha BTS`):** Ověřuje vzdálenost dle Timing Advance proti poloze BTS (ΔTA), kontroluje empirický útlum trasy (`-72 dBm @ 150 m / 1800 MHz`, `34 dB/dek`), detekuje širokopásmové rušení (`RSSI − RSRP > 38 dB` + SINR/RSRQ), 2G GSM downgrade, izolovanou buňku, oscilaci TAC a výpadky dat při silném signálu.
* **GNSS Anti-Spoofing & Fúze senzorů:** Kontroluje `Location.isMock`, pokles zisku AGC vs. C/N₀, jednotný výkon C/N₀ (`Ø > 43,5 dB-Hz`, `σ < 2,3 dB-Hz`) i inverzi elevace, kolaps Dopplerova rozptylu (`σ_v < 45 m/s`), zmizení pásma L5/E5a při silném L1, nesoulad s nehybným IMU a barometrem (`Δh > 40 m`), skoky polohy (`> 65 m/s`) i vzdálenost GNSS vs. BTS (`> 45 km`).
* **Demo Simulátor útoků:** V Demo režimu testuje scénáře *GNSS Spoof*, *GNSS Jamming*, *2G IMSI Catcher*, *TA + Výkon* a *Časový útok*.

### 7. Historie & Exporty (Room DB v6)
* **Správa relací & statistiky:** Vyhledávání, přejmenování, Dual-SIM přehled (`S1` + `S2` RSRP rozsahy, podíl `5G/4G`, průměrný PDOP, **Max. riziko** a **Počet incidentů**), otevření průběhů v **Grafech** a bodový inspektor číslovaný od **`#1`** se stavem zabezpečení, **skokem na Mapu** a mazáním bodů.
* **Streamované exporty (`Download/GNSS_Net_Logger/` & Sdílení):**
  * **CSV** (kompletní sloupce SIM 1 + SIM 2, `Sim1Bts*` / `Sim2Bts*`, `UniqueSats*`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA` / `Sim2CA`, `WaypointNote`, NMEA, Doppler, senzory + 10 bezpečnostních sloupců v1.6: `SecurityRiskScore`, `SecurityStatus`, `GnssRiskScore`, `RfRiskScore`, `TimeRiskScore`, `GnssMinusNtpMs`, `Sim1TaVsBtsDelta_m`, `Sim1RsrpResidual_dB`, `Sim1RssiRsrpGap_dB`, `SecurityAlertsSummary`)
  * **3D KML** (barevná trasa + špendlíky značek)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF, BTS, satelitní a `<security_*>` `<extensions>`)
  * **GeoJSON** (vrstva pro QGIS/ArcGIS včetně BTS, satelitů, CA a bezpečnostních metrik)

---

## Settings, PDF Manual, Permissions & Releases / Nastavení, PDF manuál, oprávnění a vydání

### Settings & Illustrated PDF Manual / Nastavení a PDF manuál
* **Language / Jazyk:** Czech, English, or System default / Čeština, Angličtina nebo dle systému.
* **Demo Mode / Demo režim:** Synthetic drive-test around FEKT VUT Brno with `DEMO` badge & attack simulator; cleanly resets on exit.
* **Sampling & Calibration / Vzorkování & Kalibrace:** **Walking (`5s / 10m`)**, **Driving (`10s / 50m`)**, **Static (`10s`)** + continuous sliders (`1–60 s`, `5–200 m`), TA multiplier (`1× 78.12 m` / `2× 156.24 m`), QNH pressure (`1013.25 hPa`), and optional BTS geocoding toggle.
* **6-Page Vector PDF Manual / 6stránkový ilustrovaný PDF manuál:** Tap the top-right **Info (`ⓘ`)** icon to download into `Download/GNSS_Net_Logger/GNSS_Net_Logger_v1.6_Manual_*.pdf` or open/share a bilingual vector-illustrated handbook covering every GNSS, RF, BTS, TA, and anti-spoofing model.

### Permissions & Privacy / Oprávnění a soukromí
| Permission / Oprávnění | Purpose (EN) | Účel (CZ) |
| :--- | :--- | :--- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GNSS position, raw satellites & cellular cell measurements | GNSS poloha, surová data družic a parametry mobilních buněk |
| `READ_PHONE_STATE` | Dual-SIM operator label resolution only (no calls, SMS, contacts, or IMEI) | Pouze rozlišení operátorů u Dual-SIM (bez hovorů, SMS, kontaktů či IMEI) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Uninterrupted background logging | Nepřerušované logování na pozadí |
| `POST_NOTIFICATIONS` | Active recording notification | Zobrazení notifikace běžícího záznamu |
| `INTERNET` / `ACCESS_NETWORK_STATE` | OpenStreetMap tiles, SNTP UDP:123 clock audit, optional BTS geocoding & `8.8.8.8:53` ping | Mapové dlaždice OSM, přesný čas SNTP UDP:123, volitelné geokódování BTS a ping `8.8.8.8:53` |

> All telemetry stays strictly on-device in Room/SQLite storage (`v6`). Zero cloud uploads or analytics SDKs are used.  
> Veškerá data zůstávají výhradně v lokální databázi zařízení (`Room v6`) bez jakéhokoli cloudu či analytických SDK.

### Releases / Vydání
| Version | Release Notes | APK Package |
| :---: | :--- | :--- |
| **v1.6** | [RELEASE_v1.6.md](RELEASE_v1.6.md) | [`APK/GNSS_Net_Logger_v1.6.apk.zip`](APK/GNSS_Net_Logger_v1.6.apk.zip) |
| **v1.5** | [RELEASE_v1.5.md](RELEASE_v1.5.md) | [`APK/GNSS_Net_Logger_v1.5.apk.zip`](APK/GNSS_Net_Logger_v1.5.apk.zip) |
| **v1.4** | [RELEASE_v1.4.md](RELEASE_v1.4.md) | [`APK/GNSS_Net_Logger_v1.4.apk.zip`](APK/GNSS_Net_Logger_v1.4.apk.zip) |
| **v1.3** | [RELEASE_v1.3.md](RELEASE_v1.3.md) | [`APK/GNSS_Net_Logger_v1.3.apk.zip`](APK/GNSS_Net_Logger_v1.3.apk.zip) |
| **v1.2** | [RELEASE_v1.2.md](RELEASE_v1.2.md) | [`APK/GNSS_Net_Logger_v1.2.apk.zip`](APK/GNSS_Net_Logger_v1.2.apk.zip) |
