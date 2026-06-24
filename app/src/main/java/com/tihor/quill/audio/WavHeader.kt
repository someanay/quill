package com.tihor.quill.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds the 44-byte canonical PCM WAV (RIFF/WAVE) header (spec §2.2 — we own the WAV format
 * rather than trusting a device encoder). Pure → unit-tested for 16 kHz / mono / 16-bit.
 *
 * `AudioRecord` gives raw PCM with no container, so we write this header then the PCM data; when the
 * stream length is unknown up front we write a header with placeholder sizes and patch them on stop.
 */
object WavHeader {

    const val HEADER_SIZE = 44

    fun build(sampleRate: Int, channels: Int, bitsPerSample: Int, dataSizeBytes: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val out = ByteArrayOutputStream(HEADER_SIZE)

        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.write(le32(36 + dataSizeBytes))          // ChunkSize = 36 + Subchunk2Size
        out.write("WAVE".toByteArray(Charsets.US_ASCII))

        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.write(le32(16))                           // Subchunk1Size (16 for PCM)
        out.write(le16(1))                            // AudioFormat = 1 (PCM)
        out.write(le16(channels))
        out.write(le32(sampleRate))
        out.write(le32(byteRate))
        out.write(le16(blockAlign))
        out.write(le16(bitsPerSample))

        out.write("data".toByteArray(Charsets.US_ASCII))
        out.write(le32(dataSizeBytes))                // Subchunk2Size = data size

        return out.toByteArray()
    }

    private fun le32(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun le16(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
}
