package com.josias.wavhuff

import java.io.*
import java.util.PriorityQueue

/** Huffman statique sur les octets, sans perte. Format WHF1, distinct d'un MP3. */
object Huffman {
    // Une feuille représente un octet (0 à 255). Son poids est son nombre d’occurrences.
    // Un nœud interne réunit deux enfants ; son poids est la somme de leurs poids.
    private data class Node(val weight: Long, val symbol: Int, val left: Node? = null, val right: Node? = null)
    /** Reconstruit le même arbre à partir des fréquences, à la compression et à la lecture. */
    private fun tree(freq: LongArray): Node? {
        // La file de priorité sort toujours le plus petit poids en premier.
        // À poids égal, le symbole départage les nœuds : l’arbre reste déterministe.
        val q = PriorityQueue<Node>(compareBy<Node> { it.weight }.thenBy { it.symbol })
        // Seuls les octets effectivement présents deviennent des feuilles.
        freq.forEachIndexed { i, n -> if (n > 0) q.add(Node(n, i)) }
        // Fusionner les deux plus petits poids, puis remettre leur parent dans la file.
        // Les symboles fréquents se retrouvent près de la racine : leur code est plus court.
        while (q.size > 1) { val a = q.remove(); val b = q.remove(); q.add(Node(a.weight + b.weight, minOf(a.symbol,b.symbol), a,b)) }
        return q.poll()
    }
    /**
     * Compresse tous les octets du WAV (en-tête compris), sans modifier le son.
     * Deux lectures sont nécessaires : compter les fréquences, puis écrire les codes.
     * [progress] reçoit un pourcentage pendant la deuxième lecture.
     * La sortie est une archive .whf, pas un MP3.
     */
    fun compress(input: File, output: File, progress: (Int) -> Unit = {}) {
        // Un octet a 256 valeurs possibles. Long évite de compter dans un Int trop petit.
        val freq = LongArray(256)
        // Lire par blocs de 64 Kio évite de charger tout le WAV en mémoire.
        // Kotlin Byte est signé : « and 255 » retrouve la valeur non signée 0..255.
        input.inputStream().buffered().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; for (i in 0 until n) freq[b[i].toInt() and 255]++ } }
        // Chaque indice contiendra le chemin binaire de l’octet correspondant.
        val codes = arrayOfNulls<String>(256)
        // Descente à gauche = 0, à droite = 1. Une feuille termine le code.
        // Avec un seul symbole, utiliser « 0 » plutôt qu’un code vide.
        fun visit(n: Node, prefix: String) { if (n.left == null) codes[n.symbol] = prefix.ifEmpty { "0" } else { visit(n.left, prefix+"0"); visit(n.right!!, prefix+"1") } }
        tree(freq)?.let { visit(it, "") }
        DataOutputStream(output.outputStream().buffered()).use { out ->
            // En-tête : signature WHF1 + 256 fréquences Long (8 octets chacune).
            // DataOutputStream écrit ces entiers en big endian. Taille fixe : 2052 octets.
            out.writeBytes("WHF1"); freq.forEach { out.writeLong(it) }
            // bits accumule les bits ; count indique combien sont déjà présents.
            var bits = 0; var count = 0; var done = 0L
            input.inputStream().buffered().use { s ->
                val b = ByteArray(65536)
                while (true) {
                    val n = s.read(b); if (n < 0) break
                    for (i in 0 until n) for (bit in codes[b[i].toInt() and 255]!!) {
                        // Décaler d’un bit vers la gauche puis ajouter 0 ou 1.
                        bits = (bits shl 1) or (bit - '0'); count++
                        // Huit bits remplissent un octet : on peut l’écrire sur disque.
                        if (count == 8) { out.writeByte(bits); bits = 0; count = 0 }
                    }
                    done += n; progress((done * 100 / maxOf(1L, input.length())).toInt())
                }
            }
            // Compléter le dernier octet par des zéros à droite.
            // La somme des fréquences permet au décodeur de ne pas lire ce remplissage.
            if (count > 0) out.writeByte(bits shl (8-count))
        }
    }
    /** Décode une archive WHF1. Fonction utilisée par les tests ; pas de bouton de décompression dans l’interface. */
    fun decompress(input: File, output: File) {
        DataInputStream(input.inputStream().buffered()).use { src ->
            // 0x57484631 est la signature ASCII « WHF1 ». Rejeter un autre format.
            require(src.readInt() == 0x57484631) { "Archive WHF1 attendue." }
            val freq = LongArray(256) { src.readLong().also { require(it >= 0) } }
            // La taille d’origine est la somme des occurrences ; addExact détecte un débordement.
            var size = 0L; freq.forEach { size = Math.addExact(size,it) }
            val root = tree(freq)
            output.outputStream().buffered().use { out ->
                var byte = 0; var remaining = 0
                // Lire un octet si nécessaire, puis rendre ses bits du plus fort au plus faible.
                fun bit(): Int { if (remaining == 0) { byte = src.readUnsignedByte(); remaining = 8 }; remaining--; return (byte shr remaining) and 1 }
                // Pour chaque octet d’origine, repartir de la racine et suivre les bits jusqu’à une feuille.
                repeatLong(size) {
                    var node = requireNotNull(root)
                    if (node.left == null) require(bit() == 0) else while (node.left != null) node = if (bit() == 0) node.left!! else node.right!!
                    out.write(node.symbol)
                }
            }
        }
    }
    // Une boucle Long accepte un nombre d’octets supérieur à la capacité d’un Int.
    private inline fun repeatLong(n: Long, block: () -> Unit) { var i = 0L; while (i < n) { block(); i++ } }
}
