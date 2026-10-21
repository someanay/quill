package com.tihor.quill.settings
import com.tihor.quill.audio.WavHeader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceWordCaptureTest {
    @Test fun silenceIsRejectedBeforeCredentialsOrProviderRequests() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("quiet-word", ".wav", context.cacheDir)
        try {
            file.writeBytes(WavHeader.build(16000,1,16,32000)+ByteArray(32000))
            val failure = VoiceWordCapture(context).transcribe(file).exceptionOrNull() as VoiceWordCapture.FailureException
            assertEquals(VoiceWordCapture.Failure.NoSpeech,failure.failure)
        } finally { file.delete() }
    }
    @Test fun realSpeechStillReachesTheKeyGate() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val file = File.createTempFile("spoken-word", ".wav", context.cacheDir)
        try {
            file.writeBytes(requireNotNull(javaClass.classLoader?.getResourceAsStream("speech.wav")).use { it.readBytes() })
            val failure = VoiceWordCapture(context).transcribe(file).exceptionOrNull() as VoiceWordCapture.FailureException
            assertEquals(VoiceWordCapture.Failure.NoKey,failure.failure)
        } finally { file.delete() }
    }
}
