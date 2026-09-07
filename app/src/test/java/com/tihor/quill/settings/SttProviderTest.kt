package com.tihor.quill.settings

import com.tihor.quill.stt.GeminiTranscriptionClient
import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.OpenRouterTranscriptionClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure provider-registry behaviour (spec §1 + the SttProvider doc): Groq is the default; unknown/null
 * ids resolve to the default; the picker exposes Groq/OpenAI/OpenRouter/Gemini/Custom; the factory
 * routes OpenAI-compatible providers through the shared client and gives OpenRouter/Gemini their own
 * adapters. Pure JVM — no Android, no network.
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
        assertSame(SttProvider.GEMINI, SttProvider.fromId("gemini"))
        assertSame(SttProvider.CUSTOM, SttProvider.fromId("custom"))
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
    fun selectable_exposesAllFiveProviders() {
        // Decision 1: Groq, OpenAI, OpenRouter, Gemini, and Custom are all selectable (no Deepgram).
        assertEquals(
            listOf(SttProvider.GROQ, SttProvider.OPENAI, SttProvider.OPENROUTER, SttProvider.GEMINI, SttProvider.CUSTOM),
            SttProvider.selectable(),
        )
    }

    @Test
    fun groq_hasConcreteDefaultModelAndBaseUrl() {
        assertEquals(OpenAiCompatibleTranscriptionClient.GROQ_DEFAULT_MODEL, SttProvider.GROQ.defaultModel)
        assertTrue(SttProvider.GROQ.defaultModel.isNotBlank())
        assertTrue(SttProvider.GROQ.baseUrl.isNotBlank())
    }

    @Test
    fun providerDefaults_matchVerifiedCatalogs() {
        // Verified against live provider docs; OpenRouter's dedicated STT model is namespaced.
        assertEquals("whisper-1", SttProvider.OPENAI.defaultModel)
        assertEquals("openai/whisper-large-v3-turbo", SttProvider.OPENROUTER.defaultModel)
        assertEquals("gemini-3.1-flash-lite", SttProvider.GEMINI.defaultModel)
        assertEquals("", SttProvider.CUSTOM.defaultModel) // free-text, user-supplied
    }

    @Test
    fun openRouterAndGemini_areNotOpenAiCompatible() {
        // OpenRouter's STT endpoint is JSON+base64 (not multipart); Gemini has no Whisper-style
        // endpoint at all. The stale openAiCompatible=true on OPENROUTER was a bug — pinned here.
        assertFalse(SttProvider.OPENROUTER.openAiCompatible)
        assertFalse(SttProvider.GEMINI.openAiCompatible)
        assertTrue(SttProvider.GROQ.openAiCompatible)
        assertTrue(SttProvider.OPENAI.openAiCompatible)
        assertTrue(SttProvider.CUSTOM.openAiCompatible)
    }

    @Test
    fun everySelectableProviderExceptCustom_hasAKeyConsoleLink() {
        SttProvider.entries.filter { it != SttProvider.CUSTOM }.forEach { p ->
            assertTrue("${p.id} needs a where-do-I-get-a-key link", !p.keyConsoleUrl.isNullOrBlank())
        }
        assertEquals(null, SttProvider.CUSTOM.keyConsoleUrl) // hidden for CUSTOM
    }

    @Test
    fun everyProvider_hasAKeyMask_inItsOwnKeyFormat() {
        // The entry-field placeholder must match the provider the key is FOR, not always Groq's.
        SttProvider.entries.forEach { p -> assertTrue("${p.id} needs a key mask", p.keyMask.isNotBlank()) }
        assertTrue(SttProvider.GROQ.keyMask.startsWith("gsk_"))
        assertTrue(SttProvider.OPENAI.keyMask.startsWith("sk-"))
        assertTrue(SttProvider.OPENROUTER.keyMask.startsWith("sk-or-"))
        // Gemini keys come in two formats (AIza… from AI Studio, AQ.… from Cloud v2/Vertex express);
        // the mask must show both so neither looks malformed.
        assertTrue(SttProvider.GEMINI.keyMask.contains("AIza"))
        assertTrue(SttProvider.GEMINI.keyMask.contains("AQ."))
    }

    @Test
    fun geminiOnly_isTaggedFreeInTheConsoleChooser() {
        assertTrue(SttProvider.GEMINI.keyConsoleFree)
        SttProvider.entries.filter { it != SttProvider.GEMINI }.forEach { p ->
            assertFalse("${p.id} must not carry the free tag", p.keyConsoleFree)
        }
    }

    @Test
    fun modelChoices_deriveFromTheProvider() {
        // Offline fallback choices must work with each provider's transcription endpoint.
        assertEquals(listOf("whisper-large-v3-turbo", "whisper-large-v3"), SttProvider.GROQ.models.map { it.id })
        assertEquals(
            listOf("whisper-1", "gpt-4o-mini-transcribe", "gpt-4o-transcribe"),
            SttProvider.OPENAI.models.map { it.id },
        )
        // OpenRouter: the dedicated Whisper STT model is first and therefore the default.
        assertEquals(
            listOf(
                "openai/whisper-large-v3-turbo",
                "openai/gpt-4o-mini-transcribe",
                "openai/gpt-4o-transcribe",
            ),
            SttProvider.OPENROUTER.models.map { it.id },
        )
        // Gemini: current-gen REST audio models only — 2.5-series is shutdown-dated (2026-10-16)
        // and Live models (gemini-*-live-*) are WebSocket-only, never valid for generateContent.
        assertEquals(
            listOf("gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3-flash-preview", "gemini-3.1-pro-preview"),
            SttProvider.GEMINI.models.map { it.id },
        )
        assertTrue(SttProvider.GEMINI.models.none { it.id.contains("live") })
        // Only Gemini's pro-preview lacks a free tier; the picker hides it for free-tier keys.
        assertEquals(
            listOf("gemini-3.1-pro-preview"),
            SttProvider.entries.flatMap { p -> p.models.filter { it.paidOnly } }.map { it.id },
        )
        assertTrue(SttProvider.CUSTOM.models.isEmpty())
        // Free-text model entry: OpenRouter's catalog is huge; Custom is user-defined.
        assertTrue(SttProvider.OPENROUTER.freeTextModel)
        assertTrue(SttProvider.CUSTOM.freeTextModel)
        assertFalse(SttProvider.GROQ.freeTextModel)
    }

    @Test
    fun factory_routesEachProviderToItsClient() {
        assertTrue(TranscriptionClientFactory.create(SttProvider.GROQ, { "k" }) is OpenAiCompatibleTranscriptionClient)
        assertTrue(TranscriptionClientFactory.create(SttProvider.OPENAI, { "k" }) is OpenAiCompatibleTranscriptionClient)
        assertTrue(TranscriptionClientFactory.create(SttProvider.OPENROUTER, { "k" }) is OpenRouterTranscriptionClient)
        assertTrue(TranscriptionClientFactory.create(SttProvider.GEMINI, { "k" }) is GeminiTranscriptionClient)
        assertTrue(
            TranscriptionClientFactory.create(SttProvider.CUSTOM, { "k" }, customBaseUrl = "https://api.example.com/v1")
                is OpenAiCompatibleTranscriptionClient,
        )
    }
}
