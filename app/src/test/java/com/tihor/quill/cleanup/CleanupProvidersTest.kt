package com.tihor.quill.cleanup

import com.tihor.quill.settings.SttProvider
import com.tihor.quill.style.WritingStyle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class CleanupProvidersTest {
    @Test fun everyProviderUsesOnlyItsOwnKey() {
        SttProvider.entries.forEach { selected ->
            val read = mutableListOf<SttProvider>()
            assertEquals(selected.id, CleanupProviders.key(selected) { read += it; it.id })
            assertEquals(listOf(selected), read)
            assertNull(CleanupProviders.key(selected) { null })
        }
    }

    @Test fun routesToProviderSpecificEndpoints() {
        SttProvider.entries.forEach { provider ->
            val base = CleanupProviders.baseUrl(provider, "https://example.com/v1")
            assertEquals(when(provider) {
                SttProvider.CUSTOM -> "https://example.com/v1"
                SttProvider.GEMINI -> "${provider.baseUrl}/v1beta/openai"
                else -> provider.baseUrl
            }, base)
        }
        listOf("http://example.com", "https://key@example.com", "https://example.com?key=x", "https://example.com/#key").forEach {
            assertTrue(runCatching { CleanupProviders.baseUrl(SttProvider.CUSTOM, it) }.isFailure)
        }
    }

    @Test fun everyProviderAndStyleUsesTheSharedGuardAndPrompt() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            SttProvider.entries.forEach { provider ->
                WritingStyle.entries.forEach { style ->
                    server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Hello."},"finish_reason":"stop"}]}"""))
                    val result = CleanupClient(server.url("/v1").toString(), { provider.id }, style = style,
                        model = "test-model", lowLatencyRouting = provider == SttProvider.OPENROUTER).clean("um hello")
                    assertEquals("Hello.", result)
                    val request = server.takeRequest()
                    assertEquals("Bearer ${provider.id}", request.getHeader("Authorization"))
                    val body = request.body.readUtf8()
                    assertTrue(body.contains("\"temperature\":0.0"))
                    assertEquals(provider == SttProvider.OPENROUTER, body.contains("\"sort\":\"latency\""))
                    assertEquals(style != WritingStyle.NEUTRAL, body.contains("WRITING STYLE"))
                }
            }
        } finally { server.shutdown() }
    }

    @Test fun incompleteOutputFallsBackToRaw() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Hello."},"finish_reason":"length"}]}"""))
            assertNull(CleanupClient(server.url("/v1").toString(), { "test" }).clean("hello"))
        } finally { server.shutdown() }
    }
}
