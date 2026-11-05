package com.josias.wavhuff

import java.io.File
import java.io.RandomAccessFile

/** Informations nécessaires pour lire uniquement les échantillons PCM du WAV. */
data class WavInfo(val sampleRate: Int, val channels: Int, val dataOffset: Long, val dataSize: Long) {
    // Une trame PCM contient un échantillon par canal, chacun sur 2 octets (16 bits).
    // En stéréo : gauche + droite = 4 octets par trame.
    val frames get() = dataSize / (channels * 2)
    // Échantillons par canal / échantillons par seconde = durée en secondes.
    val duration get() = frames.toDouble() / sampleRate
}

/**
 * Lit le conteneur RIFF/WAVE : le son ne commence pas forcément à l’octet 44.
 * Certains WAV contiennent des métadonnées ou des blocs supplémentaires à ignorer.
 * Cette version accepte uniquement PCM signé 16 bits, mono ou stéréo.
 */
object WavReader {
    // use ferme automatiquement le fichier, même si une vérification lève une exception.
    fun read(file: File): WavInfo = RandomAccessFile(file, "r").use { f ->
        // Un identifiant de bloc contient exactement quatre caractères ASCII.
        fun id(): String { val b = ByteArray(4); f.readFully(b); return String(b, Charsets.US_ASCII) }
        // WAV stocke les nombres en little endian. RandomAccessFile lit en big endian :
        // reverseBytes inverse l’ordre des octets ; le masque conserve une valeur non signée.
        fun u16(): Int = java.lang.Short.reverseBytes(f.readShort()).toInt() and 65535
        fun u32(): Long = java.lang.Integer.reverseBytes(f.readInt()).toLong() and 0xffffffffL
        // Vérifier la signature RIFF avant de faire confiance aux tailles annoncées.
        require(f.length() >= 12 && id() == "RIFF") { "Fichier WAV RIFF attendu." }
        // La taille RIFF n’inclut pas ses 8 premiers octets (signature + taille).
        val end = u32() + 8
        require(end in 12..f.length() && id() == "WAVE") { "En-tête WAV invalide ou fichier tronqué." }
        var rate = 0; var channels = 0; var offset = -1L; var size = 0L; var formatFound = false
        // Chaque bloc possède un en-tête de 8 octets : identifiant + longueur.
        while (f.filePointer + 8 <= end) {
            val chunk = id(); val length = u32(); val start = f.filePointer
            // Un bloc ne doit jamais dépasser la fin annoncée du conteneur.
            require(length <= end - start) { "Bloc WAV tronqué." }
            when (chunk) {
                // Le bloc « fmt » décrit le codage, les canaux et la fréquence.
                "fmt " -> {
                    require(!formatFound && length >= 16) { "Bloc de format WAV invalide." }
                    // Le code de format 1 signifie PCM entier non compressé.
                    require(u16() == 1) { "Seuls les WAV PCM 16 bits sont acceptés (pas de float, ADPCM ou WAV extensible)." }
                    channels = u16(); val sampleRate = u32(); val byteRate = u32(); val align = u16(); val bits = u16()
                    require(channels in 1..2 && bits == 16) { "WAV PCM 16 bits mono ou stéréo requis." }
                    require(sampleRate in listOf(8000L,11025L,12000L,16000L,22050L,24000L,32000L,44100L,48000L)) { "Fréquence WAV non prise en charge." }
                    // Vérifier la cohérence : octets/trame = canaux × 2 ;
                    // octets/seconde = fréquence × octets/trame.
                    require(align == channels * 2 && byteRate == sampleRate * align) { "Alignement PCM invalide." }
                    rate = sampleRate.toInt(); formatFound = true
                }
                // Mémoriser le début et la taille du son ; les échantillons seront lus plus tard.
                "data" -> { require(offset < 0) { "Plusieurs blocs audio non pris en charge." }; offset = start; size = length }
            }
            // RIFF aligne chaque bloc sur une adresse paire.
            // Si sa longueur est impaire, sauter également l’octet de remplissage.
            val next = start + length + (length and 1)
            require(next <= end) { "Octet de remplissage WAV manquant." }
            f.seek(next)
        }
        // Il faut un format valide, des données non vides et un nombre entier de trames.
        require(formatFound && offset >= 0 && size > 0 && size % (channels * 2) == 0L) { "Données PCM absentes ou incomplètes." }
        WavInfo(rate, channels, offset, size)
    }
}

/**
 * Statistiques à partir des tailles réelles sur disque, en-têtes inclus.
 * Exemple : WAV = 1000 octets, MP3 = 250 octets → gain 75 %, restant 25 %, ratio 4:1.
 * Un gain négatif signifie que la sortie est plus grosse que le fichier d’origine.
 */
data class CompressionStats(val original: Long, val compressed: Long) {
    init { require(original > 0 && compressed > 0) }
    // toDouble empêche une division entière qui ferait perdre les décimales.
    val gainPercent get() = (1.0 - compressed.toDouble() / original) * 100
    val remainingPercent get() = compressed.toDouble() / original * 100
    val ratio get() = original.toDouble() / compressed
}
