package com.tihor.quill.cleanup

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * M10 "write first": CleanupClient parses a Groq chat response, and maps every failure mode
 * (HTTP error, empty/malformed body, timeout, no network) to the null raw-fallback signal.
 */
class CleanupClientTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(timeoutMs: Long = 4_000) = CleanupClient(
        baseUrl = server.url("/openai/v1").toString().trimEnd('/'),
        apiKey = { "gsk_test" },
        timeoutMs = timeoutMs,
    )

    private fun chat(content: String): String =
        """{"choices":[{"message":{"role":"assistant","content":${quote(content)}}}]}"""

    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @Test
    fun success_parsesCleanedText_andPostsChatWithAuthModelAndPrompt() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chat("Hello world.")))

        val result = client().clean("um hello world")
        assertEquals("Hello world.", result)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertTrue(req.path!!.endsWith("/chat/completions"))
        assertEquals("Bearer gsk_test", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue("sends the hardcoded model", body.contains(CleanupClient.MODEL))
        assertTrue("sends the raw transcript", body.contains("um hello world"))
        assertTrue("sends a system role", body.contains("system"))
    }

    @Test
    fun httpServerError_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertNull(client().clean("hello"))
    }

    @Test
    fun badKey401_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(client().clean("hello"))
    }

    @Test
    fun emptyContent_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chat("   ")))
        assertNull(client().clean("hello"))
    }

    @Test
    fun noChoices_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))
        assertNull(client().clean("hello"))
    }

    @Test
    fun malformedBody_returnsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertNull(client().clean("hello"))
    }

    @Test
    fun timeout_returnsNull() = runTest {
        // Body arrives after 1s but the call timeout is 200ms → fall back to raw (null).
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(chat("too late")).setBodyDelay(1, TimeUnit.SECONDS),
        )
        assertNull(client(timeoutMs = 200).clean("hello"))
    }

    @Test
    fun connectionFailure_returnsNull() = runTest {
        server.shutdown() // nothing listening
        assertNull(client().clean("hello"))
    }

    @Test
    fun divergentResponse_returnsNull_failsSoftToRaw() = runTest {
        // The model obeyed the dictation instead of cleaning it. The divergence guard must reject the
        // response so the caller falls back to the raw transcript (null), exactly like a timeout.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(chat("I'm ready to type your email")),
        )
        assertNull(client().clean("type my email"))
    }

    @Test
    fun wrapsTranscriptAsDelimitedData_inUserMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(chat("Hello world.")))
        client().clean("um hello world")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("fences the transcript so the model sees it as data", body.contains("<transcript>"))
        assertTrue("still includes the raw transcript verbatim", body.contains("um hello world"))
    }

    @Test
    fun blankInput_returnsNull_withoutCallingProvider() = runTest {
        assertNull(client().clean("   "))
        assertEquals("no provider call for blank input", 0, server.requestCount)
    }
}
