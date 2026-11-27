# GNSS & Net Logger – Release v1.7

**Tag / Version:** `v1.7` (`versionCode = 12`)  
**Release Title:** `GNSS & Net Logger v1.7 – Multi-Sensor & RF Forensics Shield (Gyro/Mag/Step Fusion, NMEA/ADR/Orbital Physics, FSPL Ceiling, Dual-SIM Cross-Check & Cellular DNS Audit)`  
**Author:** Ing. Jan Kufa, Ph.D. — Department of Radio Electronics (UREL), FEKT, Brno University of Technology (VUT Brno)  
**Repository:** [https://github.com/FreshSPK/GNSS-and-Net-Logger](https://github.com/FreshSPK/GNSS-and-Net-Logger)  
**Asset:** [`APK/GNSS_Net_Logger_v1.7.apk.zip`](APK/GNSS_Net_Logger_v1.7.apk.zip)

---

## What's New in Version 1.7 / Co je nového ve verzi 1.7

### 🇨🇿 Česky

* **Pokročilá fúze senzorů (Gyroskop, Magnetometr & Krokoměr) na záložce Podvody:**
  * **Gyroskop ΔYaw vs. kurz GNSS (`gnss_gyro_heading`):** Integruje vertikální úhlovou rychlost gyroskopu (projekce na vektor gravitace) v 4,5s posuvném okně a porovnává reálné zatáčení zařízení se změnou kurzu GNSS (varování/kritické při rozporu zatáčení `> 28°` / `> 40°`) spolu s kontrolou okolního magnetického pole `|B|` (`18–95 µT`).
  * **Detekce chůze z akcelerometru vs. rychlost GNSS (`imu_step_speed`):** Počítá kadenci kroků bez potřeby zvláštních oprávnění (`6s` okno) a odhaluje rozpor chůze proti rychlosti GNSS (`> 35 km/h` při chůzi nebo zamrzlá poloha `< 0,5 km/h` při intenzivní chůzi).
* **Hloubková forenzní analýza GNSS signálu (`GNSS`):**
  * **Surové věty NMEA vs. Android Location API (`gnss_nmea_vs_location`):** Parsuje přímo `$GNGGA` / `$GPGGA` / `$GNRMC` / `$GPRMC` z modemu a spouští kritickou výstrahu při odchylce `> 35 m` od `LocationManager` (odhaluje SW hooking polohy).
  * **Fáze nosné ADR, opačný Doppler, zamrzlé nebe & skok C/N₀ (`gnss_adr_doppler_freeze`):** Sleduje ztrátu platných fází nosné `ADR_STATE_VALID` (`0 %` při `C/N₀ > 38 dB-Hz`), rozpor znaménka Dopplerova posuvu vůči trendu elevace družic (`≥ 3` družice), zcela zamrzlou oblohu satelitů po dobu `≥ 60 s`, skok průměrného `C/N₀` v klidu (`≥ +9 dB`) i zamrzlou hodnotu přesnosti při vysokém `HDOP`.
  * **Náhlý úbytek mimochodných konstelací (`gnss_constellation_drop`):** Hlídá situaci, kdy přijímač dříve viděl Multi-GNSS (Galileo/GLONASS/BeiDou), ale najednou zbývá pouze `GPS ≥ 7` se silným `C/N₀ > 33 dB-Hz` (typické pro levné jednopásmové GPS spoofery).
* **Fyzika šíření RF, Testovací ID, Dual-SIM & Mobilní DNS:**
  * **Limit volného prostoru FSPL, testovací ID, úzká šířka pásma & CQI (`sim*_fspl_and_cqi`):** Ověřuje teoretický strop přijatého RSRP z `FSPL` (`ΔFSPL > +12 dB`), testovací/rezervované identifikátory (`PLMN 00101`/`999*`, `TAC 0/1/65534/65535`, `eNB 0/1`), podezřele úzké SDR kanály (`1,4 / 3 MHz` LTE) a nízké `CQI (1–3)` při silném RSRP.
  * **Skokový pokles Timing Advance v klidu (`sim*_imu_ta_drop`):** Detekuje náhlý propad TA (např. přiblížení mobilního IMSI Catcheru) v okamžiku, kdy IMU potvrzuje klidový stav telefonu.
  * **Křížová korelace Dual-SIM (`dual_sim_cross_check`):** Při současné aktivitě obou SIM karet detekuje cílený downgrade jedné SIM na `2G` s `TA = 0`, geografický rozestup obslužných BTS `> 40 km` i současné širokopásmové rušení obou operátorů.
  * **Audit mobilního DNS & Captive Portal (`net_integrity`):** Kontroluje přes `ConnectivityManager.getLinkProperties()` přidělené privátní/lokální DNS servery (`RFC 1918`, `CGNAT`, `ULA`) na čistě mobilním rozhraní, příznak `CAPTIVE_PORTAL` i stav `DATA_SUSPENDED`.
* **Rozšířený přehled Fyzika TA, Detailní nápovědy & Ilustrovaný PDF manuál v1.7:**
  * Sub-záložka **Fyzika TA** doplněna o řádky FSPL stropu, testovacích ID / BW / CQI, skoku TA v klidu, NMEA/ADR/Doppleru, gyroskopického `ΔYaw` a magnetometru/kroků; všechny nové kontroly mají plně rozepsaný vysvětlující pop-up dialog a aktualizovaný **PDF manuál (`GNSS_Net_Logger_v1.7_Manual_CS.pdf` / `_EN.pdf`)**.

---

### 🌐 English

* **Extended Sensor Fusion (Gyroscope, Magnetometer & Step Cadence) in Spoofing Tab:**
  * **Gyroscope ΔYaw vs. GNSS Bearing (`gnss_gyro_heading`):** Integrates gravity-projected world-vertical gyroscope yaw rate over a 4.5 s sliding window and flags divergence against GNSS course turns alongside ambient magnetic flux (`18–95 µT`) anomalies.
  * **Zero-Permission Walking Cadence vs. GNSS Velocity (`imu_step_speed`):** Detects accelerometer walking steps (`6s` window) and alerts on kinematic mismatch (`> 35 km/h` while walking, or frozen `< 0.5 km/h` GNSS while walking continuously).
* **Deep GNSS Signal Forensics (`GNSS`):**
  * **Raw NMEA vs. Android Location API (`gnss_nmea_vs_location`):** Parses live `$GNGGA` / `$GPGGA` / `$GNRMC` / `$GPRMC` modem sentences and flags OS-level location hooking when divergence exceeds `35 m`.
  * **Carrier Phase ADR, Opposite Doppler, Sky Freeze & C/N₀ Step (`gnss_adr_doppler_freeze`):** Tracks carrier phase `ADR_STATE_VALID` collapse (`0%` at `C/N₀ > 38 dB-Hz`), opposite pseudorange-rate Doppler sign vs. satellite elevation trend (`≥ 3` satellites), `≥ 60 s` frozen satellite sky geometry, sudden stationary `C/N₀` takeover steps (`≥ +9 dB`), and frozen accuracy metrics under high `HDOP`.
  * **Multi-Constellation Drop (`gnss_constellation_drop`):** Flags sudden disappearance of Galileo/GLONASS/BeiDou leaving `GPS ≥ 7` with strong `C/N₀ > 33 dB-Hz` (single-constellation GPS spoofer signature).
* **RF Propagation Ceiling, Test IDs, Dual-SIM Divergence & Cellular DNS Audit:**
  * **FSPL Ceiling, Test Identifiers, Narrow SDR BW & CQI (`sim*_fspl_and_cqi`):** Checks RSRP against BTS free-space path-loss ceiling (`ΔFSPL > +12 dB`), reserved test PLMNs/TACs/eNBs (`00101`, `TAC 0/1/65534/65535`, `eNB 0/1`), unusually narrow LTE channels (`1.4 / 3 MHz`), and poor `CQI (1–3)` paired with strong RSRP.
  * **Stationary IMU TA Drop (`sim*_imu_ta_drop`):** Alerts when Timing Advance drops abruptly while IMU sensors confirm the handset is stationary.
  * **Dual-SIM Cross-Correlation (`dual_sim_cross_check`):** Evaluates both active SIM slots simultaneously to detect targeted single-SIM `2G` + `TA = 0` downgrades, `> 40 km` BTS geolocation divergence, and dual-operator RF jamming.
  * **Cellular DNS & Captive Portal Audit (`net_integrity`):** Inspects `LinkProperties` for private RFC 1918 / CGNAT / ULA DNS servers injected on non-VPN/non-Wi-Fi cellular links, `NET_CAPABILITY_CAPTIVE_PORTAL`, and `DATA_SUSPENDED` anomalies.
* **Updated Physics Workbench, Interactive Popups & PDF Manual v1.7:**
  * Added all v1.7 telemetry rows to **Physics TA**, full localized formula popups for every new check, updated Demo Attack scenarios, and bumped the downloadable bilingual handbook to **`GNSS_Net_Logger_v1.7_Manual_CS.pdf` / `_EN.pdf`**.

---

### Anti-Spoofing & Jamming Matrix Additions in v1.7 / Nové detektory ve verzi 1.7

| Domain / Doména | Check ID | Trigger Condition / Podmínka spuštění | Severity / Závažnost |
| :--- | :--- | :--- | :--- |
| **GNSS** | `gnss_constellation_drop` | Multi-GNSS seen earlier, now `GPS ≥ 7` (`Ø C/N₀ > 33 dB-Hz`) & `non-GPS == 0` | Critical |
| **GNSS** | `gnss_nmea_vs_location` | Distance between raw `$GNGGA/$GNRMC` and `LocationManager` `> 35 m` | Critical |
| **GNSS** | `gnss_adr_doppler_freeze` | Sky frozen `≥ 60 s` (Critical) or `ADR = 0%`, `OppDop ≥ 3`, `ΔC/N₀ ≥ +9 dB`, frozen acc (Warning) | Warning / Critical |
| **GNSS + IMU** | `gnss_gyro_heading` | 4.5s GNSS turn vs. Gyro `ΔYaw` mismatch (`> 40°` Crit / `> 28°` Warn) or `\|B\| ∉ 18..95 µT` | Warning / Critical |
| **GNSS + IMU** | `imu_step_speed` | Walking detected (`6s` steps) with GNSS `> 35 km/h` or frozen `< 0.5 km/h` (`≥ 8` steps) | Warning |
| **Cell RF** | `sim*_fspl_and_cqi` | Test `PLMN/TAC/eNB` or `ΔFSPL > +12 dB` (Critical); `1.4/3 MHz` LTE BW or `CQI 1–3` (Warning) | Warning / Critical |
| **Cell RF + IMU** | `sim*_imu_ta_drop` | Stationary IMU + sudden TA drop (`TA ≤ 1` on isolated cell = Critical) | Warning / Critical |
| **Dual-SIM RF** | `dual_sim_cross_check` | Single-SIM `2G + TA=0`, BTS distance `> 40 km`, or simultaneous dual-SIM jamming | Critical |
| **Network** | `net_integrity` | Private/local DNS on cellular link (Critical); Captive Portal / `DATA_SUSPENDED` (Warning) | Warning / Critical |
