package com.josias.wavhuff

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowInsets
import android.widget.*
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var picked: TextView
    private lateinit var results: TextView
    private lateinit var progress: ProgressBar
    private lateinit var bitrate: Spinner
    private lateinit var select: Button
    private lateinit var convert: Button
    private lateinit var saveMp3: Button
    private lateinit var saveHuff: Button
    private var input: File? = null
    private var mp3: File? = null
    private var archive: File? = null
    private var originalName = "audio.wav"
    private var info: WavInfo? = null
    private var busy = false
    private val green = Color.rgb(20,108,96)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(27,48,43)); setPadding(0,dp(7),0,dp(7))
        if (bold) setTypeface(typeface,Typeface.BOLD)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(green); setOnClickListener { action() }
        content.addView(this,LinearLayout.LayoutParams(-1,dp(56)).apply { topMargin = dp(8) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(243,247,245)); isFillViewport = true }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(20),dp(24),dp(32)) }
        scroll.addView(content); setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        content.addView(text("AUDIO • COMPRESSION",12f,true))
        content.addView(text("WavHuff",38f,true))
        content.addView(text("Votre WAV. Un MP3 plus léger.",21f,true))
        content.addView(text("Conversion hors ligne • Kotlin + Huffman\nWAV PCM 16 bits, mono ou stéréo. Aucun compte requis.",14f))
        select = button("1. Choisir un fichier WAV") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) },10)
        }
        picked = text("Aucun fichier sélectionné",14f); content.addView(picked)
        content.addView(text("2. Qualité du MP3",18f,true))
        bitrate = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("96 kbit/s — fichier léger","128 kbit/s — équilibré","192 kbit/s — haute qualité"))
            setSelection(1)
        }; content.addView(bitrate,LinearLayout.LayoutParams(-1,dp(52)))
        convert = button("Convertir en MP3") { runConversion() }; convert.isEnabled = false
        progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }; content.addView(progress)
        status = text("Prêt à commencer",14f); content.addView(status)
        results = text("Les tailles et le ratio apparaîtront ici après la conversion.",16f).apply {
            setPadding(dp(18),dp(16),dp(18),dp(16))
            background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        }; content.addView(results)
        saveMp3 = button("Enregistrer le MP3") { createDocument(20,"audio/mpeg","mp3") }; saveMp3.isEnabled = false
        saveHuff = button("Enregistrer l’archive Huffman (.whf)") { createDocument(21,"application/octet-stream","whf") }; saveHuff.isEnabled = false
        content.addView(text("COMPRENDRE HUFFMAN",12f,true))
        content.addView(text("Le MP3 utilise un codage Huffman sur les coefficients audio quantifiés, dans l’encodeur LAME. C’est une compression avec perte.\n\nEn complément, notre algorithme Huffman en Kotlin compresse les octets du WAV sans perte dans une archive .whf. Ce fichier n’est pas un MP3 et peut être plus volumineux selon le son.",14f))
        button("À propos et licences") {
            val license = assets.open("LAME-COPYING.txt").bufferedReader().use { it.readText() }
            AlertDialog.Builder(this).setTitle("WavHuff • LAME 3.100").setMessage("Interface et Huffman : Kotlin. MP3 : LAME via JNI. Sources : github.com/Josi-Cell-ctrl11/WavHuff\n\n$license").setPositiveButton("Fermer",null).show()
        }
    }
    private fun setBusy(value: Boolean) {
        busy = value; select.isEnabled = !value; convert.isEnabled = !value && input != null; bitrate.isEnabled = !value
        saveMp3.isEnabled = !value && mp3 != null; saveHuff.isEnabled = !value && archive != null
        progress.visibility = if (value) View.VISIBLE else View.GONE
    }
    private fun displaySize(n: Long) = String.format(Locale.FRANCE,"%,d octets (%.2f Mio)",n,n/1048576.0)
    private fun createDocument(code: Int, mime: String, extension: String) {
        val name = originalName.substringBeforeLast('.',originalName)
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = mime; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"$name.$extension") },code)
    }
    @Deprecated("Legacy result API retained for a dependency-free Activity")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK || busy) return
        val uri = data?.data ?: return
        if (requestCode == 10) loadInput(uri)
        else if (requestCode == 20 || requestCode == 21) {
            val file = if (requestCode == 20) mp3 else archive
            if (file != null) {
                setBusy(true); status.text = "Enregistrement…"
                worker.execute {
                    try {
                        contentResolver.openOutputStream(uri,"wt").use { out -> requireNotNull(out) { "Destination inaccessible." }; file.inputStream().use { it.copyTo(out) } }
                        runOnUiThread { status.text = "Fichier enregistré à l’emplacement choisi." }
                    } catch (e: Exception) { showError(e) }
                    finally { runOnUiThread { setBusy(false) } }
                }
            }
        }
    }
    private fun loadInput(uri: Uri) {
        setBusy(true); status.text = "Lecture du WAV…"; results.text = "Vérification du fichier…"
        worker.execute {
            val file = File.createTempFile("source-",".wav",cacheDir)
            try {
                var name = "audio.wav"
                contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) ?: name }
                contentResolver.openInputStream(uri).use { src -> requireNotNull(src) { "Fichier inaccessible." }; file.outputStream().use { src.copyTo(it) } }
                val details = WavReader.read(file)
                runOnUiThread {
                    input?.delete(); mp3?.delete(); archive?.delete(); mp3 = null; archive = null
                    input = file; info = details; originalName = name
                    picked.text = "$name\n${displaySize(file.length())}\n${details.sampleRate} Hz • ${details.channels} canal/canaux • ${String.format(Locale.FRANCE,"%.1f",details.duration)} s"
                    results.text = "WAV prêt. Choisissez la qualité puis lancez la conversion."; status.text = "Fichier WAV valide."
                }
            } catch (e: Exception) { file.delete(); runOnUiThread { results.text = "Sélection non validée. Le fichier précédent reste disponible." }; showError(e) }
            finally { runOnUiThread { setBusy(false) } }
        }
    }
    private fun runConversion() {
        val source = input ?: return; val details = info ?: return
        val kbps = listOf(96,128,192)[bitrate.selectedItemPosition]
        setBusy(true); progress.progress = 0; status.text = "Encodage MP3 (Huffman dans LAME)…"
        worker.execute {
            val target = File.createTempFile("converted-",".mp3",cacheDir)
            val huff = File.createTempFile("huffman-",".whf",cacheDir)
            try {
                Mp3Encoder.convert(source,target,details,kbps) { p -> runOnUiThread { progress.progress = p*70/100 } }
                runOnUiThread { status.text = "Compression Huffman en Kotlin…" }
                Huffman.compress(source,huff) { p -> runOnUiThread { progress.progress = 70+p*30/100 } }
                val stats = CompressionStats(source.length(),target.length())
                val hs = CompressionStats(source.length(),huff.length())
                runOnUiThread {
                    mp3?.delete(); archive?.delete(); mp3 = target; archive = huff
                    results.text = "RÉSULTAT MP3\n\nWAV d’origine\n${displaySize(stats.original)}\n\nMP3 produit\n${displaySize(stats.compressed)}\n\nGain de place : ${String.format(Locale.FRANCE,"%.2f",stats.gainPercent)} %\nTaille restante : ${String.format(Locale.FRANCE,"%.2f",stats.remainingPercent)} %\nRatio WAV / MP3 : ${String.format(Locale.FRANCE,"%.2f",stats.ratio)}:1\n\nHUFFMAN KOTLIN • SANS PERTE\nArchive : ${displaySize(hs.compressed)}\nGain : ${String.format(Locale.FRANCE,"%.2f",hs.gainPercent)} %\nRatio : ${String.format(Locale.FRANCE,"%.2f",hs.ratio)}:1"
                    status.text = "Conversion terminée. Vous pouvez enregistrer les fichiers."
                }
            } catch (e: Throwable) {
                target.delete(); huff.delete(); showError(e)
            } finally { runOnUiThread { setBusy(false) } }
        }
    }
    private fun showError(e: Throwable) = runOnUiThread { status.text = "Erreur : ${e.message ?: "opération impossible"}" }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
