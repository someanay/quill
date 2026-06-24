package com.tihor.quill.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** M4 "write first" test: the WAV header is 16 kHz / mono / 16-bit (testing_plan.md §1). */
class WavHeaderTest {

    private fun le32At(b: ByteArray, off: Int) =
        ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun le16At(b: ByteArray, off: Int) =
        ByteBuffer.wrap(b, off, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

    private fun ascii(b: ByteArray, off: Int, len: Int) = String(b, off, len, Charsets.US_ASCII)

    @Test
    fun header_is44Bytes() {
        assertEquals(44, WavHeader.build(16000, 1, 16, 0).size)
    }

    @Test
    fun header_fields_for16kMono16bit() {
        val dataSize = 32000 // 1 second of 16 kHz mono 16-bit
        val h = WavHeader.build(16000, 1, 16, dataSize)

        assertEquals("RIFF", ascii(h, 0, 4))
        assertEquals(36 + dataSize, le32At(h, 4))
        assertEquals("WAVE", ascii(h, 8, 4))
        assertEquals("fmt ", ascii(h, 12, 4))
        assertEquals(16, le32At(h, 16))            // PCM fmt chunk size
        assertEquals(1, le16At(h, 20))             // PCM
        assertEquals(1, le16At(h, 22))             // channels = mono
        assertEquals(16000, le32At(h, 24))         // sample rate
        assertEquals(16000 * 1 * 16 / 8, le32At(h, 28)) // byte rate
        assertEquals(1 * 16 / 8, le16At(h, 32))    // block align
        assertEquals(16, le16At(h, 34))            // bits per sample
        assertEquals("data", ascii(h, 36, 4))
        assertEquals(dataSize, le32At(h, 40))
    }
}
