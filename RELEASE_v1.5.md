# GNSS & Net Logger – Release v1.5

**Tag / Version:** `v1.5` (`versionCode = 10`)  
**Release Title:** `GNSS & Net Logger v1.5 – Czech 3-Operator BTS Database (OpenCelliD & GSMweb), Real BTS Distance/Azimuth, OSM Tower Layer & v1.5 Exports`  
**Author:** Ing. Jan Kufa, Ph.D. (Department of Radio Electronics, FEKT, VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** `APK/GNSS_Net_Logger_v1.5.apk.zip`

---

## 🚀 What's New in Version 1.5 / Co je nového ve verzi 1.5

### 🌐 English
* **Czech 3-Operator BTS Registry (`23001` T-Mobile CZ, `23002` O2 CZ, `23003` Vodafone CZ):** Added an on-device Room `bts_towers` registry with dedicated operator counters, search (by `CellID`, `eNodeB`, `PCI`, district code, or street name), manual tower coordinate editor, and bundled T-Mobile/O2 sample data.
* **OpenCelliD.org Primary GPS Import (`.csv` & `.csv.gz`) + GSMweb Bidirectional Merge:**
  * Streams full OpenCelliD Czechia dumps (`230.csv` or directly compressed `230.csv.gz` via built-in `GZIPInputStream`) in 2,000-row batches with 64 KB buffered I/O, automatically filtering `MCC 230` operators and tagging coordinates as `OPENCELLID`, with a quick link to [OpenCelliD Downloads](https://opencellid.org/downloads.php).
  * Retains **GSMweb.cz** (`CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni`, `Windows-1250`/`UTF-8`) and **CLF v3/v4** imports for rich Czech site names, districts, MHz bands, and PCI verification—without inventing GPS coordinates when absent, and **bidirectionally smart-merging** OpenCelliD GPS + GSMweb metadata in either import order.
* **Real UE-to-BTS Distance, Azimuth, ΔTA & FSPL:** Computes real Haversine ground distance (`m` / `km`), cardinal bearing (`°` + compass direction), Timing Advance vs. real distance delta ($\Delta\text{TA}$), and theoretical Free-Space Path Loss (`FSPL dB`) across *Overview* and the new **`KNOWN BTS TOWER`** card on *Cellular RF*.
* **CETIN MOCN Sharing, Co-Located Mast Bands & DB Cross-Check:** Automatically resolves shared CETIN **MOCN** sites between T-Mobile (`23001`) and O2 (`23002`), lists all co-located MHz bands on the same `eNodeB` mast, and triggers **`BTS_DB_MISMATCH`** anomaly warnings when live network PCI or TAC differs from the local BTS database.
* **Optional District / Background Address Geocoding:** Supports optional on-device `Geocoder` + Nominatim fallback per district or background serving site lookup for GPS-less GSMweb street descriptions.
* **OpenStreetMap BTS Layer & RF Link Lines:** Toggleable *Map* overlay renders color-coded nearby BTS markers (T-Mobile magenta, O2 blue, Vodafone red), serving SIM 1 / SIM 2 towers, interactive tower inspection, and dashed UE-to-BTS RF link lines.
* **Room DB v5 (`MIGRATION_4_5`) & v1.5 Exports:** Records and exports `Sim1BtsLocation`, `Sim1BtsDistance_m`, `Sim1BtsAzimuth_deg`, and `Sim2Bts*` across *History* point cards and **CSV / GPX 1.1 / GeoJSON** exports.

---

### 🇨🇿 Česky
* **Databáze vysílačů 3 českých operátorů (`23001` T-Mobile CZ, `23002` O2 CZ, `23003` Vodafone CZ):** Lokální databáze `bts_towers` v Room SQLite s přehledem počtu buněk podle operátora, vyhledáváním (podle `CellID`, `eNodeB`, `PCI`, okresu či ulice), ručním editorem GPS souřadnic a vestavěným ukázkovým vzorkem T-Mobile + O2.
* **OpenCelliD.org jako hlavní zdroj GPS (`.csv` i `.csv.gz`) + chytré sloučení s GSMweb:**
  * Streamovaně zpracuje i velké soubory (2–6+ MB) z OpenCelliD pro ČR (**`230.csv`** i přímo zabalený archiv **`230.csv.gz`** díky vestavěné dekompresi za běhu) po dávkách 2 000 řádků s 64 KB bufferem, automaticky vyfiltruje operátory `MCC 230` a obsahuje přímý odkaz na stažení z [OpenCelliD.org](https://opencellid.org/downloads.php).
  * Zachovává import **GSMweb.cz** (`CellID;PhysCID;TAC;Band;GSMCID;Datum;Okr;Umisteni` v `Windows-1250`/`UTF-8`) i **CLF v3/v4** pro české názvy lokalit, okresy, přesná MHz pásma a kontrolu PCI — bez GPS zobrazí jasné upozornění a při kombinaci s OpenCelliD **obousměrně sloučí GPS z OpenCelliD + textová metadata z GSMweb** bez ohledu na pořadí importu.
* **Reálná vzdálenost k BTS, azimut, ΔTA & útlum FSPL:** Počítá pozemní Haversine vzdálenost (`m` / `km`), azimut se světovou stranou, odchylku odhadu Timing Advance oproti reálné vzdálenosti ($\Delta\text{TA}$) a teoretický útlum volného prostoru (`FSPL dB`) na kartě *Měření* i nové detailní kartě **`ZNÁMÝ VYSÍLAČ BTS`** na záložce *Mobily*.
* **Sdílení MOCN, souběžná pásma na stožáru & křížová kontrola DB:** Automaticky rozpoznává sdílené vysílače CETIN **MOCN** mezi T-Mobile (`23001`) a O2 (`23002`), vypisuje všechna pásma na témže `eNodeB` stožáru a hlásí bezpečnostní nesoulad **`BTS_DB_MISMATCH`** při odchylce PCI či TAC živé sítě proti databázi.
* **Volitelné geokódování okresů a lokalit:** Volitelně umožňuje dohledat přibližnou GPS polohu z textových adres GSMweb pomocí Android `Geocoder` a zálohy Nominatim.
* **Vrstva BTS & RF spojnice na OpenStreetMap:** Přepínatelná vrstva na *Mapě* zobrazuje barevně rozlišené vysílače v okolí (T-Mobile, O2, Vodafone), obslužné vysílače SIM 1 / SIM 2, klikatelný detail stožáru a čárkovanou přímou RF linku od telefonu k BTS.
* **Room DB v5 (`MIGRATION_4_5`) & Exporty v1.5:** Ukládá a vyváží `Sim1BtsLocation`, `Sim1BtsDistance_m`, `Sim1BtsAzimuth_deg` a `Sim2Bts*` v inspektoru *Historie* i v exportech **CSV / GPX 1.1 / GeoJSON**.

