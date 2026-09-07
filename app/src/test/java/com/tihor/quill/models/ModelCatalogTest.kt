package com.tihor.quill.models

import com.tihor.quill.settings.SttProvider
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

class ModelCatalogTest {
    @Test fun cachedListsAreIsolatedByProviderAndSurviveReopening() {
        val directory = java.nio.file.Files.createTempDirectory("model-cache").toFile()
        try {
            ModelCatalogCache(directory, "groq").write(listOf(ModelOption("whisper-new")))
            assertEquals("whisper-new", ModelCatalogCache(directory, "groq").read().single().id)
            assertTrue(ModelCatalogCache(directory, "openai").read().isEmpty())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
    @Test fun geminiRequestsEveryPageAndKeepsKeyOutOfUrls() = runTest {
        val requests = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            val body = if (requests.size == 1)
                """{"models":[{"name":"models/gemini-3-flash","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"page2"}"""
            else """{"models":[{"name":"models/gemini-4-flash","supportedGenerationMethods":["generateContent"]}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val models = ModelCatalog.fetch(SttProvider.GEMINI, ModelUse.DICTATION, { "test-key" }, httpClient = client)
        assertEquals(2, models.size)
        assertEquals("gemini-4-flash", models.first().id)
        assertEquals("/v1beta/models", requests.first().url.encodedPath)
        assertEquals("page2", requests.last().url.queryParameter("pageToken"))
        assertEquals("test-key", requests.first().header("x-goog-api-key"))
        assertNull(requests.first().header("Authorization"))
        assertFalse(requests.any { "test-key" in it.url.toString() })
    }
    @Test fun transcriptionDoesNotOfferChatOrDiarizationModels() {
        val result = ModelCatalog.parse(SttProvider.OPENAI, ModelUse.DICTATION,
            """{"data":[{"id":"gpt-4o"},{"id":"gpt-4o-transcribe"},{"id":"gpt-4o-transcribe-diarize"},{"id":"whisper-1"}]}""")
        assertEquals(setOf("gpt-4o-transcribe", "whisper-1"), result.models.map { it.id }.toSet())
    }

    @Test fun openRouterUsesTranscriptionCapabilityNotJustAudioInput() {
        val result = ModelCatalog.parse(SttProvider.OPENROUTER, ModelUse.DICTATION,
            """{"data":[{"id":"new/asr","architecture":{"output_modalities":["transcription"]}},{"id":"audio/chat","architecture":{"input_modalities":["audio"],"output_modalities":["text"]}}]}""")
        assertEquals(listOf("new/asr"), result.models.map { it.id })
        assertTrue(ModelCatalog.endpoint(SttProvider.OPENROUTER, ModelUse.DICTATION).toString().contains("output_modalities=transcription"))
    }

    @Test fun geminiPaginatesAndExcludesLiveImageAndSpeechGeneration() {
        val result = ModelCatalog.parse(SttProvider.GEMINI, ModelUse.DICTATION,
            """{"models":[{"name":"models/gemini-4-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-live","supportedGenerationMethods":["bidiGenerateContent"]},{"name":"models/gemini-image","supportedGenerationMethods":["generateContent"]},{"name":"models/gemini-tts","supportedGenerationMethods":["generateContent"]}],"nextPageToken":"next"}""")
        assertEquals(listOf("gemini-4-flash"), result.models.map { it.id })
        assertEquals("next", result.nextPage)
    }

    @Test fun rankingPrefersStableFastModelsThenNewerCandidates() {
        val ranked = ModelCatalog.rank(listOf(ModelOption("whisper-new-preview", created = 300), ModelOption("whisper-old-turbo", created = 100), ModelOption("whisper-new-turbo", created = 200)), ModelUse.DICTATION)
        assertEquals(listOf("whisper-new-turbo", "whisper-old-turbo", "whisper-new-preview"), ranked.map { it.id })
    }

    @Test fun cleanupExcludesGuardAndSpeechModels() {
        val result = ModelCatalog.parse(SttProvider.GROQ, ModelUse.CLEANUP,
            """{"data":[{"id":"llama-3.3-70b-versatile"},{"id":"meta/llama-guard"},{"id":"whisper-large-v3"},{"id":"playai-tts"},{"id":"canopylabs/orpheus-v1-english"}]}""")
        assertEquals(listOf("llama-3.3-70b-versatile"), result.models.map { it.id })
    }

    @Test fun customEndpointsCannotEmbedCredentialsOrUseHttp() {
        for (url in listOf("http://example.com/v1", "https://key@example.com/v1", "https://example.com/v1?key=secret")) {
            assertThrows(IllegalArgumentException::class.java) { ModelCatalog.endpoint(SttProvider.CUSTOM, ModelUse.DICTATION, url) }
        }
    }

    @Test fun malformedCatalogFailsInsteadOfLookingFresh() {
        assertThrows(IllegalArgumentException::class.java) { ModelCatalog.parse(SttProvider.GROQ, ModelUse.DICTATION, "{}") }
    }
}
