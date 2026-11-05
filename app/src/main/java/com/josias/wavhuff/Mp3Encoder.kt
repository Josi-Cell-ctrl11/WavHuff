package com.josias.wavhuff

import java.io.File
import java.io.RandomAccessFile

/**
 * Relie le PCM lu en Kotlin à l’encodeur MP3 LAME, écrit en C.
 * Les méthodes external sont implémentées dans cpp/bridge.c grâce à JNI.
 * Le Huffman normalisé du MP3 est exécuté par LAME ; Huffman.kt est le moteur .whf.
 */
object Mp3Encoder {
    // Android charge libwavhuff.so ; cette bibliothèque est liée à libmp3lame.so.
    init { System.loadLibrary("wavhuff") }
    // Le handle est l’adresse du contexte C, représentée par un Long côté Kotlin.
    // On le transmet aux appels natifs sans le modifier ni le déréférencer en Kotlin.
    private external fun open(rate: Int, channels: Int, bitrate: Int, frames: Long): Long
    private external fun encode(handle: Long, pcm: ShortArray, frames: Int, out: ByteArray): Int
    private external fun finish(handle: Long, out: ByteArray): Int
    private external fun tag(handle: Long): ByteArray
    private external fun close(handle: Long)

    /** Encode uniquement le bloc audio du WAV, puis termine et ferme le MP3. */
    fun convert(wav: File, output: File, info: WavInfo, bitrate: Int, progress: (Int) -> Unit) {
        // Créer un contexte LAME configuré avec le WAV et le débit choisi (kbit/s).
        val handle = open(info.sampleRate, info.channels, bitrate, info.frames)
        check(handle != 0L) { "Impossible d'initialiser l'encodeur MP3." }
        try {
            RandomAccessFile(wav,"r").use { input ->
                output.outputStream().buffered().use { out ->
                    // Sauter les en-têtes RIFF et les éventuelles métadonnées.
                    input.seek(info.dataOffset)
                    // Traiter 8192 trames à la fois : mémoire fixe, même pour un long fichier.
                    // raw = octets du WAV ; pcm = valeurs signées 16 bits ; mp3 = sortie encodée.
                    // Le tampon MP3 de 20000 octets dépasse la marge LAME : 1,25 × 8192 + 7200.
                    val raw = ByteArray(8192 * info.channels * 2)
                    val pcm = ShortArray(8192 * info.channels)
                    val mp3 = ByteArray(20000)
                    var remaining = info.dataSize
                    while (remaining > 0) {
                        val n = minOf(remaining, raw.size.toLong()).toInt(); input.readFully(raw,0,n)
                        // Recomposer chaque entier 16 bits little endian à partir de deux octets.
                        // Le premier octet est masqué pour éviter son extension de signe.
                        for (i in 0 until n/2) pcm[i] = ((raw[i*2].toInt() and 255) or (raw[i*2+1].toInt() shl 8)).toShort()
                        // Une trame stéréo = 2 échantillons ; LAME attend le nombre par canal.
                        val written = encode(handle,pcm,n/(info.channels*2),mp3)
                        // Valeur négative = erreur LAME ; sinon écrire seulement les octets produits.
                        check(written >= 0) { "Erreur d'encodage MP3 : $written" }; out.write(mp3,0,written)
                        remaining -= n; progress(((info.dataSize-remaining)*100/info.dataSize).toInt())
                    }
                    // LAME garde des données en attente : flush écrit les dernières trames MP3.
                    val written = finish(handle,mp3)
                    check(written >= 0) { "Erreur de finalisation MP3 : $written" }; out.write(mp3,0,written)
                }
            }
            // Remplacer la trame réservée au début par le tag LAME final :
            // il décrit notamment la durée et le délai de l’encodeur pour les lecteurs compatibles.
            val frame = tag(handle)
            if (frame.isNotEmpty()) RandomAccessFile(output,"rw").use { it.write(frame) }
            check(output.length() > 0) { "Le MP3 est vide." }
        // Échec : supprimer une sortie partielle. finally libère toujours le contexte natif.
        } catch (e: Exception) { output.delete(); throw e } finally { close(handle) }
    }
}
