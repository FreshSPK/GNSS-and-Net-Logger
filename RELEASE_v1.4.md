# GNSS & Net Logger – Release v1.4

**Tag / Version:** `v1.4` (`versionCode = 9`)  
**Release Title:** `GNSS & Net Logger v1.4 – 5G NSA/SA Detection, CA Frequencies, GPS Test Alignment & Full Satellite Logging`  
**Author:** Ing. Jan Kufa, Ph.D. (Department of Radio Electronics, FEKT, VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** `APK/GNSS_Net_Logger_v1.4.apk.zip`

---

## 🚀 What's New in Version 1.4 / Co je nového ve verzi 1.4

### 🌐 English
* **5G NSA (EN-DC) vs. 5G SA & 5G NR Neighbors:** Added per-SIM `TelephonyCallback` and LTE+NR `CellSignalStrengthNr` extraction to distinguish `4G LTE`, `5G NSA` (`EN-DC LTE Anchor (Idle)` matching the status bar 5G icon vs. `EN-DC NR Active`), and `5G SA`, along with active NR SCG cell display and `All` | `4G LTE` | `5G NR` neighbor filter chips.
* **Carrier Aggregation Component Frequencies:** Displays both total aggregated CA bandwidth (`Total CA: XX MHz`) and every aggregated PCC/SCC/SCG component carrier with band label and center frequency in MHz (e.g., `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`) across *Overview*, *Cellular RF*, *History*, and exports; fixes NR-ARFCN band mapping (`n1`, `n3`, `n7`, `n20`, `n28`, `n78`).
* **GPS Test Parity & SkyPlot Legend Sync:** Reports both unique physical satellites and total `L1/E1/B1/G1 + L5/E5a/B2a` signals; groups multi-band SVs on *SkyPlot* with a 1:1 synchronized legend (**filled dot + white border** = in fix, **hollow ring** = unused, **green glow** = `L5/E5a/B2a`).
* **Demo Mode Banner & Indoor Fix-Loss Guard:** Shows a `DEMO` top-bar badge and persistent banner across all tabs with a one-tap disable button; cleanly resets synthetic state when switching back to real hardware and clears stale GPS fix/speed on indoor signal loss.
* **Per-SIM Handover Counters:** Tracks `HO SIM1` and `HO SIM2` independently across *Overview* and *Cellular RF*.
* **Recalibrated Thresholds & Full Used + Visible Satellite Logging:** Updated **PDOP** (`< 1.0`, `1.0–2.0`, `2.0–4.0`, `≥ 4.0`) and **Satellite** (`< 5`, `5–20`, `21–30`, `31–50`, `> 50`) scales; upgraded Room schema to v4 (`MIGRATION_3_4`) to record both used and visible satellites plus per-SIM CA summaries in the database, *Charts*, *History*, and **CSV / GPX 1.1 / GeoJSON** exports.
* **Non-Wrapping Settings Presets:** Updated quick profiles to **Walking (`5s / 10m`)**, **Driving (`10s / 50m`)**, and **Static (`10s`)**.
* **Author GitHub Link:** Added clickable GitHub repository link inside the *About & Help* dialog.

---

### 🇨🇿 Česky
* **Detekce 5G NSA (EN-DC) vs. 5G SA & 5G NR sousedé:** Per-SIM `TelephonyCallback` a extrakce `CellSignalStrengthNr` z LTE+NR rámců přesně rozlišují `4G LTE`, `5G NSA` (`EN-DC LTE Kotva (Idle)` odpovídající 5G ikoně v liště vs. `EN-DC NR Aktivní`) a `5G SA`; doplněna aktivní 5G NR SCG větev a filtr sousedů `Vše` | `4G LTE` | `5G NR`.
* **Agregované kmitočty v Carrier Aggregation:** Na kartách *Měření*, *Mobily*, v *Historii* i v exportech se kromě celkové šířky pásma (`Celkem CA: XX MHz`) zobrazují všechny agregované PCC/SCC/SCG nosné s pásmem a frekvencí v MHz (např. `PCC B3 1800 MHz + SCC B1 2100 MHz + SCG n78 3500 MHz`); opraveno mapování NR-ARFCN pásem (`n1`, `n3`, `n7`, `n20`, `n28`, `n78`).
* **Shoda s GPS Test & SkyPlot legenda:** Oddělené sledování fyzických unikátních družic a celkových signálů `L1/E1/B1/G1 + L5/E5a/B2a`; sdružení vícepásmových družic na *SkyPlot* radaru s 1:1 synchronizovanou legendou (**plný bod + bílý okraj** = ve fixu, **prázdný kroužek** = mimo fix, **zelená záře** = `L5/E5a/B2a`).
* **Indikace Demo režimu & Ochrana proti zatuhlému fixu v budově:** Aktivní simulace zobrazuje odznak `DEMO` v horní liště a banner s tlačítkem pro vypnutí napříč záložkami; přepnutí do reálného režimu vyčistí syntetický stav a při ztrátě fixu v interiéru se automaticky vynuluje stav fixu i rychlost.
* **Oddělené handovery SIM 1 / SIM 2:** Samostatná počítadla `HO SIM1` a `SIM2` na kartě *Měření* i *Mobily*.
* **Nové prahy DOP/Satelitů & Logování použitých i viditelných družic:** Překalibrované prahy **PDOP** (`< 1,0`, `1,0–2,0`, `2,0–4,0`, `≥ 4,0`) a **Satelitů** (`< 5`, `5–20`, `21–30`, `31–50`, `> 50`); aktualizace Room databáze na v4 (`MIGRATION_3_4`) pro ukládání použitých i viditelných satelitů a CA souhrnů do DB, *Grafů*, *Historie* a exportů **CSV / GPX 1.1 / GeoJSON**.
* **Nezalamující se předvolby v Nastavení:** Profily upraveny na **Pěší (`5s / 10m`)**, **Auto (`10s / 50m`)** a **Statické (`10s`)**.
* **Odkaz na GitHub u autora:** V okně *Nápověda a Informace o autorovi* je nyní klikatelný odkaz na tento GitHub repozitář.
