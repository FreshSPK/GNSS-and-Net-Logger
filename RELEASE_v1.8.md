# GNSS & Net Logger – Release v1.8

**Tag / Version:** `v1.8` (`versionCode = 14`)  
**Release Title:** `GNSS & Net Logger v1.8 – High-Precision BTS GPS Localization (ČÚZK RÚIAN, BeaconDB, NetMonster NTM / CLF / CellMapper eNB / KML / ZIP & Multi-Band Mast Propagation + IMU Stationary Filter)`  
**Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** [`APK/GNSS_Net_Logger_v1.8.apk.zip`](APK/GNSS_Net_Logger_v1.8.apk.zip)

---

## What's New in Version 1.8 / Co je nového ve verzi 1.8

### 🇨🇿 Česky

* **Přesné zaměření českých vysílačů z GSMwebu pomocí oficiálního registru ČÚZK RÚIAN (`~2–10 m`):**
  * Databáze GSMweb.cz často neobsahuje GPS souřadnice, ale přesnou poštovní adresu (např. *Brno - Královo Pole, Technická 3082/12, indoor FEKT*).
  * Nový normalizér adres (`BtsGeoUtils.buildRuianAddressQueries`) odfiltruje stožárový žargon (`stožár`, `komín`, `indoor`, `k.ú.`, `č.p.`, závorky) a pošle čistý dotaz do veřejného rozhraní **ČÚZK RÚIAN `GeocodeSOE`** (`ags.cuzk.gov.cz`), čímž získá přesnou WGS84 polohu budovy bez jakéhokoli API klíče.
* **Bezduplicitní propagace GPS souřadnic napříč pásmy a sektory jednoho stožáru (`eNodeB` + sdílená adresa):**
  * Fyzický stožár často nese více `eNodeB` pro různá frekvenční pásma (např. `800M` a `1800M`) se stejnou adresou.
  * Nový SQL průchod (`BtsDao.propagateKnownSiteCoordinates`, DB v7 s indexy na `locationName`) lokálně zkopíruje již známé souřadnice mezi všemi buňkami stejného `eNodeB` i stejné adresy stožáru. Dávkové geokódování navíc sdružuje stožáry nejprve podle adresy a drží v paměti cache vyřešených adres, takže se každý fyzický stožár dotazuje přes RÚIAN/BeaconDB nanejvýš jednou.
* **Bezklíčové dohledání venkovských stožárů & neznámých buněk přes BeaconDB (`api.beacondb.net`):**
  * Pro lesní/kopcovité vysílače bez čísla popisného i pro zcela neznámé buňky v terénu (pokud je zapnuté *Automatické geokódování BTS*) volá aplikace otevřené API **BeaconDB** (`POST https://api.beacondb.net/v1/geolocate`) včetně automatického záložního dotazu na **MOCN partnera** (`23001 ↔ 23002`), bez nutnosti registrace nebo API klíče.
* **Univerzální importér 7 formátů BTS (`.csv`, `.clf`, `.ntm`, `.kml`, `.csv.gz`, `.zip`):**
  * **NetMonster `.ntm` / `nm2300x`**, **CLF v3/v4**, **CellMapper eNodeB Tower CSV & App Signal Log**, streamovaný **KML `<Placemark>`**, **OpenCelliD `230.csv.gz`** a vícerozměrné **`.zip` archivy**.
* **Sektorové ztotožnění přes `eNodeB` kotvu (`eNB` / `MOCN eNB`) & hierarchie přesnosti (`coordSourcePriority`):**
  * `MANUAL (100) > CLF / NTM / KML / IMPORT (85) > RUIAN (80) > CELLMAPPER (70) > GEOCODER / NOMINATIM (55) > BEACONDB (45) > OPENCELLID (40) > CELLMAPPER_LOG (25)` – hrubé crowdsourcované centroidy nikdy nepřepíší přesně zaměřený stožár.
* **Opravený rozcestník portálů & dávkové dohledání pro všechny okresy:**
  * Opraven odkaz na `GSMweb.cz` (cesta `/celltrack/clf/` vracela `403 Forbidden`), přidány přímé odkazy na **OpenCelliD (`230.csv.gz`)** a **CellMapper**, a dávkové dohledání podporuje volbu **`Všechny` (All Sites)** včetně zobrazení lokálně zpropagovaných buněk.
