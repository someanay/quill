package com.tihor.quill.settings

import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.TranscriptionClient

/**
 * STT providers. **Groq is the default and the only working path that ships in v1** (user decision);
 * others are defined so they slot in behind [TranscriptionClient] without reworking the pipeline.
 * OpenAI-compatible providers reuse [OpenAiCompatibleTranscriptionClient]; a non-compatible one
 * (e.g. a future OpenRouter adapter) gets its own client and is switched in [TranscriptionClientFactory].
 */
enum class SttProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    /** Exposed in the picker + shipped/working in v1. */
    val shippingInV1: Boolean,
    val openAiCompatible: Boolean = true,
) {
    GROQ(
        id = "groq",
        displayName = "Groq",
        baseUrl = OpenAiCompatibleTranscriptionClient.GROQ_BASE_URL,
        defaultModel = OpenAiCompatibleTranscriptionClient.GROQ_DEFAULT_MODEL,
        shippingInV1 = true,
    ),

    // Defined for the abstraction; not exposed/shipped in v1 (Groq-only per the v1 decision).
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = OpenAiCompatibleTranscriptionClient.OPENAI_BASE_URL,
        defaultModel = "whisper-1",
        shippingInV1 = false,
    ),

    // OpenRouter adapter spec is pending; reserved so it drops in behind the interface later.
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "",
        shippingInV1 = false,
        openAiCompatible = true,
    ),
    ;

    companion object {
        val DEFAULT = GROQ
        fun fromId(id: String?): SttProvider = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Providers exposed in the v1 settings picker. */
        fun selectable(): List<SttProvider> = entries.filter { it.shippingInV1 }
    }
}

/**
 * Builds the right [TranscriptionClient] for a provider — the seam where a non-OpenAI-compatible
 * provider would switch to its own implementation. v1 routes everything through the OpenAI-compatible
 * client (only Groq is reachable in the picker).
 */
object TranscriptionClientFactory {
    fun create(provider: SttProvider, apiKey: () -> String): TranscriptionClient =
        when {
            provider.openAiCompatible ->
                OpenAiCompatibleTranscriptionClient(baseUrl = provider.baseUrl, apiKey = apiKey)
            // else -> OpenRouterTranscriptionClient(...)  // drops in when the adapter spec lands
            else -> OpenAiCompatibleTranscriptionClient(baseUrl = provider.baseUrl, apiKey = apiKey)
        }
}
