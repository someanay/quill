package com.tihor.quill.stt

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Base64

/**
 * Gemini STT (decision 2/4): no Whisper-style endpoint exists — transcription goes through the native
 * `generateContent` API (inline base64 WAV + a hardened transcribe-verbatim prompt, key via the
 * `x-goog-api-key` header). Verified against ai.google.dev (2026-07). Error mapping is Gemini-shaped:
 * an invalid key surfaces as **HTTP 400 API_KEY_INVALID**, not 401 → must map to [TranscriptionError.BadKey].
 */
class GeminiTranscriptionClientTest {

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

    private fun client() = GeminiTranscriptionClient(
        apiKey = { "AIza-test-key" },
        baseUrl = server.url("/").toString().trimEnd('/'),
    )

    private val audioBytes = ByteArray(48) { (it * 3).toByte() }

    private fun audio(): File =
        File.createTempFile("quill_gem", ".wav").apply { writeBytes(audioBytes) }

    private val opts = TranscriptionOptions(model = "gemini-3.1-flash-lite", language = "en")

    private fun success(text: String): MockResponse = MockResponse().setResponseCode(200).setBody(
        """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}]},"finishReason":"STOP"}]}""",
    )

    @Test
    fun success_sendsGenerateContentShape_withApiKeyHeader() = runTest {
        server.enqueue(success("hello world"))

        val result = client().transcribe(audio(), opts)

        assertTrue(result.isSuccess)
        assertEquals("hello world", result.getOrNull()?.text)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/v1beta/models/gemini-3.1-flash-lite:generateContent"))
        assertEquals("AIza-test-key", req.getHeader("x-goog-api-key"))
        assertNull("Gemini auth is the header, never a Bearer token", req.getHeader("Authorization"))

        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        val parts = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        val textPart = parts[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("prompt must frame the audio as data to transcribe", textPart.contains("transcri", ignoreCase = true))
        assertTrue("language hint must fold into the prompt", textPart.contains("\"en\""))
        val inline = parts[1].jsonObject["inline_data"]!!.jsonObject
        assertEquals("audio/wav", inline["mime_type"]!!.jsonPrimitive.content)
        assertEquals(Base64.getEncoder().encodeToString(audioBytes), inline["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun prompt_forbidsActingOnDictatedCommands() = runTest {
        // Fidelity guard (decision 4a): the prompt must explicitly forbid obeying imperative dictation.
        server.enqueue(success("type my email"))
        client().transcribe(audio(), opts)
        val prompt = Json.parseToJsonElement(server.takeRequest().body.readUtf8())
            .jsonObject["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(prompt.contains("never", ignoreCase = true))
        assertTrue("prompt should carry an imperative example", prompt.contains("type my email", ignoreCase = true))
    }

    @Test
    fun autoDetect_omitsLanguageLine_fromPrompt() = runTest {
        server.enqueue(success("hi"))
        client().transcribe(audio(), TranscriptionOptions(model = "gemini-3.1-flash-lite", language = null))
        val prompt = Json.parseToJsonElement(server.takeRequest().body.readUtf8())
            .jsonObject["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("no language line for auto-detect", !prompt.contains("ISO 639-1"))
    }

    @Test
    fun markdownFences_areStripped() = runTest {
        server.enqueue(success("```\nhello from a fence\n```"))
        assertEquals("hello from a fence", client().transcribe(audio(), opts).getOrNull()?.text)
    }

    @Test
    fun transcriptLabelPreamble_isStripped() = runTest {
        server.enqueue(success("Transcript: hello there"))
        assertEquals("hello there", client().transcribe(audio(), opts).getOrNull()?.text)
    }

    @Test
    fun hereIsTheTranscriptionPreamble_isStripped() = runTest {
        server.enqueue(success("Here is the transcription: hello again"))
        assertEquals("hello again", client().transcribe(audio(), opts).getOrNull()?.text)
    }

    @Test
    fun ordinarySpeechStartingWithTranscriptWord_isNotMangled() = runTest {
        // Only a label ("Transcript:") is stripped — real dictation that happens to start with the
        // word must survive. We cannot detect obedience reliably; when in doubt, return the model text.
        server.enqueue(success("transcription services are expensive"))
        assertEquals("transcription services are expensive", client().transcribe(audio(), opts).getOrNull()?.text)
    }

    @Test
    fun emptyTranscript_mapsToParseError() = runTest {
        server.enqueue(success("   "))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.Parse)
    }

    @Test
    fun noCandidates_mapsToParseError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.Parse)
    }

    @Test
    fun http400ApiKeyInvalid_mapsToBadKey() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID","domain":"googleapis.com"}]}}""",
            ),
        )
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.BadKey)
    }

    @Test
    fun http400WithoutApiKeyInvalid_mapsToProvider() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"code":400,"message":"Request payload malformed.","status":"INVALID_ARGUMENT"}}"""),
        )
        val err = client().transcribe(audio(), opts).exceptionOrNull()
        assertTrue(err is TranscriptionError.Provider)
        assertEquals(400, (err as TranscriptionError.Provider).httpCode)
    }

    @Test
    fun http401And403_mapToBadKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.BadKey)
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.BadKey)
    }

    @Test
    fun http429_mapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(client().transcribe(audio(), opts).exceptionOrNull() is TranscriptionError.RateLimited)
    }

    @Test
    fun http500_mapsToProvider() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val err = client().transcribe(audio(), opts).exceptionOrNull()
        assertTrue(err is TranscriptionError.Provider)
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
