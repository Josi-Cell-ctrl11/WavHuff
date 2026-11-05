package com.josias.wavhuff

import java.io.File
import java.io.RandomAccessFile

data class WavInfo(val sampleRate: Int, val channels: Int, val dataOffset: Long, val dataSize: Long) {
    val frames get() = dataSize / (channels * 2)
    val duration get() = frames.toDouble() / sampleRate
}

object WavReader {
    fun read(file: File): WavInfo = RandomAccessFile(file, "r").use { f ->
        fun id(): String { val b = ByteArray(4); f.readFully(b); return String(b, Charsets.US_ASCII) }
        fun u16(): Int = java.lang.Short.reverseBytes(f.readShort()).toInt() and 65535
        fun u32(): Long = java.lang.Integer.reverseBytes(f.readInt()).toLong() and 0xffffffffL
        require(f.length() >= 12 && id() == "RIFF") { "Fichier WAV RIFF attendu." }
        val end = u32() + 8
        require(end in 12..f.length() && id() == "WAVE") { "En-tête WAV invalide ou fichier tronqué." }
        var rate = 0; var channels = 0; var offset = -1L; var size = 0L; var formatFound = false
        while (f.filePointer + 8 <= end) {
            val chunk = id(); val length = u32(); val start = f.filePointer
            require(length <= end - start) { "Bloc WAV tronqué." }
            when (chunk) {
                "fmt " -> {
                    require(!formatFound && length >= 16) { "Bloc de format WAV invalide." }
                    require(u16() == 1) { "Seuls les WAV PCM 16 bits sont acceptés (pas de float, ADPCM ou WAV extensible)." }
                    channels = u16(); val sampleRate = u32(); val byteRate = u32(); val align = u16(); val bits = u16()
                    require(channels in 1..2 && bits == 16) { "WAV PCM 16 bits mono ou stéréo requis." }
                    require(sampleRate in listOf(8000L,11025L,12000L,16000L,22050L,24000L,32000L,44100L,48000L)) { "Fréquence WAV non prise en charge." }
                    require(align == channels * 2 && byteRate == sampleRate * align) { "Alignement PCM invalide." }
                    rate = sampleRate.toInt(); formatFound = true
                }
                "data" -> { require(offset < 0) { "Plusieurs blocs audio non pris en charge." }; offset = start; size = length }
            }
            val next = start + length + (length and 1)
            require(next <= end) { "Octet de remplissage WAV manquant." }
            f.seek(next)
        }
        require(formatFound && offset >= 0 && size > 0 && size % (channels * 2) == 0L) { "Données PCM absentes ou incomplètes." }
        WavInfo(rate, channels, offset, size)
    }
}

data class CompressionStats(val original: Long, val compressed: Long) {
    init { require(original > 0 && compressed > 0) }
    val gainPercent get() = (1.0 - compressed.toDouble() / original) * 100
    val remainingPercent get() = compressed.toDouble() / original * 100
    val ratio get() = original.toDouble() / compressed
}
