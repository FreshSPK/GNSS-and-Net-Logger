# 📡 GNSS & Net Logger

[English](#english-documentation) | [Česky](#česká-dokumentace)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20SQLite-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Release](https://img.shields.io/badge/Release-v1.5-10B981.svg?style=flat)](RELEASE_v1.5.md)
[![VUT FEKT](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Standalone native Android application for field RF drive-testing, multi-constellation GNSS satellite diagnostics, Dual-SIM (4G LTE / 5G NSA / 5G SA) network telemetry logging, and offline Czech 3-operator BTS registry matching. Runs 100% locally with zero external API keys, cloud accounts, or tracking SDKs.

* **Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)
* **Links:** [VUT Profile](https://www.vut.cz/lide/jan-kufa-125512) · [Releases](https://github.com/FreshSPK/GNSS-and-Net-Logger/releases)
* **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`

---

<a id="english-documentation"></a>
## 🌐 English Documentation

### Standalone Architecture
* **Maps (OSMDroid / OpenStreetMap):** Hardware-accelerated tile rendering with local disk caching (no Google Maps API key required).
* **Storage (Room / SQLite):** Local relational persistence using Kotlin Coroutines and Flow; no Firebase or cloud login.
* **Hardware Telemetry:** Direct reads via Android `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `TelephonyManager`, `SensorManager`, `BatteryManager`, and a direct socket ping probe (`8.8.8.8:53`).

### Application Screens & Features

#### 1. Overview (Live Telemetry HUD)
* **Foreground Recording:** Start, Pause, Resume, and Stop background logging with a persistent notification, `WakeLock`, live chronometer, odometer, and separate **`HO SIM1` / `SIM2`** handover counters.
* **Field Waypoints (`+ Mark`) & Selective Logging (`Fields`):** Tag terrain checkpoints (*Tunnel*, *Interference*, *BTS*) on the fly and toggle individual logged data groups (GNSS/DOP, Cellular RF, Neighbors, Sensors, Battery, NMEA, Ping).
* **GNSS Kinematics & Filtered DOP:** 6-decimal coordinates, speed, bearing, 68% error bounds, and live HDOP / VDOP / PDOP (shows `"—"` instead of stale or invalid values when fewer than 4 satellites are in fix, with automatic indoor fix-loss timeout).
* **Satellites & Dual-SIM Summary:** Unique physical satellites vs. tracked L1+L5/E5a/B2a signals, constellation breakdown, stacked SIM 1 / SIM 2 signal bars with Carrier Aggregation (`CA:`) frequencies, and barometer/battery/ping readouts.

#### 2. Cellular RF (Serving BTS, CZ BTS Registry, 5G NSA/SA & Security)
* **SIM Switcher (`SIM 1` | `SIM 2` | `Both SIMs`):** Strict per-subscription DSDS isolation showing `DATA ACTIVE (RRC Connected)` vs. `VOICE & SMS (DSDS Standby)` and live $\Delta\text{RSRP}$ operator delta.
* **Czech 3-Operator BTS Database (T-Mobile CZ `23001`, O2 CZ `23002`, Vodafone CZ `23003`):**
  * **Streaming OpenCelliD.org (`.csv` / `.csv.gz`) & GSMweb CSV Import:** Streams full OpenCelliD Czechia dumps (`230.csv` or `230.csv.gz` directly with WGS84 GPS, auto-filtering `mcc=230`), plus GSMweb (`CP1250`/`UTF-8`, street names/bands/PCI without GPS) and CLF v3/v4 in 2,000-row batches — smartly merging OpenCelliD GPS with GSMweb metadata in either import order.
  * **Real BTS Distance & Azimuth:** Computes Haversine ground distance, cardinal bearing, $\Delta$ Timing Advance vs. real GPS delta, Free-Space Path Loss (`FSPL dB`), co-located bands on the same eNodeB mast, and CETIN **MOCN** sharing fallback.
  * **Coordinate Editor & Optional Geocoding:** Lets you set exact tower coordinates manually or optionally geocode GPS-less GSMweb addresses.
* **5G NSA (EN-DC) vs. 5G SA Detection:** Per-SIM `TelephonyCallback` distinguishes `4G LTE`, `5G NSA` (`EN-DC LTE Anchor (Idle)` matching the status bar 5G icon vs. `EN-DC NR Active`), and `5G SA`, including `SS-RSRP/RSRQ/SINR` and `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Cell Decoding:** Exact DL/UL MHz frequencies, `Total CA` bandwidth, and **per-carrier PCC/SCC/SCG component badges with bands & MHz frequencies** (e.g., `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`), plus PLMN, TAC, PCI, `eNodeB/gNodeB + Sector ID`, real modem CQI, and Timing Advance BTS distance (`1×` / `2×`).
* **Neighbors & Anomaly Detector:** Per-SIM neighbor tables with **`All` | `4G LTE` | `5G NR`** filter chips, handover candidate highlighting ($\Delta \le 3\text{ dB}$), and alerts for **2G Downgrade Attacks**, **Isolated BTS**, **Co-Channel Interference**, and **BTS DB PCI/TAC Mismatch**.

#### 3. Satellites (GNSS & SkyPlot)
* **Constellations & Dual-Band:** Tracks GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, and NavIC across L1/E1/B1/G1 and L5/E5a/B2a, reporting both unique physical satellites and total signals (aligned with *GPS Test*), with live filter chips.
* **SkyPlot Radar:** Groups multi-band signals per physical satellite with two color modes (**By Constellation** / **By C/N₀**), tap-to-inspect details, and a 1:1 synchronized legend:
  * **Solid fill** = In fix
  * **Colored ring** = Unused
  * **Green glow** = Dual-band **L5 / E5a / B2a**
* **C/N₀ Bars, Table, AGC & Raw Data:** Signal strength bar chart, full satellite table, `GnssMeasurementsEvent` AGC jamming monitor, Doppler shift, and live `$GNGGA` / `$GNRMC` NMEA view.

#### 4. Map (OpenStreetMap, BTS Layer & Live/Replay Tracks)
* **BTS Towers & Serving RF Link Layer:** Toggleable OpenStreetMap overlay displaying operator-colored BTS markers (T-Mobile, O2, Vodafone), serving SIM 1 / SIM 2 towers, and dashed RF link lines from UE GPS to the serving BTS.
* **Live & Replay Tracks:** Continuously collects background live breadcrumbs from startup (`since start` counter) across tabs and renders color-coded tracks identically in Live and History Replay modes.
* **Color Layers (`S1` / `S2` toggle):**
  * **RSRP:** Green ($> -80\text{ dBm}$) to Red ($< -105\text{ dBm}$)
  * **Speed:** Blue ($< 10\text{ km/h}$) to Red ($> 90\text{ km/h}$)
  * **Satellites:** Red ($< 5$), Orange ($5–20$), Cyan ($21–30$), Emerald ($31–50$), Blue ($> 50$)
  * **PDOP:** Emerald ($< 0.7$), Cyan ($0.7–1.5$), Amber ($1.5–2.5$), Orange ($2.5–3.5$), Red ($> 3.5$)
* **Controls:** BTS overlay toggle, dark/light map filter, auto-follow lock, clickable point/waypoint/tower details, zoom controls, and trail reset.

#### 5. Charts
Interactive touch-scrubbing Canvas waveforms (available live and for historical sessions):
* **Dual-SIM RSRP & SINR** (SIM 1 vs. SIM 2 side-by-side)
* **Speed** (km/h)
* **Satellites** (simultaneous **Used in Fix** vs. **Visible** curves)
* **PDOP** ($< 0.7$ best, $> 3.5$ worst) & **AGC Interference**
* **Ping RTT** (`8.8.8.8:53`), **Accelerometer**, **Gyroscope**, **Compass**, and **Battery Temperature**

#### 6. History & Exports
* **Session Management:** Search, rename, view Dual-SIM session analytics (`S1` + `S2` RSRP ranges, `5G/4G` share, avg PDOP), open waveforms in **Charts**, or inspect 1-based numbered samples (`#1`, `#2`, …) with clear per-SIM labels, **jump-to-Map**, and single-point deletion.
* **Export & Storage:** Save directly to `Download/GNSS_Net_Logger/` or share via Android system share sheet in four streaming formats:
  * **CSV** (v1.5 schema including full SIM 1 + SIM 2 RF parameters, `Sim1BtsLocation`, `Sim1BtsDistance_m`, `Sim1BtsAzimuth_deg`, `Sim2BtsLocation`, `Sim2BtsDistance_m`, `Sim2BtsAzimuth_deg`, `UniqueSatsInView`, `UniqueSatsUsed`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA`, `Sim2CA`, `WaypointNote`, NMEA, Doppler, sensors)
  * **3D KML** (color-coded path + waypoint `<Placemark>` pins)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF, BTS, and `used_satellites` / `visible_satellites` `<extensions>`)
  * **GeoJSON** (QGIS/ArcGIS-ready features with BTS distance/azimuth, satellite, and CA metadata)

### Settings & Demo Mode
* **Language:** Czech, English, or System default.
* **Simulation / Demo Mode:** Generates synthetic drive-test telemetry around Brno FEKT VUT with a visible `DEMO` top-bar badge and persistent banner across all tabs; disabling it cleanly resets synthetic state.
* **Sampling & Calibration:** Presets for **Walking (`5s / 10m`)**, **Driving (`10s / 50m`)**, and **Static (`10s`)**; TA multiplier (`1× 78.12 m` / `2× 156.24 m`); and QNH reference pressure (`1013.25 hPa` default).

### Permissions & Privacy
| Permission | Purpose |
| :--- | :--- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GNSS position, raw satellite status, and cellular cell measurements |
| `READ_PHONE_STATE` | Dual-SIM operator label resolution only (no calls, SMS, contacts, or IMEI) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Uninterrupted background logging |
| `POST_NOTIFICATIONS` | Active recording notification |
| `INTERNET` / `ACCESS_NETWORK_STATE` | OpenStreetMap tile loading and `8.8.8.8:53` socket ping |

> All telemetry stays strictly on-device in Room/SQLite storage. Zero cloud uploads or analytics SDKs are used.

### Releases
* [Release v1.5 Notes](RELEASE_v1.5.md) · Binary: [`APK/GNSS_Net_Logger_v1.5.apk.zip`](APK/GNSS_Net_Logger_v1.5.apk.zip)
* [Release v1.4 Notes](RELEASE_v1.4.md) · Binary: [`APK/GNSS_Net_Logger_v1.4.apk.zip`](APK/GNSS_Net_Logger_v1.4.apk.zip)
* [Release v1.3 Notes](RELEASE_v1.3.md) · Binary: [`APK/GNSS_Net_Logger_v1.3.apk.zip`](APK/GNSS_Net_Logger_v1.3.apk.zip)
* [Release v1.2 Notes](RELEASE_v1.2.md) · Binary: [`APK/GNSS_Net_Logger_v1.2.apk.zip`](APK/GNSS_Net_Logger_v1.2.apk.zip)

---

<a id="česká-dokumentace"></a>
## 🇨🇿 Česká dokumentace

### Samostatná architektura (100% bez API klíčů)
* **Mapy (OSMDroid / OpenStreetMap):** Hardwarově akcelerované vykreslování dlaždic s lokální mezipamětí na disku (bez Google Maps API klíče).
* **Úložiště (Room / SQLite):** Kompletní lokální relační databáze pomocí Kotlin Coroutines a Flow; bez cloudu a přihlašování.
* **Hardwarová telemetrie:** Přímé čtení systémových rozhraní `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `TelephonyManager`, `SensorManager`, `BatteryManager` a nízkoúrovňový socket ping (`8.8.8.8:53`).

### Přehled záložek a funkcí

#### 1. Měření (Přehledový HUD)
* **Záznam na pozadí:** Spuštění, pozastavení, obnovení a zastavení měření se stálou notifikací, `WakeLock`, stopkami, počítadlem vzdálenosti a oddělenými **handovery `HO SIM1` / `SIM2`**.
* **Značky (`+ Značka`) & Výběr dat (`Položky`):** Vkládání bodů zájmu v terénu (*Tunel*, *Rušení*, *BTS*) a nezávislé zapínání logovaných skupin (GNSS/DOP, Mobilní RF, Sousedé, Senzory, Baterie, NMEA, Ping).
* **GNSS poloha & filtrované DOP:** Souřadnice na 6 desetinných míst, rychlost, azimut, přesnost (68 %) a HDOP / VDOP / PDOP (při méně než 4 satelitech nebo ztrátě fixu v budově zobrazuje čistou pomlčku `"—"` a automaticky nuluje zastaralou rychlost).
* **Satelity & Dual-SIM přehled:** Počet unikátních družic i L1+L5/E5a/B2a signálů, rozpad podle konstelací, sloupcové grafy signálu SIM 1 / SIM 2 s rozpisem agregovaných kmitočtů (`CA:`) a údaje z barometru, baterie i pingu.

#### 2. Mobily (Vysílače, Databáze BTS ČR, 5G NSA/SA & Bezpečnost)
* **Přepínač (`SIM 1` | `SIM 2` | `Obě SIM`):** Striktní DSDS oddělení obou karet s indikací `DATA AKTIVNÍ (RRC Connected)` vs. `HLAS & SMS (DSDS Standby)` a živým výpočtem rozdílu signálu $\Delta\text{RSRP}$.
* **Databáze BTS 3 českých operátorů (T-Mobile CZ `23001`, O2 CZ `23002`, Vodafone CZ `23003`):**
  * **Streamovaný import OpenCelliD.org (`.csv` / `.csv.gz`) & GSMweb CSV:** Zpracuje celý český export OpenCelliD (`230.csv` i přímo zabalený `230.csv.gz` s GPS souřadnicemi pro `mcc=230`) i CSV z GSMweb (`CP1250`/`UTF-8` bez GPS, s českou adresou, okresem, pásmem a PCI) po dávkách 2 000 řádků — a chytře sloučí GPS z OpenCelliD s metadaty z GSMweb v libovolném pořadí importu.
  * **Skutečná vzdálenost k BTS & azimut:** Počítá Haversine vzdálenost z GNSS k vysílači, kompasový směr, odchylku oproti Timing Advance ($\Delta\text{TA}$), teoretický útlum volného prostoru (`FSPL dB`), souběžná pásma na stejném `eNodeB` i sdílení **MOCN** (T-Mobile/O2).
  * **Editor souřadnic & volitelné geokódování:** Umožňuje ruční zadání přesných GPS souřadnic nebo volitelné dohledání GPS z adres GSMweb bez souřadnic.
* **Detekce 5G NSA (EN-DC) vs. 5G SA:** Per-SIM `TelephonyCallback` rozlišuje `4G LTE`, `5G NSA` (`EN-DC LTE Kotva (Idle)` odpovídající 5G ikoně v liště vs. `EN-DC NR Aktivní`) a `5G SA`, včetně metrik `SS-RSRP/RSRQ/SINR` a `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Dekódování buněk:** Přesné DL/UL frekvence v MHz, celková šířka pásma `Celkem CA` a **přehled všech agregovaných PCC/SCC/SCG nosných s pásmem a frekvencí v MHz** (např. `PCC B3 1800 MHz + SCC B1 2100 MHz + n78 3500 MHz`), PLMN, TAC, PCI, `eNodeB/gNodeB + Sektor`, reálné CQI z modemu a vzdálenost BTS dle Timing Advance (`1×` / `2×`).
* **Sousední buňky & Detektor anomálií:** Oddělené seznamy sousedů pro každou SIM s filtrem **`Vše` | `4G LTE` | `5G NR`**, zvýrazněním kandidátů na handover ($\Delta \le 3\text{ dB}$) a varováním před **2G Downgrade útokem**, **izolovanou BTS**, **soukanálovým zarušením** i **nesouladem PCI/TAC proti databázi BTS**.

#### 3. Satelity (GNSS & SkyPlot)
* **Konstelace & Dual-Band:** Podpora GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS a NavIC v pásmech L1/E1/B1/G1 a L5/E5a/B2a se zobrazením unikátních fyzických družic i celkových signálů (v souladu s *GPS Test*) a filtračními čipy.
* **SkyPlot Radar:** Sdružuje vícepásmové signály do jedné družice se dvěma barevnými režimy (**Podle konstelace** / **Podle síly C/N₀**), detailem po klepnutí a 1:1 synchronizovanou legendou:
  * **Plná barva** = Ve fixu
  * **Barevný kroužek** = Mimo fix
  * **Zelená záře okolo** = Dvoupásmový příjem **L5 / E5a / B2a**
* **C/N₀ grafy, Tabulka, AGC & NMEA:** Sloupcové grafy síly signálu, kompletní tabulka, AGC monitor rušení z `GnssMeasurementsEvent`, Dopplerův posuv a živý tok `$GNGGA` / `$GNRMC`.

#### 4. Mapa (OpenStreetMap, Vrstva BTS & Živá trasa)
* **Vrstva BTS & RF spojnice:** Přepínatelná vrstva na OpenStreetMap zobrazující barevně rozlišené vysílače T-Mobile, O2 a Vodafone, obslužné vysílače SIM 1 / SIM 2 a čárkovanou přímou RF linku od vaší polohy k obslužné BTS.
* **Živá trasa i přehrávání historie:** Průběžně sbírá trasu na pozadí od spuštění aplikace napříč kartami (`od startu`) a vykresluje ji stejným enginem živě i při zpětném přehrávání.
* **Barevné vrstvy (přepínač `S1` / `S2`):**
  * **RSRP:** Zelená ($> -80\text{ dBm}$) až Červená ($< -105\text{ dBm}$)
  * **Rychlost:** Modrá ($< 10\text{ km/h}$) až Červená ($> 90\text{ km/h}$)
  * **Satelity:** Červená ($< 5$), Oranžová ($5–20$), Azurová ($21–30$), Smaragdová ($31–50$), Modrá ($> 50$)
  * **PDOP:** Smaragdová ($< 0,7$), Azurová ($0,7–1,5$), Oranžová ($1,5–2,5$), Oranžovočervená ($2,5–3,5$), Červená ($> 3,5$)
* **Ovládání:** Přepínač vrstvy BTS, tmavý/světlý režim mapy, auto-follow sledování polohy, klikatelné body s detailem, přiblížení a smazání živé stopy.

#### 5. Grafy
Interaktivní dotykové křivky s min/prům/max statistikou (živě i pro historické relace):
* **Duální RSRP & SINR** (porovnání SIM 1 vs. SIM 2)
* **Rychlost pohybu** (km/h)
* **Satelity** (současné křivky **Ve fixu** a **Viditelné**)
* **PDOP** ($< 0,7$ nejlepší, $> 3,5$ nejhorší) & **AGC rušení**
* **Ping RTT** (`8.8.8.8:53`), **Akcelerometr**, **Gyroskop**, **Kompas** a **Teplota baterie**

#### 6. Historie & Exporty
* **Správa relací:** Vyhledávání, přejmenování, Dual-SIM souhrnné statistiky (`S1` + `S2` RSRP rozsahy, podíl `5G/4G`, průměrný PDOP), otevření průběhů v **Grafech** a přehledný bodový inspektor číslovaný v každém záznamu od **`#1`** se **skokem na Mapu** i mazáním vzorků.
* **Uložení & Sdílení:** Přímý zápis do složky `Download/GNSS_Net_Logger/` nebo systémové sdílení ve čtyřech streamovaných formátech:
  * **CSV** (kompletní sloupce v1.5 pro SIM 1 + SIM 2 včetně `Sim1BtsLocation`, `Sim1BtsDistance_m`, `Sim1BtsAzimuth_deg`, `Sim2Bts*`, `UniqueSatsInView`, `UniqueSatsUsed`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA`, `Sim2CA`, `WaypointNote`, NMEA, Doppler, senzory)
  * **3D KML** (barevná trasa + špendlíky značek)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF, BTS a satelitní `<extensions>` včetně `used_satellites` a `visible_satellites`)
  * **GeoJSON** (vrstva pro QGIS/ArcGIS včetně BTS vzdálenosti/azimutu, seznamů satelitů a CA souhrnů)

### Nastavení a Demo režim
* **Jazyk:** Čeština, Angličtina nebo dle systému.
* **Simulační / Demo režim:** Generuje testovací jízdu kolem kampusu FEKT VUT v Brně se štítkem `DEMO` v horní liště a trvalým bannerem; při vypnutí automaticky vymaže syntetický stav.
* **Vzorkování & Kalibrace:** Profily **Pěší (`5s / 10m`)**, **Auto (`10s / 50m`)** a **Statické (`10s`)**, násobič TA (`1× 78,12 m` / `2× 156,24 m`), referenční tlak QNH (`1013,25 hPa`) a přepínač automatického geokódování BTS.

### Oprávnění a soukromí
| Oprávnění | Účel |
| :--- | :--- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GNSS poloha, satelity a parametry mobilních buněk |
| `READ_PHONE_STATE` | Pouze rozlišení operátorů u Dual-SIM (bez přístupu k hovorům, SMS, kontaktům či IMEI) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Nepřerušované logování na pozadí |
| `POST_NOTIFICATIONS` | Zobrazení notifikace běžícího záznamu |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Stahování mapových dlaždic OpenStreetMap, volitelné geokódování adres BTS a socket ping na `8.8.8.8:53` |

> Veškerá data zůstávají výhradně v lokální databázi zařízení bez jakéhokoli cloudu či analytických SDK.

### Vydání (Releases)
* [Poznámky k vydání v1.5](RELEASE_v1.5.md) · APK: [`APK/GNSS_Net_Logger_v1.5.apk.zip`](APK/GNSS_Net_Logger_v1.5.apk.zip)
* [Poznámky k vydání v1.4](RELEASE_v1.4.md) · APK: [`APK/GNSS_Net_Logger_v1.4.apk.zip`](APK/GNSS_Net_Logger_v1.4.apk.zip)
* [Poznámky k vydání v1.3](RELEASE_v1.3.md) · APK: [`APK/GNSS_Net_Logger_v1.3.apk.zip`](APK/GNSS_Net_Logger_v1.3.apk.zip)
* [Poznámky k vydání v1.2](RELEASE_v1.2.md) · APK: [`APK/GNSS_Net_Logger_v1.2.apk.zip`](APK/GNSS_Net_Logger_v1.2.apk.zip)
