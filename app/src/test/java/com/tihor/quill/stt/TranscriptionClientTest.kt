package com.tihor.quill.stt

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** M5 "write first": MockWebServer success / 401 / 5xx / 429 / network (testing_plan.md §1). */
class TranscriptionClientTest {

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

    private fun client() = OpenAiCompatibleTranscriptionClient(
        baseUrl = server.url("/openai/v1").toString().trimEnd('/'),
        apiKey = { "test-key" },
    )

    private fun audio(): File =
        File.createTempFile("quill", ".wav").apply { writeBytes(ByteArray(128) { 1 }) }

    private val opts = TranscriptionOptions(model = "whisper-large-v3-turbo", language = "en")

    @Test
    fun success_parsesTranscript_andSendsMultipartWithAuth() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"hello world"}"""))

        val result = client().transcribe(audio(), opts)

        assertTrue(result.isSuccess)
        assertEquals("hello world", result.getOrNull()?.text)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/openai/v1/audio/transcriptions"))
        assertEquals("Bearer test-key", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("whisper-large-v3-turbo")) // model part
        assertTrue(body.contains("name=\"file\""))           // file part
    }

    @Test
    fun http401_mapsToBadKey() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val result = client().transcribe(audio(), opts)
        assertTrue(result.exceptionOrNull() is TranscriptionError.BadKey)
    }

    @Test
    fun http429_mapsToRateLimited() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val result = client().transcribe(audio(), opts)
        assertTrue(result.exceptionOrNull() is TranscriptionError.RateLimited)
    }

    @Test
    fun http500_mapsToProviderError_withCode() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))
        val result = client().transcribe(audio(), opts)
        val err = result.exceptionOrNull()
        assertTrue(err is TranscriptionError.Provider)
        assertEquals(500, (err as TranscriptionError.Provider).httpCode)
    }

    @Test
    fun malformedSuccessBody_mapsToParseError() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        val result = client().transcribe(audio(), opts)
        assertTrue(result.exceptionOrNull() is TranscriptionError.Parse)
    }

    @Test
    fun connectionFailure_mapsToNetworkError() = runTest {
        server.shutdown() // nothing listening
        val result = client().transcribe(audio(), opts)
        assertTrue(result.exceptionOrNull() is TranscriptionError.Network)
    }
}
