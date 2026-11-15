# GNSS & Net Logger – Release v1.4

**Tag / Version:** `v1.4` (`versionCode = 9`)  
**Release Title:** `GNSS & Net Logger v1.4 – 5G NSA/SA Detection, CA Frequencies, GPS Test Alignment & Full Satellite Logging`  
**Author:** Ing. Jan Kufa, Ph.D. (Ústav radioelektroniky FEKT VUT v Brně)  
**APK Binary:** `APK/GNSS_Net_Logger_v1.4.apk.zip`

---

## 🚀 Co je nového ve verzi 1.4 / What's New in Version 1.4

### 🇨🇿 České shrnutí (Czech)
- **Přesná detekce 5G NSA (EN-DC) vs. 5G SA & 5G NR sousedé:**
  - Registrace per-SIM `TelephonyCallback` (`DisplayInfoListener`, `PhysicalChannelConfigListener`, `CellInfoListener`, `SignalStrengthsListener`) a čtení `CellSignalStrengthNr` i u LTE+NR kombinovaných rámců.
  - Rozlišení `4G LTE`, `5G NSA` (`EN-DC LTE Kotva (Idle)` při shodě s 5G ikonou v systémové liště i při nevyužité datové vrstvě a `EN-DC NR Aktivní` při aktivní SCG větvi) a `5G SA`.
  - Oprava parsování `CellInfoNr` u sousedních i sekundárních buněk a doplnění 5G NR SCG uzlu do přehledu s odděleným filtrem **`Vše` | `4G LTE` | `5G NR`** (včetně vysvětlení, proč 5G NSA kotevní buňky vysílají primárně 4G SIB sousedy).
- **Konkrétní agregované kmitočty v Carrier Aggregation (CA):**
  - Na kartách *Měření* i *Mobily* se nyní u každé SIM zobrazují nejen celková šířka pásma (`Celkem CA: XX MHz`), ale i **přesný rozpis všech agregovaných nosných (PCC + SCC)** s pásmem a nosnou frekvencí v MHz (např. `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`).
  - Oprava převodu NR-ARFCN na pásma a frekvence (n1, n3, n7, n20, n28, n78) a zachování detekovaných SCC kanálů z `PhysicalChannelConfig` / `ServiceState.cellBandwidths`.
- **Shoda s aplikací GPS Test & Sjednocení SkyPlotu:**
  - Zpřesněno počítání a zobrazení **fyzických unikátních družic** vs. **celkových signálů (L1 + L5/E5a/B2a)** v hlavičce i v přehledu konstelací (GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, NavIC) včetně opravy překryvu popisku dvoufrekvenčního příjmu na hlavní kartě.
  - **1:1 synchronizace SkyPlot radaru a legendy:** fyzické družice s více frekvencemi jsou sdruženy do jednoho bodu; družice **ve fixu** jsou vykresleny plnou sytou barvou s bílým ohraničením, družice **mimo fix** jako prázdný kroužek (šedý v režimu C/N₀), dvoufrekvenční příjem L5/E5a/B2a zvýrazňuje zelené vnější mezikruží.
- **Oddělené počítadlo handoverů (HO) pro SIM 1 a SIM 2:**
  - Nezávislé sledování změn buněk pro `SIM 1` a `SIM 2` zobrazené přímo na přehledu (`HO SIM1: x | SIM2: y (Celkem: z)`) i na kartě *Mobily* (`HO S1:x | S2:y (Σz)`).
- **Nové prahy mapy a grafů + Logování použitých i viditelných satelitů:**
  - Překalibrovaná legenda **PDOP**: `< 1,0` (výborný), `1,0–2,0` (dobrý), `2,0–4,0` (střední), `≥ 4,0` (kritický) napříč kartami *Měření*, *Satelity*, *Mapa* i *Grafy* (`0–4`).
  - Překalibrovaná legenda **Počet satelitů**: `< 5`, `5–20`, `21–30`, `31–50`, `> 50`.
  - **Duální logování satelitů (Room DB v4 + `MIGRATION_3_4`):** každý vzorek nyní ukládá jak použité, tak viditelné satelity (`uniqueSatellitesInView`, `uniqueSatellitesUsed`, detailní seznamy `usedSatellitesDetail`, `visibleSatellitesDetail`) a CA souhrny `sim1CaSummary` / `sim2CaSummary` do databáze, notifikace, bodového inspektoru *Historie*, duální křivky v *Grafech* i exportů **CSV, GPX 1.1 a GeoJSON**.
- **Čisté předvolby v Nastavení:**
  - Nezalamující se `FlowRow` čipy rychlých profilů s aktualizovanými hodnotami: **Pěší (`5s / 10m`)**, **Auto (`10s / 50m`)** a **Statické (`10s`)**.

---

### 🌐 English Summary
- **Accurate 5G NSA (EN-DC) vs. 5G SA Detection & 5G NR Neighbor Scan:**
  - Added per-subscription `TelephonyCallback` (`DisplayInfoListener`, `PhysicalChannelConfigListener`, `CellInfoListener`, `SignalStrengthsListener`) and LTE+NR `SignalStrength` extraction.
  - Distinguishes `4G LTE`, `5G NSA` (`EN-DC LTE Anchor (Idle)` matching the system status bar 5G icon even when NR RRC leg is idle, vs. `EN-DC NR Active`), and `5G SA`.
  - Fixed `CellInfoNr` neighbor/SCG parsing and appended active 5G NR SCG cells with **`All` | `4G LTE` | `5G NR`** neighbor filtering.
- **Exact Carrier Aggregation (CA) Component Frequencies:**
  - Displays both total aggregated bandwidth (`Total CA: XX MHz`) and every aggregated PCC/SCC/SCG component carrier with band label and center frequency in MHz (e.g., `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`) across *Overview*, *Cellular RF*, *History*, and exports.
- **GPS Test Parity & Synchronized SkyPlot Legend:**
  - Tracks and displays both unique physical satellites and individual frequency signals (`L1/E1/B1/G1 + L5/E5a/B2a`) across GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS, and NavIC, fixing dual-band header overlap on *Overview*.
  - Grouped multi-band satellites per physical SV on *SkyPlot*: in-fix satellites render as filled solid dots with a white border, unused satellites as hollow rings (gray in C/N₀ mode), with a green outer ring for dual-band L5/E5a/B2a reception—matching the legend 1:1.
- **Per-SIM Handover Counters (`SIM 1` & `SIM 2`):**
  - Independent cell transition counters (`HO SIM1: x | SIM2: y (Total: z)`) displayed on both *Overview* and *Cellular RF*.
- **Updated DOP / Satellite Thresholds & Full Used + Visible Satellite Logging:**
  - Recalibrated **PDOP** thresholds (`< 1.0` optimal, `1.0–2.0`, `2.0–4.0`, `≥ 4.0` poor) and **Satellite** map legend (`< 5`, `5–20`, `21–30`, `31–50`, `> 50`).
  - Upgraded Room schema to **v4 (`MIGRATION_3_4`)** to persist both used and visible satellites (`uniqueSatellitesInView`, `uniqueSatellitesUsed`, `usedSatellitesDetail`, `visibleSatellitesDetail`) and per-SIM CA summaries into the database, foreground notification, *Charts* dual satellite sparkline, *History* inspector, and **CSV / GPX 1.1 / GeoJSON** exports.
- **Non-Wrapping Settings Presets:**
  - Single-line preset chips updated to **Walking (`5s / 10m`)**, **Driving (`10s / 50m`)**, and **Static (`10s`)**.
