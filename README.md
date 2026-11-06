# 📡 GNSS & Net Logger

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![OpenStreetMap](https://img.shields.io/badge/Map-OSMDroid%20(OpenStreetMap)-7EBC6F.svg?style=flat&logo=openstreetmap)](https://github.com/osmdroid/osmdroid)
[![Zero API Keys](https://img.shields.io/badge/Architecture-Zero%20External%20Keys-06B6D4.svg?style=flat)](#zero-external-api-keys-architecture-100-standalone)
[![VUT FEKT](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Advanced native Android application for **scientific field measurements, radiofrequency drive-test analysis, and long-term recording of multi-constellation GNSS satellite telemetry and cellular mobile network parameters (LTE 4G & 5G NR)**. Designed for telecommunications researchers, RF drive-test engineers, university students, and radio enthusiasts.

**Author:** Ing. Jan Kufa, Ph.D.  
**Department:** Department of Radio Electronics (UREL), Faculty of Electrical Engineering and Communication (FEKT), Brno University of Technology (VUT Brno)  
**Academic Profile:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512) | **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`  

---

## 🌐 English Documentation

### Zero External API Keys Architecture (100% Standalone)

The application functions completely autonomously without dependence on paid cloud services, proprietary SDKs, or developer tokens:
- **Map Overlays (OSMDroid / OpenStreetMap):** Hardware-accelerated Canvas vector map renderer with local disk tile caching. **Requires zero Google Maps API keys or cloud billing.**
- **Local Relational Storage (Room / SQLite):** Complete on-device database persistence using Kotlin Coroutines and asynchronous StateFlows. Requires zero Firebase configuration or cloud user logins.
- **Direct Hardware Telemetry & Network Ping:** Native Android hardware frameworks (`TelephonyManager`, `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `SensorManager`, `BatteryManager`) and low-level socket ping probe to `8.8.8.8:53`.

---

### User Guide: How to Use the Application

#### 1. Starting and Controlling a Measurement Session
* **Start Recording:** On the initial **Overview** (Měření) tab, tap the prominent **„START MEASUREMENT RECORDING“** button. The application spawns a foreground service with a persistent system notification and a CPU `WakeLock`, ensuring continuous background logging even when the screen is locked.
* **Pause Recording (Pause):** During active logging, tap **Pause**. Telemetry collection is temporarily suspended (e.g., while idling in traffic or taking a break) without splitting the session into multiple disconnected records.
* **Resume & Stop:** Tap **Resume** to continue logging, or tap **Stop** to conclude the session and commit all points to the local database.

#### 2. Selecting Logging Fields (Matrix Button „Fields / Položky“)
Tap the **Fields** button in the logging banner to open the selective logging matrix. You can independently toggle which telemetry sets are captured with each recorded sample:
- GNSS Satellite Data & DOP Geometry
- Cellular RF Parameters (RSRP, SINR, Timing Advance)
- Neighbor Cells & Handover RF Survey
- Environmental Sensors (Barometer, IMU Accelerometer, Gyroscope, Compass)
- Device Battery Temperature and Electrical Voltage
- Raw NMEA Sentence Stream (`$GNGGA`, `$GNRMC`)
- Network Socket Ping Latency to 8.8.8.8

#### 3. Measurement Parameters & Settings (Sliders Icon in Top Bar)
* **Application Language:** Dynamic toggle between **Czech**, **English**, and Automatic System Locale (translates all tabs, HUD indicators, charts, legends, and help dialogs).
* **Simulation / Demo Mode:** Generates synthetic vehicle drive-test motion around the FEKT VUT campus in Brno with realistic signal fluctuations, dual-SIM cells, and satellite constellations for testing without going outside.
* **Timing Advance (TA) Distance Step:** Distance calculation multiplier:
  * `1× (78.12 m / step)` – 3GPP theoretical value.
  * `2× (156.24 m / step)` – empirical calibration suited for most modern Android handsets.
* **Sampling Rate & Criteria:** Configurable interval (1 s, 2 s, 5 s, 10 s, 30 s) based on time, distance, or hybrid triggers.
* **QNH Reference Sea-Level Pressure:** Configurable base pressure (default 1013.25 hPa) for accurate barometric altitude calculations.

#### 4. Help & Information Dialog (Top-Right „i“ Button)
Tap the blue **(i)** icon in the top-right corner to open the technical reference guide explaining radio metrology metrics (RSRP, RSRQ, SINR, CQI, DOP, AGC), spoofing detection mechanics, and author accreditation.

---

### Comprehensive Feature & Tab Overview

#### 1. „Overview“ Tab (Live Telemetry HUD)
* **GNSS Kinematics:** Latitude and Longitude to 6 decimal places ($0.000001^\circ$), vehicle speed (km/h & m/s), bearing azimuth ($0–360^\circ$), and 68% confidence horizontal/vertical error bounds.
* **DOP Dilution of Precision:** Live HDOP (horizontal), VDOP (vertical), and PDOP (3D spatial geometry) with explanatory popup dialog.
* **GNSS Satellites & Bands:** Total used vs. visible satellites, dual-frequency reception indicator (L1 single-band vs. L1+L5 dual-band), and per-constellation breakdowns.
* **Cellular Glance (SIM 1 & SIM 2):** Real-time stacked signal strength bargraphs with color indicators, operator names, and calculated BTS distances.
* **Hardware & Battery Telemetry:** Battery temperature (°C), charge level, battery voltage (mV), station pressure, and mean sea-level pressure (MSL / QFF).

#### 2. „Cellular RF“ Tab (BTS & Serving Network Metrology)
* **Parallel Dual-SIM Stacked Monitor:** SIM 1 and SIM 2 monitored simultaneously stacked vertically without manual switching.
* **Operator & PLMN Identification:** Mobile Country Code (MCC) + Mobile Network Code (MNC) detection (e.g., `PLMN 230-01` T-Mobile, `PLMN 230-02` O2, `PLMN 230-03` Vodafone).
* **Cell ID Decomposition:**
  * 4G LTE: $\text{eNodeB ID} = \lfloor \text{CI} / 256 \rfloor, \quad \text{Sector ID} = \text{CI} \pmod{256}$
  * 5G NR: $\text{gNodeB ID} = \lfloor \text{NCI} / 256 \rfloor, \quad \text{Sector ID} = \text{NCI} \pmod{256}$
  * Tracking Area Code (TAC) and Physical Cell ID (PCI).
* **5G NR Dual Metrology (SS vs. CSI):**
  * Synchronization Signal Block: `SS-RSRP`, `SS-RSRQ`, `SS-SINR`
  * Channel State Information: `CSI-RSRP`, `CSI-RSRQ`, `CSI-SINR`
* **Carrier Frequencies & Channel Bandwidth:** Downlink (DL) and Uplink (UL) carrier frequencies in MHz calculated from EARFCN/NR-ARFCN, alongside channel bandwidth (10, 15, 20, 40 MHz).
* **Neighbor Cells & Handover Analysis:** Table of all detected neighbor cells with delta signal computation ($\Delta\text{RSRP} = \text{RSRP}_{\text{serving}} - \text{RSRP}_{\text{neighbor}}$), highlighting handover candidates when $\Delta \le 3\text{ dB}$.
* **Cellular Security & Anomaly / Spoofing Detector:**
  * **2G Downgrade Attack (CRITICAL):** Alerts when phone is forced onto 2G GSM without mutual authentication (IMSI Catcher / Stingray signature).
  * **Isolated Cell / Fake BTS (WARNING):** Detects strong cells with zero SIB neighbor relations.
  * **Co-Channel Interference / Jamming (WARNING):** Detects high RSRP ($> -90\text{ dBm}$) with negative SINR ($< -3\text{ dB}$).

#### 3. „Satellites“ Tab (GNSS Constellations & Signal Quality)
* **Multi-Constellation Support:** GPS (USA), Galileo (EU), GLONASS (Russia), BeiDou (China), QZSS (Japan), and NavIC (India).
* **Dual-Band Carrier Tracking (L1/E1 + L5/E5a):** Frequency separation (1575.42 MHz vs. 1176.45 MHz) and real-time dual-frequency ratio badge.
* **Constellation Filter Chips:** Instant filtering for *All*, *L5 / E5a Only*, *GPS*, *Galileo*, *GLONASS*, and *BeiDou*.
* **Three Diagnostic Views:**
  1. **Skyplot Radar:** 2D polar projection (Azimuth $0–360^\circ$, Elevation $0–90^\circ$). Green ring indicates L5/E5a carriers; white glow indicates satellites used in active fix.
  2. **C/N0 Signal Bars:** Horizontal bar charts of Carrier-to-Noise density ratio ($C/N_0$ in dB-Hz).
  3. **Detailed Table:** Full list with SVID/PRN, constellation, band, elevation, azimuth, and fix state.
* **AGC Jamming & Interference Monitor:** Evaluates Automatic Gain Control from `GnssMeasurementsEvent` to alert against intentional jamming or receiver front-end saturation.
* **Raw GNSS Measurements & NMEA Stream:** Raw Doppler shift (Hz), Accumulated Delta Range (ADR) phase measurements, and live sentence stream (`$GNGGA`, `$GNRMC`).

#### 4. „Map“ Tab (Spatial Drive-Test Trajectory)
* **Interactive OpenStreetMap Plaid:** Continuous hardware Canvas rendering of logged breadcrumb coordinates.
* **Dynamic Layer Coloring:**
  * **RSRP Signal:** Green ($> -80\text{ dBm}$), Light Green, Amber, Red ($< -105\text{ dBm}$).
  * **Speed:** Blue ($< 10\text{ km/h}$), Cyan, Green, Red ($> 90\text{ km/h}$).
  * **Satellites:** Red ($< 5$), Yellow ($5–10$), Green ($11–25$), Blue ($> 25$).
  * **PDOP:** Green ($< 1.5$), Cyan, Amber, Red ($> 5.0$).
* **Floating Legend & Controls:** Responsive legend adapting to active mode, zoom controls (+/-), and cyan recentering FAB.

#### 5. „Charts“ Tab (Real-Time Sparklines)
Hardware Canvas graphs with live Min, Average, and Max statistics:
- Serving Cell RSRP (dBm)
- Travel Speed (km/h)
- Satellite Fix Count
- Network Socket Ping RTT Latency to 8.8.8.8 (ms)
- IMU Accelerometer G-Force ($m/s^2$)
- Gyroscope Yaw Angular Velocity ($rad/s$)
- Magnetic Compass Heading ($0–360^\circ$)
- Battery Thermal Temperature (°C)

#### 6. „History“ Tab (Session Inspection & Multi-Format Exporters)
* **Search & Filter:** Instant multi-parameter search across stored sessions.
* **Session Renaming:** Pencil button to assign custom descriptive titles.
* **Point-by-Point Sample Inspector:** Inspect every recorded sample `#1 • 19:01:21.043` with coordinates, satellites, DOP, cellular parameters, Doppler, and battery telemetry. Includes single-point deletion.
* **Exporters:**
  * **CSV:** Full raw spreadsheet matrix (including PLMN, Doppler, NMEA strings, and battery metrics).
  * **3D KML:** Extruded 3D trajectory for Google Earth.
  * **GeoJSON:** Standards-compliant geospatial layer for QGIS and ArcGIS.

---

### Security, System Permissions & Privacy

The application adheres strictly to the principle of least privilege:
- **`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`:** Required by the Android OS to access GNSS satellites and raw cellular base station metrics.
- **`ACCESS_BACKGROUND_LOCATION`:** Allows drive-test logging to continue when the screen turns off.
- **`BODY_SENSORS` / Hardware Sensors:** Access to barometer, accelerometer, and gyroscope.
- **`FOREGROUND_SERVICE_LOCATION`:** Ensures the background measurement engine is not prematurely killed by Android power management.
- **`INTERNET` & `ACCESS_NETWORK_STATE`:** Required strictly for loading OpenStreetMap tiles and dispatching socket ping probes to 8.8.8.8.

> **🛡️ Privacy Assurance (Zero Phone Call / Identity Permissions):**  
> The application **never requests or requires** `READ_PHONE_STATE`. All physical cell identifiers (eNodeB, PCI, TAC, RSRP, SINR, PLMN) are delivered through location-bound telephony APIs. The application has zero access to your phone calls, contacts, SMS, phone number, or IMEI.

---

### Author & Academic Attribution

* **Author:** Ing. Jan Kufa, Ph.D.
* **Department:** Department of Radio Electronics (UREL)
* **Faculty:** Faculty of Electrical Engineering and Communication (FEKT)
* **University:** Brno University of Technology (VUT Brno), Czech Republic
* **Academic Profile:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512)
* **Contact:** `kufa@vut.cz` / `kufa@vutbr.cz`

---
---

## 🇨🇿 Česká dokumentace

### Architektura: Zcela bez externích API klíčů (100% Standalone)

Aplikace funguje naprosto autonomně bez závislosti na placených cloudových službách nebo vývojářských tokenech:
- **Mapové podklady (OSMDroid / OpenStreetMap):** Hardwarově akcelerované vektorové plátno s lokálním diskovým mezipaměťováním dlaždic. **Nevyžaduje žádný Google Maps API klíč.**
- **Místní databáze (Room / SQLite):** Kompletní relační ukládání měřených dat přímo na zařízení s podporou asynchronních Kotlin Coroutines/Flow. Nevyžaduje cloudové přihlašování ani Firebase.
- **Přímá telemetrie hardwaru a síťový Ping:** Nativní systémová rozhraní Androidu (`TelephonyManager`, `LocationManager`, `GnssStatus`, `GnssMeasurementsEvent`, `SensorManager`, `BatteryManager`) a nízkoúrovňový socket ping na `8.8.8.8:53`.

---

### Návod k použití aplikace (Uživatelská příručka)

#### 1. Spuštění a řízení měřicí relace
* **Zahájení záznamu:** Na úvodní záložce **Měření** stiskněte velké tlačítko **„ZAHÁJIT ZÁZNAM MĚŘENÍ“**. Aplikace spustí foreground službu s trvalou notifikací a zámkem WakeLock, takže měření běží spolehlivě i při vypnuté obrazovce telefonu.
* **Pozastavení měření (Pause):** Během měření můžete klepnout na tlačítko **Pozastavit**. Záznam se dočasně uspí (např. při čekání na semaforu nebo zastávce), aniž by se relace rozdělila na dvě samostatná měření.
* **Obnovení (Resume) a Ukončení (Stop):** Tlačítkem **Pokračovat** měření opět aktivujete, tlačítkem **Ukončit** relaci uzavřete a automaticky uložíte do databáze.

#### 2. Výběr logovaných položek (Tlačítko „Položky“)
V horním panelu řízení měření klepněte na tlačítko **Položky**. Otevře se matice, kde lze zaškrtnout přesně ty datové skupiny, které chcete při každém vzorku ukládat:
- GNSS satelitní data & geometrie DOP
- Mobilní RF parametry (RSRP, SINR, Timing Advance)
- Sousední buňky & RF průzkum okolí
- Environmentální senzory (Barometr, IMU akcelerometr, gyroskop, kompas)
- Teplota a elektrické napětí baterie
- Surový proud NMEA vět (`$GNGGA`, `$GNRMC`)
- Síťová odezva (Socket Ping na 8.8.8.8)

#### 3. Nastavení parametrů (Ikona posuvníků nahoře)
* **Jazyk aplikace:** Přepínání mezi **Češtinou**, **Angličtinou** a automatickým systémovým jazykem (přepíná celé rozhraní, grafy, legendu i nápovědu).
* **Simulační / Demo režim:** Umožňuje testovat všechny funkce bez nutnosti chodit venku. Generuje syntetický pohyb vozidla kolem kampusu FEKT VUT v Brně, reálné kolísání signálu, družice i sousední buňky.
* **Krok Timing Advance (TA):** Volba kalibrační konstanty vzdálenosti od BTS:
  * `1× (78,12 m / krok)` – teoretická hodnota dle 3GPP.
  * `2× (156,24 m / krok)` – doporučená kalibrace pro většinu moderních Android telefonů.
* **Vzorkovací interval:** Volba ukládání vzorků (1 s, 2 s, 5 s, 10 s, 30 s) a kritéria (časové, vzdálenostní nebo hybridní).
* **Referenční tlak QNH:** Možnost zadat aktuální tlak na hladině moře (výchozí 1013,25 hPa) pro přesný barometrický výpočet výšky.

#### 4. Nápověda a informace o programu (Ikona „i“ vpravo nahoře)
Kliknutím na modré tlačítko **(i)** v pravém horním rohu aplikace se otevře podrobná příručka s popisem fungování jednotlivých RF metrik, vysvětlením zkratek (RSRP, SINR, CQI, DOP, AGC) a akademickými kredity autora z FEKT VUT.

---

### Přehled záložek a vlastností aplikace

#### 1. Záložka „Měření“ (Přehledový HUD)
* **GNSS Poloha a kinetika:** Zeměpisná šířka a délka na 6 desetinných míst, okamžitá rychlost (km/h a m/s), azimut směru pohybu ($0–360^\circ$) a horizontální/vertikální přesnost na 68% intervalu spolehlivosti.
* **Geometrie rozložení satelitů (DOP):** Živé hodnoty HDOP (horizontální), VDOP (vertikální) a PDOP (prostorový 3D). Klepnutím na kartu se otevře technické vysvětlení limitů přesnosti.
* **GNSS Satelity & Pásma:** Poměr použitých a viditelných satelitů, indikátor jednofrekvenčního (L1) nebo dvoufrekvenčního (L1+L5) příjmu a rozpad na konstelace (GPS, GLONASS, BeiDou, Galileo).
* **Stav mobilní sítě (SIM 1 & SIM 2):** Rychlý náhled na obě SIM karty s barevnými sloupcovými grafy síly signálu RSRP, identifikací sítě a odhadem vzdálenosti k BTS.
* **Telemetrie hardwaru a baterie:** Teplota baterie (°C), úroveň nabití, napětí článku (mV), staniční tlak z barometru a přepočítaný tlak na hladinu moře (MSL/QFF).

#### 2. Záložka „Mobily“ (Detailní RF parametry a vysílače)
* **Paralelní Dual-SIM monitor:** Obě SIM karty jsou sledovány současně pod sebou bez nutnosti přepínání.
* **Identifikace operátora & PLMN:** Detekce kódu země a sítě (např. `PLMN 230-01` T-Mobile, `PLMN 230-02` O2, `PLMN 230-03` Vodafone).
* **Rozklad Cell ID na buňku a sektor:**
  * Pro 4G LTE: $\text{eNodeB ID} = \lfloor \text{CI} / 256 \rfloor, \quad \text{Sektor ID} = \text{CI} \pmod{256}$
  * Pro 5G NR: $\text{gNodeB ID} = \lfloor \text{NCI} / 256 \rfloor, \quad \text{Sektor ID} = \text{NCI} \pmod{256}$
  * Zobrazení TAC (Tracking Area Code) a PCI (Physical Cell ID).
* **Kompletní metrologie 5G NR (SS vs. CSI):**
  * Synchronizační blok: `SS-RSRP`, `SS-RSRQ`, `SS-SINR`
  * Datový kanál: `CSI-RSRP`, `CSI-RSRQ`, `CSI-SINR`
* **Frekvenční spektrum a kanály:** Výpočet přesné nosné frekvence downlinku (DL) a uplinku (UL) v MHz z kanálových čísel EARFCN a NR-ARFCN včetně šířky kanálu (10, 15, 20, 40 MHz).
* **Průzkum sousedních buněk (Neighbor Cells):** Tabulka všech viditelných sousedních buněk s výpočtem odstupu signálu $\Delta\text{RSRP}$. Automatické označení kandidátů na handover při $\Delta \le 3\text{ dB}$.
* **Detektor anomálií a spoofingu mobilní sítě:**
  * Detekce 2G Downgrade útoků (vynucení GSM sítě typické pro IMSI Catchery).
  * Detekce izolovaných buněk s vysokým výkonem bez sousedů v SIB tabulce (falešné vysílače).
  * Detekce soukanálového zarušení (vysoký RSRP při záporném SINR).

#### 3. Záložka „Satelity“ (GNSS konstelace a pásma)
* **Podpora všech globálních konstelací:** GPS (USA), Galileo (EU), GLONASS (Rusko), BeiDou (Čína), QZSS (Japonsko) a NavIC (Indie).
* **Dvoufrekvenční příjem (L1/E1 + L5/E5a):** Identifikace nosné frekvence každé družice (1575,42 MHz vs. 1176,45 MHz) a procentuální podíl dvoufrekvenčního fixu.
* **Filtrování konstelací:** Rychlé přepínání zobrazení: *Všechny*, *Pouze L5/E5a*, *GPS*, *Galileo*, *GLONASS*, *BeiDou*.
* **Tři režimy zobrazení:**
  1. **Skyplot Radar:** 2D polární diagram rozmístění satelitů na obloze (azimut $0–360^\circ$, elevace $0–90^\circ$). Zelené mezikruží značí dvoufrekvenční příjem L5/E5a, bílá záře fix.
  2. **Signály C/N0:** Horizontální sloupcové grafy poměru nosná/šum ($C/N_0$ v dB-Hz).
  3. **Podrobná tabulka:** Kompletní soupis všech satelitů s PRN/SVID, konstelací, pásmem, elevací, azimutem a stavem fixu.
* **Monitor RF rušení (AGC Jamming Detector):** Sledování automatického řízení zisku (AGC) z událostí `GnssMeasurementsEvent`. Varuje před náhlým propadem zisku způsobeným záměrným rušením či silným parazitním signálem.
* **Surová měření a NMEA proud:** Čtení Dopplerova posuvu satelitů (Hz), čítač fázových měření ADR a živý náhled vět `$GNGGA`/`$GNRMC`.

#### 4. Záložka „Mapa“ (Trajektorie a prostorové vrstvy)
* **Interaktivní OpenStreetMap:** Zobrazení projeté trasy s barevným odlišením jednotlivých měřených bodů.
* **Přepínatelné vizuální vrstvy:**
  * **RSRP Signál:** Zelená ($> -80\text{ dBm}$), Světle zelená, Oranžová, Červená ($< -105\text{ dBm}$).
  * **Rychlost pohybu:** Modrá ($< 10\text{ km/h}$), Tyrkysová, Zelená, Červená ($> 90\text{ km/h}$).
  * **Množství satelitů:** Červená ($< 5$), Žlutá ($5–10$), Zelená ($11–25$), Modrá ($> 25$).
  * **PDOP přesnost:** Zelená ($< 1,5$), Tyrkysová, Oranžová, Červená ($> 5,0$).
* **Plovoucí dynamická legenda:** Automaticky zobrazuje vysvětlivky a prahové hodnoty pro aktuálně vybranou vrstvu.
* **Ovládací prvky:** Tlačítka pro přiblížení/oddálení (+/-) a plovoucí tyrkysové tlačítko pro vycentrování mapy na aktuální polohu.

#### 5. Záložka „Grafy“ (Časové průběhy v reálném čase)
Hardwarově vykreslované spojité grafy (Canvas sparklines) s ukazateli minimální, průměrné a maximální hodnoty pro:
- Výkon signálu obslužné buňky RSRP (dBm)
- Rychlost pohybu vozidla/pěšího (km/h)
- Počet satelitů zapojených do výpočtu polohy
- Síťovou odezvu (Socket Ping RTT v ms na 8.8.8.8)
- Přetížení a zrychlení z akcelerometru ($m/s^2$)
- Úhlovou rychlost otáčení z gyroskopu ($rad/s$)
- Magnetický azimut kompasu ($0–360^\circ$)
- Teplotu baterie (°C) – sledování termálního zahřívání přístroje

#### 6. Záložka „Historie“ (Správa relací a bodový inspektor)
* **Vyhledávání a filtrace:** Okamžité vyhledávání mezi uloženými měřeními.
* **Přejmenování relace:** Možnost klepnout na ikonu tužky a relaci libovolně pojmenovat.
* **Detailní prohlížení bod po bodu:** Otevření relace s možností procházet každý uložený vzorek `#1 • 19:01:21.043` s přesnými souřadnicemi, satelity, DOP, operátorem, frekvenčním pásmem, Dopplerem i teplotou baterie.
* **Mazání nechtěných bodů:** Ikona koše u jednotlivých vzorků pro odstranění chybného měření.
* **Tři formáty exportu dat:**
  * **CSV:** Kompletní tabulka všech parametrů (včetně PLMN, Dopplerova posuvu, NMEA vět a baterie) pro Excel, MATLAB nebo Python pandas.
  * **3D KML:** Záznam pro Google Earth s 3D trajektorií a barevným kódováním.
  * **GeoJSON:** Standardizovaný formát pro geografické informační systémy (QGIS, ArcGIS).

---

### Bezpečnost, systémová oprávnění a ochrana soukromí

Aplikace striktně dodržuje zásadu minimálních nutných oprávnění:
- **`ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`:** Nezbytné pro čtení GNSS souřadnic, satelitů a fyzických parametrů rádiových buněk v Androidu.
- **`ACCESS_BACKGROUND_LOCATION`:** Umožňuje pokračovat v záznamu trasy i po zhasnutí obrazovky nebo při přechodu do jiné aplikace.
- **`BODY_SENSORS` / Senzory:** Přístup k barometru, akcelerometru a gyroskopu.
- **`FOREGROUND_SERVICE_LOCATION`:** Zajišťuje nepřetržitý běh měřicí služby na pozadí.
- **`INTERNET` & `ACCESS_NETWORK_STATE`:** Pouze pro stahování mapových dlaždic OpenStreetMap a odesílání socket ping sondy na adresu 8.8.8.8.

> **🛡️ Ochrana soukromí (Žádné oprávnění k telefonním hovorům):**  
> Aplikace **nikdy nevyžaduje** invazivní oprávnění `READ_PHONE_STATE`. Všechna data o mobilních vysílačích (eNodeB, PCI, TAC, RSRP, SINR, PLMN) jsou v moderním Androidu bezpečnostně svázána s polohou, nikoliv s telefonními hovory či kontakty. Aplikace nemá přístup k vašemu telefonnímu číslu, kontaktům, hovorům ani IMEI.

---

### Informace o autorovi a akademickém pracovišti

* **Autor:** Ing. Jan Kufa, Ph.D.
* **Ústav:** Ústav radioelektroniky (UREL)
* **Fakulta:** Fakulta elektrotechniky a komunikačních technologií (FEKT)
* **Univerzita:** Vysoké učení technické v Brně (VUT)
* **Osobní profil:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512)
* **E-mail:** `kufa@vut.cz` / `kufa@vutbr.cz`
