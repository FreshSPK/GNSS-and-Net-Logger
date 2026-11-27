# GNSS & Net Logger – Release v1.6

**Tag / Version:** `v1.6` (`versionCode = 11`)  
**Release Title:** `GNSS & Net Logger v1.6 – Spoofing & Jamming Shield, 3-Clock UTC Audit, TA-RSRP Physics, Spoofing Telemetry Logging & PDF Manual`  
**Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** [`APK/GNSS_Net_Logger_v1.6.apk.zip`](APK/GNSS_Net_Logger_v1.6.apk.zip)

---

## What's New in Version 1.6 / Co je nového ve verzi 1.6

### 🇨🇿 Česky

* **Nová záložka „Podvody“ (Anti-Spoofing, Jamming & Bezpečnostní štít):**
  * Souhrnný **Threat Risk Index (`0–100 %`)** s dílčími skóre pro **GNSS**, **RF sítě** a **Čas**, živou indikací varování ve spodní navigační liště, tlačítkem **Hloubkový sken** a chronologickým **Deníkem incidentů**.
* **Audit přesného času ze 3 zdrojů (`GNSS · NITZ · Internet NTP`):**
  * Vestavěný **SNTP v4 (UDP:123)** klient (`time.google.com`, `tik.cesnet.cz`, `pool.ntp.org`, `time.cloudflare.com`) s kompenzací RTT porovnává hardwarový UTC čas `GnssClock`, čas GNSS fixu a síťový čas NITZ (`Δ(GNSS−NTP)`, `Δ(Síť−NTP)`, `Δ(GNSS−Síť)`).
  * Sleduje skokové diskontinuity a frekvenční drift interního GNSS oscilátoru (`GnssClock.hardwareClockDiscontinuityCount`, `driftNanosPerSecond`).
* **RF Fyzika (Timing Advance vs. RSRP & poloha BTS):**
  * Kontroluje odchylku vzdálenosti z **Timing Advance** oproti reálné Haversine vzdálenosti známé **BTS** (`ΔTA`, varování `> 1 500 m`, kritické `> 3 500 m`).
  * Porovnává naměřený **RSRP** s empirickým modelem útlumu šíření pro dané TA a kmitočet (`-72 dBm @ 150 m / 1800 MHz`, `34 dB/dekáda`) — odhaluje podezřele silné blízké vysílače při `TA = 0–1` (profil IMSI Catcheru) i fyzikálně nereálný výkon při velkém TA (`residual > +28 dB`, nelegální opakovač).
  * Detekuje širokopásmové rušení pomocí rozdílu **`RSSI − RSRP > 38 dB`** + poklesu SINR/RSRQ, vynucený **2G GSM downgrade**, izolovanou buňku bez sousedů, oscilaci **TAC**, výpadky pingu při silném signálu a nesoulad **PCI/TAC** proti databázi BTS.
* **Pokročilý GNSS Anti-Spoofing & fúze senzorů:**
  * Kontroluje `Location.isMock`, kompresi **AGC** vůči **C/N₀**, podezřele jednotný výkon (`Ø C/N₀ > 43,5 dB-Hz`, `σ < 2,3 dB-Hz`) i inverzi elevace (`LowEl ≥ HighEl + 2,5 dB-Hz`), kolaps orbitálního Dopplerova rozptylu (`σ_v < 45 m/s`), vymizení pásma **L5/E5a** při silném **L1**, nesoulad pohybu s **IMU** (akcelerometr + gyroskop) a barometrem (`Δh > 40 m`), skoky polohy (`> 65 m/s`) a prostorovou kontrolu **GNSS vs. BTS (`> 45 km`)**.
* **Logování spoofingu, Room DB v6 & rozšířené exporty:**
  * Přidána volitelná položka **Detekce spoofingu & rušení** (`logSpoofing`) v dialogu *Položky* na záložce *Přehled* (včetně akustického upozornění při kritickém incidentu během záznamu).
  * Bezpečná migrace databáze **`MIGRATION_5_6`** (Room DB v6) ukládá 10 bezpečnostních polí ke každému vzorku, zobrazuje **Max. riziko** a **Počet incidentů** v *Historii* a vyváží je do **CSV, GPX 1.1 a GeoJSON** (`SecurityRiskScore`, `SecurityStatus`, `GnssRiskScore`, `RfRiskScore`, `TimeRiskScore`, `GnssMinusNtpMs`, `Sim1TaVsBtsDelta_m`, `Sim1RsrpResidual_dB`, `Sim1RssiRsrpGap_dB`, `SecurityAlertsSummary`).
* **Demo Simulátor útoků & Ilustrovaný PDF manuál:**
  * V *Demo režimu* lze jedním klepnutím spustit 5 testovacích scénářů (*GNSS Spoof*, *GNSS Jamming*, *2G IMSI Catcher*, *TA + Výkon*, *Časový drift*).
  * V dialogu pod ikonou **Info (`ⓘ`)** lze přímo do `Download/GNSS_Net_Logger/` stáhnout nebo otevřít **6stránkový ilustrovaný PDF manuál** s vektorovými diagramy v češtině i angličtině.
* **Úklid záložky RF parametry & kompatibilita Android 8–16:**
  * Odstraněn duplicitní panel *Detekce spoofingu & integrita RF sítě* ze záložky *RF parametry* (zůstala pouze křížová kontrola `PCI/TAC` u karty známé BTS) a doplněny přesné API pojistky (`API 28+` pro `ServiceState.cellBandwidths`, `API 29+` / `API 31+` pro 5G NR) s plným průchodem Android Lint i Robolectric testů.

