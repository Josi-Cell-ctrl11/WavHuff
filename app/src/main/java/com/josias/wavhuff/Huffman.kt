package com.josias.wavhuff

import java.io.*
import java.util.PriorityQueue

/** Huffman statique sur les octets, sans perte. Format WHF1, distinct d'un MP3. */
object Huffman {
    private data class Node(val weight: Long, val symbol: Int, val left: Node? = null, val right: Node? = null)
    private fun tree(freq: LongArray): Node? {
        val q = PriorityQueue<Node>(compareBy<Node> { it.weight }.thenBy { it.symbol })
        freq.forEachIndexed { i, n -> if (n > 0) q.add(Node(n, i)) }
        while (q.size > 1) { val a = q.remove(); val b = q.remove(); q.add(Node(a.weight + b.weight, minOf(a.symbol,b.symbol), a,b)) }
        return q.poll()
    }
    fun compress(input: File, output: File, progress: (Int) -> Unit = {}) {
        val freq = LongArray(256)
        input.inputStream().buffered().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; for (i in 0 until n) freq[b[i].toInt() and 255]++ } }
        val codes = arrayOfNulls<String>(256)
        fun visit(n: Node, prefix: String) { if (n.left == null) codes[n.symbol] = prefix.ifEmpty { "0" } else { visit(n.left, prefix+"0"); visit(n.right!!, prefix+"1") } }
        tree(freq)?.let { visit(it, "") }
        DataOutputStream(output.outputStream().buffered()).use { out ->
            out.writeBytes("WHF1"); freq.forEach { out.writeLong(it) }
            var bits = 0; var count = 0; var done = 0L
            input.inputStream().buffered().use { s ->
                val b = ByteArray(65536)
                while (true) {
                    val n = s.read(b); if (n < 0) break
                    for (i in 0 until n) for (bit in codes[b[i].toInt() and 255]!!) {
                        bits = (bits shl 1) or (bit - '0'); count++
                        if (count == 8) { out.writeByte(bits); bits = 0; count = 0 }
                    }
                    done += n; progress((done * 100 / maxOf(1L, input.length())).toInt())
                }
            }
            if (count > 0) out.writeByte(bits shl (8-count))
        }
    }
    fun decompress(input: File, output: File) {
        DataInputStream(input.inputStream().buffered()).use { src ->
            require(src.readInt() == 0x57484631) { "Archive WHF1 attendue." }
            val freq = LongArray(256) { src.readLong().also { require(it >= 0) } }
            var size = 0L; freq.forEach { size = Math.addExact(size,it) }
            val root = tree(freq)
            output.outputStream().buffered().use { out ->
                var byte = 0; var remaining = 0
                fun bit(): Int { if (remaining == 0) { byte = src.readUnsignedByte(); remaining = 8 }; remaining--; return (byte shr remaining) and 1 }
                repeatLong(size) {
                    var node = requireNotNull(root)
                    if (node.left == null) require(bit() == 0) else while (node.left != null) node = if (bit() == 0) node.left!! else node.right!!
                    out.write(node.symbol)
                }
            }
        }
    }
    private inline fun repeatLong(n: Long, block: () -> Unit) { var i = 0L; while (i < n) { block(); i++ } }
}
