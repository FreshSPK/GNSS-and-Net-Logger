# GNSS & Net Logger – Release v1.3

**Tag / Version:** `v1.3` (`versionCode = 8`)  
**Release Title:** `GNSS & Net Logger v1.3 – Battery Endurance, Honest RF Metrology, Waypoints & Streaming Export`  
**Author:** Ing. Jan Kufa, Ph.D. (Ústav radioelektroniky FEKT VUT v Brně)  
**APK Binary:** `APK/GNSS_Net_Logger_v1.3.apk.zip`

---

## 🚀 Co je nového ve verzi 1.3 / What's New in Version 1.3

### 🇨🇿 České shrnutí (Czech)
- **Balíček 1 – Stabilita, výdrž baterie a oprava bugů:**
  - Chytrý lifecycle kontroler (`ON_START`/`ON_STOP`): pokud neprobíhá aktivní záznam, aplikace po přechodu na pozadí uspí GPS, senzory, ping i grafovou smyčku.
  - Průběžné ukládání počtu bodů a vzdálenosti relace každých 10 vzorků proti ztrátě dat při neočekávaném ukončení.
  - Kontinuální `PARTIAL_WAKE_LOCK` + `START_REDELIVER_INTENT` pro spolehlivé mnohahodinové měření se zhasnutou obrazovkou.
  - Filtr statického GPS driftu v režimu `Vzdálenost` (eliminuje falešné metry při stání s horší přesností).
  - Živá změna nastavení za běhu (interval, režim spouštění, TA násobič) bez nutnosti restartovat relaci.
  - Ukládání kompletní matice položek, režimu TA, tmavé mapy i akustických výstrah do `SharedPreferences`.
  - Oprava resetování zoomu mapy v režimu přehrávání relace (přesun do `LaunchedEffect`).
- **Balíček 2 – Profi RF/GNSS & UI rozšíření:**
  - **Bez falešných hodnot:** Odstraněno umělé CQI `13`, odhadovaná 5G SS/CSI matematika i falešné konstanty – nově reálné CQI z modemu (API 26+), detekce **Carrier Aggregation (`CA` / `5G EN-DC`)** a pomlčka `—` při nedostupnosti čidla/pingu.
  - **GNSS vylepšení:** Zobrazení doby prvního fixu (**TTFF**), geoidní separace $N$ (**WGS84 vs MSL**), tlačítko **Kalibrovat AGC**, oprava záporného L1 čítače a kompasové + elevační popisky na SkyPlot radaru s možností **klepnutí na satelit** pro zobrazení detailu.
  - **Terénní waypointy a HUD:** Tlačítko **`+ Značka`** s předvolbami (*Tunel*, *Rušení*, *Budova*, *BTS*), živé **stopky**, ujetá vzdálenost a počítadlo **handoverů (`HO`)**.
  - **Mapa a grafy:** Tmavý noční filtr OpenStreetMap, tlačítko **Auto-Follow**, přepínač SIM 1 / SIM 2 na přehrávané trase, klikatelné značky na mapě, dotykový odečet hodnot na grafech, dynamické škálování osy Y (60+ satelitů) a nové grafy **SINR**, **PDOP** a **AGC rušení**.
  - **Historie & Export:** Souhrnná analytická karta relace, tlačítko **Grafy** pro historickou relaci, plné sloupce **SIM 2** a `WaypointNote` v CSV, vícebarevné 3D **KML**, **GPX 1.1** a streamovaný zápis do úložiště bez zátěže RAM.
  - **Profily měření a výstrahy:** Rychlé presety nastavení (*Chůze*, *Auto*, *Stativ*), volitelné akustické pípnutí při rušení AGC nebo 2G downgrade a zkratka do optimalizace baterie.
  - **Agregovaná šířka pásma & Sousedé:** Čtení 5G NR / **Carrier Aggregation** celkové šířky pásma (`Celkem CA: XX MHz` z `PhysicalChannelConfig` & `ServiceState.cellBandwidths`), striktní oddělení sousedních buněk pro SIM 1 / SIM 2 bez přejímání cizích dat a filtr sousedů **`Vše` | `4G LTE` | `5G NR`**.
  - **Živá barevná mapa & Skok z Historie:** Průběžný sběr živé trasy na pozadí od spuštění aplikace napříč záložkami, sjednocené barevné vykreslování živé i přehrávané trasy (RSRP `S1`/`S2`, Rychlost, Satelity, PDOP) a možnost otevřít libovolný bod z **Historie** přímo vycentrovaný na **Mapě**.
  - **Hladký běh bez záseků UI:** Odstranění kolize vláken v `CellularTelemetryEngine` (`limitedParallelism(1)`), přesun GNSS/NMEA a senzorových callbacků na dedikovaná vlákna (`HandlerThread`) a bezpečné uvolňování OSMDroid mapy přes `onRelease`.
- **Balíček 3 – Optimalizace APK & Ukončení aplikace:**
  - Zapnutý R8 shrinker (`isMinifyEnabled = true`, `isShrinkResources = true`), pročištěné nepoužívané pluginy a binární podklady převedeny do WebP.
  - **Tlačítko Ukončit (`PowerSettingsNew`)** v horní liště aplikace pro okamžité čisté vypnutí všech služeb, čidel i procesu aplikace (s bezpečnostním dialogem při probíhajícím záznamu).

---

### 🌐 English Summary
- **Package 1 – Stability & Battery Endurance:**
  - Smart lifecycle gating (`ON_START`/`ON_STOP`) pauses GNSS, cell polling, sensors, and socket ping when backgrounded without active recording.
  - Crash-safe incremental session checkpoints every 10 records, continuous WakeLock with `START_REDELIVER_INTENT`, static GPS drift filter in Distance mode, dynamic settings reload mid-session, full preferences persistence, and fixed replay map zoom reset bug.
- **Package 2 – Pro RF/GNSS & UI Extensions:**
  - Honest telemetry with real modem LTE CQI (API 26+), Carrier Aggregation / 5G EN-DC badges, GNSS TTFF timer, Geoid separation $N$ (WGS84 vs MSL), AGC baseline calibration, compass/elevation SkyPlot labels with tap-to-inspect satellites, `+ Mark` waypoints, live chronometer + odometer + handover (`HO`) counter, Dark Night OSM filter, Auto-Follow FAB, touch-drag Chart scrubbing, dynamic Y-scaling, SINR/PDOP/AGC charts, Session Analytics summary card, and streaming CSV (full SIM 2 columns), multi-color KML, GPX 1.1, and GeoJSON exporters.
- **Package 3 – APK Optimization, Thread Hardening & Power Exit:**
  - Enabled R8 code & resource shrinking for release builds, cleaned unused Gradle plugins/drawables, converted `app_logo` to WebP, and added a dedicated **Power Exit** button in the top app bar.
  - Eliminated UI jank and monitor contention via serialized RF dispatcher (`limitedParallelism(1)`), dedicated background `HandlerThread`s for GNSS/NMEA and environment sensors, `AndroidView.onRelease` OSMDroid cleanup, unified live/replay colorized map trails with continuous background collection, aggregated CA bandwidth readout, LTE/5G neighbor filter chips, and History point-to-map deep links.
