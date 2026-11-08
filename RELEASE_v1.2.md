# GNSS & Net Logger – Release v1.2

**Tag / Version:** `v1.2`  
**Release Title:** `GNSS & Net Logger v1.2 – Dual Skyplot Radar, DSDS & DOP Optimization`  
**Author:** Ing. Jan Kufa, Ph.D. (Ústav radioelektroniky FEKT VUT v Brně)  
**APK Binaries:** `APK/GNSS_Net_Logger_v1.2.apk` (and `GNSS_Net_Logger_v1.2.apk.zip`)

---

## 🚀 Co je nového ve verzi 1.2 / What's New in Version 1.2

### 🇨🇿 České shrnutí (Czech)
- **Přepínatelný Skyplot Radar (karta Satelity):**
  - Nový přepínač režimů zobrazení:
    - **`[ Podle konstelace ]`**: Přehledné barevné odlišení družic podle systémů – GPS (azurová), Galileo (zelená), GLONASS (žlutá) a BeiDou (fialová).
    - **`[ Podle síly C/N₀ ]`**: Obarvení podle úrovně signálu ($\ge 35\text{ dB-Hz}$ zelená, $25–34\text{ dB-Hz}$ azurová, $< 25\text{ dB-Hz}$ oranžová, nepoužité šedá).
  - V obou režimech je zachována bílá záře kolem aktivně fixovaných družic a zelený vnější prstenec u dvoufázových nosných **L5 / E5a**.
  - Vysvětlivky a legenda se dynamicky synchronizují s vybraným režimem.
- **Korekce DOP faktorů při absenci fixu:**
  - Pokud přijímač zrovna nemá 3D fix nebo je viditelných méně než 4 satelity, aplikace namísto zavádějící hodnoty `9` nebo `9.9` zobrazuje pomlčku **`"—"`** (v exportovaném CSV **`"-"`**).
- **Paralelní Dual SIM podpora (DSDS):**
  - Přepínač zobrazení pro detailní inspekci `[SIM 1]`, `[SIM 2]` nebo srovnání `[Obě SIM]`.
  - Správná detekce a indikace stavu: aktivní datové spojení (`RRC Connected`) vs. pohotovostní režim pro hovory a SMS (`DSDS Standby / RRC Idle`) bez falešného míchání parametrů.
- **Real-time srovnání v grafech:**
  - Současné vykreslování spojitých časových křivek signálu RSRP pro obě SIM karty vedle sebe (SIM 1 azurová, SIM 2 oranžová) na společné RF škále.
- **Přímé stažení do paměti telefonu:**
  - Možnost v záložce Historie uložit exportované soubory (CSV, KML, GeoJSON) přímo do složky `Download/GNSS_Net_Logger/` pro snadný přístup bez nutnosti cloudu.
- **Optimalizace pro Android 14/15:**
  - Implementace `OnBackInvokedCallback` pro plynulou podporu systémových prediktivních gest zpět bez varovných hlášení v logcatu.

---

### 🌐 English Summary
- **Dual-Mode Polar Skyplot Radar (Satellites Tab):**
  - Mode selector between **`[ By Constellation ]`** (GPS cyan, Galileo green, GLONASS amber, BeiDou purple) and **`[ By C/N₀ Signal ]`** ($\ge 35\text{ dB-Hz}$, $25–34\text{ dB-Hz}$, $< 25\text{ dB-Hz}$, Unused gray).
  - Synchronized interactive legend, white halos for fix-locked satellites, and outer glowing rings for dual-band **L5 / E5a** carriers.
- **Intelligent DOP No-Fix Filtering:**
  - Accurately displays a clean dash **`"—"`** (and **`"-"`** in CSV) when position fix is unavailable or fewer than 4 satellites are locked, preventing misleading `9.0` readings.
- **Advanced Dual-SIM DSDS Support:**
  - Dedicated selector for `[SIM 1]`, `[SIM 2]`, and `[Both SIMs]`.
  - Explicit identification of active data slot (`RRC Connected`) versus voice/SMS slot (`DSDS Standby / RRC Idle`) preventing cross-slot telemetry distortion.
- **Real-Time Dual Carrier RF Curves:**
  - Live simultaneous RSRP time-series sparklines for SIM 1 (Cyan) and SIM 2 (Amber) side-by-side on a unified $-125\text{ to }-65\text{ dBm}$ RF grid.
- **Direct Offline Device Downloads:**
  - Export CSV, 3D KML, and GeoJSON files directly into the Android `Download/GNSS_Net_Logger/` folder via MediaStore.
- **Modern Android 14/15 Back Gesture Support:**
  - Registered `enableOnBackInvokedCallback` for smooth predictive back handling.

---

## 📦 Binární soubory / Assets to Upload to Release
1. `GNSS_Net_Logger_v1.2.apk` (přímý instalační balíček)
2. `GNSS_Net_Logger_v1.2.apk.zip` (komprimovaný archiv pro bezpečné stažení)
