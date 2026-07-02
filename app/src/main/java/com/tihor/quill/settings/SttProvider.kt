package com.tihor.quill.settings

import com.tihor.quill.copy.CopyKeys
import com.tihor.quill.stt.GeminiTranscriptionClient
import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.OpenRouterTranscriptionClient
import com.tihor.quill.stt.TranscriptionClient

/** A selectable model for a provider: the raw id (copy-exempt) + a soft-coded subtitle key. */
data class ProviderModel(val id: String, val subtitleKey: String)

/**
 * STT providers. All five ship selectable (decision 1, 2026-07): Groq (default), OpenAI, OpenRouter,
 * Gemini, and a user-configured Custom OpenAI-compatible endpoint. Each provider's key lives in its
 * **own** Keystore alias ([com.tihor.quill.secrets.SttKeyStore]) so a key entered for provider A is
 * never sent to provider B.
 *
 * [openAiCompatible] marks providers served by [OpenAiCompatibleTranscriptionClient] (multipart
 * `audio/transcriptions`). OpenRouter is **not** compatible — its STT endpoint takes JSON + base64
 * ([OpenRouterTranscriptionClient]); Gemini has no Whisper-style endpoint at all and transcribes via
 * `generateContent` ([GeminiTranscriptionClient]). Model ids verified against live docs 2026-07.
 */
enum class SttProvider(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    /** Exposed in the picker + shipped/working in v1. */
    val shippingInV1: Boolean,
    val openAiCompatible: Boolean = true,
    /** The provider's key console for the "Where do I get a key?" link; null hides the link (Custom). */
    val keyConsoleUrl: String? = null,
    /** Fixed model choices rendered by the picker (decision 6). Empty = free-text only. */
    val models: List<ProviderModel> = emptyList(),
    /** Whether the picker offers a free-text model field (OpenRouter's catalog is huge; Custom is user-defined). */
    val freeTextModel: Boolean = false,
) {
    GROQ(
        id = "groq",
        displayName = "Groq",
        baseUrl = OpenAiCompatibleTranscriptionClient.GROQ_BASE_URL,
        defaultModel = OpenAiCompatibleTranscriptionClient.GROQ_DEFAULT_MODEL,
        shippingInV1 = true,
        keyConsoleUrl = "https://console.groq.com/keys",
        models = listOf(
            ProviderModel("whisper-large-v3-turbo", CopyKeys.SETTINGS_MODEL_TURBO_SUB),
            ProviderModel("whisper-large-v3", CopyKeys.SETTINGS_MODEL_ACCURATE_SUB),
        ),
    ),

    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        baseUrl = OpenAiCompatibleTranscriptionClient.OPENAI_BASE_URL,
        defaultModel = "whisper-1",
        shippingInV1 = true,
        keyConsoleUrl = "https://platform.openai.com/api-keys",
        models = listOf(
            ProviderModel("whisper-1", CopyKeys.SETTINGS_MODEL_OPENAI_WHISPER_SUB),
            ProviderModel("gpt-4o-mini-transcribe", CopyKeys.SETTINGS_MODEL_OPENAI_MINI_SUB),
        ),
    ),

    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        baseUrl = OpenRouterTranscriptionClient.BASE_URL,
        defaultModel = OpenRouterTranscriptionClient.DEFAULT_MODEL,
        shippingInV1 = true,
        openAiCompatible = false, // JSON + base64 `input_audio`, not multipart (fixed: was stale-true)
        keyConsoleUrl = "https://openrouter.ai/keys",
        models = listOf(
            ProviderModel(OpenRouterTranscriptionClient.DEFAULT_MODEL, CopyKeys.SETTINGS_MODEL_OPENROUTER_DEFAULT_SUB),
        ),
        freeTextModel = true,
    ),

    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        baseUrl = GeminiTranscriptionClient.BASE_URL,
        defaultModel = GeminiTranscriptionClient.DEFAULT_MODEL,
        shippingInV1 = true,
        openAiCompatible = false, // no Whisper-style endpoint; native generateContent
        keyConsoleUrl = "https://aistudio.google.com/apikey",
        models = listOf(
            ProviderModel(GeminiTranscriptionClient.DEFAULT_MODEL, CopyKeys.SETTINGS_MODEL_GEMINI_LITE_SUB),
            ProviderModel("gemini-3.5-flash", CopyKeys.SETTINGS_MODEL_GEMINI_FLASH_SUB),
        ),
    ),

    /** User-supplied OpenAI-compatible endpoint: base URL + model both required (https-validated). */
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        baseUrl = "", // comes from settings.customBaseUrl, never the enum
        defaultModel = "",
        shippingInV1 = true,
        keyConsoleUrl = null, // no "where do I get a key" — the endpoint is the user's own
        freeTextModel = true,
    ),
    ;

    companion object {
        val DEFAULT = GROQ
        fun fromId(id: String?): SttProvider = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Providers exposed in the settings picker. */
        fun selectable(): List<SttProvider> = entries.filter { it.shippingInV1 }
    }
}

/**
 * Builds the right [TranscriptionClient] for a provider (decision 2). [customBaseUrl] is required
 * for [SttProvider.CUSTOM] (from `settings.customBaseUrl`); ignored for everyone else.
 */
object TranscriptionClientFactory {
    fun create(
        provider: SttProvider,
        apiKey: () -> String,
        customBaseUrl: String? = null,
    ): TranscriptionClient = when (provider) {
        SttProvider.OPENROUTER -> OpenRouterTranscriptionClient(apiKey = apiKey)
        SttProvider.GEMINI -> GeminiTranscriptionClient(apiKey = apiKey)
        SttProvider.CUSTOM ->
            OpenAiCompatibleTranscriptionClient(baseUrl = customBaseUrl?.trim().orEmpty(), apiKey = apiKey)
        else -> OpenAiCompatibleTranscriptionClient(baseUrl = provider.baseUrl, apiKey = apiKey)
    }
}
