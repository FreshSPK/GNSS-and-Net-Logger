# 📡 GNSS & Net Logger

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid%20(OpenStreetMap)-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Zero API Keys](https://img.shields.io/badge/Architecture-Zero%20External%20Keys-06B6D4.svg?style=flat)](#zero-external-api-keys)
[![Release](https://img.shields.io/badge/Release-v1.1%20Stable-10B981.svg?style=flat)](https://github.com/)
[![University](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Advanced native Android application for **real-time measurement, scientific analysis, and recording of multi-constellation GNSS satellite telemetry and cellular mobile network parameters (LTE 4G & 5G NR)**. Designed for telecommunications researchers, RF drive-test engineers, students, and radio enthusiasts.

**Author:** Ing. Jan Kufa, Ph.D.  
**Institution:** Department of Radio Electronics (UREL), Faculty of Electrical Engineering and Communication (FEKT), Brno University of Technology (VUT Brno)  
**Profile:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512) | **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`  
**Current Version:** Release v1.1 (Build Code 2, November 2026)  

---

### 📦 Quick Downloads (Rychlé stažení instalačních balíčků)

| Soubor / File | Popis / Description | Velikost / Size | Odkaz v repozitáři / Repo Link |
| :--- | :--- | :--- | :--- |
| **`GNSS_Net_Logger_Release_v1.1.apk`** | Přímý instalační balíček pro Android (Sideload APK) | ~19 MB | [Stáhnout / Download](APK/GNSS_Net_Logger_Release_v1.1.apk) |
| **`GNSS_Net_Logger_Release_v1.1.apk.zip`** | Zazipovaný archiv pro bezpečné stažení přes prohlížeč | ~18 MB | [Stáhnout / Download](APK/GNSS_Net_Logger_Release_v1.1.apk.zip) |

---

## 🌐 English Documentation

### Key Architecture: 100% Zero External API Keys & Standalone
- **OpenStreetMap (OSMDroid):** Built-in hardware Canvas vector tile renderer with local disk caching. Requires **zero Google Maps API keys or cloud billing**.
- **Room Database (SQLite):** 100% on-device relational storage with Coroutines/Flow. No cloud login or external servers required.
- **Hardware Telemetry & Socket Latency:** Native Android hardware APIs (`TelephonyManager`, `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `SensorManager`, `BatteryManager`) and low-level socket ping probe to `8.8.8.8:53`.

---

### Core Functional Modules & What's New in Release v1.1

#### 1. Overview & HUD (Záložka Měření / Overview)
- **Session Lifecycle Controls:** Start, Pause (freezes sample collection without splitting session), Resume, and Stop.
- **Selective Logging Matrix ("Položky / Fields"):** Dialog to check/uncheck exact parameter groups saved per point:
  - GNSS Satellite Data & DOP
  - Cellular RF Parameters (RSRP, SINR, TA)
  - Neighbor Cells & Handover Survey
  - Environmental Sensors (Barometer, IMU)
  - Device Battery Thermal & Voltage (v1.1)
  - Raw NMEA Sentence Stream (v1.1)
  - Network Latency (Socket Ping 8.8.8.8)
- **Live Instruments:** Exact Latitude & Longitude ($0.000001^\circ$), Vehicle Speed (km/h & m/s), 68% Confidence Horizontal/Vertical Accuracy, MSL/WGS84 Altitude, Bearing Azimuth ($0–360^\circ$).
- **Geometry DOP:** HDOP, VDOP, PDOP with live status tags.
- **Dual-SIM Stacked Glance:** Instant RSRP signal bargraphs for SIM 1 & SIM 2 with calibrated BTS distances and PLMN network badges.
- **Hardware & Battery Telemetry:** Battery temperature (°C), battery charge level (%), battery voltage (mV), barometric station and calculated Sea-Level Pressure (MSL/QFF).

#### 2. Cellular RF & BTS Survey (Záložka Mobily / Cellular)
- **Parallel Dual-SIM Monitor:** SIM 1 and SIM 2 monitored simultaneously stacked vertically without manual switching.
- **PLMN Identification (v1.1):** Mobile Country Code (MCC) + Mobile Network Code (MNC) detection (e.g. `PLMN 230-01` T-Mobile, `PLMN 230-02` O2, `PLMN 230-03` Vodafone).
- **Signal Quality Bargraphs:** Color-coded RSRP bars with signal condition evaluation (Good $\ge -85\text{ dBm}$, Fair, Poor).
- **5G NR Split Metrology (SS vs. CSI):**
  - Synchronization Signal Block: `SS-RSRP`, `SS-RSRQ`, `SS-SINR`
  - Datapath State Information: `CSI-RSRP`, `CSI-RSRQ`, `CSI-SINR`
- **Cell Identity & Decoded IDs:**
  - LTE: $\text{eNodeB ID} = \lfloor \text{CI} / 256 \rfloor, \quad \text{Sector ID} = \text{CI} \pmod{256}$
  - 5G NR: $\text{gNodeB ID} = \lfloor \text{NCI} / 256 \rfloor, \quad \text{Sector ID} = \text{NCI} \pmod{256}$
  - TAC (Tracking Area Code), PCI (Physical Cell ID).
- **Carrier Frequencies & Channel Bandwidth:** Downlink (DL) and Uplink (UL) carrier frequencies in MHz calculated from EARFCN/NR-ARFCN, alongside channel bandwidth (e.g., 10 MHz, 20 MHz, 40 MHz).
- **Calibrated Timing Advance (TA) Distance:** Configurable step multiplier in settings: **1× (78.12 m)** or **2× (156.24 m / step)** matching device calibration.
- **Neighbor Cells & Handover Analysis:** Live table of all visible neighbor cells with $\Delta\text{RSRP} = \text{RSRP}_{\text{serving}} - \text{RSRP}_{\text{neighbor}}$. Automatically highlights *✔ Handover Candidates* when $\Delta \le 3\text{ dB}$.
- **Cellular Security & Anomaly / Spoofing Detector (v1.1):** Real-time heuristic detection of rogue BTS / IMSI Catchers, forced 2G downgrade attacks (Stingray attack signature without mutual authentication), isolated cells suppressing SIB neighbor broadcasts, TAC inconsistencies, and RF co-channel jamming (high RSRP > -90 dBm with negative SINR < -3 dB). Includes an interactive modal breakdown explaining detection criteria.

#### 3. GNSS Satellites & Bands (Záložka Satelity / Satellites)
- **Constellation Tracking:** GPS, Galileo, GLONASS, BeiDou, QZSS, NavIC.
- **Dual-Band L1 + L5 Reception:** Real-time carrier frequency identification (`1575.42 MHz` L1/E1 vs. `1176.45 MHz` L5/E5a) and dual-frequency ratio badge.
- **Constellation Filters:** One-click filter chips for **All**, **L5 / E5a Only**, **GPS**, **Galileo**, **GLONASS**, and **BeiDou**.
- **Three Sub-tabs:**
  1. **Skyplot Radar:** 2D polar view (Azimuth $0–360^\circ$, Elevation $0–90^\circ$) with white glow for fixed satellites and green rings for L5/E5a carriers.
  2. **C/N0 Signals:** Real-time signal-to-noise ratio ($C/N_0$ in dB-Hz) horizontal progress bars.
  3. **Detailed Table:** Exhaustive list containing SVID, Constellation, Carrier Band, $C/N_0$, Elevation/Azimuth, and Fix status.
- **AGC Jamming & Interference Monitor:** Real-time analysis of Automatic Gain Control (AGC) from `GnssMeasurementsEvent`. Warns against sudden front-end gain collapses triggered by deliberate jamming or high RF interference.
- **Raw GNSS Measurements & NMEA Stream (v1.1):** Live display of raw Doppler shift (Hz), Carrier Phase Accumulated Delta Range (ADR) valid fix count, and latest NMEA sentence stream (`$GNGGA`, `$GNRMC`).
- **DOP Dialog (i):** In-depth technical breakdown of HDOP, VDOP, and PDOP precision boundaries.

#### 4. Map & Path Overlays (Záložka Mapa / Map)
- **Visual Path Modes Switcher:**
  - **RSRP Signal:** Green ($> -80\text{ dBm}$), Light Green, Amber, Red ($< -105\text{ dBm}$)
  - **Speed:** Blue ($< 10\text{ km/h}$), Cyan, Green, Red ($> 90\text{ km/h}$)
  - **Satellites:** Red ($< 5$), Yellow ($5–10$), Green ($11–25$), Blue ($> 25$)
  - **PDOP:** Green ($< 1.5$), Cyan, Amber, Red ($> 5.0$)
- **Floating Legend & Author Badge:** Dynamic legend in top-left and `Jan Kufa • VUT` badge in top-right.
- **Controls:** Floating zoom (+/-) controls and bright cyan recentering FAB.

#### 5. Real-Time Charts (Záložka Grafy / Charts)
- Interactively renders Canvas sparklines with Min/Avg/Max indicators:
  - Serving Cell RSRP (dBm)
  - Movement Speed (km/h)
  - Satellites in Fix Count
  - Socket Ping RTT Latency to 8.8.8.8 (ms)
  - Accelerometer G-Force / Jolt ($m/s^2$)
  - Gyroscope Yaw Angular Velocity ($rad/s$)
  - Magnetic Compass Azimuth ($0–360^\circ$)
  - Battery Temperature (°C) — thermal throttle monitor (v1.1)

#### 6. History & Point-by-Point Inspector (Záložka Historie / History)
- **Search & Filter:** Instant multi-parameter search across stored sessions.
- **Rename Session:** Pencil icon to edit session titles dynamically.
- **Point-by-Point Sample Inspector:** Open any session to browse through every sample `#1 • 19:01:21.043` with full coordinate, satellite, DOP, network, Doppler, battery temperature, and RSRP details, including individual point deletion.
- **Multi-Format Exporters:**
  - **CSV:** Full raw matrix including PLMN, NMEA sentences, battery metrics, and raw Doppler
  - **3D KML:** Google Earth tracks with extrusion and color coding
  - **GeoJSON:** GIS layers for QGIS and ArcGIS

---

## 🇨🇿 Česká dokumentace

Pokročilá nativní aplikace pro terénní radiofrekvenční (RF) a GNSS měření vyvinutá na Ústavu radioelektroniky (UREL) FEKT VUT v Brně.

### Hlavní přednosti a novinky verze 1.1 (Release v1.1):
- **Zcela bez externích API klíčů:** Využívá OpenStreetMap (OSMDroid) a lokální SQLite Room databázi.
- **Bilingvní rozhraní:** Kompletní dynamické přepínání mezi češtinou a angličtinou (včetně menu nastavení, tlačítek spodní lišty, nápovědy a dialogů).
- **Paralelní Dual-SIM monitor & PLMN:** Zobrazení obou SIM karet pod sebou, detekce PLMN (MCC + MNC, např. 230-01), rozpad Cell ID na eNodeB/gNodeB a sektor, 5G metriky SS vs. CSI, nosné frekvence v MHz, šířka kanálu a nastavitelný Timing Advance (1× 78m nebo 2× 156m).
- **Detekce spoofingu a anomálií mobilní sítě (v1.1):** Heuristický detektor podvržených základnových stanic (Rogue BTS / IMSI Catcher), 2G downgrade útoků (vynucení GSM bez vzájemné autentizace), izolovaných buněk potlačujících SIB sousedy, neplatných TAC/eNodeB a RF zarušení (vysoký RSRP > -90 dBm při záporném SINR < -3 dB). Včetně podrobného technického vysvětlení v modálním okně.
- **Surová měření GNSS & NMEA proud:** Čtení surových NMEA vět (`$GNGGA`, `$GNRMC`), výpočet Dopplerova posuvu satelitů (Hz), fázový stav ADR (Accumulated Delta Range).
- **Telemetrie baterie & termální monitor:** Záznam teploty baterie (°C), napětí (mV) a úrovně nabití pro sledování termálního škrcení (throttling) během 5G drive testů.
- **Barometrický přepočet tlaku na hladinu moře (MSL):** Výpočet redukovaného tlaku QFF/MSL ze staničního tlaku a GNSS výšky.
- **GNSS & AGC Jamming Monitor:** Polar SkyPlot, tabulka všech satelitů, filtry pro GPS/Galileo/L5, C/N0 grafy, výpočet DOP a detektor radiofrekvenčního rušení.
- **Bodová inspekce historie:** Možnost otevřít relaci a procházet bod po bodu s časem v milisekundách, souřadnicemi, Dopplerem, baterií a RSRP.
- **Export dat:** Rozšířený CSV (včetně NMEA a Doppleru), 3D KML (Google Earth) a GeoJSON (QGIS).

---

## 🔒 Bezpečnost & Google Play Protect

Při instalaci staženého APK souboru mimo Google Play Store (sideloading) může systém Android zobrazit hlášení **Google Play Protect**: *„Neznámý vývojář“*.

### Proč se hlášení zobrazuje:
Jedná se o standardní bezpečnostní dialog systému Android pro aplikace podepsané nezávislým vývojářským certifikátem.

### Postup instalace:
1. V okně Play Protect klepněte na **„Více podrobností“** (More details).
2. Zvolte **„Přesto nainstalovat“** (Install anyway).

### Ochrana soukromí (Žádné oprávnění k hovorům):
Aplikace **nevyžaduje** invazivní oprávnění `READ_PHONE_STATE`. Fyzická data o buňkách mobilních operátorů (eNodeB, PCI, TAC, RSRP, SINR, PLMN) jsou v Androidu navázána na geolokaci (**`ACCESS_FINE_LOCATION`**), což garantuje nulový přístup k telefonním hovorům, kontaktům či IMEI telefonu.

---

## 📁 Skutečná struktura projektu (Aktuální stav - Release v1.1)

```text
├── APK/
│   ├── GNSS_Net_Logger_Release_v1.1.apk      # Aktuální stabilní Release v1.1 (19 MB)
│   └── GNSS_Net_Logger_Release_v1.1.apk.zip  # Zazipovaný balíček pro bezpečné stažení (18 MB)
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt                   # Hlavní aktivita, TopBar (i), spodní navigace (6 záložek)
│   │   ├── SignalLoggerApp.kt                # Aplikační třída, inicializace enginů a osmdroidu
│   │   ├── data/
│   │   │   └── db/
│   │   │       ├── AppDatabase.kt            # Room databáze (SQLite, v2)
│   │   │       ├── LogSession.kt             # Entita měřicí relace
│   │   │       ├── TelemetryRecord.kt        # Entita naměřeného vzorku (v1.1 rozšířená)
│   │   │       └── TelemetryDao.kt           # DAO rozhraní (přejmenování, mazání bodů, dotazy)
│   │   ├── export/
│   │   │   └── Exporter.kt                   # Exportéry do CSV (včetně NMEA a Doppleru), 3D KML a GeoJSON
│   │   ├── model/
│   │   │   └── TelemetryModels.kt            # Datové modely (GNSS, Raw, Cellular, Baterie, Senzory)
│   │   ├── service/
│   │   │   └── TelemetryLoggingService.kt    # Foreground služba (WakeLock, Pause/Resume, notifikace)
│   │   ├── telemetry/
│   │   │   ├── CellularTelemetryEngine.kt    # TelephonyManager, Dual-SIM, PLMN, 5G/LTE, Ping probe
│   │   │   ├── GnssTelemetryEngine.kt        # LocationManager, NMEA listener, Raw Doppler/ADR, AGC Jamming
│   │   │   ├── EnvironmentSensorEngine.kt    # Akcelerometr, Gyroskop, Kompas, Barometr MSL, Baterie
│   │   │   └── DemoSimulationEngine.kt       # Simulační režim kolem areálu FEKT VUT
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   ├── AboutAndHelpDialog.kt     # Podrobná nápověda, novinky v1.1 a informace o autorovi (bilingvní)
│   │   │   │   ├── RealtimeSparklineChart.kt # Canvas grafy časových průběhů (včetně teploty baterie)
│   │   │   │   └── SkyPlotView.kt            # Polární diagram oblohy pro satelity
│   │   │   ├── i18n/
│   │   │   │   └── Strings.kt                # Bilingvní překladové slovníky (Čeština / English)
│   │   │   ├── tabs/
│   │   │   │   ├── OverviewTab.kt            # Záložka Měření (HUD, hardware & baterie, matice položek)
│   │   │   │   ├── CellularRfTab.kt          # Záložka Mobily (Dual-SIM, PLMN, 5G SS/CSI, TA, sousedé)
│   │   │   │   ├── GnssTab.kt                # Záložka Satelity (SkyPlot, C/N0, tabulka, AGC rušení, Raw/NMEA)
│   │   │   │   ├── MapTab.kt                 # Záložka Mapa (OSM plátno, přepínače režimů, legenda)
│   │   │   │   ├── ChartsTab.kt              # Záložka Grafy (RSRP, rychlost, satelity, ping, IMU, teplota)
│   │   │   │   ├── HistoryTab.kt             # Záložka Historie (přejmenování, bodový inspektor)
│   │   │   │   └── SettingsDialog.kt         # Nastavení parametrů (jazyk, TA krok, interval, QNH)
│   │   │   └── theme/                        # Barvy, typografie a Material 3 téma
│   │   └── util/
│   │       └── BandMapper.kt                 # Přepočty EARFCN/NR-ARFCN, nosných v MHz a TA vzdálenosti
│   └── src/main/res/
│       ├── drawable/
│       │   ├── app_logo.png                  # Logo aplikace
│       │   └── ic_vut_logo.xml               # Vektorový emblém VUT
│       └── values/strings.xml
├── APK/                                      # Složka se sestavenými APK soubory
├── build.gradle.kts
├── metadata.json
└── README.md
```

---

## 🚀 Jak vytvořit nový Release v1.1 na GitHubu (Návod krok za krokem)

Když máte repozitář na GitHubu, vytvoření oficiálního releasu s možností stažení APK pro studenty a kolegy je velmi jednoduché:

### Varianta A: Přes webové rozhraní GitHub (Nejjednodušší)
1. **Otevřete svůj repozitář na GitHubu** ve webovém prohlížeči (např. `https://github.com/vut-kufa/gnss-net-logger`).
2. V pravém postranním panelu klikněte na odkaz **Releases** (případně nahoře na záložku nebo přejděte na adresu `.../releases`).
3. Klikněte na tlačítko **„Draft a new release“** (nebo *„Create a new release“*).
4. **Zvolte tag (Choose a tag):**
   * Do pole vepište `v1.1` a zvolte *„Create new tag: v1.1 on publish“*.
5. **Název vydání (Release title):**
   * Vepište např.: `GNSS & Net Logger Release v1.1`
6. **Přiložte sestavené APK soubory (Attach binaries):**
   * V sekci *„Attach binaries by dropping them here or selecting them“* přetáhněte myší z vaší složky `APK/` tyto dva soubory:
     * `GNSS_Net_Logger_Release_v1.1.apk`
     * `GNSS_Net_Logger_Release_v1.1.apk.zip`
7. **Popis vydání (Release description):**
   * Do textového pole vložte připravené poznámky k vydání (viz rámeček níže).
8. Klikněte na zelené tlačítko **„Publish release“**.
   * *Výsledkem je oficiální stránka s přímým stažením APK pro uživatele!*

---

### Varianta B: Pomocí GitHub CLI nebo příkazové řádky
Pokud používáte nástroj `gh` (GitHub CLI):
```bash
# Vytvoření tagu a nahrání obou instalačních balíčků do nového releasu:
gh release create v1.1 \
  APK/GNSS_Net_Logger_Release_v1.1.apk \
  APK/GNSS_Net_Logger_Release_v1.1.apk.zip \
  --title "GNSS & Net Logger Release v1.1" \
  --notes "Release v1.1: Detekce spoofingu/IMSI Catcheru, oprava zobrazení SIM karet, baterie a raw NMEA stream."
```

Nebo standardním gitem:
```bash
git add .
git commit -m "Release v1.1: Cellular spoofing detector, layout fixes, APK build"
git tag -a v1.1 -m "Release v1.1"
git push origin main --tags
```

---

### 📋 Text pro Release Notes na GitHubu (K zkopírování)

> **GNSS & Net Logger – Release v1.1**
> 
> Stabilní aktualizace přinášející pokročilou bezpečnostní analýzu mobilních sítí, nová měření a optimalizaci uživatelského rozhraní:
> 
> - 🛡️ **Detektor spoofingu a anomálií mobilní sítě:** Automatická heuristická detekce 2G downgrade útoků (zneužití GSM bez vzájemné autentizace – typický znak IMSI Catcherů), izolovaných buněk potlačujících SIB sousedy a vysokého soukanálového RF zarušení ($\text{SINR} < -3\text{ dB}$).
> - 📱 **Opraveno zobrazení SIM karet:** Přeuspořádání hlavičky na záložce Mobily a Měření, eliminace nežádoucího vertikálního zalamování stavových štítků na úzkých displejích.
> - 🛰️ **GNSS Raw měření a NMEA stream:** Zobrazení Dopplerova posuvu satelitů (Hz), čítač Accumulated Delta Range (ADR) a živý proud vět `$GNGGA`/`$GNRMC`.
> - 🔋 **Telemetrie baterie:** Záznam teploty (°C), napětí (mV) a stavu nabití pro monitorování termálního škrcení (thermal throttling) během dlouhých drive-testů.
> - 🧭 **Opravy stability:** Optimalizace formátování čísel nezávisle na systémovém jazyku, vylepšený export CSV a bodový inspektor.
> - ⚙️ **100% Standalone:** Žádné externí API klíče, vestavěná offline mapa OSMDroid a lokální SQLite databáze.

---

## 👨‍🔬 Autor a akademické pracoviště

**Ing. Jan Kufa, Ph.D.**  
Ústav radioelektroniky (UREL)  
Fakulta elektrotechniky a komunikačních technologií (FEKT)  
Vysoké učení technické v Brně (VUT)  
Profil: [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512)  
E-mail: `kufa@vut.cz` / `kufa@vutbr.cz`

