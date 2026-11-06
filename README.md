# GNSS & Net Logger 

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid%20(OpenStreetMap)-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Zero API Keys](https://img.shields.io/badge/Architecture-Zero%20External%20Keys-06B6D4.svg?style=flat)](#zero-external-api-keys)
[![University](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Advanced native Android application for **real-time measurement, analysis, and recording of multi-constellation GNSS satellite telemetry and cellular mobile network parameters (LTE 4G & 5G NR)**. Designed for telecommunications researchers, RF drive-test engineers, students, and radio enthusiasts.

**Author:** Ing. Jan Kufa, Ph.D.  
**Institution:** Department of Radio Electronics (UREL), Faculty of Electrical Engineering and Communication (FEKT), Brno University of Technology (VUT Brno)  
**Profile:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512) | **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`  
**Current Version:** Release 2.0 (November 2026)  
**Installable Packages:** Located in `APK/` directory (`APK/GNSS_Net_Logger_Release_2.apk` and `APK/GNSS_Net_Logger_Release_2.apk.zip`)

---

## 🌐 English Documentation

### Key Architecture: 100% Zero External API Keys & Standalone
- **OpenStreetMap (OSMDroid):** Built-in hardware Canvas vector tile renderer with local disk caching. Requires **zero Google Maps API keys or cloud billing**.
- **Room Database (SQLite):** 100% on-device relational storage with Coroutines/Flow. No cloud login or external servers required.
- **Hardware Telemetry & Socket Latency:** Native Android hardware APIs (`TelephonyManager`, `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `SensorManager`) and low-level socket ping probe to `8.8.8.8:53`.

---

### Core Functional Modules

#### 1. Overview & HUD (Záložka Měření / Overview)
- **Session Lifecycle Controls:** Start, Pause (freezes sample collection without splitting session), Resume, and Stop.
- **Selective Logging Matrix ("Položky / Fields"):** Dialog to check/uncheck exact parameter groups saved per point:
  - GNSS Satellite Data & DOP
  - Cellular RF Parameters (RSRP, SINR, TA)
  - Neighbor Cells & Handover Survey
  - Environmental Sensors (Barometer, IMU)
  - Network Latency (Socket Ping 8.8.8.8)
- **Live Instruments:** Exact Latitude & Longitude ($0.000001^\circ$), Vehicle Speed (km/h & m/s), 68% Confidence Horizontal/Vertical Accuracy, MSL/WGS84 Altitude, Bearing Azimuth ($0–360^\circ$).
- **Geometry DOP:** HDOP, VDOP, PDOP with live status tags.
- **Dual-SIM Stacked Glance:** Instant RSRP signal bargraphs for SIM 1 & SIM 2 with calibrated BTS distances.

#### 2. Cellular RF & BTS Survey (Záložka Mobily / Cellular)
- **Parallel Dual-SIM Monitor:** SIM 1 and SIM 2 monitored simultaneously stacked vertically without manual switching.
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

#### 3. GNSS Satellites & Pásma (Záložka Satelity / Satellites)
- **Constellation Tracking:** GPS, Galileo, GLONASS, BeiDou, QZSS, NavIC.
- **Dual-Band L1 + L5 Reception:** Real-time carrier frequency identification (`1575.42 MHz` L1/E1 vs. `1176.45 MHz` L5/E5a) and dual-frequency ratio badge.
- **Constellation Filters:** One-click filter chips for **All**, **L5 / E5a Only**, **GPS**, **Galileo**, **GLONASS**, and **BeiDou**.
- **Three Sub-tabs:**
  1. **Skyplot Radar:** 2D polar view (Azimuth $0–360^\circ$, Elevation $0–90^\circ$) with white glow for fixed satellites and green rings for L5/E5a carriers.
  2. **C/N0 Signals:** Real-time signal-to-noise ratio ($C/N_0$ in dB-Hz) horizontal progress bars.
  3. **Detailed Table:** Exhaustive list containing SVID, Constellation, Carrier Band, $C/N_0$, Elevation/Azimuth, and Fix status.
- **AGC Jamming & Interference Monitor:** Real-time analysis of Automatic Gain Control (AGC) from `GnssMeasurementsEvent`. Warns against sudden front-end gain collapses triggered by deliberate jamming or high RF interference.
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

#### 6. History & Point-by-Point Inspector (Záložka Historie / History)
- **Search & Filter:** Instant multi-parameter search across stored sessions.
- **Rename Session:** Pencil icon to edit session titles dynamically.
- **Point-by-Point Sample Inspector:** Open any session to browse through every sample `#1 • 19:01:21.043` with full coordinate, satellite, DOP, network, and RSRP details, including individual point deletion.
- **Multi-Format Exporters:**
  - **CSV:** Full raw matrix
  - **3D KML:** Google Earth tracks with extrusion and color coding
  - **GeoJSON:** GIS layers for QGIS and ArcGIS

---

## 🇨🇿 Česká dokumentace

Pokročilá aplikace pro terénní radiofrekvenční (RF) a GNSS měření vytvořená na Ústavu radioelektroniky (UREL) FEKT VUT v Brně.

### Hlavní přednosti verze 2.0:
- **Zcela bez externích API klíčů:** Využívá OpenStreetMap (OSMDroid) a lokální SQLite Room databázi.
- **Bilingvní rozhraní:** Kompletní dynamické přepínání mezi češtinou a angličtinou (včetně menu nastavení, tlačítek spodní lišty, nápovědy a dialogů).
- **Paralelní Dual-SIM monitor:** Zobrazení obou SIM karet pod sebou, rozpad Cell ID na eNodeB/gNodeB a sektor, 5G metriky SS vs. CSI, nosné frekvence v MHz, šířka kanálu a nastavitelný Timing Advance (1× 78m nebo 2× 156m).
- **GNSS & AGC Jamming Monitor:** Polar SkyPlot, tabulka všech satelitů, filtry pro GPS/Galileo/L5, C/N0 grafy, výpočet DOP a detektor radiofrekvenčního rušení.
- **Bodová inspekce historie:** Možnost otevřít relaci a procházet bod po bodu s časem v milisekundách, souřadnicemi a RSRP.
- **Export dat:** CSV, 3D KML (Google Earth) a GeoJSON (QGIS).

---

## 🔒 Bezpečnost & Google Play Protect

Při instalaci staženého APK souboru mimo Google Play Store (sideloading) může systém Android zobrazit hlášení **Google Play Protect**: *„Neznámý vývojář“*.

### Proč se hlášení zobrazuje:
Jedná se o standardní bezpečnostní dialog systému Android pro aplikace podepsané nezávislým vývojářským certifikátem.

### Postup instalace:
1. V okně Play Protect klepněte na **„Více podrobností“** (More details).
2. Zvolte **„Přesto nainstalovat“** (Install anyway).

### Ochrana soukromí (Žádné oprávnění k hovorům):
Aplikace **nevyžaduje** invazivní oprávnění `READ_PHONE_STATE`. Fyzická data o buňkách mobilních operátorů (eNodeB, PCI, TAC, RSRP, SINR) jsou v Androidu navázána na geolokaci (**`ACCESS_FINE_LOCATION`**), což garantuje nulový přístup k telefonním hovorům, kontaktům či IMEI telefonu.

---

## 📁 Skutečná struktura projektu (Aktuální stav)

```text
├── APK/
│   ├── GNSS_Net_Logger_Release_2.apk      # Aktuální stabilní Release 2.0 (19 MB)
│   └── GNSS_Net_Logger_Release_2.apk.zip  # Zazipovaný balíček pro bezpečné stažení (18 MB)
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt                # Hlavní aktivita, TopBar (i), spodní navigace (6 záložek)
│   │   ├── SignalLoggerApp.kt             # Aplikační třída, inicializace enginů a osmdroidu
│   │   ├── data/
│   │   │   └── db/
│   │   │       ├── AppDatabase.kt         # Room databáze (SQLite)
│   │   │       ├── LogSession.kt          # Entita měřicí relace
│   │   │       ├── TelemetryRecord.kt     # Entita naměřeného vzorku
│   │   │       └── TelemetryDao.kt        # DAO rozhraní (přejmenování, mazání bodů, dotazy)
│   │   ├── export/
│   │   │   └── Exporter.kt                # Exportéry do CSV, 3D KML a GeoJSON
│   │   ├── model/
│   │   │   └── TelemetryModels.kt         # Datové modely (GNSS, Cellular, Senzory, Nastavení)
│   │   ├── service/
│   │   │   └── TelemetryLoggingService.kt # Foreground služba (WakeLock, Pause/Resume, notifikace)
│   │   ├── telemetry/
│   │   │   ├── CellularTelemetryEngine.kt # TelephonyManager, Dual-SIM, 5G/LTE, Ping probe
│   │   │   ├── GnssTelemetryEngine.kt     # LocationManager, GnssStatus, Carrier Freq, AGC Jamming
│   │   │   ├── EnvironmentSensorEngine.kt # Akcelerometr, Gyroskop, Kompas, Barometr
│   │   │   └── DemoSimulationEngine.kt    # Simulační režim kolem areálu FEKT VUT
│   │   ├── ui/
│   │   │   ├── components/
│   │   │   │   ├── AboutAndHelpDialog.kt  # Podrobná nápověda a informace o autorovi (bilingvní)
│   │   │   │   ├── RealtimeSparklineChart.kt # Canvas grafy časových průběhů
│   │   │   │   └── SkyPlotView.kt         # Polární diagram oblohy pro satelity
│   │   │   ├── i18n/
│   │   │   │   └── Strings.kt             # Bilingvní překladové slovníky (Čeština / English)
│   │   │   ├── tabs/
│   │   │   │   ├── OverviewTab.kt         # Záložka Měření (HUD, Pause/Resume, matice položek)
│   │   │   │   ├── CellularRfTab.kt       # Záložka Mobily (Dual-SIM, 5G SS/CSI, TA, sousedé)
│   │   │   │   ├── GnssTab.kt             # Záložka Satelity (SkyPlot, C/N0, tabulka, AGC rušení)
│   │   │   │   ├── MapTab.kt              # Záložka Mapa (OSM plátno, přepínače režimů, legenda)
│   │   │   │   ├── ChartsTab.kt           # Záložka Grafy (RSRP, rychlost, satelity, ping, IMU)
│   │   │   │   ├── HistoryTab.kt          # Záložka Historie (přejmenování, bodový inspektor)
│   │   │   │   └── SettingsDialog.kt      # Nastavení parametrů (jazyk, TA krok, interval, QNH)
│   │   │   └── theme/                     # Barvy, typografie a Material 3 téma
│   │   └── util/
│   │       └── BandMapper.kt              # Přepočty EARFCN/NR-ARFCN, nosných v MHz a TA vzdálenosti
│   └── src/main/res/
│       ├── drawable/
│       │   ├── app_logo.png               # Logo aplikace
│       │   └── ic_vut_logo.xml            # Vektorový emblém VUT
│       └── values/strings.xml
├── APK/                                   # Složka se sestavenými APK soubory
├── build.gradle.kts
├── metadata.json
└── README.md
```

---

## 👨‍🔬 Autor a akademické pracoviště

**Ing. Jan Kufa, Ph.D.**  
Ústav radioelektroniky (UREL)  
Fakulta elektrotechniky a komunikačních technologií (FEKT)  
Vysoké učení technické v Brně (VUT)  
Profil: [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512)  
E-mail: `kufa@vut.cz` / `kufa@vutbr.cz`
