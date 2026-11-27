# GNSS & Net Logger – Release v1.8

**Tag / Version:** `v1.8` (`versionCode = 13`)  
**Release Title:** `GNSS & Net Logger v1.8 – High-Precision BTS GPS Localization (ČÚZK RÚIAN, BeaconDB, NetMonster NTM / CLF / CellMapper eNB / KML / ZIP & Coordinate Priority)`  
**Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** [`APK/GNSS_Net_Logger_v1.8.apk.zip`](APK/GNSS_Net_Logger_v1.8.apk.zip)

---

## What's New in Version 1.8 / Co je nového ve verzi 1.8

### 🇨🇿 Česky

* **Přesné zaměření českých vysílačů z GSMwebu pomocí oficiálního registru ČÚZK RÚIAN (`~2–10 m`):**
  * Databáze GSMweb.cz často neobsahuje GPS souřadnice, ale přesnou poštovní adresu (např. *Brno - Královo Pole, Technická 3082/12, indoor FEKT*).
  * Nový normalizér adres (`BtsGeoUtils.buildRuianAddressQueries`) odfiltruje stožárový žargon (`stožár`, `komín`, `indoor`, `k.ú.`, `č.p.`, závorky) a pošle čistý dotaz do veřejného rozhraní **ČÚZK RÚIAN `GeocodeSOE`** (`ags.cuzk.gov.cz`), čímž získá přesnou WGS84 polohu budovy bez jakéhokoli API klíče.
* **Bezklíčové dohledání venkovských stožárů & neznámých buněk přes BeaconDB (`api.beacondb.net`):**
  * Pro lesní/kopcovité vysílače bez čísla popisného i pro zcela neznámé buňky v terénu (pokud je zapnuté *Automatické geokódování BTS*) volá aplikace otevřené API **BeaconDB** (`POST https://api.beacondb.net/v1/geolocate`) včetně automatického záložního dotazu na **MOCN partnera** (`23001 ↔ 23002`), bez nutnosti registrace nebo API klíče.
* **Univerzální importér 7 formátů BTS (`.csv`, `.clf`, `.ntm`, `.kml`, `.csv.gz`, `.zip`):**
  * **NetMonster `.ntm` / `nm2300x`:** Přímý import 9 i 10sloupcových souborů z NetMonsteru včetně rozlišení od čárkou odděleného OpenCelliD.
  * **CLF v3/v4 (`;`-oddělené):** Podpora hexadecimálních i dekadických `CID/LAC` a hlaviček začínajících `//` nebo `#`.
  * **CellMapper eNodeB Tower CSV & App Signal Log:** Rozpozná věžové eNodeB exporty (i bez jednotlivých sektorových `CellID`) a uloží je jako stožárové kotvy (`applyEnodebTowersPreservingPriority`), stejně tak zpracuje i surové logy z mobilní aplikace CellMapper.
  * **KML `<Placemark>` & vícerozměrné `.zip` archivy:** Streamovaný parser pro Google Earth `.kml` a sekvenční zpracování všech podporovaných souborů uvnitř `.zip` balíčků.
* **Sektorové ztotožnění přes `eNodeB` kotvu (`eNB` / `MOCN eNB`):**
  * `BtsRepository.matchSimCell` nyní přiřadí všechny sektory jednoho stožáru i tehdy, pokud je v databázi znám pouze `eNodeB` z CellMapperu nebo jiný sektor stejného stožáru — bez falešných varování na neshodu `PCI`.
* **Hierarchie přesnosti souřadnic (`coordSourcePriority`):**
  * `MANUAL (100) > CLF / NTM / KML / IMPORT (85) > RUIAN (80) > CELLMAPPER (70) > GEOCODER / NOMINATIM (55) > BEACONDB (45) > OPENCELLID (40) > CELLMAPPER_LOG (25)`.
  * Hrubé uživatelské centroidy z OpenCelliD nebo logů už nikdy nepřepíší přesně zaměřený stožár z CLF, NetMonsteru, KML ani RÚIAN.
* **Opravený rozcestník portálů & dávkové dohledání pro všechny okresy:**
  * Opraven odkaz na `GSMweb.cz` (cesta `/celltrack/clf/` vracela na serveru `403 Forbidden` kvůli zakázanému výpisu adresáře – portál se otevírá přes hlavní menu `gsmweb.cz`), přidány přímé odkazy na **OpenCelliD (`230.csv.gz`)** a **CellMapper**, a dávkové dohledání GPS nyní podporuje volbu **`Všechny` (All Sites)** i jednotlivé okresy.
