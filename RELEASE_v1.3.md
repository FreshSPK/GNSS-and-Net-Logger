# GNSS & Net Logger – Release v1.3

**Tag / Version:** `v1.3` (`versionCode = 8`)  
**Release Title:** `GNSS & Net Logger v1.3 – Battery Endurance, Honest RF Metrology, Waypoints & Streaming Export`  
**Author:** Ing. Jan Kufa, Ph.D. (Department of Radio Electronics, FEKT, VUT Brno)  
**Asset:** `APK/GNSS_Net_Logger_v1.3.apk.zip`

---

## 🚀 What's New in Version 1.3 / Co je nového ve verzi 1.3

### 🌐 English
* **Stability & Battery Endurance:** Lifecycle gating (`ON_START`/`ON_STOP`) pauses sensors/GNSS when backgrounded without active logging; crash-safe 10-sample checkpoints, `PARTIAL_WAKE_LOCK` + `START_REDELIVER_INTENT`, static GPS drift filter in Distance mode, dynamic settings reload mid-session, and full `SharedPreferences` persistence.
* **Honest RF & GNSS Metrology:** Replaced estimated values with real modem LTE CQI (API 26+), Carrier Aggregation / 5G EN-DC badges, GNSS TTFF, Geoid separation $N$ (WGS84 vs. MSL), AGC calibration reset, SkyPlot compass/elevation labels with tap-to-inspect, `+ Mark` waypoints, chronometer, odometer, and handover (`HO`) counter.
* **Map, Charts & Threading:** Dark OSM night filter, Auto-Follow FAB, SIM 1 / SIM 2 map toggle, touch-drag Chart scrubbing, dynamic Y-scaling, SINR/PDOP/AGC charts, Session Analytics summary card, History point-to-map deep links, and streaming CSV (full SIM 2 columns), multi-color 3D KML, GPX 1.1, and GeoJSON exporters.
* **APK Optimization & Power Exit:** Enabled R8 shrinking for release builds, converted `app_logo` to WebP, serialized RF polling / dedicated sensor `HandlerThread`s, and added a **Power Exit** button in the top app bar.

---

### 🇨🇿 Česky
* **Stabilita a výdrž baterie:** Lifecycle řízení (`ON_START`/`ON_STOP`) uspí senzory a GNSS na pozadí mimo aktivní záznam; ukládání checkpointů každých 10 vzorků, `PARTIAL_WAKE_LOCK` + `START_REDELIVER_INTENT`, filtr statického driftu v režimu vzdálenosti, živé změny nastavení za běhu a plná perzistence v `SharedPreferences`.
* **Reálná RF a GNSS metrologie:** Nahrazeny odhady za reálné CQI z modemu (API 26+), detekce Carrier Aggregation / 5G EN-DC, GNSS TTFF, geoidní separace $N$ (WGS84 vs. MSL), kalibrace AGC, kompasové a elevační popisky na SkyPlot radaru s klepnutím na satelit, značky `+ Značka`, stopky, ujetá vzdálenost a počítadlo handoverů (`HO`).
* **Mapa, Grafy a Plynulost UI:** Tmavý filtr OpenStreetMap, tlačítko Auto-Follow, přepínač SIM 1 / SIM 2 na mapě, dotykový odečet grafů, dynamická osa Y, grafy SINR/PDOP/AGC, souhrnná analytická karta relace, skok z Historie na mapu a streamovaný export CSV (plné sloupce SIM 2), 3D KML, GPX 1.1 a GeoJSON.
* **Optimalizace APK & Tlačítko Ukončit:** Zapnutý R8 shrinker, převod `app_logo` do WebP, serializace RF vláken a dedikované `HandlerThread`y pro senzory/GNSS a tlačítko **Ukončit** v horní liště.
