# GNSS & Net Logger (Release 1)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%E2%80%9335)-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-FFA000.svg?style=flat)](https://developer.android.com/training/data-storage/room)
[![University](https://img.shields.io/badge/VUT-FEKT%20Brno-E4002B.svg?style=flat)](https://www.fekt.vut.cz/)

Pokročilá mobilní aplikace pro **měření, analýzu a záznam GNSS satelitní telemetrie a rádiových parametrů mobilních sítí (LTE 4G / NR 5G)**. Aplikace je navržena pro studenty, inženýry, radioamatéry a výzkumníky zabývající se měřením pokrytí radiových sítí a přesností satelitní navigace.

**Autor projektu:** Jan Kufa  
**Instituce:** Vysoké učení technické v Brně (VUT), Fakulta elektrotechniky a komunikačních technologií (FEKT)  
**Verze:** Release 1.0 (Listopad 2026)  
**Instalační balíček:** Složka `APK/` v editoru AI Studia (`APK/GNSS_Net_Logger_Release_1.apk`) nebo sekce **Releases** na GitHubu.

---

## 📱 Hlavní funkcionality

### 1. Satelitní GNSS telemetrie (Záložka Satelity)
* **Podpora všech světových konstelací:** GPS (USA), Galileo (Evropa), GLONASS (Rusko), BeiDou (Čína), QZSS (Japonsko) a SBAS/EGNOS.
* **Dvoufrekvenční příjem (L1 vs. L5 / E5a):** Automatická detekce nosné frekvence každého satelitu. Rozlišení standardního pásma L1/E1 (1575,42 MHz) a moderního vysokopřesného pásma L5/E5a (1176,45 MHz), které minimalizuje chybu způsobenou ionosférickým zpožděním.
* **Faktory degradace přesnosti (DOP):** Přesný výpočet a zobrazení HDOP (horizontální), VDOP (vertikální) a PDOP (prostorový 3D). Hodnoty pod 1,5 indikují ideální geometrické rozmístění satelitů na obloze.
* **Skyplot radar:** Interaktivní polární zobrazení polohy satelitů podle azimutu (0–360°) a elevace (0–90°) s barevným odlišením pásem L1 a L5 a poměru signál/šum ($C/N_0$ v dB-Hz).
* **Rychlost a směr:** Přesný výpočet okamžité rychlosti v km/h včetně přesnosti, azimutu pohybu (bearing) a nadmořské výšky.

---

### 2. Rádiové parametry mobilního operátora & BTS (Záložka Mobily)
* **Identifikace základnové stanice BTS:**
  * **eNodeB ID & Sektor ID:** Přesný rozpad Cell Identity (CI):
    $$\text{eNodeB ID} = \lfloor \text{CI} / 256 \rfloor, \quad \text{Sektor ID} = \text{CI} \pmod{256}$$
  * TAC (Tracking Area Code), PCI (Physical Cell ID), PLMN kód sítě (MCC + MNC).
* **Timing Advance (TA) & Vzdálenost od vysílače:**
  * Každý krok Timing Advance odpovídá zpoždění šíření rádiové vlny na vzdálenost přibližně **78,12 metrů**. Aplikace v reálném čase počítá odhadovanou vzdálenost zařízení od BTS věže.
* **RF signálové metriky:**
  * **RSRP (Reference Signal Received Power):** Výkon referenčního signálu v dBm (-140 až -44 dBm) s barevnou indikací kvality.
  * **RSRQ (Reference Signal Received Quality):** Poměr kvality a vytížení buňky v dB (-20 až -3 dB).
  * **SINR (Signal to Interference plus Noise Ratio):** Čistota signálu vůči šumu a interferencím v dB s vestavěným filtrem anomálií a nesmyslných hodnot (> 100 dB).
  * **RSSI & CQI:** Indikátor síly signálu a Channel Quality Indicator pro adaptivní modulaci.
* **Frekvence a pásma:**
  * Výpočet přesných nosných frekvencí Downlink i Uplink v MHz odvozených z čísel kanálů **EARFCN** (LTE) a **NR-ARFCN** (5G).
* **Sousední buňky & Handover:**
  * Přehled okolních buněk s výpočtem $\Delta\text{RSRP}$. Pokud $\Delta\text{RSRP} \ge -3\text{ dB}$, buňka je indikována jako bezprostřední kandidát na Handover.
* **Podpora Dual SIM:** Přehled aktivních SIM slotů, názvů operátorů a přiřazení datového připojení.

---

### 3. Interaktivní mapa (Záložka Mapa)
* Mapové podklady **OpenStreetMap** vykreslované na hardwarovém plátnu (Canvas) bez nutnosti proprietárních Google Maps API klíčů.
* **Barevná vizualizace trasy (barevné prahy):**
  * **RSRP signál operátora:** Zelená ($> -80\text{ dBm}$), světle zelená, jantarová, červená ($< -105\text{ dBm}$).
  * **Rychlost pohybu v km/h:** Barevná škála od modré ($< 3\text{ km/h}$) po červenou ($> 50\text{ km/h}$).
  * **Počet viditelných satelitů:**
    * **$< 5$ satelitů:** Červená (`#EF4444`) – slabá geometrie
    * **$5$ až $10$ satelitů:** Žlutá (`#EAB308`) – základní fix
    * **$11$ až $25$ satelitů:** Zelená (`#10B981`) – výborný fix
    * **$> 25$ satelitů:** Modrá (`#3B82F6`) – špičkový vícesystémový fix
* Klepnutím na libovolný bod na mapě se otevře podrobný inspekční detail vzorku.

---

### 4. Časové průběhy v reálném čase (Záložka Grafy)
* Interaktivní grafy časového průběhu vykreslované přímo z paměti pro:
  * RSRP signál základnové stanice v čase
  * Rychlost pohybu (km/h)
  * Počet satelitů v fixu a v dosahu
* Zobrazení statistických ukazatelů: **Minimum, Průměr, Maximum**.

---

### 5. Běh na pozadí & Trvalá notifikace (Foreground Service)
* **Foreground Service s typem `location`:** Zajišťuje nepřetržitý sběr dat i při zhasnutém displeji telefonu nebo přepnutí do jiné aplikace.
* **Zámek procesoru (WakeLock):** `PowerManager.PARTIAL_WAKE_LOCK` brání operačnímu systému Android a režimu Doze v ukončení či usínání aplikace.
* **Trvalá interaktivní notifikace:**
  * Zobrazuje v reálném čase uplynulý čas, počet vzorků, rychlost, počet satelitů a sílu signálu RSRP.
  * Obsahuje akční tlačítka **„Pozastavit / Pokračovat“** a **„Zastavit“** pro ovládání přímo ze zamčené obrazovky.

---

### 6. Ukládání dat, CSV Export a Cloud
* **100% lokální fungování bez přihlašování:** Všechna data se ukládají lokálně do SQLite/Room databáze na zařízení.
* **Export do CSV:** Automatický export do systémové složky `Dokumenty/GNSS_Net_Logs/` s možností sdílení (e-mail, cloud, Bluetooth).
* **Volitelná záloha do Google Firestore:** Možnost volitelného přihlášení přes Google účet s automatickou synchronizací relací.

---

## 🔒 Bezpečnost & Google Play Protect

Při instalaci staženého APK souboru mimo obchod Google Play (sideloading) může systém Android zobrazit hlášení **Google Play Protect**: *„Neznámý vývojář“*.

### Proč se hlášení zobrazuje:
Jedná se o standardní bezpečnostní upozornění Androidu u každé aplikace, která je podepsána samostatným vývojářským klíčem a není distribuována přes Google Play Store.

### Jak nainstalovat:
1. V okně Play Protect klepněte na **„Více podrobností“** (More details).
2. Zvolte **„Přesto nainstalovat“** (Install anyway).

### Proč aplikace nepotřebuje `READ_PHONE_STATE`:
* Android řadí fyzické informace o vysílačích BTS (eNodeB, PCI, TAC, RSRP, SINR) mezi **geolokační telemetrii**, protože umožňují triangulaci polohy zařízení. Všechna tato rádiová data jsou vázána na oprávnění **`ACCESS_FINE_LOCATION`**.
* Oprávnění `READ_PHONE_STATE` (které Play Protect často penalizuje) bylo z aplikace **zcela odstraněno**, čímž je chráněno soukromí uživatele (žádný přístup k telefonním hovorům ani IMEI).

---

## 📁 Struktura projektu

```text
├── APK/                               # Instalační balíčky pro telefon
│   ├── GNSS_Net_Logger_Release_1.apk  # Aktuální stabilní Release 1.0 (29 MB)
│   └── GNSS_Net_Logger_debug.apk      # Debug sestavení
├── app/
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt            # Hlavní aktivita a navigační lišta (6 záložek)
│   │   ├── data/
│   │   │   ├── db/                    # Room Database (AppDatabase, Daos, Entities)
│   │   │   ├── export/                # CsvExporter (export do CSV)
│   │   │   ├── firestore/             # Firebase Firestore repozitář
│   │   │   └── model/                 # Datové modely (TelemetrySnapshot, GnssData, CellularData)
│   │   ├── telemetry/
│   │   │   ├── CellBandHelper.kt      # Výpočty LTE/5G pásem, nosných frekvencí, eNodeB a GNSS
│   │   │   ├── TelemetryCollector.kt  # Sběr dat z LocationManager, GnssStatus, NMEA a Telephony
│   │   │   └── TelemetryLoggingService.kt # Foreground Service s trvalou notifikací a WakeLockem
│   │   └── ui/
│   │       ├── MainViewModel.kt       # ViewModel s obousměrnou synchronizací služby
│   │       ├── cellular/              # Obrazovka Mobily (BTS, eNodeB, RF signály, Dual SIM)
│   │       ├── charts/                # Obrazovka Grafy (interaktivní časové řady)
│   │       ├── components/            # AboutDialog (nápověda, autor, Play Protect popis)
│   │       ├── dashboard/             # Obrazovka Měření (živé tachometry, ovládání)
│   │       ├── logs/                  # Obrazovka Historie (správa relací, CSV export)
│   │       ├── map/                   # Obrazovka Mapa (OSM plátno s barevnými prahy)
│   │       └── satellites/            # Obrazovka Satelity (Skyplot radar, L1/L5 pásma, DOP)
│   └── src/main/res/                  # Vektorové ikony, řetězce a styly
├── build.gradle.kts                   # Konfigurace sestavení
├── metadata.json                      # Metadata projektu pro platformu
└── README.md                          # Dokumentace projektu
```

---

## 🛠️ Použité technologie & Knihovny

* **Jazyk:** Kotlin 2.0+ (100% Kotlin DSL)
* **Uživatelské rozhraní:** Jetpack Compose, Material Design 3 (M3)
* **Architektura:** MVVM (Model-View-ViewModel), StateFlow, Coroutines
* **Databáze:** Android Jetpack Room (SQLite)
* **Běh na pozadí:** Android Foreground Service s `FOREGROUND_SERVICE_LOCATION` a `PowerManager.WakeLock`
* **Satelitní a rádiový subsystém:**
  * `android.location.LocationManager`
  * `android.location.GnssStatus.Callback`
  * `android.location.OnNmeaMessageListener`
  * `android.telephony.TelephonyManager` & `TelephonyCallback`
* **Cloud & Auth:** Firebase Firestore, Jetpack Credential Manager (volitelné Google přihlášení)

---

## 🚀 Jak aplikaci nainstalovat na telefon

1. Stáhněte soubor **`GNSS_Net_Logger_Release_1.apk`** ze složky `APK/` v kořenovém adresáři projektu.
2. Zkopírujte APK soubor do telefonu (přes USB kabel, Google Drive, e-mail nebo prohlížeč).
3. V telefonu otevřete stažený soubor a povolte instalaci z neznámých zdrojů (pokud budete vyzváni).
4. Při zobrazení dialogu Google Play Protect zvolte **„Více podrobností“** -> **„Přesto nainstalovat“**.
5. Po spuštění udělte oprávnění k poloze (**Přesná poloha / While using the app**) a oznámením pro zobrazení trvalé notifikace při běhu na pozadí.

---

## 👨‍💻 Autor

**Jan Kufa**  
Vysoké učení technické v Brně (VUT)  
Fakulta elektrotechniky a komunikačních technologií (FEKT)  
E-mail: `kufa@vutbr.cz`
