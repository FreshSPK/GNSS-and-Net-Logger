# GNSS & Net Logger – Release v1.6

**Tag / Version:** `v1.6` (`versionCode = 11`)  
**Release Title:** `GNSS & Net Logger v1.6 – Podvody / Spoofing & Jamming Security Shield, 3-Clock NTP/NITZ/GNSS Audit, TA-RSRP RF Physics & Illustrated PDF Manual`  
**Author:** Ing. Jan Kufa, Ph.D. (Department of Radio Electronics, FEKT, VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** `APK/GNSS_Net_Logger_v1.6.apk.zip`

---

## 🚀 What's New in Version 1.6 / Co je nového ve verzi 1.6

### 🇨🇿 Česky
* **Nová záložka „Podvody“ (Anti-Spoofing, Jamming & Bezpečnostní štít):**
  * Souhrnný **Threat Risk Index (0–100 %)** s dílčími indexy pro **GNSS**, **RF Sítě** a **Čas**, živou indikací varování na spodní liště a tlačítkem pro **Hloubkový sken**.
* **Srovnání přesného času (GNSS · Mobilní síť NITZ · Internet NTP):**
  * Vestavěný **SNTP v4 (UDP:123)** klient (`time.google.com`, `tik.cesnet.cz`, `pool.ntp.org`, `time.cloudflare.com`) s kompenzací RTT, porovnáním proti hardwarovému času `GnssClock` UTC, času fixu i síťovému času NITZ (`Δ(GNSS−NTP)`, `Δ(Síť−NTP)`, `Δ(GNSS−Síť)`).
  * Monitoruje skokové diskontinuity a frekvenční drift interního GNSS oscilátoru (`GnssClock.hardwareClockDiscontinuityCount`, `driftNanosPerSecond`).
* **RF Fyzika: Timing Advance (TA) vs. výkon RSRP & poloha BTS:**
  * Kontroluje rozdíl odhadu vzdálenosti z **TA** oproti skutečné Haversine vzdálenosti ke známé **BTS** ($\Delta\text{TA}$).
  * Porovnává naměřený **RSRP** s empirickým modelem útlumu šíření pro dané TA a frekvenci — odhaluje podezřele silné blízké vysílače při `TA=0–1` (profil IMSI Catcheru) i fyzikálně nepravděpodobný silný signál při velkém TA (nelegální opakovač / manipulace s TA).
  * Detekuje širokopásmové rušení kanálu pomocí energetického rozdílu **`RSSI − RSRP > 38 dB`**, degradace **SINR**, výpadků paketů při silném RF, **2G GSM downgrade**, izolované buňky bez sousedů, kmitání **TAC** a nesoulad **PCI/TAC** proti databázi BTS.
* **Pokročilý GNSS Anti-Spoofing & Fúze senzorů:**
  * Kontrola softwarového podvržení polohy (`Location.isMock`), zisku **AGC** proti poklesu **C/N0**, jednotného podezřelého výkonu ($\sigma_{C/N_0} < 2.3\text{ dB-Hz}$) a inverze elevace (nízko nad obzorem silnější než v zenitu), kolapsu orbitálního Dopplerova rozptylu, zmizení pásma **L5/E5a** při silném **L1**, nesouladu pohybu GNSS vůči **IMU (akcelerometr + gyroskop)** a **barometru**, i prostorové křížové kontroly **GNSS vs. BTS (> 45 km)**.
* **Deník incidentů & Demo Simulátor útoků:**
  * Automaticky zaznamenává bezpečnostní události s časovým razítkem a v *Demo režimu* umožňuje otestovat všech 5 typů útoků (*GNSS Spoof*, *GNSS Jamming*, *2G IMSI Catcher*, *TA + Výkon*, *Časový drift*).
* **Ilustrovaný PDF Uživatelský & technický manuál přímo v mobilu:**
  * V dialogu pod ikonkou **Info (`ⓘ`)** vpravo nahoře lze jedním klepnutím vygenerovat a stáhnout do `Download/GNSS_Net_Logger/` (nebo rovnou otevřít/sdílet) 6stránkový ilustrovaný PDF manuál s vektorovými schématy a grafy popisující detailně fungování všech modelů aplikace (v češtině i angličtině).
* **Úklid záložky Mobily (odstranění duplicity):**
  * Ze záložky *Mobily* byl odstraněn původní samostatný panel *Detekce spoofingu & integrita RF sítě*, protože veškerá detekce podvodů a rušení je nyní přehledně soustředěna na nové záložce *Podvody*.

### 🌐 English
* **New "Spoofing" Tab (Anti-Spoofing, Jamming & Security Shield):** Real-time Threat Risk Index (`0–100%`) with domain sub-scores (**GNSS**, **Cell RF**, **Clock**), bottom-bar alert badge, and On-Demand Deep Scan.
* **Multi-Source UTC Clock Audit (GNSS · Cellular NITZ · Internet NTP):** Built-in RFC 4330 SNTP UDP:123 client with RTT compensation compared against hardware `GnssClock` UTC and carrier NITZ wall-clock time to detect GNSS replay/meaconing and NITZ time spoofing.
* **RF Propagation Physics (TA vs. RSRP & BTS Distance):** Cross-checks Timing Advance against real Haversine BTS distance ($\Delta\text{TA}$), evaluates measured RSRP against empirical path-loss for TA distance, and flags wideband LTE interference via `RSSI − RSRP > 38 dB` alongside SINR, packet loss, 2G downgrade, isolated SIB cells, TAC oscillations, and BTS DB mismatches.
* **Deep GNSS Anti-Spoofing & Sensor Fusion:** Evaluates `Location.isMock`, AGC vs. C/N0 discrimination, uniform C/N0 & elevation inversion, orbital Doppler spread collapse, L1-only spoofing (vanishing L5/E5a), IMU/barometer kinematic consistency, and GNSS-vs-BTS spatial sanity (`> 45 km`).
* **Forensic Incident Timeline & Demo Attack Simulator:** Logs timestamped alerts and includes interactive Demo Mode attack scenarios.
* **Downloadable Illustrated PDF Manual (CS/EN):** Available via the top-right **Info (`ⓘ`)** icon to download into `Download/GNSS_Net_Logger/` or open/share a 6-page vector-illustrated technical handbook covering every model in the app.
* **Cellular RF Deduplication:** Removed the redundant standalone *Spoofing Detection & RF Integrity* banner from the *Cellular RF* tab now that comprehensive security monitoring lives in *Spoofing*.

---

## 📦 GitHub Push & Release Checklist

```bash
git add -A
git commit -m "Release v1.6: Podvody/Spoofing shield, 3-clock NTP/NITZ/GNSS, TA-RSRP physics, PDF manual & APK"
git tag v1.6
git push origin main --tags
```

- **GitHub Release Tag:** `v1.6`
- **GitHub Release Title:** `GNSS & Net Logger v1.6 – Podvody / Spoofing Shield, 3-Clock UTC Audit, TA-RSRP Physics & PDF Manual`
- **Release Asset to Upload:** `APK/GNSS_Net_Logger_v1.6.apk.zip` (contains `GNSS_Net_Logger_v1.6.apk`)
- **Release Notes Source:** Copy sections from `RELEASE_v1.6.md`.