* **Aktualizace manuálu a verzování (`v1.8`):**
  * Aktualizován dialog *O aplikaci*, interní 6stránkový ilustrovaný **PDF manuál (`GNSS_Net_Logger_v1.8_Manual_CS.pdf` / `_EN.pdf`)** a hlavičky exportů.

---

### 🌐 English

* **Building-Level Czech Tower Geocoding via Official ČÚZK RÚIAN (`~2–10 m`):**
  * GSMweb.cz exports often omit WGS84 coordinates but include exact Czech street addresses.
  * `BtsGeoUtils.buildRuianAddressQueries` strips tower jargon (`stožár`, `komín`, `indoor`, `k.ú.`, `č.p.`, parentheticals) and queries the public **ČÚZK RÚIAN `GeocodeSOE`** REST endpoint (`ags.cuzk.gov.cz`, `outSR=4326`) with zero API keys required.
* **Keyless Rural Mast & Real-Time Fallback Lookup via BeaconDB (`api.beacondb.net`):**
  * Rural/forest masts without street numbers and previously unmapped serving cells (when *Auto-geocode BTS* is enabled) are resolved via **BeaconDB** (`POST https://api.beacondb.net/v1/geolocate`) with automatic **CETIN MOCN** partner retry (`23001 ↔ 23002`), requiring no API keys or login.
* **Universal 7-Format Streaming BTS Importer (`.csv`, `.clf`, `.ntm`, `.kml`, `.csv.gz`, `.zip`):**
  * **NetMonster `.ntm` / `nm2300x`** (9-column & 10-column semicolon/CSV exports), **CLF v3/v4** (decimal/hex IDs, `#`/`//` headers), **CellMapper eNodeB Tower CSV & App Signal Logs**, streaming **KML `<Placemark>`**, **OpenCelliD `.csv` / `.csv.gz`**, and multi-entry **`.zip` archives**.
* **eNodeB Anchor Sibling Fallback (`eNB` / `MOCN eNB`):**
  * `BtsRepository.matchSimCell` resolves all co-sited sectors of an eNodeB from a single CellMapper tower pin or sibling sector without triggering false `PCI` mismatch alerts.
* **Coordinate Source Priority Hierarchy (`coordSourcePriority`):**
  * `MANUAL (100) > CLF / NTM / KML / IMPORT (85) > RUIAN (80) > CELLMAPPER (70) > GEOCODER / NOMINATIM (55) > BEACONDB (45) > OPENCELLID (40) > CELLMAPPER_LOG (25)` ensures coarse crowdsourced centroids never overwrite exact mast coordinates.
* **Fixed Portal Shortcuts & All-District Batch Resolution:**
  * Replaced the broken `/celltrack/clf/` link (which returns Apache `403 Forbidden` because directory indexing is disabled) with main-menu portal buttons for **GSMweb.cz**, **OpenCelliD (`230.csv.gz`)**, and **CellMapper**, plus **`All Sites`** / per-district **RÚIAN + BeaconDB** batch resolution in the BTS DB dialog.
* **Updated Documentation & PDF Manual (`v1.8`):**
  * Bumped downloadable bilingual vector manual to **`GNSS_Net_Logger_v1.8_Manual_CS.pdf` / `_EN.pdf`**, updated About credits, Settings descriptions, and export headers.

---

### Coordinate Precision Hierarchy & Importer Formats in v1.8

| Priority | `coordSource` | Origin / Zdroj | Typical Accuracy / Typická přesnost |
| :---: | :--- | :--- | :--- |
| **100** | `MANUAL` | User pin / manual editor in DB dialog | Exact user pin (~1–5 m) |
| **85** | `CLF`, `NTM`, `KML`, `IMPORT` | Verified CLF v3/v4, NetMonster `.ntm`, Google Earth `.kml` | Exact mast (~2–15 m) |
| **80** | `RUIAN` | Official Czech ČÚZK RÚIAN `GeocodeSOE` | Building cadastre (~2–10 m) |
| **70** | `CELLMAPPER` | CellMapper verified green eNodeB / gNodeB tower pins | Tower pin (~10–50 m) |
| **55** | `GEOCODER`, `NOMINATIM` | Android Platform Geocoder / OSM Nominatim | Street / village (~20–300 m) |
| **45** | `BEACONDB` | BeaconDB Wireless Geolocate API (`api.beacondb.net`) | Cell coverage centroid |
| **40** | `OPENCELLID` | OpenCelliD `230.csv.gz` cell dump | Coverage centroid (coarse) |
| **25** | `CELLMAPPER_LOG` | Raw CellMapper UE measurement trail points | UE measurement position |
