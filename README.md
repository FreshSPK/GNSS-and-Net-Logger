# Signal & GNSS Field Logger / Terénní záznamník signálu a GNSS

*Standalone, zero-external-API-key native Android telemetry & field survey tool.*
*Vysoce přesný terénní záznamník mobilních sítí a satelitní navigace bez závislosti na externích klíčích.*

---

## 👨‍🔬 Author Attribution & Academic Credits / Informace o autorovi
- **Author / Autor:** Ing. Jan Kufa, Ph.D.
- **Affiliation / Pracoviště:** Department of Radio Electronics (UREL), Faculty of Electrical Engineering and Communication (FEKT), Brno University of Technology (VUT Brno) / Ústav radioelektroniky (UREL), Fakulta elektrotechniky a komunikačních technologií (FEKT), Vysoké učení technické v Brně (VUT v Brně).
- **Researcher Profile / Profil výzkumníka:** [https://www.vut.cz/lide/jan-kufa-125512](https://www.vut.cz/lide/jan-kufa-125512)
- **Contact / Kontakt:** `kufa@vut.cz`

---

## 🇬🇧 English Overview
**Signal & GNSS Field Logger** is an offline-capable, production-grade Android application engineered for radio frequency (RF) drive tests, mobile network surveys, and multi-constellation satellite navigation (GNSS) field diagnostics.

### Key Capabilities:
1. **Zero External API Keys Requirement:**
   - **Interactive Map:** Built using **OSMDroid (OpenStreetMap)** with client-side tile caching and zero Google Maps API keys or billing quotas required.
   - **Local Database:** Fast SQLite Room Database with Coroutines/Flow for reliable on-device persistence. No Firebase setup needed.
   - **Raw Telemetry & Latency Probe:** Pure native Android `TelephonyManager`, `LocationManager`, `GnssStatus`, and direct socket ping to `8.8.8.8:53`.

2. **Dual-SIM & Cellular RF Engine:**
   - Stacked monitor for SIM Slot 1 and SIM Slot 2.
   - Decodes Network Generation: 2G GSM, 3G UMTS, 4G LTE, 5G NSA (EN-DC anchor), and 5G SA.
   - Decodes Cell Identity: TAC, LAC, CI, PCI, eNodeB ID + Sector ID (LTE), and gNodeB ID + Sector ID (5G).
   - EARFCN & NR-ARFCN to 3GPP Band mapper (B1, B3, B7, B20, n78, etc.).
   - Full signal metrics: RSRP, RSRQ, RSSI, sanitized SINR in dB, 5G SS-metrics (SS-RSRP, SS-RSRQ, SS-SINR), and 5G CSI-metrics.
   - Timing Advance (TA) physical BTS distance estimator (`TA * 78.12 m`).
   - Neighbor cells table with live Δ (Serving RSRP - Neighbor RSRP) delta.

3. **GNSS & Multi-Frequency Engine:**
   - Constellation status (GPS, GLONASS, Galileo, BeiDou, QZSS, NavIC).
   - Carrier frequency detection (L1 vs. L5 / E1 vs. E5a) with real-time Dual-Frequency ratio calculation.
   - Geometric Dilution of Precision: HDOP, VDOP, PDOP.
   - **Polar SkyPlot:** Real-time azimuth (0–360°) and elevation (0–90°) orbit visualization color-coded by C/N0 (dB-Hz).
   - **Jamming / Interference Detector:** Analyzes Automatic Gain Control (AGC) from raw `GnssMeasurementsEvent` with sudden gain loss alerts.

4. **Sensor Fusion & Atmosphere:**
   - Hardware Barometer (`Sensor.TYPE_PRESSURE`) in hPa with configurable QNH for relative elevation profiling.

5. **Foreground Service & Flexible Logging:**
   - Android Foreground Service with continuous partial `WakeLock`.
   - Triggers: Time interval (1s, 2s, 5s, 10s, 30s), Distance displacement (5m, 10m, 25m, 50m), or Hybrid.
   - Search & filter past sessions in SQLite.
   - Multi-format file export:
     - **CSV** (full raw data matrix)
     - **3D KML** (Google Earth with altitude tracks and satellite-color coding)
     - **GeoJSON** (QGIS / ArcGIS ready)

6. **Interactive Map HUD & Trace Replay:**
   - Breadcrumb trace color-coded strictly by satellite count (<5 Red, 5-10 Yellow, 11-25 Green, >25 Blue).
   - Dynamic HUD overlay (Speed, RSRP dBm, Accuracy, DOP, Satellites, Ping RTT).
   - Trace Replay: Instantly load any past session directly onto the map.

7. **Simulation / Demo Mode:**
   - Toggle in Settings to generate synthetic GNSS motion around VUT FEKT Brno, Dual-SIM handovers, and fluctuating RF metrics for outdoor-free testing.

---

## 🇨🇿 Český popis
**Signal & GNSS Field Logger** je plně samostatná, robustní aplikace pro terénní měření mobilních radiových sítí a satelitních navigačních systémů.

### Hlavní funkce:
- **Architektura bez externích API klíčů:** Využívá OpenStreetMap (OSMDroid) a lokální SQLite Room databázi.
- **Dual-SIM RF monitor:** Podpora dvou SIM karet současně, detekce generací 2G/3G/4G/5G NSA/5G SA, rozpad eNodeB/gNodeB ID, sektorů, PCI, TAC, EARFCN/NR-ARFCN na 3GPP pásma, Timing Advance přepočet na metry a tabulka sousedních buněk s relací Delta dB.
- **GNSS a vícefrekvenční příjem:** Polar SkyPlot diagram oblohy, vyhodnocení carrier frekvencí (L1/L5 a E1/E5a), poměr dual-frequency příjmu, výpočet DOP a detektor radiofrekvenčního rušení (AGC Jamming Monitor).
- **Záznam v popředí a export:** Foreground služba se zámkem probuzení, nastavitelný interval (čas, vzdálenost nebo hybrid) a export do CSV, 3D KML (Google Earth) a GeoJSON (QGIS).
- **Režim simulace:** Umožňuje plné testování v laboratorním prostředí s generováním syntetické trasy kolem areálu VUT FEKT v Brně.
