package com.tihor.quill.stt

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * OpenRouter STT (decision 2): `POST /api/v1/audio/transcriptions` takes a **JSON body with
 * base64-encoded audio** (`input_audio` contract) — NOT the OpenAI multipart form. Verified against
 * openrouter.ai/docs (2026-07): body = `{model, input_audio: {data, format}, language?}`,
 * response = `{"text": ...}`, ISO-639-1 `language`.
 */
class OpenRouterTranscriptionClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OpenRouterTranscriptionClient(
        apiKey = { "or-test-key" },
        baseUrl = server.url("/api/v1").toString().trimEnd('/'),
    )

    private val audioBytes = ByteArray(64) { it.toByte() }

    private fun audio(): File =
        File.createTempFile("quill_or", ".wav").apply { writeBytes(audioBytes) }

    private val opts = TranscriptionOptions(model = OpenRouterTranscriptionClient.DEFAULT_MODEL, language = "en")

    @Test
    fun success_sendsJsonBase64Body_andParsesTranscript() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"hello there","usage":{"seconds":1.2}}"""))

        val result = client().transcribe(audio(), opts)

        assertTrue(result.isSuccess)
        assertEquals("hello there", result.getOrNull()?.text)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/api/v1/audio/transcriptions"))
        assertEquals("Bearer or-test-key", req.getHeader("Authorization"))
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))

        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals(OpenRouterTranscriptionClient.DEFAULT_MODEL, body["model"]!!.jsonPrimitive.content)
        assertEquals("en", body["language"]!!.jsonPrimitive.content)
        val inputAudio = body["input_audio"]!!.jsonObject
        assertEquals("wav", inputAudio["format"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(audioBytes), inputAudio["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun autoDetect_omitsLanguageField() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"hi"}"""))

        client().transcribe(audio(), TranscriptionOptions(model = OpenRouterTranscriptionClient.DEFAULT_MODEL, language = null))

        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse("language must be omitted for auto-detect, not null", "language" in body)
    }

    @Test
    fun transcript_isTrimmed() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":" hello "}"""))
        assertEquals("hello", client().transcribe(audio(), opts).getOrNull()?.text)
    }

    @Test
    fun http401_mapsToBadKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":401,"message":"No auth"}}"""))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.BadKey)
    }

    @Test
    fun http403_mapsToBadKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.BadKey)
    }

    @Test
    fun http429_mapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.RateLimited)
    }

    @Test
    fun http402_outOfCredits_mapsToQuotaExhausted() = runTest {
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"message":"Insufficient credits"}}"""))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.QuotaExhausted)
    }

    @Test
    fun http500_mapsToProviderError_withCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"error":{"code":502,"message":"upstream"}}"""))
        val err = client().transcribe(audio(), opts).exceptionOrNull()
        assertTrue(err is TranscriptionError.Provider)
        assertEquals(502, (err as TranscriptionError.Provider).httpCode)
    }

    @Test
    fun malformedSuccessBody_mapsToParseError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.Parse)
    }

    @Test
    fun connectionFailure_mapsToNetworkError() = runTest {
        server.shutdown()
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.Network)
    }
}
