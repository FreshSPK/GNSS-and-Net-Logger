# 📡 GNSS & Net Logger

[English](#english-documentation) | [Česky](#česká-dokumentace)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20SQLite-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Release](https://img.shields.io/badge/Release-v1.4-10B981.svg?style=flat)](RELEASE_v1.4.md)
[![VUT FEKT](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Standalone native Android application for field RF drive-testing, multi-constellation GNSS satellite diagnostics, and Dual-SIM (4G LTE / 5G NSA / 5G SA) network telemetry logging. Runs 100% locally with zero external API keys, cloud accounts, or tracking SDKs.

* **Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)
* **Links:** [VUT Profile](https://www.vut.cz/lide/jan-kufa-125512) · [Releases](https://github.com/FreshSPK/GNSS-and-Net-Logger/releases) · **Latest APK:** [`APK/GNSS_Net_Logger_v1.4.apk.zip`](APK/GNSS_Net_Logger_v1.4.apk.zip)
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

#### 2. Cellular RF (Serving BTS, 5G NSA/SA & Security)
* **SIM Switcher (`SIM 1` | `SIM 2` | `Both SIMs`):** Strict per-subscription DSDS isolation showing `DATA ACTIVE (RRC Connected)` vs. `VOICE & SMS (DSDS Standby)` and live $\Delta\text{RSRP}$ operator delta.
* **5G NSA (EN-DC) vs. 5G SA Detection:** Per-SIM `TelephonyCallback` distinguishes `4G LTE`, `5G NSA` (`EN-DC LTE Anchor (Idle)` matching the status bar 5G icon vs. `EN-DC NR Active`), and `5G SA`, including `SS-RSRP/RSRQ/SINR` and `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Cell Decoding:** Exact DL/UL MHz frequencies, `Total CA` bandwidth, and **per-carrier PCC/SCC/SCG component badges with bands & MHz frequencies** (e.g., `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`), plus PLMN, TAC, PCI, `eNodeB/gNodeB + Sector ID`, real modem CQI, and Timing Advance BTS distance (`1×` / `2×`).
* **Neighbors & Anomaly Detector:** Per-SIM neighbor tables with **`All` | `4G LTE` | `5G NR`** filter chips, handover candidate highlighting ($\Delta \le 3\text{ dB}$), and alerts for **2G Downgrade Attacks**, **Isolated BTS**, and **Co-Channel Interference**.

#### 3. Satellites (GNSS & SkyPlot)
* **Constellations & Dual-Band:** Tracks GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, and NavIC across L1/E1/B1/G1 and L5/E5a/B2a, reporting both unique physical satellites and total signals (aligned with *GPS Test*), with live filter chips.
* **SkyPlot Radar:** Groups multi-band signals per physical satellite with two color modes (**By Constellation** / **By C/N₀**), tap-to-inspect details, and a 1:1 synchronized legend:
  * **Filled dot + white border** = Used in fix
  * **Hollow ring** (gray in C/N₀ mode) = Unused
  * **Green glow** = Dual-band **L5 / E5a / B2a**
* **C/N₀ Bars, Table, AGC & Raw Data:** Signal strength bar chart, full satellite table, `GnssMeasurementsEvent` AGC jamming monitor, Doppler shift, and live `$GNGGA` / `$GNRMC` NMEA view.

#### 4. Map (OpenStreetMap)
* **Live & Replay Tracks:** Continuously collects background live breadcrumbs from startup across tabs and renders color-coded tracks identically in Live and History Replay modes.
* **Color Layers (`S1` / `S2` toggle):**
  * **RSRP:** Green ($> -80\text{ dBm}$) to Red ($< -105\text{ dBm}$)
  * **Speed:** Blue ($< 10\text{ km/h}$) to Red ($> 90\text{ km/h}$)
  * **Satellites:** Red ($< 5$), Orange ($5–20$), Light Green ($21–30$), Emerald ($31–50$), Cyan ($> 50$)
  * **PDOP:** Emerald ($< 1.0$), Cyan ($1.0–2.0$), Amber ($2.0–4.0$), Red ($\ge 4.0$)
* **Controls:** Dark/light map filter, auto-follow lock, clickable point/waypoint details, zoom controls, and trail reset.

#### 5. Charts
Interactive touch-scrubbing Canvas waveforms (available live and for historical sessions):
* **Dual-SIM RSRP & SINR** (SIM 1 vs. SIM 2 side-by-side)
* **Speed** (km/h)
* **Satellites** (simultaneous **Used in Fix** vs. **Visible** curves)
* **PDOP** ($0–4$ scale) & **AGC Interference**
* **Ping RTT** (`8.8.8.8:53`), **Accelerometer**, **Gyroscope**, **Compass**, and **Battery Temperature**

#### 6. History & Exports
* **Session Management:** Search, rename, view automatic session analytics (min/avg/max RSRP, avg PDOP, 5G/4G split), open waveforms in **Charts**, or inspect individual samples with **jump-to-Map** and single-point deletion.
* **Export & Storage:** Save directly to `Download/GNSS_Net_Logger/` or share via Android system share sheet in four streaming formats:
  * **CSV** (full SIM 1 + SIM 2 RF parameters, `UniqueSatsInView`, `UniqueSatsUsed`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA`, `Sim2CA`, `WaypointNote`, NMEA, Doppler, sensors)
  * **3D KML** (color-coded path + waypoint `<Placemark>` pins)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF and `used_satellites` / `visible_satellites` `<extensions>`)
  * **GeoJSON** (QGIS/ArcGIS-ready features with satellite and CA metadata)

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

#### 2. Mobily (Vysílače, 5G NSA/SA & Bezpečnost)
* **Přepínač (`SIM 1` | `SIM 2` | `Obě SIM`):** Striktní DSDS oddělení obou karet s indikací `DATA AKTIVNÍ (RRC Connected)` vs. `HLAS & SMS (DSDS Standby)` a živým výpočtem rozdílu signálu $\Delta\text{RSRP}$.
* **Detekce 5G NSA (EN-DC) vs. 5G SA:** Per-SIM `TelephonyCallback` rozlišuje `4G LTE`, `5G NSA` (`EN-DC LTE Kotva (Idle)` odpovídající 5G ikoně v liště vs. `EN-DC NR Aktivní`) a `5G SA`, včetně metrik `SS-RSRP/RSRQ/SINR` a `CSI-RSRP/RSRQ/SINR`.
* **Carrier Aggregation & Dekódování buněk:** Přesné DL/UL frekvence v MHz, celková šířka pásma `Celkem CA` a **přehled všech agregovaných PCC/SCC/SCG nosných s pásmem a frekvencí v MHz** (např. `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`), PLMN, TAC, PCI, `eNodeB/gNodeB + Sektor`, reálné CQI z modemu a vzdálenost BTS dle Timing Advance (`1×` / `2×`).
* **Sousední buňky & Detektor anomálií:** Oddělené seznamy sousedů pro každou SIM s filtrem **`Vše` | `4G LTE` | `5G NR`**, zvýrazněním kandidátů na handover ($\Delta \le 3\text{ dB}$) a varováním před **2G Downgrade útokem**, **izolovanou BTS** a **soukanálovým zarušením**.

#### 3. Satelity (GNSS & SkyPlot)
* **Konstelace & Dual-Band:** Podpora GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS a NavIC v pásmech L1/E1/B1/G1 a L5/E5a/B2a se zobrazením unikátních fyzických družic i celkových signálů (v souladu s *GPS Test*) a filtračními čipy.
* **SkyPlot Radar:** Sdružuje vícepásmové signály do jedné družice se dvěma barevnými režimy (**Podle konstelace** / **Podle síly C/N₀**), detailem po klepnutí a 1:1 synchronizovanou legendou:
  * **Plný bod + bílý okraj** = Ve fixu
  * **Prázdný kroužek** (šedý v režimu C/N₀) = Mimo fix
  * **Zelená záře** = Dvoupásmový příjem **L5 / E5a / B2a**
* **C/N₀ grafy, Tabulka, AGC & NMEA:** Sloupcové grafy síly signálu, kompletní tabulka, AGC monitor rušení z `GnssMeasurementsEvent`, Dopplerův posuv a živý tok `$GNGGA` / `$GNRMC`.

#### 4. Mapa (OpenStreetMap)
* **Živá trasa i přehrávání historie:** Průběžně sbírá trasu na pozadí od spuštění aplikace napříč kartami a vykresluje ji stejným enginem živě i při zpětném přehrávání.
* **Barevné vrstvy (přepínač `S1` / `S2`):**
  * **RSRP:** Zelená ($> -80\text{ dBm}$) až Červená ($< -105\text{ dBm}$)
  * **Rychlost:** Modrá ($< 10\text{ km/h}$) až Červená ($> 90\text{ km/h}$)
  * **Satelity:** Červená ($< 5$), Oranžová ($5–20$), Světle zelená ($21–30$), Smaragdová ($31–50$), Azurová ($> 50$)
  * **PDOP:** Smaragdová ($< 1,0$), Azurová ($1,0–2,0$), Oranžová ($2,0–4,0$), Červená ($\ge 4,0$)
* **Ovládání:** Tmavý/světlý režim mapy, auto-follow sledování polohy, klikatelné body s detailem, přiblížení a smazání živé stopy.

#### 5. Grafy
Interaktivní dotykové křivky s min/prům/max statistikou (živě i pro historické relace):
* **Duální RSRP & SINR** (porovnání SIM 1 vs. SIM 2)
* **Rychlost pohybu** (km/h)
* **Satelity** (současné křivky **Ve fixu** a **Viditelné**)
* **PDOP** (škála $0–4$) & **AGC rušení**
* **Ping RTT** (`8.8.8.8:53`), **Akcelerometr**, **Gyroskop**, **Kompas** a **Teplota baterie**

#### 6. Historie & Exporty
* **Správa relací:** Vyhledávání, přejmenování, automatické souhrnné statistiky (min/prům/max RSRP, průměrný PDOP, podíl 5G/4G), otevření průběhů v **Grafech** a bodový inspektor se **skokem na Mapu** i mazáním jednotlivých vzorků.
* **Uložení & Sdílení:** Přímý zápis do složky `Download/GNSS_Net_Logger/` nebo systémové sdílení ve čtyřech streamovaných formátech:
  * **CSV** (kompletní sloupce pro SIM 1 + SIM 2, `UniqueSatsInView`, `UniqueSatsUsed`, `UsedSatellites`, `VisibleSatellites`, `Sim1CA`, `Sim2CA`, `WaypointNote`, NMEA, Doppler, senzory)
  * **3D KML** (barevná trasa + špendlíky značek)
  * **GPX 1.1** (`<trkpt>`, `<wpt>`, RF a satelitní `<extensions>` včetně `used_satellites` a `visible_satellites`)
  * **GeoJSON** (vrstva pro QGIS/ArcGIS včetně seznamů satelitů a CA souhrnů)

### Nastavení a Demo režim
* **Jazyk:** Čeština, Angličtina nebo dle systému.
* **Simulační / Demo režim:** Generuje testovací jízdu kolem kampusu FEKT VUT v Brně se štítkem `DEMO` v horní liště a trvalým bannerem; při vypnutí automaticky vymaže syntetický stav.
* **Vzorkování & Kalibrace:** Profily **Pěší (`5s / 10m`)**, **Auto (`10s / 50m`)** a **Statické (`10s`)**, násobič TA (`1× 78,12 m` / `2× 156,24 m`) a referenční tlak QNH (`1013,25 hPa`).

### Oprávnění a soukromí
| Oprávnění | Účel |
| :--- | :--- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GNSS poloha, satelity a parametry mobilních buněk |
| `READ_PHONE_STATE` | Pouze rozlišení operátorů u Dual-SIM (bez přístupu k hovorům, SMS, kontaktům či IMEI) |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Nepřerušované logování na pozadí |
| `POST_NOTIFICATIONS` | Zobrazení notifikace běžícího záznamu |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Stahování mapových dlaždic OpenStreetMap a socket ping na `8.8.8.8:53` |

> Veškerá data zůstávají výhradně v lokální databázi zařízení bez jakéhokoli cloudu či analytických SDK.

### Vydání (Releases)
* [Poznámky k vydání v1.4](RELEASE_v1.4.md) · APK: [`APK/GNSS_Net_Logger_v1.4.apk.zip`](APK/GNSS_Net_Logger_v1.4.apk.zip)
* [Poznámky k vydání v1.3](RELEASE_v1.3.md) · APK: [`APK/GNSS_Net_Logger_v1.3.apk.zip`](APK/GNSS_Net_Logger_v1.3.apk.zip)
* [Poznámky k vydání v1.2](RELEASE_v1.2.md) · APK: [`APK/GNSS_Net_Logger_v1.2.apk.zip`](APK/GNSS_Net_Logger_v1.2.apk.zip)
