package com.josias.wavhuff

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class CoreTest {
    @Test fun huffmanRoundTrips() {
        val cases = listOf(byteArrayOf(),ByteArray(10001),ByteArray(10000) { 255.toByte() },ByteArray(65537) { (it % 256).toByte() },Random(42).nextBytes(300000))
        for (data in cases) {
            val source = File.createTempFile("source", ".bin"); val archive = File.createTempFile("archive", ".whf"); val decoded = File.createTempFile("decoded", ".bin")
            try { source.writeBytes(data); Huffman.compress(source,archive); Huffman.decompress(archive,decoded); assertArrayEquals(data,decoded.readBytes()) }
            finally { source.delete(); archive.delete(); decoded.delete() }
        }
    }
    @Test fun statsUseActualBytesIncludingExpansion() {
        val s = CompressionStats(1000,250)
        assertEquals(75.0,s.gainPercent,0.00001); assertEquals(25.0,s.remainingPercent,0.00001); assertEquals(4.0,s.ratio,0.00001)
        assertEquals(-50.0,CompressionStats(100,150).gainPercent,0.00001)
    }
    private fun wav(bits: Int = 16, channels: Int = 2, sampleRate: Int = 44100): ByteArray {
        val b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(56); b.put("WAVE".toByteArray())
        b.put("JUNK".toByteArray()); b.putInt(3); b.put(byteArrayOf(1,2,3,0))
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort()); b.putInt(sampleRate)
        b.putInt(sampleRate*channels*bits/8); b.putShort((channels*bits/8).toShort()); b.putShort(bits.toShort())
        b.put("data".toByteArray()); b.putInt(8); b.put(ByteArray(8)); return b.array()
    }
    private fun withWav(bytes: ByteArray, block: (File) -> Unit) {
        val f = File.createTempFile("test", ".wav"); try { f.writeBytes(bytes); block(f) } finally { f.delete() }
    }
    @Test fun readsChunksAndPadding() = withWav(wav()) {
        val result = WavReader.read(it); assertEquals(44100,result.sampleRate); assertEquals(2,result.channels); assertEquals(56L,result.dataOffset); assertEquals(8L,result.dataSize)
    }
    @Test fun rejectsUnsupportedAndTruncatedFiles() {
        for (bytes in listOf(wav(bits=8),wav(channels=3),wav(sampleRate=12345),wav().copyOf(61),"not a wav".toByteArray())) {
            withWav(bytes) { f -> try { WavReader.read(f); fail("Must reject invalid WAV") } catch (_: IllegalArgumentException) {} }
        }
    }
    @Test fun rejectsCorruptHuffmanHeader() {
        val src = File.createTempFile("bad", ".whf"); val dst = File.createTempFile("out", ".wav")
        try { src.writeBytes(ByteArray(2052)); try { Huffman.decompress(src,dst); fail() } catch (_: IllegalArgumentException) {} } finally { src.delete(); dst.delete() }
    }
}
