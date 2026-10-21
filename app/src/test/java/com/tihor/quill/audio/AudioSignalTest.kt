package com.tihor.quill.audio
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class AudioSignalTest {
    private fun wav(count: Int = 16000, sample: (Int) -> Short): ByteArray {
        val pcm = ByteBuffer.allocate(count*2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(count) { pcm.putShort(sample(it)) }
        return WavHeader.build(16000,1,16,pcm.capacity()) + pcm.array()
    }
    @Test fun quietDitherDcAndClickHaveNoSignal() {
        assertFalse(AudioSignal.hasAudibleSignal(wav { 0 }))
        assertFalse(AudioSignal.hasAudibleSignal(wav { (it%7-3).toShort() }))
        assertFalse(AudioSignal.hasAudibleSignal(wav { 3000 }))
        assertFalse(AudioSignal.hasAudibleSignal(wav { if (it == 2000) Short.MAX_VALUE else 0 }))
    }
    @Test fun shortSoftAndLoudSignalsPass() {
        for (amplitude in listOf(200,2000,32000)) assertTrue(AudioSignal.hasAudibleSignal(wav(1600) { (sin(it*Math.PI*440/8000)*amplitude).toInt().toShort() }))
    }
    @Test fun malformedAudioIsRejected() {
        for (data in listOf(byteArrayOf(), wav { 0 }.copyOf(40), wav { 0 }.copyOf(100))) {
            assertThrows(IllegalArgumentException::class.java) { AudioSignal.hasAudibleSignal(data) }
        }
    }
    @Test fun realSpeechPasses() {
        val data = requireNotNull(javaClass.classLoader?.getResourceAsStream("speech.wav")).use { it.readBytes() }
        assertTrue(AudioSignal.hasAudibleSignal(data))
    }
}
