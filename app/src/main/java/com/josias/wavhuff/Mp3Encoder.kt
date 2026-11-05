package com.josias.wavhuff

import java.io.File
import java.io.RandomAccessFile

object Mp3Encoder {
    init { System.loadLibrary("wavhuff") }
    private external fun open(rate: Int, channels: Int, bitrate: Int, frames: Long): Long
    private external fun encode(handle: Long, pcm: ShortArray, frames: Int, out: ByteArray): Int
    private external fun finish(handle: Long, out: ByteArray): Int
    private external fun tag(handle: Long): ByteArray
    private external fun close(handle: Long)

    fun convert(wav: File, output: File, info: WavInfo, bitrate: Int, progress: (Int) -> Unit) {
        val handle = open(info.sampleRate, info.channels, bitrate, info.frames)
        check(handle != 0L) { "Impossible d'initialiser l'encodeur MP3." }
        try {
            RandomAccessFile(wav,"r").use { input ->
                output.outputStream().buffered().use { out ->
                    input.seek(info.dataOffset)
                    val raw = ByteArray(8192 * info.channels * 2)
                    val pcm = ShortArray(8192 * info.channels)
                    val mp3 = ByteArray(20000)
                    var remaining = info.dataSize
                    while (remaining > 0) {
                        val n = minOf(remaining, raw.size.toLong()).toInt(); input.readFully(raw,0,n)
                        for (i in 0 until n/2) pcm[i] = ((raw[i*2].toInt() and 255) or (raw[i*2+1].toInt() shl 8)).toShort()
                        val written = encode(handle,pcm,n/(info.channels*2),mp3)
                        check(written >= 0) { "Erreur d'encodage MP3 : $written" }; out.write(mp3,0,written)
                        remaining -= n; progress(((info.dataSize-remaining)*100/info.dataSize).toInt())
                    }
                    val written = finish(handle,mp3)
                    check(written >= 0) { "Erreur de finalisation MP3 : $written" }; out.write(mp3,0,written)
                }
            }
            val frame = tag(handle)
            if (frame.isNotEmpty()) RandomAccessFile(output,"rw").use { it.write(frame) }
            check(output.length() > 0) { "Le MP3 est vide." }
        } catch (e: Exception) { output.delete(); throw e } finally { close(handle) }
    }
}
