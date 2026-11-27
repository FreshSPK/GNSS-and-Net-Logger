package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.R
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

data class PdfManualSaveResult(
    val success: Boolean,
    val displayPath: String = "",
    val mediaStoreUri: Uri? = null,
    val cacheFile: File? = null,
    val errorMessage: String? = null
)

object UserManualPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 34f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Document color palette (clean printable engineering theme)
    private val COLOR_DARK_NAVY = Color.parseColor("#0F172A")
    private val COLOR_SLATE = Color.parseColor("#1E293B")
    private val COLOR_BODY = Color.parseColor("#1F2937")
    private val COLOR_MUTED = Color.parseColor("#475569")
    private val COLOR_LIGHT_BG = Color.parseColor("#F8FAFC")
    private val COLOR_CARD_BORDER = Color.parseColor("#CBD5E1")
    private val COLOR_CYAN = Color.parseColor("#0284C7")
    private val COLOR_CYAN_BG = Color.parseColor("#E0F2FE")
    private val COLOR_EMERALD = Color.parseColor("#059669")
    private val COLOR_EMERALD_BG = Color.parseColor("#DCFCE7")
    private val COLOR_AMBER = Color.parseColor("#D97706")
    private val COLOR_AMBER_BG = Color.parseColor("#FEF3C7")
    private val COLOR_CRIMSON = Color.parseColor("#DC2626")
    private val COLOR_CRIMSON_BG = Color.parseColor("#FEE2E2")
    private val COLOR_PURPLE = Color.parseColor("#7C3AED")
    private val COLOR_PURPLE_BG = Color.parseColor("#EDE9FE")
    private val COLOR_VUT_RED = Color.parseColor("#E4002B")

    fun manualFilename(isCs: Boolean): String =
        if (isCs) "GNSS_Net_Logger_v1.8_Manual_CS.pdf" else "GNSS_Net_Logger_v1.8_Manual_EN.pdf"

    fun createShareableOrViewableManualFile(context: Context, lang: String): File {
        val isCs = lang != "EN"
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportDir, manualFilename(isCs))
        FileOutputStream(file).use { fos ->
            writeManualToStream(context, isCs, fos)
        }
        return file
    }

    fun saveManualToDownloads(context: Context, lang: String): PdfManualSaveResult {
        val isCs = lang != "EN"
        val filename = manualFilename(isCs)
        return try {
            val cacheFile = createShareableOrViewableManualFile(context, lang)
            var savedPath = ""
            var mediaUri: Uri? = null
            var saved = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GNSS_Net_Logger")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        cacheFile.inputStream().use { input -> input.copyTo(os) }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    saved = true
                    mediaUri = uri
                    savedPath = "Download/GNSS_Net_Logger/$filename"
                }
            }

            if (!saved) {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "GNSS_Net_Logger").apply { if (!exists()) mkdirs() }
                val targetFile = File(appFolder, filename)
                cacheFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { out -> input.copyTo(out) }
                }
                saved = true
                savedPath = targetFile.absolutePath
            }

            PdfManualSaveResult(
                success = saved,
                displayPath = savedPath,
                mediaStoreUri = mediaUri,
                cacheFile = cacheFile
            )
        } catch (e: Exception) {
            PdfManualSaveResult(
                success = false,
                errorMessage = e.message ?: "PDF generation error"
            )
        }
    }

    fun writeManualToStream(context: Context, isCs: Boolean, outputStream: OutputStream) {
        val doc = PdfDocument()
        try {
            val totalPages = 6
            drawPage1CoverAndArchitecture(context, doc, isCs, totalPages)
            drawPage2GnssModels(doc, isCs, totalPages)
            drawPage3CellularAndBtsModels(doc, isCs, totalPages)
            drawPage4GnssAntiSpoofing(doc, isCs, totalPages)
            drawPage5RfPhysicsAndClocks(doc, isCs, totalPages)
            drawPage6WorkflowsExportsAndDemo(doc, isCs, totalPages)
            doc.writeTo(outputStream)
            outputStream.flush()
        } finally {
            runCatching { doc.close() }
        }
    }

    // -------------------------------------------------------------------------
    // PAGE 1: Cover, Overview, Architecture Block Diagram & 7-Tab Matrix
    // -------------------------------------------------------------------------
    private fun drawPage1CoverAndArchitecture(
        context: Context,
        doc: PdfDocument,
        isCs: Boolean,
        totalPages: Int
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas
        c.drawColor(Color.WHITE)

        // Hero Banner
        val bannerRect = RectF(0f, 0f, PAGE_WIDTH.toFloat(), 132f)
        c.drawRect(bannerRect, fillPaint(COLOR_DARK_NAVY))
        c.drawRect(0f, 128f, PAGE_WIDTH.toFloat(), 133f, fillPaint(COLOR_VUT_RED))

        // App Logo inside banner (safe decode)
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)?.let { bmp ->
                val dest = RectF(MARGIN, 24f, MARGIN + 68f, 92f)
                c.drawBitmap(bmp, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }

        val textLeft = MARGIN + 82f
        val titlePaint = textPaint(19f, Color.WHITE, bold = true)
        c.drawText("GNSS & Net Logger v1.8", textLeft, 44f, titlePaint)

        val subtitlePaint = textPaint(11.5f, Color.parseColor("#38BDF8"), bold = true)
        c.drawText(
            if (isCs) "Uživatelský manuál & technický popis modelů měření"
            else "User Manual & Technical Reference of Measurement Models",
            textLeft,
            63f,
            subtitlePaint
        )

        val authorPaint = textPaint(9.2f, Color.parseColor("#CBD5E1"))
        c.drawText("Ing. Jan Kufa, Ph.D. • ÚREL FEKT VUT v Brně (Brno University of Technology)", textLeft, 82f, authorPaint)
        c.drawText("GitHub: github.com/FreshSPK/GNSS-and-Net-Logger  •  kufa@vut.cz", textLeft, 96f, authorPaint)
        c.drawText(
            if (isCs) "100 % lokální architektura • Bez cloudu • Bez API klíčů"
            else "100% On-Device Architecture • Zero Cloud • Zero API Keys",
            textLeft,
            112f,
            textPaint(8.8f, Color.parseColor("#34D399"), bold = true)
        )

        var y = 148f
        y = drawSectionHeading(
            c, y,
            if (isCs) "1. Filozofie aplikace a systémová architektura" else "1. Application Philosophy & System Architecture",
            COLOR_CYAN
        )

        y = drawParagraph(
            c, y,
            if (isCs) {
                "GNSS & Net Logger je terénní měřicí nástroj pro RF drive-testing, vícekonstelační diagnostiku GNSS a aktivní detekci podvržení signálu (spoofing/jamming). Veškerá data jsou čtena přímo z hardwarových rozhraní Androidu a ukládána lokálně v databázi Room (SQLite) bez závislosti na externích serverech či placených API."
            } else {
                "GNSS & Net Logger is a field engineering instrument for cellular RF drive-testing, multi-constellation GNSS diagnostics, and active anti-spoofing / anti-jamming forensics. All telemetry is sampled directly from Android hardware HALs and stored strictly on-device in Room (SQLite) with zero external API keys."
            },
            fontSize = 9.3f
        )
        y += 6f

        // Figure 1: Architecture Block Diagram
        y = drawArchitectureDiagram(c, y, isCs)
        y += 10f

        y = drawSectionHeading(
            c, y,
            if (isCs) "2. Přehled 7 hlavních záložek aplikace" else "2. Overview of the 7 Application Tabs",
            COLOR_DARK_NAVY
        )

        val tabs = if (isCs) {
            listOf(
                "1. Měření" to "Centrální HUD: start/pauza/stop logování na pozadí, stopky, ujetá dráha, počítadla handoverů HO SIM1/SIM2, tlačítko „Značka“ pro terénní waypointy a filtr ukládaných položek.",
                "2. Mobily" to "Dual-SIM RF analyzátor (S1 / S2 / Obě SIM): RSRP, RSRQ, RSSI, SINR, CQI, 5G NSA/SA (SS/CSI), rozklad Carrier Aggregation, párování na DB BTS ČR (OpenCelliD/GSMweb/MOCN) a sousední buňky.",
                "3. Satelity" to "Multi-konstelační L1+L5/E5a přijímač, interaktivní SkyPlot radar (unikátní družice i signály), C/N0 grafy, PDOP/HDOP/VDOP, geoid N, Dopplerův posuv, AGC rušení a živý tok NMEA.",
                "4. Mapa" to "OpenStreetMap (tmavý/světlý režim), vrstva známých BTS ČR, režim „Jen BTS“, přímá RF linka k obslužným BTS a barevná trasa dle RSRP, rychlosti, počtu satelitů nebo PDOP.",
                "5. Grafy" to "Dotykové křivky v reálném čase i pro uložené relace: duální RSRP/SINR, rychlost, použité & viditelné satelity, PDOP, AGC skóre, Ping RTT, akcelerometr, gyroskop, kompas a teplota baterie.",
                "6. Podvody" to "Bezpečnostní štít (v1.7): Threat Risk Index, 3× čas (GNSS/NITZ/NTP), fyzika TA vs. RSRP a FSPL strop, Dual-SIM křížová kontrola, fúze IMU/gyro/magnetometru/kroků, ΔNMEA, ADR & Demo útoky.",
                "7. Historie" to "Analytika uložených relací, bodový prohlížeč od #1 se skokem na Mapu a streamované exporty (CSV, 3D KML, GPX 1.1, GeoJSON) do Downloads nebo systémového sdílení."
            )
        } else {
            listOf(
                "1. Overview" to "Telemetry cockpit: background foreground-service recording, chronometer, odometer, separate SIM 1 / SIM 2 handover counters, quick terrain waypoints, and selective field toggles.",
                "2. Cellular RF" to "Dual-SIM analyzer (S1 / S2 / Both): RSRP, RSRQ, RSSI, SINR, CQI, 5G NSA/SA (SS/CSI), PCC/SCC/SCG Carrier Aggregation, Czech BTS matching (OpenCelliD/GSMweb/MOCN), and neighbor cells.",
                "3. Satellites" to "Multi-constellation L1+L5/E5a monitor, tap-to-inspect SkyPlot radar (physical SVs vs. signals), C/N0 bars, HDOP/VDOP/PDOP, Geoid N, Doppler shift, AGC jamming score, and NMEA stream.",
                "4. Map" to "OpenStreetMap (dark/light), Czech BTS tower layer, BTS-only browse mode, dashed serving RF link lines, and color-coded tracks by RSRP, Speed, Satellites, or PDOP (live & replay).",
                "5. Charts" to "Touch-scrubbing waveforms (live & historical): dual-SIM RSRP/SINR, speed, used & visible satellites, PDOP, AGC jamming, Ping RTT, accelerometer, gyroscope, compass, and battery temp.",
                "6. Spoofing" to "Security Shield (v1.7): Threat Risk Index, 3-clock UTC audit, TA vs. RSRP & FSPL ceiling, Dual-SIM cross-check, IMU/gyro/mag/step fusion, ΔNMEA, ADR phase, and Demo attacks.",
                "7. History" to "Session analytics, point-by-point inspector starting from #1 with jump-to-Map, and memory-safe streaming exports (CSV, 3D KML, GPX 1.1, GeoJSON) directly to Downloads."
            )
        }

        for ((name, desc) in tabs) {
            y = drawKeyValueRowBox(c, y, name, desc, COLOR_LIGHT_BG, COLOR_CYAN)
            y += 4f
        }

        drawPageFooter(c, 1, totalPages, isCs)
        doc.finishPage(page)
    }

    private fun drawArchitectureDiagram(c: Canvas, startY: Float, isCs: Boolean): Float {
        val boxH = 124f
        val bgRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(bgRect, 8f, 8f, fillPaint(COLOR_LIGHT_BG))
        c.drawRoundRect(bgRect, 8f, 8f, strokePaint(COLOR_CARD_BORDER, 1f))

        c.drawText(
            if (isCs) "Obr. 1: Blokové schéma zpracování telemetrie a bezpečnostních modelů v reálném čase"
            else "Fig. 1: Real-Time Telemetry & Security Pipeline Block Diagram",
            MARGIN + 10f,
            startY + 14f,
            textPaint(8.5f, COLOR_SLATE, bold = true)
        )

        val colW = (CONTENT_WIDTH - 56f) / 3f
        val col1X = MARGIN + 10f
        val col2X = col1X + colW + 18f
        val col3X = col2X + colW + 18f

        // Left column: Hardware sources
        drawDiagramBox(c, col1X, startY + 24f, colW, 26f, COLOR_CYAN_BG, COLOR_CYAN,
            if (isCs) "GNSS + GnssClock\n(L1/L5, C/N0, AGC, Doppler)" else "GNSS + GnssClock\n(L1/L5, C/N0, AGC, Doppler)")
        drawDiagramBox(c, col1X, startY + 55f, colW, 26f, COLOR_AMBER_BG, COLOR_AMBER,
            if (isCs) "Dual-SIM Telephony\n(RSRP, TA, CA, Sousedé)" else "Dual-SIM Telephony\n(RSRP, TA, CA, Neighbors)")
        drawDiagramBox(c, col1X, startY + 86f, colW, 26f, COLOR_PURPLE_BG, COLOR_PURPLE,
            if (isCs) "IMU / Baro / SNTP\n(UDP:123 NTP, Ping, Baterie)" else "IMU / Baro / SNTP\n(UDP:123 NTP, Ping, Battery)")

        // Arrows Col 1 -> Col 2
        drawArrow(c, col1X + colW + 2f, startY + 37f, col2X - 2f, startY + 55f, COLOR_MUTED)
        drawArrow(c, col1X + colW + 2f, startY + 68f, col2X - 2f, startY + 68f, COLOR_MUTED)
        drawArrow(c, col1X + colW + 2f, startY + 99f, col2X - 2f, startY + 81f, COLOR_MUTED)

        // Middle column: Core Engines
        drawDiagramBox(c, col2X, startY + 26f, colW, 38f, COLOR_DARK_NAVY, COLOR_DARK_NAVY,
            if (isCs) "BtsRepository + Room DB\nPárování eNodeB/MOCN & TA" else "BtsRepository + Room DB\neNodeB/MOCN & TA Matching",
            textColor = Color.WHITE)
        drawDiagramBox(c, col2X, startY + 70f, colW, 42f, COLOR_CRIMSON_BG, COLOR_CRIMSON,
            if (isCs) "SecurityAnalyzer (1 Hz)\nFyzika TA, 3× Čas, GNSS Spoof" else "SecurityAnalyzer (1 Hz)\nTA Physics, 3× Clock, Spoofing")

        // Arrows Col 2 -> Col 3
        drawArrow(c, col2X + colW + 2f, startY + 45f, col3X - 2f, startY + 45f, COLOR_MUTED)
        drawArrow(c, col2X + colW + 2f, startY + 91f, col3X - 2f, startY + 91f, COLOR_MUTED)

        // Right column: Outputs
        drawDiagramBox(c, col3X, startY + 24f, colW, 26f, COLOR_EMERALD_BG, COLOR_EMERALD,
            if (isCs) "7× Compose UI záložek\nŽivé HUD, SkyPlot, Podvody" else "7× Compose UI Tabs\nLive HUD, SkyPlot, Spoofing")
        drawDiagramBox(c, col3X, startY + 55f, colW, 26f, COLOR_CYAN_BG, COLOR_CYAN,
            if (isCs) "Foreground Service\nRoom SQLite v6 záznamy" else "Foreground Service\nRoom SQLite v6 Recording")
        drawDiagramBox(c, col3X, startY + 86f, colW, 26f, COLOR_AMBER_BG, COLOR_AMBER,
            if (isCs) "Streamované Exporty\nCSV, 3D KML, GPX, GeoJSON" else "Streaming Exports\nCSV, 3D KML, GPX, GeoJSON")

        return startY + boxH
    }

    // -------------------------------------------------------------------------
    // PAGE 2: GNSS Models, Dual-Band L1+L5/E5a, DOP & SkyPlot Illustration
    // -------------------------------------------------------------------------
    private fun drawPage2GnssModels(doc: PdfDocument, isCs: Boolean, totalPages: Int) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 2).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawRunningHeader(c, if (isCs) "KAPITOLA 1 • MODELY GNSS, DUAL-BAND & SKYPLOT" else "CHAPTER 1 • GNSS MODELS, DUAL-BAND & SKYPLOT")

        var y = 52f
        y = drawSectionHeading(c, y,
            if (isCs) "Jak funguje příjem GNSS a výpočet polohy v mobilním telefonu" else "How Smartphone GNSS & Positioning Models Work",
            COLOR_CYAN
        )

        y = drawParagraph(c, y,
            if (isCs) {
                "Android rozlišuje dvě vrstvy GNSS telemetrie: vypočtený fixační výstup z LocationManager (souřadnice, rychlost, přesnost 68 % CEP, NMEA věty) a nízkoúrovňová měření z GnssStatus a GnssMeasurementsEvent (surové pseudovzdálenosti, Dopplerův posuv v m/s, fázový zisk předzesilovače AGC a hardwarové hodiny GnssClock)."
            } else {
                "Android exposes two GNSS layers: computed fix outputs from LocationManager (coordinates, speed, 68% CEP accuracy, NMEA sentences) and raw hardware measurements from GnssStatus and GnssMeasurementsEvent (pseudorange rates / Doppler in m/s, RF front-end AGC gain, and GnssClock hardware timing)."
            },
            fontSize = 9.2f
        )
        y += 6f

        y = drawCalloutCard(
            c, y,
            if (isCs) "Fyzické družice vs. jednotlivé signály (Dual-Band L1 + L5 / E5a / B2a)"
            else "Unique Physical Satellites vs. Tracked Signals (Dual-Band L1 + L5 / E5a / B2a)",
            if (isCs) {
                "Moderní družice (GPS Block IIF/III, Galileo, BeiDou-3) vysílají současně na pásmu L1/E1 (1575,42 MHz) i modernějším širokopásmovém L5/E5a/B2a (1176,45 MHz). Pásmo L5 má 10× vyšší čipovou rychlost (10,23 Mcps oproti 1,023 Mcps u L1 C/A), takže výrazně lépe potlačuje odrazy od budov (multipath) a umožňuje přímou korekci ionosférického zpoždění. Aplikace sčítá jak unikátní fyzické družice (shoda s aplikací GPS Test), tak celkový počet sledovaných signálů."
            } else {
                "Modern satellites (GPS III, Galileo, BeiDou-3) simultaneously broadcast legacy L1/E1 (1575.42 MHz) and wideband L5/E5a/B2a (1176.45 MHz). Because L5 uses a 10× higher chipping rate (10.23 Mcps vs. 1.023 Mcps on L1 C/A), it sharply rejects urban multipath reflections and cancels first-order ionospheric delay. The app tracks both unique physical satellites (aligned with GPS Test) and total RF signals."
            },
            COLOR_EMERALD_BG, COLOR_EMERALD
        )
        y += 8f

        // Figure 2: SkyPlot & C/N0 Elevation Curve
        y = drawSkyPlotAndCn0Diagram(c, y, isCs)
        y += 10f

        y = drawSectionHeading(c, y,
            if (isCs) "Geometrie družic (DOP), Geoid N a interní prahy kvality" else "Satellite Geometry (DOP), Geoid N & Quality Thresholds",
            COLOR_DARK_NAVY
        )

        val blocks = if (isCs) listOf(
            "PDOP / HDOP / VDOP (Dilution of Precision)" to "Vyjadřuje geometrické rozložení družic na obloze. Pokud jsou satelity nahuštěny v jednom směru (např. městský kaňon), PDOP roste (> 3,5 = špatná geometrie). Pro spolehlivý 3D fix jsou nutné alespoň 4 družice; při ztrátě fixu v interiéru (< 4 satelity nebo timeout > 4,5 s) aplikace potlačí zastaralé DOP i rychlost a zobrazí čistou pomlčku.",
            "Nadmořská výška MSL vs. Elipsoid WGS-84" to "Surová GNSS výška je vztažena k matematickému elipsoidu WGS-84. Skutečnou nadmořskou výšku nad hladinou moře (MSL) poskytuje oprava o geoidní undulaci N (z věty \$GNGGA; ve střední Evropě přibližně +43 až +47 m).",
            "TTFF (Time To First Fix) & AGC Jamming Score" to "TTFF měří dobu od startu přijímače k prvnímu platnému 3D fixu. AGC (Automatic Gain Control) sleduje zesílení vstupního RF řetězce v dB: silný rušič (jammer) přinutí AGC skokově snížit zisk, přestože užitečný signál C/N0 prudce klesá."
        ) else listOf(
            "PDOP / HDOP / VDOP (Dilution of Precision)" to "Quantifies satellite sky geometry. Clustered satellites (urban canyons) inflate PDOP (> 3.5 = poor). A valid 3D fix requires at least 4 satellites; upon indoor fix loss (< 4 SVs or > 4.5 s timeout), stale speed and DOP values are safely cleared instead of freezing old numbers.",
            "MSL Altitude vs. WGS-84 Ellipsoid" to "Raw GNSS height references the mathematical WGS-84 ellipsoid. True Mean Sea Level (MSL) elevation subtracts local geoid separation N parsed from \$GNGGA sentences (~+43 to +47 m across Central Europe).",
            "TTFF & AGC Jamming Score" to "TTFF measures time from receiver start to first 3D fix. Hardware AGC monitors front-end gain in dB: an in-band RF jammer forces AGC gain sharply downward while carrier-to-noise C/N0 collapses."
        )
        for ((k, v) in blocks) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_LIGHT_BG, COLOR_CYAN)
            y += 5f
        }

        drawPageFooter(c, 2, totalPages, isCs)
        doc.finishPage(page)
    }

    private fun drawSkyPlotAndCn0Diagram(c: Canvas, startY: Float, isCs: Boolean): Float {
        val boxH = 195f
        val bgRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(bgRect, 8f, 8f, fillPaint(COLOR_LIGHT_BG))
        c.drawRoundRect(bgRect, 8f, 8f, strokePaint(COLOR_CARD_BORDER, 1f))

        c.drawText(
            if (isCs) "Obr. 2: Model polárního SkyPlot radaru (vlevo) a typická závislost C/N0 na elevaci (vpravo)"
            else "Fig. 2: Polar SkyPlot Radar Model (Left) & Natural C/N0 vs. Elevation Curve (Right)",
            MARGIN + 10f,
            startY + 14f,
            textPaint(8.5f, COLOR_SLATE, bold = true)
        )

        // Left: SkyPlot Polar Radar
        val cx = MARGIN + 108f
        val cy = startY + 104f
        val maxR = 68f
        c.drawCircle(cx, cy, maxR, fillPaint(COLOR_DARK_NAVY))
        for (frac in listOf(1f, 0.66f, 0.33f)) {
            c.drawCircle(cx, cy, maxR * frac, strokePaint(Color.parseColor("#334155"), 0.8f))
        }
        c.drawLine(cx - maxR, cy, cx + maxR, cy, strokePaint(Color.parseColor("#334155"), 0.8f))
        c.drawLine(cx, cy - maxR, cx, cy + maxR, strokePaint(Color.parseColor("#334155"), 0.8f))

        val dirPaint = textPaint(7.5f, Color.WHITE, bold = true)
        c.drawText("N 0°", cx - 9f, cy - maxR - 4f, textPaint(7.5f, COLOR_SLATE, bold = true))
        c.drawText("S 180°", cx - 12f, cy + maxR + 10f, textPaint(7.5f, COLOR_SLATE, bold = true))
        c.drawText("E", cx + maxR + 4f, cy + 3f, textPaint(7.5f, COLOR_SLATE, bold = true))
        c.drawText("W", cx - maxR - 11f, cy + 3f, textPaint(7.5f, COLOR_SLATE, bold = true))
        c.drawText("60°", cx + 3f, cy - maxR * 0.33f - 2f, dirPaint)
        c.drawText("30°", cx + 3f, cy - maxR * 0.66f - 2f, dirPaint)

        // Sample satellites on radar
        fun drawSat(azDeg: Float, elDeg: Float, color: Int, inFix: Boolean, dualBand: Boolean, label: String) {
            val r = maxR * ((90f - elDeg) / 90f)
            val rad = Math.toRadians(azDeg.toDouble())
            val sx = cx + (r * sin(rad)).toFloat()
            val sy = cy - (r * cos(rad)).toFloat()
            if (dualBand) {
                c.drawCircle(sx, sy, 8f, strokePaint(Color.parseColor("#34D399"), 1.6f))
            }
            if (inFix) {
                c.drawCircle(sx, sy, 4.5f, fillPaint(color))
            } else {
                c.drawCircle(sx, sy, 4.5f, strokePaint(color, 1.5f))
            }
            c.drawText(label, sx + 6f, sy + 2.5f, textPaint(6.5f, Color.WHITE))
        }
        drawSat(35f, 65f, Color.parseColor("#38BDF8"), true, true, "G04")
        drawSat(145f, 48f, Color.parseColor("#34D399"), true, true, "E11")
        drawSat(260f, 38f, Color.parseColor("#F59E0B"), true, false, "R07")
        drawSat(310f, 15f, Color.parseColor("#A78BFA"), false, false, "C21")

        // Mini radar legend below SkyPlot
        c.drawText(
            if (isCs) "● Ve fixu   ○ Mimo fix   ◎ Zelená záře = L5/E5a"
            else "● In fix    ○ Unused     ◎ Green halo = L5/E5a",
            MARGIN + 18f,
            startY + boxH - 8f,
            textPaint(7.2f, COLOR_MUTED, bold = true)
        )

        // Right: C/N0 vs Elevation chart
        val gx = MARGIN + 235f
        val gy = startY + 34f
        val gw = CONTENT_WIDTH - 252f
        val gh = 122f
        c.drawRect(gx, gy, gx + gw, gy + gh, fillPaint(Color.WHITE))
        c.drawRect(gx, gy, gx + gw, gy + gh, strokePaint(COLOR_CARD_BORDER, 1f))

        // Axis labels
        c.drawText("C/N0 [dB-Hz]", gx + 4f, gy + 11f, textPaint(7.2f, COLOR_SLATE, bold = true))
        c.drawText("50", gx - 14f, gy + 16f, textPaint(7f, COLOR_MUTED))
        c.drawText("30", gx - 14f, gy + gh * 0.55f, textPaint(7f, COLOR_MUTED))
        c.drawText("15", gx - 14f, gy + gh - 4f, textPaint(7f, COLOR_MUTED))
        c.drawText("0°", gx + 2f, gy + gh + 10f, textPaint(7f, COLOR_MUTED))
        c.drawText("45°", gx + gw * 0.48f, gy + gh + 10f, textPaint(7f, COLOR_MUTED))
        c.drawText(if (isCs) "90° (Zenit)" else "90° (Zenith)", gx + gw - 42f, gy + gh + 10f, textPaint(7f, COLOR_MUTED))

        // Expected realistic curve (low at horizon, high at zenith)
        val curvePath = Path().apply {
            moveTo(gx + 10f, gy + gh - 22f)
            cubicTo(gx + gw * 0.35f, gy + gh * 0.65f, gx + gw * 0.65f, gy + 28f, gx + gw - 12f, gy + 18f)
        }
        c.drawPath(curvePath, strokePaint(COLOR_EMERALD, 2.2f))
        c.drawText(
            if (isCs) "Reálná atmosféra (+vyšší C/N0 v zenitu)" else "Real Sky (+higher C/N0 near zenith)",
            gx + gw * 0.25f, gy + 24f, textPaint(7.2f, COLOR_EMERALD, bold = true)
        )

        // Spoofed flat line (red dashed)
        val spoofPaint = strokePaint(COLOR_CRIMSON, 1.8f).apply {
            pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
        }
        c.drawLine(gx + 10f, gy + 26f, gx + gw - 12f, gy + 36f, spoofPaint)
        c.drawText(
            if (isCs) "Spoofer (plochý/převrácený profil)" else "Spoofer (flat/inverted profile)",
            gx + 14f, gy + 48f, textPaint(7.2f, COLOR_CRIMSON, bold = true)
        )

        return startY + boxH
    }

    // -------------------------------------------------------------------------
    // PAGE 3: Cellular RF Models, 5G NSA/SA, CA, TA & Czech BTS Registry
    // -------------------------------------------------------------------------
    private fun drawPage3CellularAndBtsModels(doc: PdfDocument, isCs: Boolean, totalPages: Int) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 3).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawRunningHeader(c, if (isCs) "KAPITOLA 2 • MOBILNÍ SÍTĚ, 5G NSA/SA, TA & DATABÁZE BTS" else "CHAPTER 2 • CELLULAR RF, 5G NSA/SA, TA & BTS REGISTRY")

        var y = 52f
        y = drawSectionHeading(c, y,
            if (isCs) "Rádiové metriky LTE / 5G NR a architektura Dual-SIM (DSDS)" else "LTE / 5G NR Radio Metrics & Dual-SIM (DSDS) Isolation",
            COLOR_AMBER
        )

        y = drawParagraph(c, y,
            if (isCs) {
                "Aplikace registruje oddělený TelephonyCallback pro každé Subscription ID (SIM 1 a SIM 2). Zatímco aktivní datová SIM drží stav RRC Connected (a poskytuje i Timing Advance či Carrier Aggregation), druhá SIM v režimu DSDS Standby průběžně měří RSRP/RSRQ a Cell ID na úrovni vlastního přijímače."
            } else {
                "The engine registers isolated TelephonyCallback listeners per Subscription ID (SIM 1 & SIM 2). While the data-active SIM maintains RRC Connected state (exposing Timing Advance and Carrier Aggregation), the standby SIM continuously reports RSRP/RSRQ and serving Cell ID independently."
            },
            fontSize = 9.1f
        )
        y += 5f

        val rfItems = if (isCs) listOf(
            "RSRP / SS-RSRP (dBm)" to "Výkon referenčních symbolů (typicky -65 dBm výborný až -120 dBm okraj pokrytí). Slouží pro přepínání buněk (Handover) i výpočet ztrát trasy.",
            "RSRQ (dB) & SINR (dB)" to "RSRQ (-3 až -20 dB) odráží zátěž a interferenci buňky. SINR (> +15 dB čisté, < 0 dB rušené) měří odstup užitečného signálu od šumu a okolních BTS.",
            "5G NSA (EN-DC) vs. 5G SA" to "V síti 5G NSA drží signalizaci LTE kotva (PCC). Aplikace rozlišuje „LTE Kotva (Idle)“ vs. aktivní 5G datový kanál „EN-DC NR Aktivní“ (SCG větev s SS-RSRP/SINR a NR-ARFCN).",
            "Carrier Aggregation (CA)" to "Zobrazuje součtovou šířku pásma (např. 40 MHz) i jednotlivé složkové nosné PCC + SCC + SCG v MHz (např. B3 1800 + B1 2100 + n78 3500 MHz)."
        ) else listOf(
            "RSRP / SS-RSRP (dBm)" to "Reference Signal Received Power (-65 dBm strong down to -120 dBm cell edge). Primary metric for handovers and path-loss physics.",
            "RSRQ (dB) & SINR (dB)" to "RSRQ (-3 to -20 dB) reflects cell load and interference. SINR (> +15 dB clean, < 0 dB degraded) quantifies signal-to-interference-plus-noise ratio.",
            "5G NSA (EN-DC) vs. 5G SA" to "In 5G NSA, control signaling stays on the LTE anchor (PCC). The app separates 'LTE Anchor (Idle)' from active 'EN-DC NR Active' (SCG leg with SS-RSRP/SINR and NR-ARFCN).",
            "Carrier Aggregation (CA)" to "Reports both total aggregated bandwidth and per-carrier PCC/SCC/SCG component badges with MHz frequencies (e.g., B3 1800 + B1 2100 + n78 3500 MHz)."
        )
        for ((k, v) in rfItems) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_LIGHT_BG, COLOR_AMBER)
            y += 4f
        }
        y += 4f

        // Figure 3: TA vs BTS Geometry Diagram
        y = drawTaAndBtsDiagram(c, y, isCs)
        y += 8f

        y = drawCalloutCard(
            c, y,
            if (isCs) "Databáze českých BTS (RÚIAN + BeaconDB + NTM/CLF/CellMapper) a výpočet FSPL"
            else "Czech 3-Operator BTS Registry (RUIAN + BeaconDB + NTM/CLF/CellMapper) & FSPL",
            if (isCs) {
                "Lokální databáze Room páruje CellID i sdílené eNodeB operátorů 23001/23002/23003 (včetně MOCN). Podporuje import GSMweb CSV, CLF v3/v4, NetMonster .ntm, CellMapper eNB CSV, KML, OpenCelliD (.csv.gz) i .ZIP s prioritou přesnosti (Ruční > CLF/NTM/KML > ČÚZK RÚIAN > CellMapper > BeaconDB > OpenCelliD). Pro přesné GPS u adres z GSMwebu využívá ČÚZK RÚIAN (~2–10 m) a pro lesní či neznámé buňky BeaconDB. Počítá Haversine vzdálenost, azimut, ΔTA a útlum: FSPL = 20·log10(d_km) + 20·log10(f_MHz) + 32,45."
            } else {
                "The local Room database matches CellID and sibling eNodeB towers across 23001/23002/23003 (including MOCN). Supports GSMweb CSV, CLF v3/v4, NetMonster .ntm, CellMapper eNB CSV, KML, OpenCelliD (.csv.gz), and .ZIP with precision ranking (Manual > CLF/NTM/KML > RUIAN > CellMapper > BeaconDB > OpenCelliD). Resolves GSMweb addresses via CUZK RUIAN (~2–10 m) and rural/unknown cells via BeaconDB to compute Haversine distance, bearing, ΔTA, and FSPL = 20·log10(d_km) + 20·log10(f_MHz) + 32.45."
            },
            COLOR_CYAN_BG, COLOR_CYAN
        )

        drawPageFooter(c, 3, totalPages, isCs)
        doc.finishPage(page)
    }

    private fun drawTaAndBtsDiagram(c: Canvas, startY: Float, isCs: Boolean): Float {
        val boxH = 152f
        val bgRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(bgRect, 8f, 8f, fillPaint(COLOR_LIGHT_BG))
        c.drawRoundRect(bgRect, 8f, 8f, strokePaint(COLOR_CARD_BORDER, 1f))

        c.drawText(
            if (isCs) "Obr. 3: Princip Timing Advance (TA = n × 78,12 m) a porovnání s GPS vzdáleností BTS"
            else "Fig. 3: Timing Advance Geometry (TA = n × 78.12 m) vs. Real GPS-to-BTS Distance",
            MARGIN + 10f,
            startY + 14f,
            textPaint(8.5f, COLOR_SLATE, bold = true)
        )

        val towerX = MARGIN + 74f
        val towerY = startY + 95f

        // Concentric TA rings centered on BTS tower
        val radii = listOf(45f, 82f, 120f, 158f)
        val ringLabels = listOf("TA=1", "TA=3", "TA=5", "TA=7")
        val ringPaint = strokePaint(COLOR_CYAN.copyAlpha(110), 1f).apply {
            pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
        }
        radii.forEachIndexed { idx, r ->
            val arcRect = RectF(towerX - r, towerY - r * 0.45f, towerX + r, towerY + r * 0.45f)
            c.drawArc(arcRect, -62f, 124f, false, ringPaint)
            c.drawText(ringLabels[idx], towerX + r - 12f, towerY - r * 0.34f, textPaint(6.8f, COLOR_CYAN, bold = true))
        }

        // BTS tower vector icon
        val towerPath = Path().apply {
            moveTo(towerX, towerY - 34f)
            lineTo(towerX - 11f, towerY + 14f)
            lineTo(towerX + 11f, towerY + 14f)
            close()
        }
        c.drawPath(towerPath, strokePaint(COLOR_VUT_RED, 2f))
        c.drawCircle(towerX, towerY - 36f, 4f, fillPaint(COLOR_VUT_RED))
        c.drawText("BTS", towerX - 10f, towerY + 26f, textPaint(8f, COLOR_DARK_NAVY, bold = true))

        // UE icon
        val ueX = towerX + 122f
        val ueY = towerY - 4f
        c.drawRoundRect(RectF(ueX - 7f, ueY - 12f, ueX + 7f, ueY + 12f), 2f, 2f, fillPaint(COLOR_DARK_NAVY))
        c.drawRect(ueX - 5f, ueY - 9f, ueX + 5f, ueY + 7f, fillPaint(Color.parseColor("#38BDF8")))
        c.drawText("UE (Telefon)", ueX - 20f, ueY + 24f, textPaint(7.5f, COLOR_DARK_NAVY, bold = true))

        // Line between BTS and UE
        drawArrow(c, towerX + 14f, towerY - 4f, ueX - 10f, ueY, COLOR_EMERALD)
        c.drawText(
            if (isCs) "Reálná d_GPS + Azimut" else "Real d_GPS + Bearing",
            towerX + 26f, towerY - 10f, textPaint(7.2f, COLOR_EMERALD, bold = true)
        )

        // Formula Box on right side
        val fx = MARGIN + 272f
        val fw = CONTENT_WIDTH - 284f
        val fRect = RectF(fx, startY + 26f, fx + fw, startY + boxH - 12f)
        c.drawRoundRect(fRect, 6f, 6f, fillPaint(Color.WHITE))
        c.drawRoundRect(fRect, 6f, 6f, strokePaint(COLOR_AMBER, 1.2f))

        drawParagraphInBox(
            c, fx + 8f, startY + 33f, fw - 16f,
            if (isCs) {
                "Krok LTE TA (Ts):\n" +
                "• 1× mód: d_TA = TA × 78,12 m\n" +
                "• 2× mód: d_TA = TA × 156,24 m\n" +
                "• Odchylka: ΔTA = d_TA − d_GPS\n\n" +
                "Bezpečnostní pravidlo:\n" +
                "|ΔTA| > 2 km (nebo RSRP nesedí k TA) značí opakovač či falešnou BTS!"
            } else {
                "LTE Timing Advance (Ts):\n" +
                "• 1× mode: d_TA = TA × 78.12 m\n" +
                "• 2× mode: d_TA = TA × 156.24 m\n" +
                "• Delta: ΔTA = d_TA − d_GPS\n\n" +
                "Security Rule:\n" +
                "|ΔTA| > 2 km (or RSRP mismatch) flags repeaters or rogue BTS!"
            },
            fontSize = 8f,
            color = COLOR_BODY
        )

        return startY + boxH
    }

    // -------------------------------------------------------------------------
    // PAGE 4: Security Shield Part 1 - GNSS Anti-Spoofing & Sensor Fusion
    // -------------------------------------------------------------------------
    private fun drawPage4GnssAntiSpoofing(doc: PdfDocument, isCs: Boolean, totalPages: Int) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 4).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawRunningHeader(c, if (isCs) "KAPITOLA 3 • ZÁLOŽKA PODVODY: ANTI-SPOOFING & RUŠENÍ GNSS" else "CHAPTER 3 • SPOOFING TAB: GNSS ANTI-SPOOFING & JAMMING")

        var y = 52f
        y = drawSectionHeading(c, y,
            if (isCs) "Co dokáže standardní smartphone odhalit u GNSS útoků (bez rootu)" else "Smartphone-Feasible GNSS Anti-Spoofing & Jamming Detectors",
            COLOR_CRIMSON
        )

        y = drawParagraph(c, y,
            if (isCs) {
                "Pozemní GNSS spoofer i rušička (jammer) porušují více fyzikálních zákonů šíření signálu najednou. Záložka Podvody (v1.7) každou sekundu kombinuje surová data GNSS přijímače a fúzi senzorů telefonu do GNSS Threat Indexu (0–100 %):"
            } else {
                "A terrestrial GNSS spoofer or RF jammer violates multiple orbital and kinematic laws simultaneously. The Spoofing tab (v1.7) fuses raw GNSS observables with phone sensors at 1 Hz into the GNSS Threat Index (0–100%):"
            },
            fontSize = 9.0f
        )
        y += 5f

        val checks = if (isCs) listOf(
            "1. Softwarové Mock & ΔNMEA" to "Detekuje příznak isMock=true i odchylku souřadnic surové věty NMEA (\$GNGGA/\$GNRMC) od Android Location API (> 35 m – OS hooking).",
            "2. AGC vs. C/N0 (Jamming & Spoof)" to "Pokles zisku AGC (> 6 dB pod bázi) + pokles C/N0 = rušička; jednotný výkon C/N0 (σ < 2,3 dB-Hz), inverze elevace nebo skok C/N0 (≥ +9 dB v klidu) = RF spoofer.",
            "3. Doppler, ADR & Zamrzlé nebe" to "Kolaps Dopplerova rozptylu (σ_v < 45 m/s), opačné znaménko Dopplera vůči elevaci, výpadek fáze nosné ADR (0 %) a zamrznutí polohy družic po ≥ 60 s.",
            "4. Konstelace & Pásmo L5/E5a" to "Výpadek Galileo/GLONASS/BeiDou (zůstává jen ≥ 7 GPS) nebo zmizení dvoupásma L5/E5a při silném L1 odhalí jednokanálový spoofer.",
            "5. Fúze IMU, Gyro, Magnetometr & Kroky" to "Klid IMU vs. rychlost GNSS, projekce rotace gyroskopu (∫ω_vert dt) vs. změna kurzu GNSS, odchylka magnetometru |B| (µT), kadence kroků (6 s) a barometr Δh.",
            "6. Prostorová kontrola GNSS vs. BTS" to "Vzdálenost souřadnic GNSS od známé obslužné BTS v ČR > 45 km i teleportační skoky polohy (> 65 m/s)."
        ) else listOf(
            "1. Software Mock & ΔNMEA Check" to "Catches isMock=true and Haversine divergence between raw modem NMEA (\$GNGGA/\$GNRMC) and Android Location API (> 35 m OS hooking).",
            "2. AGC vs. C/N0 (Jamming & Spoof)" to "AGC compression (> 6 dB) with falling C/N0 = jammer; flat C/N0 (σ < 2.3), elevation inversion, or stationary C/N0 takeover jump (≥ +9 dB) = RF spoofer.",
            "3. Doppler, ADR & Sky Freeze" to "Collapsed Doppler spread (σ_v < 45 m/s), Doppler sign contradicting elevation slope, 0% ADR carrier phase at strong C/N0, and 60s frozen sky constellation.",
            "4. Multi-Constellation & L5 Drop" to "Vanishing Galileo/GLONASS/BeiDou (GPS-only ≥ 7) or sudden loss of L5/E5a under strong L1 exposes single-band spoofers.",
            "5. IMU, Gyro, Magnetometer & Step Fusion" to "Stationary IMU vs. speed, gravity-projected gyro yaw (∫ω_vert dt) vs. GNSS bearing turn, magnetometer |B| (µT), 6s step cadence vs. speed, and barometer Δh.",
            "6. Spatial GNSS vs. BTS Sanity" to "Flags GNSS coordinates > 45 km from matched Czech serving BTS and abrupt teleport jumps (> 65 m/s)."
        )
        for ((k, v) in checks) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_LIGHT_BG, COLOR_CRIMSON)
            y += 4f
        }
        y += 4f

        // Figure 4: Real vs Spoofed Doppler & C/N0 fingerprint
        y = drawGnssSpoofComparisonDiagram(c, y, isCs)

        drawPageFooter(c, 4, totalPages, isCs)
        doc.finishPage(page)
    }

    private fun drawGnssSpoofComparisonDiagram(c: Canvas, startY: Float, isCs: Boolean): Float {
        val boxH = 122f
        val bgRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(bgRect, 8f, 8f, fillPaint(COLOR_LIGHT_BG))
        c.drawRoundRect(bgRect, 8f, 8f, strokePaint(COLOR_CARD_BORDER, 1f))

        c.drawText(
            if (isCs) "Obr. 4: Fyzikální podpis pravého satelitního nebe vs. pozemní SDR spoofer"
            else "Fig. 4: Authentic Orbital Constellation vs. Ground-Based SDR Spoofer Signature",
            MARGIN + 10f, startY + 14f, textPaint(8.5f, COLOR_SLATE, bold = true)
        )

        val halfW = (CONTENT_WIDTH - 30f) / 2f
        val leftX = MARGIN + 10f
        val rightX = leftX + halfW + 10f
        val chartY = startY + 24f
        val chartH = 86f

        // Left: Authentic Doppler + C/N0
        c.drawRoundRect(RectF(leftX, chartY, leftX + halfW, chartY + chartH), 6f, 6f, fillPaint(COLOR_EMERALD_BG))
        c.drawRoundRect(RectF(leftX, chartY, leftX + halfW, chartY + chartH), 6f, 6f, strokePaint(COLOR_EMERALD, 1f))
        c.drawText(
            if (isCs) "✔ REÁLNÁ KONSTELACE (Zdravý stav)" else "✔ AUTHENTIC SKY (Nominal)",
            leftX + 8f, chartY + 14f, textPaint(8f, COLOR_EMERALD, bold = true)
        )
        // Wide Doppler bars across -600..+600 m/s
        val midL = leftX + halfW * 0.5f
        val baseL = chartY + chartH - 16f
        c.drawLine(leftX + 12f, baseL, leftX + halfW - 12f, baseL, strokePaint(COLOR_SLATE, 1f))
        for (offset in listOf(-75f, -48f, -20f, 15f, 44f, 78f)) {
            val h = 24f + (offset % 19f)
            c.drawLine(midL + offset, baseL, midL + offset, baseL - kotlin.math.abs(h), strokePaint(COLOR_CYAN, 3.5f))
        }
        c.drawText(
            if (isCs) "Široký Doppler rozptyl (σ_v > 180 m/s) + L1 & L5 aktivní"
            else "Wide Doppler spread (σ_v > 180 m/s) + L1 & L5 active",
            leftX + 8f, chartY + chartH - 5f, textPaint(7f, COLOR_BODY)
        )

        // Right: Spoofed collapsed cluster
        c.drawRoundRect(RectF(rightX, chartY, rightX + halfW, chartY + chartH), 6f, 6f, fillPaint(COLOR_CRIMSON_BG))
        c.drawRoundRect(RectF(rightX, chartY, rightX + halfW, chartY + chartH), 6f, 6f, strokePaint(COLOR_CRIMSON, 1f))
        c.drawText(
            if (isCs) "✖ POZEMNÍ SPOOFER (Anomálie)" else "✖ GROUND SPOOFER (Anomaly)",
            rightX + 8f, chartY + 14f, textPaint(8f, COLOR_CRIMSON, bold = true)
        )
        val midR = rightX + halfW * 0.5f
        c.drawLine(rightX + 12f, baseL, rightX + halfW - 12f, baseL, strokePaint(COLOR_SLATE, 1f))
        for (offset in listOf(-8f, -4f, 0f, 4f, 8f)) {
            c.drawLine(midR + offset, baseL, midR + offset, baseL - 44f, strokePaint(COLOR_CRIMSON, 3.5f))
        }
        c.drawText(
            if (isCs) "Kolaps Dopplera (σ_v < 45 m/s) + jednotný L1 výkon"
            else "Collapsed Doppler (σ_v < 45 m/s) + uniform L1 power",
            rightX + 8f, chartY + chartH - 5f, textPaint(7f, COLOR_BODY)
        )

        return startY + boxH
    }

    // -------------------------------------------------------------------------
    // PAGE 5: Security Shield Part 2 - RF TA-Power Physics & 3-Clock UTC Audit
    // -------------------------------------------------------------------------
    private fun drawPage5RfPhysicsAndClocks(doc: PdfDocument, isCs: Boolean, totalPages: Int) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 5).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawRunningHeader(c, if (isCs) "KAPITOLA 4 • FYZIKA TA VS. RSRP, IMSI CATCHER & 3× ČAS UTC" else "CHAPTER 4 • TA VS. RSRP PHYSICS, IMSI CATCHER & 3× CLOCK AUDIT")

        var y = 52f
        y = drawSectionHeading(c, y,
            if (isCs) "Model šíření v mobilní síti: Timing Advance vs. přijatý výkon RSRP" else "Cellular Propagation Model: Timing Advance vs. Received RSRP Power",
            COLOR_PURPLE
        )

        y = drawCalloutCard(
            c, y,
            if (isCs) "Fyzika šíření RSRP vs. TA, strop FSPL, Dual-SIM & Síťový stack (v1.7)"
            else "RSRP vs. TA Path-Loss, FSPL Ceiling, Dual-SIM & Network Stack (v1.7)",
            if (isCs) {
                "Pro vzdálenost d_TA z Timing Advance platí model RSRP_exp ≈ −72 − 34·log10(d/150) − 20·log10(f/1800) a strop volného prostoru FSPL_max = 40 − FSPL_dB (+12 dB).\n" +
                "• IMSI Catcher, Test ID & Skok TA v klidu: 2G downgrade, testovací PLMN (00101/999) či TAC/eNB 0/1, úzký SDR kanál (1,4/3 MHz), nízké CQI při silném RSRP a náhlý pokles TA při klidném IMU.\n" +
                "• Dual-SIM & DNS integrita: křížová kontrola SIM 1 vs. SIM 2 (jednostranný 2G TA=0 útok, ΔBTS > 40 km), mezera RSSI−RSRP > 38 dB a detekce privátních DNS (192.168/10.x) na mobilních datech."
            } else {
                "Given Timing Advance range d_TA, expected RSRP follows −72 − 34·log10(d/150) − 20·log10(f/1800) with free-space ceiling FSPL_max = 40 − FSPL_dB (+12 dB).\n" +
                "• Stingray, Test IDs & Stationary TA Drop: 2G downgrade, reserved PLMN (00101/999) or TAC/eNB 0/1, narrow SDR bandwidth (1.4/3 MHz), low CQI at strong RSRP, and abrupt TA drop while IMU-stationary.\n" +
                "• Dual-SIM & DNS Stack: cross-checks SIM 1 vs. SIM 2 (single-SIM 2G/TA=0 attack, > 40 km BTS divergence), RSSI−RSRP > 38 dB jamming, and private cellular DNS servers."
            },
            COLOR_PURPLE_BG, COLOR_PURPLE
        )
        y += 8f

        // Figure 5: Dual diagram (Left: RSRP vs TA Corridor, Right: 3-Clock Triangle)
        y = drawTaPowerAndClocksDiagrams(c, y, isCs)
        y += 10f

        y = drawSectionHeading(c, y,
            if (isCs) "Trojitý audit přesného času UTC (GNSS · Mobilní síť NITZ · Internet NTP)" else "Multi-Source UTC Time Forensics (GNSS · Cellular NITZ · Internet NTP)",
            COLOR_CYAN
        )

        val clockRows = if (isCs) listOf(
            "1. Internetový NTP (RFC 4330 SNTP)" to "Přímý UDP paket na port 123 (time.google.com, tik.cesnet.cz, time.cloudflare.com) s výpočtem RTT/2. Aplikace dotaz opakuje každých 60 s (nebo ručně tlačítkem Sync NTP) a mezitím referenční čas počítá přes monotónní čítač elapsedRealtime(), aniž by měnila systémové hodiny OS.",
            "2. Čas mobilní sítě / OS (NITZ)" to "Čas základnové stanice operátora (NITZ) posílaný událostně při registraci Attach, změně Tracking Area či přechodu 4G↔5G s krokem 1 s a systémové hodiny Androidu.",
            "3. Hardwarový čas GNSS (GnssClock)" to "Výpočet přímého satelitního času z nanosekundového čítače (timeNanos − fullBiasNanos − biasNanos) s odečtem přestupných sekund GPS–UTC, doplněný o sledování skokových diskontinuit a frekvenčního driftu oscilátoru (ns/s).",
            "Jak odhaluje podvody" to "Replay útok (meaconing) přehrává starší záznam GNSS a způsobí odchylku Δ(GNSS − NTP) > 800 ms (kriticky > 2 000 ms). Podvržená BTS vysílající falešný NITZ čas vytvoří odchylku Δ(NITZ − NTP) při zachování platného NTP."
        ) else listOf(
            "1. Internet NTP (RFC 4330 SNTP)" to "Direct UDP:123 query (time.google.com, tik.cesnet.cz, time.cloudflare.com) with half-RTT compensation, refreshed every 60s (or on demand) and propagated locally via monotonic elapsedRealtime() without altering OS time.",
            "2. Mobile Network / OS Time (NITZ)" to "Carrier base-station NITZ broadcast (pushed event-driven on Attach, TA update, or RAT change at 1s steps) and Android wall-clock time.",
            "3. Hardware GNSS UTC (GnssClock)" to "Computes exact satellite UTC directly from raw nanosecond counters (timeNanos − fullBiasNanos − biasNanos) minus GPS-UTC leap seconds, while monitoring oscillator drift (ns/s) and clock discontinuities.",
            "What Skews Expose" to "GNSS replay (meaconing) injects delayed satellite signals causing Δ(GNSS − NTP) > 800 ms (critical > 2,000 ms). A rogue BTS sending fake NITZ triggers Δ(NITZ − NTP) while internet NTP stays accurate."
        )
        for ((k, v) in clockRows) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_LIGHT_BG, COLOR_CYAN)
            y += 4f
        }

        drawPageFooter(c, 5, totalPages, isCs)
        doc.finishPage(page)
    }

    private fun drawTaPowerAndClocksDiagrams(c: Canvas, startY: Float, isCs: Boolean): Float {
        val boxH = 166f
        val bgRect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(bgRect, 8f, 8f, fillPaint(COLOR_LIGHT_BG))
        c.drawRoundRect(bgRect, 8f, 8f, strokePaint(COLOR_CARD_BORDER, 1f))

        c.drawText(
            if (isCs) "Obr. 5: Koridor výkonu RSRP vs. TA (vlevo) a trojitá kontrola UTC času (vpravo)"
            else "Fig. 5: RSRP vs. TA Path-Loss Corridor (Left) & 3-Clock UTC Cross-Check Triangle (Right)",
            MARGIN + 10f, startY + 14f, textPaint(8.5f, COLOR_SLATE, bold = true)
        )

        val halfW = (CONTENT_WIDTH - 28f) / 2f
        val lx = MARGIN + 10f
        val rx = lx + halfW + 8f
        val cy = startY + 24f
        val ch = 130f

        // Left: RSRP vs TA plot
        c.drawRect(lx, cy, lx + halfW, cy + ch, fillPaint(Color.WHITE))
        c.drawRect(lx, cy, lx + halfW, cy + ch, strokePaint(COLOR_CARD_BORDER, 1f))
        c.drawText("RSRP [dBm]", lx + 6f, cy + 12f, textPaint(7.2f, COLOR_SLATE, bold = true))
        c.drawText("-55", lx + 4f, cy + 26f, textPaint(6.8f, COLOR_MUTED))
        c.drawText("-90", lx + 4f, cy + ch * 0.55f, textPaint(6.8f, COLOR_MUTED))
        c.drawText("-120", lx + 4f, cy + ch - 14f, textPaint(6.8f, COLOR_MUTED))
        c.drawText("TA=0", lx + 24f, cy + ch - 4f, textPaint(6.8f, COLOR_MUTED))
        c.drawText("TA=30 (~2.3 km)", lx + halfW - 62f, cy + ch - 4f, textPaint(6.8f, COLOR_MUTED))

        // Corridor polygon (emerald band)
        val band = Path().apply {
            moveTo(lx + 24f, cy + 26f)
            lineTo(lx + halfW - 10f, cy + ch - 36f)
            lineTo(lx + halfW - 10f, cy + ch - 16f)
            lineTo(lx + 24f, cy + 52f)
            close()
        }
        c.drawPath(band, fillPaint(COLOR_EMERALD_BG))
        c.drawLine(lx + 24f, cy + 39f, lx + halfW - 10f, cy + ch - 26f, strokePaint(COLOR_EMERALD, 2f))
        c.drawText(if (isCs) "Očekávané pásmo" else "Expected corridor", lx + 64f, cy + 68f, textPaint(7f, COLOR_EMERALD, bold = true))

        // Impossible spoof dot top-right
        val spX = lx + halfW - 35f
        val spY = cy + 24f
        c.drawCircle(spX, spY, 4.5f, fillPaint(COLOR_CRIMSON))
        c.drawText(if (isCs) "Opakovač / Spoof" else "Repeater / Spoof", spX - 58f, spY - 6f, textPaint(6.8f, COLOR_CRIMSON, bold = true))

        // Right: 3-Clock Triangle
        c.drawRect(rx, cy, rx + halfW, cy + ch, fillPaint(Color.WHITE))
        c.drawRect(rx, cy, rx + halfW, cy + ch, strokePaint(COLOR_CARD_BORDER, 1f))

        val nX = rx + halfW * 0.5f
        val nY = cy + 24f
        val gX = rx + 48f
        val gY = cy + ch - 26f
        val cX = rx + halfW - 48f
        val cY = cy + ch - 26f

        c.drawLine(nX, nY + 12f, gX + 12f, gY - 8f, strokePaint(COLOR_CYAN, 1.8f))
        c.drawLine(nX, nY + 12f, cX - 12f, cY - 8f, strokePaint(COLOR_AMBER, 1.8f))
        c.drawLine(gX + 26f, gY, cX - 26f, cY, strokePaint(COLOR_EMERALD, 1.8f))

        drawNodePill(c, nX, nY, "Internet NTP", COLOR_CYAN_BG, COLOR_CYAN)
        drawNodePill(c, gX, gY, "GNSS UTC", COLOR_EMERALD_BG, COLOR_EMERALD)
        drawNodePill(c, cX, cY, "Síť NITZ", COLOR_AMBER_BG, COLOR_AMBER)

        c.drawText("Δ(GNSS−NTP)", rx + 20f, cy + 66f, textPaint(6.8f, COLOR_CYAN, bold = true))
        c.drawText("Δ(NITZ−NTP)", rx + halfW - 70f, cy + 66f, textPaint(6.8f, COLOR_AMBER, bold = true))
        c.drawText("Δ(GNSS−NITZ)", rx + halfW * 0.5f - 28f, cy + ch - 8f, textPaint(6.8f, COLOR_EMERALD, bold = true))

        return startY + boxH
    }

    // -------------------------------------------------------------------------
    // PAGE 6: Map, Charts, Exports, Demo Attack Simulator & Privacy
    // -------------------------------------------------------------------------
    private fun drawPage6WorkflowsExportsAndDemo(doc: PdfDocument, isCs: Boolean, totalPages: Int) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 6).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        drawRunningHeader(c, if (isCs) "KAPITOLA 5 • MAPA, GRAFY, EXPORTY & DEMO SIMULÁTOR ÚTOKŮ" else "CHAPTER 5 • MAP, CHARTS, EXPORTS & DEMO ATTACK SIMULATOR")

        var y = 52f
        y = drawSectionHeading(c, y,
            if (isCs) "Mapové vrstvy, barevné škály a exportní formáty" else "Map Layers, Color Thresholds & Export Formats",
            COLOR_EMERALD
        )

        val mapItems = if (isCs) listOf(
            "Vrstva Mapa (OpenStreetMap)" to "• RSRP (S1/S2): Zelená (> -80 dBm), Limetková (-80 až -90), Oranžová (-90 až -105), Červená (< -105 dBm).\n• Satelity: < 5 červená, 5–20 oranžová, 21–30 žlutá, 31–50 zelená, > 50 fialová.\n• PDOP: < 0,7 zelená, 0,7–1,5 žlutá, 1,5–2,5 oranžová, 2,5–3,5 fialová, > 3,5 červená.",
            "Exporty z Historie (Download/GNSS_Net_Logger/)" to "• CSV: Kompletní tabulka pro Excel/Python (včetně SIM1/SIM2 BTS vzdáleností, azimutů, CA, satelitů i skóre spoofingu, ΔTA a NTP).\n• 3D KML: Barevná trasa + špendlíky Waypointů pro Google Earth.\n• GPX 1.1 & GeoJSON: Otevřené standardy (vč. bezpečnostních rozšíření) pro QGIS, ArcGIS a GPS nástroje."
        ) else listOf(
            "Map Layers (OpenStreetMap)" to "• RSRP (S1/S2): Green (> -80 dBm), Lime (-80..-90), Orange (-90..-105), Red (< -105 dBm).\n• Satellites: < 5 Red, 5–20 Orange, 21–30 Yellow, 31–50 Green, > 50 Violet.\n• PDOP: < 0.7 Green, 0.7–1.5 Yellow, 1.5–2.5 Orange, 2.5–3.5 Violet, > 3.5 Red.",
            "History Exports (Download/GNSS_Net_Logger/)" to "• CSV: Full spreadsheet schema (including SIM 1 / SIM 2 BTS distances, bearings, CA, satellites, risk score, ΔTA & NTP).\n• 3D KML: Color-coded path + Waypoint pins for Google Earth.\n• GPX 1.1 & GeoJSON: Open GIS standards (with security extensions) ready for QGIS & ArcGIS."
        )
        for ((k, v) in mapItems) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_LIGHT_BG, COLOR_EMERALD)
            y += 6f
        }

        y += 6f
        y = drawSectionHeading(c, y,
            if (isCs) "Demo režim a interaktivní Simulátor útoků (pro výuku i ověření)" else "Demo Mode & Interactive Attack Simulator (Training & Verification)",
            COLOR_AMBER
        )

        y = drawParagraph(c, y,
            if (isCs) {
                "Zapnutím volby „Simulační / Demo režim“ v Nastavení aplikace generuje realistickou zkušební trasu v okolí kampusu FEKT VUT v Brně (Technická 3082/12). Na záložce Podvody se v Demo režimu odemkne panel Simulátoru útoků, na kterém lze bezpečně v laboratoři otestovat reakci všech detektorů:"
            } else {
                "Enabling 'Simulation / Demo Mode' in Settings generates a synthetic drive-test around Brno VUT FEKT campus. Inside the Spoofing tab, an interactive Demo Attack Simulator bar unlocks to safely verify every detector in the classroom or lab:"
            },
            fontSize = 9.1f
        )
        y += 5f

        val demoScenarios = if (isCs) listOf(
            "GNSS Spoof" to "Simuluje pozemní spoofer: nízký rozptyl C/N0, inverze elevace, kolaps Dopplera, zmizení L5 a nesoulad s nehybným IMU.",
            "GNSS Jamming" to "Simuluje širokopásmovou rušičku L1/L5: pokles AGC o −11 dB a prudké zhoršení poměru C/N0.",
            "2G IMSI Catcher" to "Simuluje vynucený downgrade na 2G GSM při TA = 0 a nulovém seznamu sousedů (Stingray profil).",
            "TA + Výkon" to "Simuluje nereálně silný RSRP (−60 dBm) při vysokém TA = 16 (~2,5 km) + širokopásmovou mezeru RSSI − RSRP > 45 dB.",
            "Časový útok" to "Simuluje posun hodin GNSS oproti referenčnímu NTP o 4,8 s a diskontinuitu hardwarového GnssClock."
        ) else listOf(
            "GNSS Spoof" to "Simulates a ground spoofer: flat C/N0 dispersion, elevation inversion, Doppler collapse, lost L5, and stationary IMU conflict.",
            "GNSS Jamming" to "Simulates wideband L1/L5 jamming: −11 dB AGC gain drop paired with sharp C/N0 degradation.",
            "2G IMSI Catcher" to "Simulates forced 2G GSM downgrade at TA = 0 with zero neighbor cells (Stingray signature).",
            "TA + Power" to "Simulates impossibly strong RSRP (−60 dBm) at TA = 16 (~2.5 km) plus wideband RSSI − RSRP > 45 dB jamming.",
            "Time Drift" to "Simulates a 4.8 s GNSS clock skew against Internet NTP plus GnssClock hardware discontinuities."
        )
        for ((k, v) in demoScenarios) {
            y = drawKeyValueRowBox(c, y, k, v, COLOR_AMBER_BG.copyAlpha(110), COLOR_AMBER)
            y += 4f
        }

        y += 10f
        y = drawCalloutCard(
            c, y,
            if (isCs) "Ochrana soukromí, licence a kontakt na autora" else "Privacy Guarantee, Repository & Author Contact",
            if (isCs) {
                "• Aplikace neodesílá žádná naměřená data, polohu ani identifikátory do cloudu.\n" +
                "• Oprávnění READ_PHONE_STATE slouží pouze pro názvy Dual-SIM operátorů (žádný přístup k hovorům, SMS ani kontaktům).\n" +
                "• Zdrojové kódy a vydání APK: https://github.com/FreshSPK/GNSS-and-Net-Logger\n" +
                "• Autor: Ing. Jan Kufa, Ph.D. — ÚREL FEKT VUT v Brně (kufa@vut.cz / kufa@vutbr.cz)"
            } else {
                "• Zero telemetry, location, or SIM identifiers ever leave your phone.\n" +
                "• READ_PHONE_STATE resolves Dual-SIM carrier names only (no access to calls, SMS, or contacts).\n" +
                "• Source code & APK releases: https://github.com/FreshSPK/GNSS-and-Net-Logger\n" +
                "• Author: Ing. Jan Kufa, Ph.D. — UREL FEKT, Brno University of Technology (kufa@vut.cz)"
            },
            COLOR_DARK_NAVY, COLOR_VUT_RED, titleColor = Color.WHITE, bodyColor = Color.parseColor("#E2E8F0")
        )

        drawPageFooter(c, 6, totalPages, isCs)
        doc.finishPage(page)
    }

    // -------------------------------------------------------------------------
    // Drawing Helpers
    // -------------------------------------------------------------------------
    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun Int.copyAlpha(alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))

    private fun drawRunningHeader(c: Canvas, text: String) {
        c.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 32f, fillPaint(COLOR_DARK_NAVY))
        c.drawRect(0f, 32f, PAGE_WIDTH.toFloat(), 34.5f, fillPaint(COLOR_CYAN))
        c.drawText(text, MARGIN, 21f, textPaint(8.8f, Color.WHITE, bold = true))
        c.drawText("v1.8", PAGE_WIDTH - MARGIN - 20f, 21f, textPaint(8.8f, Color.parseColor("#38BDF8"), bold = true))
    }

    private fun drawPageFooter(c: Canvas, pageNum: Int, totalPages: Int, isCs: Boolean) {
        val y = PAGE_HEIGHT - 26f
        c.drawLine(MARGIN, y - 8f, PAGE_WIDTH - MARGIN, y - 8f, strokePaint(COLOR_CARD_BORDER, 0.8f))
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        c.drawText(
            "GNSS & Net Logger v1.8 • Ing. Jan Kufa, Ph.D. (VUT FEKT UREL) • $dateStr",
            MARGIN, y + 4f, textPaint(7.5f, COLOR_MUTED)
        )
        val pageLabel = if (isCs) "Strana $pageNum / $totalPages" else "Page $pageNum / $totalPages"
        c.drawText(pageLabel, PAGE_WIDTH - MARGIN - 54f, y + 4f, textPaint(7.8f, COLOR_SLATE, bold = true))
    }

    private fun drawSectionHeading(c: Canvas, startY: Float, title: String, accentColor: Int): Float {
        c.drawRect(MARGIN, startY + 2f, MARGIN + 4f, startY + 17f, fillPaint(accentColor))
        c.drawText(title, MARGIN + 10f, startY + 14f, textPaint(12f, COLOR_DARK_NAVY, bold = true))
        return startY + 24f
    }

    private fun drawParagraph(c: Canvas, startY: Float, text: String, fontSize: Float, color: Int = COLOR_BODY): Float {
        return drawParagraphInBox(c, MARGIN, startY, CONTENT_WIDTH, text, fontSize, color)
    }

    private fun drawParagraphInBox(
        c: Canvas,
        x: Float,
        y: Float,
        width: Float,
        text: String,
        fontSize: Float,
        color: Int
    ): Float {
        val tp = textPaint(fontSize, color)
        val w = width.toInt().coerceAtLeast(10)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f, 1.12f)
            .setIncludePad(false)
            .build()
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
        return y + layout.height
    }

    private fun drawKeyValueRowBox(
        c: Canvas,
        startY: Float,
        title: String,
        body: String,
        bgColor: Int,
        accentColor: Int
    ): Float {
        val pad = 8f
        val innerW = (CONTENT_WIDTH - pad * 2 - 6f).toInt().coerceAtLeast(10)
        val titleTp = textPaint(9.0f, accentColor, bold = true)
        val bodyTp = textPaint(8.4f, COLOR_BODY)
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titleTp, innerW)
            .setIncludePad(false).build()
        val bodyLayout = StaticLayout.Builder.obtain(body, 0, body.length, bodyTp, innerW)
            .setLineSpacing(1.2f, 1.1f).setIncludePad(false).build()

        val boxH = pad * 2 + titleLayout.height + 2f + bodyLayout.height
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(rect, 6f, 6f, fillPaint(bgColor))
        c.drawRoundRect(rect, 6f, 6f, strokePaint(COLOR_CARD_BORDER, 0.8f))
        c.drawRect(MARGIN, startY + 3f, MARGIN + 3.5f, startY + boxH - 3f, fillPaint(accentColor))

        c.save()
        c.translate(MARGIN + pad + 4f, startY + pad)
        titleLayout.draw(c)
        c.translate(0f, titleLayout.height + 2f)
        bodyLayout.draw(c)
        c.restore()

        return startY + boxH
    }

    private fun drawCalloutCard(
        c: Canvas,
        startY: Float,
        title: String,
        body: String,
        bgColor: Int,
        borderColor: Int,
        titleColor: Int = borderColor,
        bodyColor: Int = COLOR_BODY
    ): Float {
        val pad = 10f
        val innerW = (CONTENT_WIDTH - pad * 2).toInt().coerceAtLeast(10)
        val titleTp = textPaint(9.8f, titleColor, bold = true)
        val bodyTp = textPaint(8.6f, bodyColor)
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titleTp, innerW)
            .setIncludePad(false).build()
        val bodyLayout = StaticLayout.Builder.obtain(body, 0, body.length, bodyTp, innerW)
            .setLineSpacing(1.5f, 1.13f).setIncludePad(false).build()

        val boxH = pad * 2 + titleLayout.height + 4f + bodyLayout.height
        val rect = RectF(MARGIN, startY, PAGE_WIDTH - MARGIN, startY + boxH)
        c.drawRoundRect(rect, 8f, 8f, fillPaint(bgColor))
        c.drawRoundRect(rect, 8f, 8f, strokePaint(borderColor, 1.3f))

        c.save()
        c.translate(MARGIN + pad, startY + pad)
        titleLayout.draw(c)
        c.translate(0f, titleLayout.height + 4f)
        bodyLayout.draw(c)
        c.restore()

        return startY + boxH
    }

    private fun drawDiagramBox(
        c: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        bgColor: Int,
        borderColor: Int,
        text: String,
        textColor: Int = COLOR_DARK_NAVY
    ) {
        val rect = RectF(x, y, x + w, y + h)
        c.drawRoundRect(rect, 5f, 5f, fillPaint(bgColor))
        c.drawRoundRect(rect, 5f, 5f, strokePaint(borderColor, 1.1f))
        val tp = textPaint(7.1f, textColor, bold = true)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, (w - 8f).toInt().coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val dy = ((h - layout.height) / 2f).coerceAtLeast(2f)
        c.save()
        c.translate(x + 4f, y + dy)
        layout.draw(c)
        c.restore()
    }

    private fun drawArrow(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        val p = strokePaint(color, 1.3f)
        c.drawLine(x1, y1, x2, y2, p)
        val angle = kotlin.math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val len = 5.0
        val a1 = angle - 0.5
        val a2 = angle + 0.5
        c.drawLine(x2, y2, (x2 - len * cos(a1)).toFloat(), (y2 - len * sin(a1)).toFloat(), p)
        c.drawLine(x2, y2, (x2 - len * cos(a2)).toFloat(), (y2 - len * sin(a2)).toFloat(), p)
    }

    private fun drawNodePill(c: Canvas, cx: Float, cy: Float, label: String, bg: Int, border: Int) {
        val w = 68f
        val h = 20f
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        c.drawRoundRect(rect, 10f, 10f, fillPaint(bg))
        c.drawRoundRect(rect, 10f, 10f, strokePaint(border, 1.3f))
        val tp = textPaint(7.2f, COLOR_DARK_NAVY, bold = true)
        val textW = tp.measureText(label)
        c.drawText(label, cx - textW / 2f, cy + 2.5f, tp)
    }
}
