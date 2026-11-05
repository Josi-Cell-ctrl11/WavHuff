package com.josias.wavhuff

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val worker = Executors.newSingleThreadExecutor()

    // Palette (reprise de l'app : teal #146C60)
    private val green = Color.rgb(20, 108, 96)
    private val greenDark = Color.rgb(15, 82, 72)
    private val ground = Color.rgb(243, 247, 245)
    private val ink = Color.rgb(21, 39, 31)
    private val muted = Color.rgb(94, 113, 105)
    private val line = Color.rgb(227, 234, 231)
    private val tint = Color.rgb(230, 240, 237)
    private val disabledBg = Color.rgb(212, 222, 218)
    private val disabledInk = Color.rgb(138, 155, 149)

    private lateinit var content: LinearLayout
    private lateinit var fileCard: LinearLayout
    private lateinit var convert: Button
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var resultsZone: LinearLayout
    private lateinit var resultsContent: LinearLayout
    private lateinit var saveMp3: Button
    private lateinit var saveHuff: Button

    private val segViews = ArrayList<LinearLayout>()
    private val segNums = ArrayList<TextView>()
    private val segSubs = ArrayList<TextView>()
    private var selectedBitrate = 1
    private val bitrates = listOf(96, 128, 192)

    private var input: File? = null
    private var mp3: File? = null
    private var archive: File? = null
    private var originalName = "audio.wav"
    private var info: WavInfo? = null
    private var busy = false

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(ground); isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(30), dp(20), dp(24))
        }
        scroll.addView(content); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }

        buildHeader()
        content.addView(sectionLabel("01 — Fichier source"), marginTop(dp(8)))
        buildFileCard()
        content.addView(sectionLabel("02 — Qualité MP3"), marginTop(dp(22)))
        buildSegmented()
        buildConvert()
        buildResultsZone()
        buildFooter()

        renderFileCard()
        selectBitrate(1)
        styleConvert(false)
        setStatus("Prêt à commencer")
    }

    // ---------- UI construction ----------

    private fun buildHeader() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(chip(38, green, "wave", Color.WHITE, 11, 20))
        val name = TextView(this).apply {
            text = "WavHuff"; textSize = 19f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
        }
        header.addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(11) })
        val about = TextView(this).apply {
            text = "À propos"; textSize = 13f; setTextColor(green); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(8), dp(4), dp(8)); isClickable = true
            setOnClickListener { showAbout() }
        }
        header.addView(about)
        content.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
    }

    private fun buildFileCard() {
        fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            setOnClickListener { if (!busy) pickFile() }
        }
        content.addView(fileCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun renderFileCard() {
        fileCard.removeAllViews()
        if (input == null) {
            fileCard.background = roundRect(Color.WHITE, 18, line, 2, dashed = true)
            fileCard.addView(chip(46, tint, "up", green, 13, 24))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(bodyText("Choisir un fichier WAV", 16f, ink, bold = true))
            col.addView(bodyText("PCM 16 bits · mono ou stéréo", 13f, muted))
            fileCard.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        } else {
            fileCard.background = roundRect(Color.WHITE, 18, line, 1)
            fileCard.addView(chip(46, tint, "wave", green, 13, 22))
            val details = info
            val meta = if (details != null)
                "${fmtRate(details.sampleRate)} · ${if (details.channels == 1) "mono" else "stéréo"} · ${String.format(Locale.FRANCE, "%.1f", details.duration)} s · ${fmtMio(input!!.length())}"
            else fmtMio(input!!.length())
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(bodyText(originalName, 15f, ink, bold = true).apply {
                maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            col.addView(bodyText(meta, 12.5f, muted))
            fileCard.addView(col, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
            val replace = TextView(this).apply {
                text = "Remplacer"; textSize = 13f; setTextColor(green); typeface = Typeface.DEFAULT_BOLD
            }
            fileCard.addView(replace)
        }
    }

    private fun buildSegmented() {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bitrates.forEachIndexed { i, kb ->
            val sub = listOf("Léger", "Équilibré", "Qualité")[i]
            val seg = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(14), dp(6), dp(14))
                isClickable = true
                contentDescription = "$kb kbit/s, $sub"
                setOnClickListener { if (!busy) selectBitrate(i) }
            }
            val num = TextView(this).apply { text = kb.toString(); textSize = 19f; typeface = Typeface.DEFAULT_BOLD }
            val subTv = TextView(this).apply { text = sub; textSize = 11.5f; setPadding(0, dp(2), 0, 0) }
            seg.addView(num); seg.addView(subTv)
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            if (i > 0) lp.leftMargin = dp(8)
            row.addView(seg, lp)
            segViews.add(seg); segNums.add(num); segSubs.add(subTv)
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun selectBitrate(index: Int) {
        selectedBitrate = index
        segViews.forEachIndexed { i, seg ->
            val sel = i == index
            seg.background = roundRect(if (sel) green else Color.WHITE, 14, if (sel) green else line, if (sel) 0 else 1)
            segNums[i].setTextColor(if (sel) Color.WHITE else ink)
            segSubs[i].setTextColor(if (sel) Color.argb(220, 255, 255, 255) else muted)
        }
    }

    private fun buildConvert() {
        convert = Button(this).apply {
            text = "Convertir en MP3"; isAllCaps = false; textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { runConversion() }
            stateListAnimator = null
        }
        content.addView(convert, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(22) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(green)
        }
        content.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        status = TextView(this).apply { textSize = 12.5f; setTextColor(muted); gravity = Gravity.CENTER }
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun styleConvert(enabled: Boolean) {
        convert.isEnabled = enabled
        convert.background = roundRect(if (enabled) green else disabledBg, 16)
        convert.setTextColor(if (enabled) Color.WHITE else disabledInk)
    }

    private fun buildResultsZone() {
        resultsZone = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        resultsContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultsZone.addView(resultsContent, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })

        saveMp3 = Button(this).apply {
            text = "Enregistrer le MP3"; isAllCaps = false; textSize = 16f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = roundRect(green, 16); stateListAnimator = null
            setOnClickListener { createDocument(20, "audio/mpeg", "mp3") }
        }
        resultsZone.addView(saveMp3, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(16) })

        saveHuff = Button(this).apply {
            text = "Enregistrer l’archive .whf"; isAllCaps = false; textSize = 15f
            typeface = Typeface.DEFAULT_BOLD; setTextColor(green)
            background = roundRect(Color.WHITE, 16, Color.rgb(203, 217, 211), 1); stateListAnimator = null
            setOnClickListener { createDocument(21, "application/octet-stream", "whf") }
        }
        resultsZone.addView(saveHuff, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })

        val again = TextView(this).apply {
            text = "↺  Nouvelle conversion"; textSize = 13f; setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(6)); isClickable = true
            setOnClickListener { if (!busy) resetAll() }
        }
        resultsZone.addView(again, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        content.addView(resultsZone, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildFooter() {
        val footer = TextView(this).apply {
            text = "Hors ligne · aucun compte · Kotlin + LAME 3.100"
            textSize = 11.5f; setTextColor(Color.rgb(127, 147, 140)); gravity = Gravity.CENTER
        }
        content.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
    }

    // ---------- Results rendering ----------

    private fun renderResults(stats: CompressionStats, hs: CompressionStats, kbps: Int) {
        resultsContent.removeAllViews()

        // Carte héro teal
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(green, 20); setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        val ratioCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ratioCol.addView(TextView(this).apply {
            text = String.format(Locale.FRANCE, "%.1f:1", stats.ratio)
            textSize = 40f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        ratioCol.addView(TextView(this).apply {
            text = "Ratio WAV → MP3"; textSize = 13f; setTextColor(Color.argb(210, 255, 255, 255))
        })
        topRow.addView(ratioCol, LinearLayout.LayoutParams(0, -2, 1f))
        topRow.addView(badge(signedPercent(stats.gainPercent)))
        hero.addView(topRow, LinearLayout.LayoutParams(-1, -2))

        hero.addView(barRow("WAV", 100.0, fmtMio(stats.original)), marginTop(dp(16)))
        hero.addView(barRow("MP3", stats.remainingPercent, fmtMio(stats.compressed)), marginTop(dp(10)))
        hero.addView(TextView(this).apply {
            text = "$kbps kbit/s · le MP3 pèse ${String.format(Locale.FRANCE, "%.2f", stats.remainingPercent)} % du WAV"
            textSize = 12f; setTextColor(Color.argb(185, 255, 255, 255))
        }, marginTop(dp(12)))
        resultsContent.addView(hero, LinearLayout.LayoutParams(-1, -2))

        // Carte Huffman
        val huff = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.WHITE, 18, line, 1); setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val hHead = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hHead.addView(chip(38, Color.rgb(238, 241, 236), "shield", Color.rgb(60, 107, 78), 11, 20))
        val hCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        hCol.addView(bodyText("Archive Huffman", 15f, ink, bold = true))
        hCol.addView(bodyText("Kotlin · sans perte", 12f, muted))
        hHead.addView(hCol, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        hHead.addView(TextView(this).apply {
            text = ".whf"; textSize = 11.5f; setTextColor(Color.rgb(60, 107, 78)); typeface = Typeface.DEFAULT_BOLD
            background = roundRect(Color.rgb(238, 241, 236), 7); setPadding(dp(9), dp(4), dp(9), dp(4))
        })
        huff.addView(hHead, LinearLayout.LayoutParams(-1, -2))
        huff.addView(View(this).apply { setBackgroundColor(Color.rgb(237, 241, 239)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })
        val stat = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stat.addView(miniStat(fmtMio(hs.compressed), "Taille"), LinearLayout.LayoutParams(0, -2, 1f))
        stat.addView(miniStat(String.format(Locale.FRANCE, "%.1f %%", hs.gainPercent), "Gain"), LinearLayout.LayoutParams(0, -2, 1f))
        stat.addView(miniStat(String.format(Locale.FRANCE, "%.2f:1", hs.ratio), "Ratio"), LinearLayout.LayoutParams(0, -2, 1f))
        huff.addView(stat, LinearLayout.LayoutParams(-1, -2))
        resultsContent.addView(huff, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
    }

    private fun barRow(label: String, pct: Double, value: String): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = label; textSize = 11.5f; setTextColor(Color.argb(205, 255, 255, 255)); typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(dp(34), -2))
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(Color.argb(46, 255, 255, 255), 5)
            weightSum = 100f
        }
        val fillPct = Math.max(pct, 3.0).toFloat()
        track.addView(View(this).apply { background = roundRect(Color.WHITE, 5) },
            LinearLayout.LayoutParams(0, dp(9), fillPct))
        if (fillPct < 100f) track.addView(View(this), LinearLayout.LayoutParams(0, dp(9), 100f - fillPct))
        row.addView(track, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12); rightMargin = dp(12) })
        row.addView(TextView(this).apply {
            text = value; textSize = 12.5f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }, LinearLayout.LayoutParams(dp(60), -2))
        return row
    }

    private fun miniStat(value: String, label: String): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = value; textSize = 17f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD })
        col.addView(TextView(this).apply { text = label; textSize = 11.5f; setTextColor(muted); setPadding(0, dp(2), 0, 0) })
        return col
    }

    private fun badge(txt: String) = TextView(this).apply {
        text = txt; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        background = roundRect(Color.argb(40, 255, 255, 255), 999); setPadding(dp(12), dp(7), dp(12), dp(7))
    }

    // ---------- Helpers ----------

    private fun roundRect(fill: Int, radius: Int, stroke: Int = 0, strokeW: Int = 0, dashed: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(radius).toFloat()
            if (strokeW > 0) {
                if (dashed) setStroke(dp(strokeW), stroke, dp(6).toFloat(), dp(5).toFloat())
                else setStroke(dp(strokeW), stroke)
            }
        }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t.uppercase(Locale.FRANCE); textSize = 11f; setTextColor(muted)
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f
    }

    private fun bodyText(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(1), 0, dp(1))
    }

    private fun marginTop(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top }

    private fun chip(sizeDp: Int, bgColor: Int, kind: String, iconColor: Int, radiusDp: Int, iconDp: Int): FrameLayout {
        val f = FrameLayout(this)
        f.background = roundRect(bgColor, radiusDp)
        f.addView(IconView(this, kind, iconColor), FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
        f.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        return f
    }

    private fun fmtMio(n: Long) = String.format(Locale.FRANCE, "%.2f Mio", n / 1048576.0)
    private fun fmtRate(hz: Int) = if (hz % 1000 == 0) "$hz Hz" else String.format(Locale.FRANCE, "%.1f kHz", hz / 1000.0)
    private fun signedPercent(gain: Double): String =
        (if (gain >= 0) "−" else "+") + String.format(Locale.FRANCE, "%.2f", Math.abs(gain)) + " %"

    private fun setStatus(t: String) { status.text = t }

    // ---------- Icône vectorielle minimale ----------

    private inner class IconView(ctx: Context, private val kind: String, strokeColor: Int) : View(ctx) {
        private val d = resources.displayMetrics.density
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = strokeColor; strokeWidth = 1.8f * d
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = strokeColor }
        override fun onDraw(c: Canvas) {
            val s = Math.min(width, height).toFloat()
            fun px(f: Float) = f * s
            when (kind) {
                "wave" -> {
                    val hs = floatArrayOf(0.34f, 0.6f, 0.94f, 0.52f)
                    val bw = 0.1f * s; val gap = 0.07f * s
                    val total = hs.size * bw + (hs.size - 1) * gap
                    var x = (s - total) / 2f
                    for (fr in hs) {
                        val bh = s * 0.6f * fr + s * 0.1f
                        val top = (s - bh) / 2f
                        c.drawRoundRect(x, top, x + bw, top + bh, bw / 2, bw / 2, fill)
                        x += bw + gap
                    }
                }
                "up" -> {
                    c.drawLine(px(0.5f), px(0.2f), px(0.5f), px(0.66f), stroke)
                    val a = Path().apply { moveTo(px(0.32f), px(0.38f)); lineTo(px(0.5f), px(0.2f)); lineTo(px(0.68f), px(0.38f)) }
                    c.drawPath(a, stroke)
                    c.drawLine(px(0.24f), px(0.8f), px(0.76f), px(0.8f), stroke)
                }
                "down" -> {
                    c.drawLine(px(0.5f), px(0.2f), px(0.5f), px(0.64f), stroke)
                    val a = Path().apply { moveTo(px(0.32f), px(0.46f)); lineTo(px(0.5f), px(0.64f)); lineTo(px(0.68f), px(0.46f)) }
                    c.drawPath(a, stroke)
                    c.drawLine(px(0.24f), px(0.82f), px(0.76f), px(0.82f), stroke)
                }
                "shield" -> {
                    val p = Path().apply {
                        moveTo(px(0.5f), px(0.14f)); lineTo(px(0.82f), px(0.28f))
                        lineTo(px(0.82f), px(0.52f))
                        cubicTo(px(0.82f), px(0.74f), px(0.68f), px(0.84f), px(0.5f), px(0.9f))
                        cubicTo(px(0.32f), px(0.84f), px(0.18f), px(0.74f), px(0.18f), px(0.52f))
                        lineTo(px(0.18f), px(0.28f)); close()
                    }
                    c.drawPath(p, stroke)
                    val chk = Path().apply { moveTo(px(0.38f), px(0.5f)); lineTo(px(0.47f), px(0.59f)); lineTo(px(0.64f), px(0.42f)) }
                    c.drawPath(chk, stroke)
                }
            }
        }
    }

    // ---------- Logique (inchangée) ----------

    private fun pickFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }, 10)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        fileCard.isEnabled = !value
        for (seg in segViews) seg.isEnabled = !value
        styleConvert(!value && input != null)
        saveMp3.isEnabled = !value && mp3 != null
        saveHuff.isEnabled = !value && archive != null
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }

    private fun resetAll() {
        input?.delete(); mp3?.delete(); archive?.delete()
        input = null; mp3 = null; archive = null; info = null; originalName = "audio.wav"
        resultsZone.visibility = View.GONE
        renderFileCard(); styleConvert(false); setStatus("Prêt à commencer")
    }

    private fun createDocument(code: Int, mime: String, extension: String) {
        val name = originalName.substringBeforeLast('.', originalName)
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mime; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, "$name.$extension")
        }, code)
    }

    @Deprecated("Legacy result API retained for a dependency-free Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || busy) return
        val uri = data?.data ?: return
        if (requestCode == 10) loadInput(uri)
        else if (requestCode == 20 || requestCode == 21) {
            val file = if (requestCode == 20) mp3 else archive
            if (file != null) {
                setBusy(true); setStatus("Enregistrement…")
                worker.execute {
                    try {
                        contentResolver.openOutputStream(uri, "wt").use { out ->
                            requireNotNull(out) { "Destination inaccessible." }; file.inputStream().use { it.copyTo(out) }
                        }
                        runOnUiThread { setStatus("Fichier enregistré à l’emplacement choisi.") }
                    } catch (e: Exception) { showError(e) }
                    finally { runOnUiThread { setBusy(false) } }
                }
            }
        }
    }

    private fun loadInput(uri: Uri) {
        setBusy(true); setStatus("Lecture du WAV…")
        worker.execute {
            val file = File.createTempFile("source-", ".wav", cacheDir)
            try {
                var name = "audio.wav"
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0) ?: name
                }
                contentResolver.openInputStream(uri).use { src ->
                    requireNotNull(src) { "Fichier inaccessible." }; file.outputStream().use { src.copyTo(it) }
                }
                val details = WavReader.read(file)
                runOnUiThread {
                    input?.delete(); mp3?.delete(); archive?.delete(); mp3 = null; archive = null
                    input = file; info = details; originalName = name
                    resultsZone.visibility = View.GONE
                    renderFileCard(); setStatus("Fichier WAV valide.")
                }
            } catch (e: Exception) {
                file.delete()
                runOnUiThread { setStatus("Sélection non validée. Le fichier précédent reste disponible.") }
            }
            finally { runOnUiThread { setBusy(false) } }
        }
    }

    private fun runConversion() {
        val source = input ?: return; val details = info ?: return
        val kbps = bitrates[selectedBitrate]
        setBusy(true); progress.progress = 0; setStatus("Encodage MP3 (Huffman dans LAME)…")
        worker.execute {
            val target = File.createTempFile("converted-", ".mp3", cacheDir)
            val huff = File.createTempFile("huffman-", ".whf", cacheDir)
            try {
                Mp3Encoder.convert(source, target, details, kbps) { p -> runOnUiThread { progress.progress = p * 70 / 100 } }
                runOnUiThread { setStatus("Compression Huffman en Kotlin…") }
                Huffman.compress(source, huff) { p -> runOnUiThread { progress.progress = 70 + p * 30 / 100 } }
                val stats = CompressionStats(source.length(), target.length())
                val hs = CompressionStats(source.length(), huff.length())
                runOnUiThread {
                    mp3?.delete(); archive?.delete(); mp3 = target; archive = huff
                    renderResults(stats, hs, kbps)
                    resultsZone.visibility = View.VISIBLE
                    setStatus("Conversion terminée.")
                }
            } catch (e: Throwable) {
                target.delete(); huff.delete(); showError(e)
            } finally { runOnUiThread { setBusy(false) } }
        }
    }

    private fun showError(e: Throwable) = runOnUiThread { setStatus("Erreur : ${e.message ?: "opération impossible"}") }

    private fun showAbout() {
        val license = assets.open("LAME-COPYING.txt").bufferedReader().use { it.readText() }
        AlertDialog.Builder(this)
            .setTitle("WavHuff • LAME 3.100")
            .setMessage("Interface et Huffman : Kotlin. MP3 : LAME via JNI.\nSources : github.com/Josi-Cell-ctrl11/WavHuff\n\n$license")
            .setPositiveButton("Fermer", null).show()
    }

    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
