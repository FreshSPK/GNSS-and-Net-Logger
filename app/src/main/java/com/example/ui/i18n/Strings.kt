package com.example.ui.i18n

object Strings {
    fun get(key: String, lang: String): String {
        val isCs = lang == "CS"
        return when (key) {
            // Tabs
            "tab_overview" -> if (isCs) "Přehled" else "Overview"
            "tab_rf" -> if (isCs) "RF parametry" else "Cellular RF"
            "tab_gnss" -> if (isCs) "GNSS" else "GNSS"
            "tab_map" -> if (isCs) "Mapa" else "Map"
            "tab_charts" -> if (isCs) "Grafy" else "Charts"
            "tab_history" -> if (isCs) "Historie" else "History"

            // Overview
            "live_telemetry_hud" -> if (isCs) "Živý telemetrický HUD" else "Live Telemetry HUD"
            "logging_active" -> if (isCs) "ZÁZNAM BĚŽÍ" else "LOGGING ACTIVE"
            "logging_idle" -> if (isCs) "ZÁZNAM ZASTAVEN" else "LOGGING IDLE"
            "btn_start_log" -> if (isCs) "Spustit záznam" else "Start Logging"
            "btn_stop_log" -> if (isCs) "Zastavit záznam" else "Stop Logging"
            "speed" -> if (isCs) "Rychlost" else "Speed"
            "altitude" -> if (isCs) "Nadmořská výška" else "Altitude"
            "accuracy" -> if (isCs) "Přesnost" else "Accuracy"
            "fix_status" -> if (isCs) "Stav fixu" else "Fix Status"
            "satellites" -> if (isCs) "Družice" else "Satellites"
            "dual_freq_ratio" -> if (isCs) "Dual-Freq (L1/L5)" else "Dual-Freq (L1/L5)"
            "serving_cell" -> if (isCs) "Obsluhující buňka" else "Serving Cell"
            "best_neighbor" -> if (isCs) "Nejsilnější soused" else "Best Neighbor"
            "ping_latency" -> if (isCs) "Odezva (8.8.8.8)" else "Ping RTT (8.8.8.8)"
            "pressure" -> if (isCs) "Tlak vzduchu" else "Baro Pressure"

            // Cellular RF
            "sim_slot_1" -> if (isCs) "SIM Slot 1" else "SIM Slot 1"
            "sim_slot_2" -> if (isCs) "SIM Slot 2" else "SIM Slot 2"
            "neighbors_title" -> if (isCs) "Sousední buňky & Delta" else "Neighbor Cells & Delta"
            "timing_advance" -> if (isCs) "Timing Advance (TA)" else "Timing Advance (TA)"
            "cell_distance" -> if (isCs) "Odhad vzdálenosti k BTS" else "Estimated BTS Distance"
            "ss_metrics" -> if (isCs) "5G SS Metriky" else "5G SS Metrics"
            "csi_metrics" -> if (isCs) "5G CSI Metriky" else "5G CSI Metrics"

            // GNSS
            "skyplot_title" -> if (isCs) "SkyPlot (polární diagram oblohy)" else "SkyPlot (Polar View)"
            "constellation_status" -> if (isCs) "Stav konstelací" else "Constellation Status"
            "jamming_monitor" -> if (isCs) "Detekce rušení a zarušení (AGC)" else "Jamming & AGC Interference Monitor"
            "jamming_alert" -> if (isCs) "VAROVÁNÍ: Detekován pokles AGC / potenciální rušení!" else "ALERT: Sudden AGC drop / potential RF jamming detected!"
            "nominal_state" -> if (isCs) "Úroveň AGC nominální (bez anomálií)" else "AGC baseline nominal (clean RF environment)"

            // Map
            "sat_legend" -> if (isCs) "Barevné kódování trasy podle počtu družic:" else "Track color coding strictly by satellite count:"
            "map_hud_overlay" -> if (isCs) "Mapový HUD překryv" else "Map HUD Overlay"
            "center_location" -> if (isCs) "Vycentrovat polohu" else "Center Location"
            "replay_active" -> if (isCs) "Přehrávání relace z historie" else "Replaying Historical Session"
            "clear_replay" -> if (isCs) "Zpět na živou polohu" else "Exit Replay"

            // Charts
            "charts_title" -> if (isCs) "Reálné časové průběhy telemetrie" else "Real-time Telemetry Waveforms"
            "rsrp_chart" -> if (isCs) "Výkon signálu RSRP (dBm)" else "Serving RSRP (dBm)"
            "speed_chart" -> if (isCs) "Rychlost pohybu (km/h)" else "Speed (km/h)"
            "sat_chart" -> if (isCs) "Počet využitých družic" else "Satellites in Fix"
            "ping_chart" -> if (isCs) "Latence pingu (ms)" else "Socket Ping RTT (ms)"

            // History
            "history_title" -> if (isCs) "Historie měřicích relací" else "Measurement Sessions History"
            "filter_search" -> if (isCs) "Filtrovat relace..." else "Search & filter sessions..."
            "no_sessions" -> if (isCs) "Zatím žádné zaznamenané relace. Spusťte záznam na záložce Přehled." else "No sessions recorded yet. Start logging from the Overview tab."
            "export_csv" -> if (isCs) "Export CSV" else "Export CSV"
            "export_kml" -> if (isCs) "Export 3D KML" else "Export 3D KML"
            "export_geojson" -> if (isCs) "Export GeoJSON" else "Export GeoJSON"
            "show_on_map" -> if (isCs) "Zobrazit na mapě" else "View on Map"
            "delete_session" -> if (isCs) "Smazat" else "Delete"

            // Settings & About
            "settings_title" -> if (isCs) "Nastavení a Autor" else "Settings & Credits"
            "demo_mode" -> if (isCs) "Simulační / Demo režim (pohyb kolem FEKT VUT)" else "Simulation / Demo Mode (moving around FEKT VUT)"
            "trigger_mode" -> if (isCs) "Spoušť ukládání bodů" else "Log Trigger Criterion"
            "time_interval" -> if (isCs) "Časový interval" else "Time Interval"
            "dist_threshold" -> if (isCs) "Vzdálenostní krok" else "Distance Step"
            "author_title" -> if (isCs) "O aplikaci & Autorská práva" else "About & Author Attribution"
            "author_name" -> "Ing. Jan Kufa, Ph.D."
            "author_dept" -> if (isCs) "Ústav radioelektroniky (UREL), Fakulta elektrotechniky a komunikačních technologií (FEKT), Vysoké učení technické v Brně (VUT)." else "Department of Radio Electronics (UREL), Faculty of Electrical Engineering and Communication (FEKT), Brno University of Technology (VUT Brno)."
            "author_profile" -> "https://www.vut.cz/lide/jan-kufa-125512"
            "author_contact" -> "kufa@vut.cz"
            "language_switch" -> if (isCs) "Jazyk rozhraní" else "App Language"
            else -> key
        }
    }
}