---

### 🌐 English

* **New "Spoofing" Tab (Anti-Spoofing, Jamming & Security Shield):** Real-time **Threat Risk Index (`0–100%`)** across **GNSS**, **Cell RF**, and **Clock**, bottom-bar warning badge, **On-Demand Deep Scan**, and timestamped **Incident Log**.
* **3-Clock UTC Audit (`GNSS · Cellular NITZ · Internet NTP`):** Built-in RFC 4330 SNTP UDP:123 client (`time.google.com`, `tik.cesnet.cz`, `pool.ntp.org`, `time.cloudflare.com`) with RTT compensation compared against hardware `GnssClock` UTC, fix time, and NITZ time (`Δ(GNSS−NTP)`, `Δ(Net−NTP)`, `Δ(GNSS−Net)`), plus TCXO oscillator drift and discontinuity tracking.
* **RF Propagation Physics (`TA vs. RSRP` & `BTS Distance`):** Cross-checks Timing Advance against real Haversine BTS distance (`ΔTA`), validates measured RSRP against empirical path-loss for TA distance and carrier frequency, and flags wideband interference (`RSSI − RSRP > 38 dB` + SINR), 2G GSM downgrades, isolated SIB cells, TAC oscillations, data blackholes, and BTS DB `PCI/TAC` mismatches.
* **Deep GNSS Anti-Spoofing & Sensor Fusion:** Checks `Location.isMock`, AGC compression vs. C/N₀, uniform C/N₀ (`σ < 2.3 dB-Hz`) and elevation inversion, orbital Doppler spread collapse (`σ_v < 45 m/s`), L1-only spoofing (vanishing L5/E5a), IMU/barometer kinematic consistency (`Δh > 40 m`), teleport jumps (`> 65 m/s`), and GNSS-vs-BTS distance sanity (`> 45 km`).
* **Spoofing Telemetry Logging, Room DB v6 & Exports:** Added toggleable **Spoofing & Jamming Telemetry** (`logSpoofing`) in the *Fields* dialog with non-destructive **`MIGRATION_5_6`**, session risk/alert summaries and per-point indicators in *History*, and 10 new exported security columns across **CSV, GPX 1.1, and GeoJSON**.
* **Demo Attack Simulator & Illustrated PDF Manual:** One-tap Demo Mode verification scenarios (*GNSS Spoof*, *GNSS Jamming*, *2G IMSI Catcher*, *TA + Power*, *Time Drift*) plus a downloadable 6-page vector-illustrated PDF handbook (CS/EN) accessible via the top-right **Info (`ⓘ`)** button.
* **Cellular RF Cleanup & Android API 26–36 Hardening:** Removed the redundant *Spoofing Detection & RF Integrity* card from *Cellular RF*, added strict Android P/Q/S (`API 28/29/31+`) API guards in `CellularTelemetryEngine`, and verified all unit, Robolectric, Room migration, and Android Lint checks.

---

### Anti-Spoofing & Jamming Detection Matrix / Přehled detektorů v1.6

| Domain / Doména | Detector / Detektor | Trigger Threshold / Prahová podmínka | Severity / Závažnost |
| :--- | :--- | :--- | :--- |
| **GNSS** | Mock Location (`isMock`) | `Location.isMock == true` | Critical |
| **GNSS** | AGC Band Jamming | AGC score `≥ 35%` / `≥ 65%` or alert | Warning / Critical |
| **GNSS** | C/N₀ Uniformity & Elevation Inversion | `Ø > 43.5 dB-Hz` & `σ < 2.3 dB-Hz` / `LowEl ≥ HighEl + 2.5 dB-Hz` | Critical / Warning |
| **GNSS** | Orbital Doppler Spread | `≥ 5 sats` & `σ_v < 45 m/s` | Warning |
| **GNSS** | L1-Only Dual-Band Drop | `L5/E5a == 0` when L1 `≥ 8 sats` & `Ø > 32 dB-Hz` | Warning |
| **GNSS** | IMU & Barometer Kinematics | Stationary IMU `≥ 4 s` & GNSS `> 12 km/h` / `Δh > 40 m` / jump `> 65 m/s` | Critical / Warning |
| **GNSS + BTS** | Spatial Cross-Check | GNSS distance to known serving BTS `> 45 km` | Critical |
| **Time / Čas** | 3-Clock Drift (`GNSS · NITZ · NTP`) | `\|GNSS − NTP\| > 800 ms` / `> 2 000 ms` or `\|GNSS − NITZ\| > 800 ms` | Warning / Critical |
| **Time / Čas** | Hardware Oscillator Stability | Clock discontinuity delta `> 0` or `\|drift\| > 50 000 ns/s` | Warning |
| **Cell RF** | `ΔTA` vs. BTS Haversine | `\|TA_dist − BTS_dist\| > 1 500 m` / `> 3 500 m` | Warning / Critical |
| **Cell RF** | `TA vs. RSRP` Path-Loss Physics | `TA ≤ 1 & RSRP > -55 dBm` (isolated) or `residual > +28 dB` | Critical / Warning |
| **Cell RF** | Wideband RF Jamming | `RSSI − RSRP > 38 dB` with `SINR < 5 dB` or `RSRQ < -15 dB` | Critical |
| **Cell RF** | IMSI Catcher / Anomalies | 2G GSM downgrade, isolated cell (`RSRP > -85 dBm`), TAC oscillation (`≥ 3 / 2 min`), `PCI/TAC` DB mismatch, data blackhole | Warning / Critical |