* **Realistické limity přesného času & stabilní `GnssClock` bez falešných poplachů:**
  * Rozšířené zelené tolerance porovnání času: **`|GNSS − NTP| ≤ 2 000 ms`** (`> 2 000 ms` varování `+20` b., `> 5 000 ms` kritické `+70` b.), **`|GNSS − NITZ| ≤ 2 500 ms`** a **`|NITZ − NTP| ≤ 3 500 ms`** s projekcí stáří callbacku na společný čas `elapsedRealtime`.
  * Kontrola `GnssClock` nově vyčkává na první reálný `GnssMeasurementsEvent` (neuchovává nulu před prvním měřením), ignoruje skoky bez platného 3D fixu (`< 4` satelity při úsporném spánku modemu v budově), hlásí varování až od `>= 2` skoků během aktivního 3D fixu a při restartu relace nebo aplikace vždy nuluje výchozí stav.
* **Chytrý filtr klidového stavu pomocí IMU (Akcelerometr + Gyroskop + Magnetometr + Kroky):**
  * Při pomalé chůzi nebo stání na místě se slabým GPS signálem (`~10 m` drift) potlačí opakované body podle časovače i falešné GPS skoky na živé mapě, jakmile jsou senzory alespoň 4 sekundy v klidu (okamžitě se odblokuje při prvním pohybu). Přepínač v *Nastavení* (výchozí zapnuto pro *Pěší* / *Auto*) + indikátor **`IMU KLID • Hold`** na kartě *Přehled*.
* **Aktualizace manuálu a verzování (`v1.8`):**
  * Aktualizován dialog *O aplikaci*, interní 6stránkový ilustrovaný **PDF manuál (`GNSS_Net_Logger_v1.8_Manual_CS.pdf` / `_EN.pdf`)** a hlavičky exportů.

---

### 🌐 English

* **Building-Level Czech Tower Geocoding via Official ČÚZK RÚIAN (`~2–10 m`):**
  * `BtsGeoUtils.buildRuianAddressQueries` strips tower jargon (`stožár`, `komín`, `indoor`, `k.ú.`, `č.p.`, parentheticals) and queries **ČÚZK RÚIAN `GeocodeSOE`** (`ags.cuzk.gov.cz`, `outSR=4326`) with zero API keys required.
* **Cross-Band Multi-Sector Mast GPS Propagation (`eNodeB` + Shared Address):**
  * Co-located bands on the same physical tower often use distinct `eNodeB` IDs (e.g., `800M` and `1800M`) sharing the exact same address.
  * `BtsDao.propagateKnownSiteCoordinates` (Room DB v7 with `locationName` indices) copies resolved coordinates locally across matching `eNodeB` and address siblings, while batch geocoding groups unlocated sites by normalized address first and maintains an in-batch address cache to eliminate duplicate network lookups.
* **Keyless Rural Mast & Real-Time Fallback Lookup via BeaconDB (`api.beacondb.net`):**
  * Resolves rural masts and unmapped serving cells with automatic **CETIN MOCN** partner retry (`23001 ↔ 23002`), requiring no API keys or login.
* **Universal 7-Format Streaming BTS Importer (`.csv`, `.clf`, `.ntm`, `.kml`, `.csv.gz`, `.zip`) & `coordSourcePriority`:**
  * Supports NetMonster `.ntm`, CLF v3/v4, CellMapper eNodeB tower CSVs & signal logs, streaming KML, OpenCelliD, and `.zip` archives while enforcing coordinate precision priority (`MANUAL > CLF/NTM/KML > RUIAN > CELLMAPPER > GEOCODER > BEACONDB > OPENCELLID > CELLMAPPER_LOG`).
* **Realistic Clock Windows & False-Alarm-Free `GnssClock` Continuity:**
  * Widened nominal UTC tolerances (**`|GNSS − NTP| ≤ 2,000 ms`**, **`|GNSS − NITZ| ≤ 2,500 ms`**, **`|NITZ − NTP| ≤ 3,500 ms`**) with age-skew projection to a common `elapsedRealtime` epoch and softened `+20` warning impact.
  * `GnssClock` discontinuity tracking waits for the first real `GnssMeasurementsEvent`, ignores indoor no-fix hardware duty cycling, alerts at `>= 2` jumps during active 3D fix, and resets cleanly on session/app restart.
* **Smart Multi-Sensor IMU Stationary Logging Filter (Accel + Gyro + Mag + Steps):**
  * Prevents redundant timer records and ~10 m GPS wander while standing still in poor signal coverage (`IMU STILL • Hold` badge in Overview, toggleable in *Settings*, enabled by default for *Walking* / *Auto*).
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
