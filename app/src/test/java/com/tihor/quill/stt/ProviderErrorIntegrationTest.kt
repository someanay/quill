package com.tihor.quill.stt

import com.tihor.quill.errors.DictationIssue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit

/** Exercise the real request/response path of every speech adapter, not only the shared mapper. */
@RunWith(Parameterized::class)
class ProviderErrorIntegrationTest(private val provider: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun providers() = listOf(arrayOf("compatible"), arrayOf("gemini"), arrayOf("openrouter"))
    }
    private lateinit var server: MockWebServer
    private lateinit var audio: File
    private val opts = TranscriptionOptions("test-model")
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        audio = File.createTempFile("quill-errors", ".wav").apply { writeBytes(ByteArray(128)) }
    }
    @After fun cleanup() { server.shutdown(); audio.delete() }

    private fun client(key: String = "test-key", http: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false).callTimeout(1, TimeUnit.SECONDS).build()): TranscriptionClient {
        val base = server.url("/").toString().trimEnd('/')
        return when (provider) {
            "gemini" -> GeminiTranscriptionClient({ key }, base, http)
            "openrouter" -> OpenRouterTranscriptionClient({ key }, base, http)
            else -> OpenAiCompatibleTranscriptionClient(base, { key }, http)
        }
    }

    @Test fun httpErrorsReachTheUiClassifierWithoutPrivateResponseText() = runTest {
        mapOf(401 to DictationIssue.BAD_KEY, 403 to DictationIssue.ACCESS_DENIED,
            408 to DictationIssue.TIMEOUT, 413 to DictationIssue.AUDIO_TOO_LARGE,
            415 to DictationIssue.AUDIO_FORMAT, 429 to DictationIssue.RATE_LIMIT,
            500 to DictationIssue.SERVER, 503 to DictationIssue.PROVIDER_BUSY,
            504 to DictationIssue.TIMEOUT).forEach { (status, expected) ->
            server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error":{"message":"private-speech-and-key"}}"""))
            val error = client().transcribe(audio, opts).exceptionOrNull()!!
            assertEquals("HTTP $status", expected, DictationIssue.from(error))
            assertFalse(error.toString().contains("private-speech-and-key"))
        }
    }

    @Test fun emptyReplyIsNotSuccessfulDictation() = runTest {
        server.enqueue(MockResponse().setBody("""{"text":"  "}"""))
        assertEquals(DictationIssue.NO_SPEECH, DictationIssue.from(client().transcribe(audio, opts).exceptionOrNull()!!))
    }

    @Test fun invalidKeyAndMissingAudioAreRejectedBeforeUpload() = runTest {
        assertEquals(DictationIssue.KEY_FORMAT, DictationIssue.from(client("bad\nkey").transcribe(audio, opts).exceptionOrNull()!!))
        audio.delete()
        assertEquals(DictationIssue.AUDIO_MISSING, DictationIssue.from(client().transcribe(audio, opts).exceptionOrNull()!!))
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellingDictationCancelsTheActualHttpCall() = runTest {
        val call = AtomicReference<Call>()
        val http = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callStart(started: Call) { call.set(started) }
        }).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch(Dispatchers.IO) { client(http = http).transcribe(audio, opts) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(call.get().isCanceled())
    }

    @Test fun slowProviderReportsTimeout() = runTest {
        server.enqueue(MockResponse().setHeadersDelay(3, TimeUnit.SECONDS).setBody("{}"))
        assertEquals(DictationIssue.TIMEOUT, DictationIssue.from(client().transcribe(audio, opts).exceptionOrNull()!!))
    }
}
