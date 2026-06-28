package com.tihor.quill.settings

import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.TranscriptionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure provider-registry behaviour (spec §1 + the SttProvider doc): Groq is the default and the only
 * provider exposed in the v1 picker; unknown/null ids resolve to the default; every provider routes
 * through the OpenAI-compatible client in v1. Pure JVM — no Android, no network.
 */
class SttProviderTest {

    @Test
    fun default_isGroq() {
        assertSame(SttProvider.GROQ, SttProvider.DEFAULT)
    }

    @Test
    fun fromId_knownId_resolvesThatProvider() {
        assertSame(SttProvider.GROQ, SttProvider.fromId("groq"))
        assertSame(SttProvider.OPENAI, SttProvider.fromId("openai"))
        assertSame(SttProvider.OPENROUTER, SttProvider.fromId("openrouter"))
    }

    @Test
    fun fromId_unknownOrNull_fallsBackToDefault() {
        assertSame(SttProvider.GROQ, SttProvider.fromId(null))
        assertSame(SttProvider.GROQ, SttProvider.fromId(""))
        assertSame(SttProvider.GROQ, SttProvider.fromId("does-not-exist"))
    }

    @Test
    fun fromId_isCaseSensitiveOnStoredIds() {
        // Stored ids are lowercase; a wrong-case id is simply unknown and falls back to the default —
        // it must not accidentally resolve OPENAI.
        assertSame(SttProvider.GROQ, SttProvider.fromId("OPENAI"))
    }

    @Test
    fun everyProvider_roundTripsThroughItsId() {
        SttProvider.entries.forEach { p -> assertSame(p, SttProvider.fromId(p.id)) }
    }

    @Test
    fun selectable_inV1_isGroqOnly() {
        // v1 decision: Groq is the only working/shipped path exposed in the picker.
        assertEquals(listOf(SttProvider.GROQ), SttProvider.selectable())
        assertTrue(SttProvider.GROQ.shippingInV1)
        assertFalse(SttProvider.OPENAI.shippingInV1)
        assertFalse(SttProvider.OPENROUTER.shippingInV1)
    }

    @Test
    fun groq_hasConcreteDefaultModelAndBaseUrl() {
        assertEquals(OpenAiCompatibleTranscriptionClient.GROQ_DEFAULT_MODEL, SttProvider.GROQ.defaultModel)
        assertTrue(SttProvider.GROQ.defaultModel.isNotBlank())
        assertTrue(SttProvider.GROQ.baseUrl.isNotBlank())
    }

    @Test
    fun factory_routesEveryProviderThroughTheOpenAiCompatibleClient_inV1() {
        SttProvider.entries.forEach { p ->
            val client: TranscriptionClient = TranscriptionClientFactory.create(p) { "key" }
            assertTrue(
                "expected OpenAiCompatibleTranscriptionClient for ${p.id}",
                client is OpenAiCompatibleTranscriptionClient,
            )
        }
    }
}
