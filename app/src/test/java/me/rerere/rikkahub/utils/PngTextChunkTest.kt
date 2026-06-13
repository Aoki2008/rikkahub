package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class PngTextChunkTest {

    private fun intBytes(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()
    )

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value
        val out = ByteArrayOutputStream()
        out.write(intBytes(data.size))
        out.write(typeBytes)
        out.write(data)
        out.write(intBytes(crc.toInt()))
        return out.toByteArray()
    }

    private fun minimalPng(): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ByteArray(13)))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    @Test
    fun `isPng detects signature`() {
        assertTrue(PngTextChunk.isPng(minimalPng()))
        assertFalse(PngTextChunk.isPng(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `add then read round-trips a text chunk`() {
        val png = minimalPng()
        val result = PngTextChunk.addTextChunk(png, "chara", "SGVsbG8=")
        val chunks = PngTextChunk.readTextChunks(result)
        assertEquals("SGVsbG8=", chunks["chara"])
    }

    @Test
    fun `result remains a valid png ending in IEND`() {
        val result = PngTextChunk.addTextChunk(minimalPng(), "ccv3", "abc")
        assertTrue(PngTextChunk.isPng(result))
        val tail = String(result.copyOfRange(result.size - 8, result.size - 4), Charsets.US_ASCII)
        assertEquals("IEND", tail)
    }

    @Test
    fun `adding same keyword replaces previous chunk`() {
        var png = PngTextChunk.addTextChunk(minimalPng(), "chara", "first")
        png = PngTextChunk.addTextChunk(png, "chara", "second")
        val chunks = PngTextChunk.readTextChunks(png)
        assertEquals("second", chunks["chara"])
        // only one chara chunk
        assertEquals(1, chunks.count { it.key == "chara" })
    }

    @Test
    fun `multiple distinct keywords coexist`() {
        var png = PngTextChunk.addTextChunk(minimalPng(), "chara", "v2data")
        png = PngTextChunk.addTextChunk(png, "ccv3", "v3data")
        val chunks = PngTextChunk.readTextChunks(png)
        assertEquals("v2data", chunks["chara"])
        assertEquals("v3data", chunks["ccv3"])
    }

    @Test
    fun `readTextChunks on non-png returns empty`() {
        assertTrue(PngTextChunk.readTextChunks(byteArrayOf(0, 1, 2)).isEmpty())
    }
}
