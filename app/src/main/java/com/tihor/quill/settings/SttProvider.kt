package com.tihor.quill.settings

import com.tihor.quill.copy.CopyKeys
import com.tihor.quill.stt.GeminiTranscriptionClient
import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.OpenRouterTranscriptionClient
import com.tihor.quill.stt.TranscriptionClient

/**
 * A selectable model for a provider: the raw id (copy-exempt) + a soft-coded subtitle key.
 * [paidOnly] = no free tier exists for this model; the picker hides it once a key-save probe has
 * confirmed the stored key is free-tier-only (see `KeyValidator.hasPaidAccess`).
 */
data class ProviderModel(val id: String, val subtitleKey: String, val paidOnly: Boolean = false)

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
    /** Marked "free" in the key-console picker (Gemini's AI Studio key has a no-card free tier). */
    val keyConsoleFree: Boolean = false,
    /** Copy key for the step-by-step "get your key" guide shown before opening the console; null = open directly. */
    val keyGuideStepsKey: String? = null,
    /** Placeholder mask matching THIS provider's key format (copy-exempt: a format, not copy). */
    val keyMask: String = "••••••••••••••••••••",
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
        keyGuideStepsKey = CopyKeys.KEY_GUIDE_GROQ_STEPS,
        keyMask = "gsk_••••••••••••••••",
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
        keyGuideStepsKey = CopyKeys.KEY_GUIDE_OPENAI_STEPS,
        keyMask = "sk-••••••••••••••••••",
        // /audio/transcriptions catalog 2026-07 (dated snapshots + the diarize variant omitted:
        // diarization is multi-speaker, pointless for single-voice dictation).
        models = listOf(
            ProviderModel("whisper-1", CopyKeys.SETTINGS_MODEL_OPENAI_WHISPER_SUB),
            ProviderModel("gpt-4o-mini-transcribe", CopyKeys.SETTINGS_MODEL_OPENAI_MINI_SUB),
            ProviderModel("gpt-4o-transcribe", CopyKeys.SETTINGS_MODEL_OPENAI_4O_SUB),
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
        keyGuideStepsKey = CopyKeys.KEY_GUIDE_OPENROUTER_STEPS,
        keyMask = "sk-or-v1-••••••••••••",
        // Every audio-input model on OpenRouter (catalog check 2026-07-02); Whisper is delisted.
        models = listOf(
            ProviderModel(OpenRouterTranscriptionClient.DEFAULT_MODEL, CopyKeys.SETTINGS_MODEL_OPENROUTER_VOXTRAL_SUB),
            ProviderModel("openai/gpt-audio-mini", CopyKeys.SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_MINI_SUB),
            ProviderModel("openai/gpt-audio", CopyKeys.SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_SUB),
            ProviderModel("google/gemini-3.5-flash", CopyKeys.SETTINGS_MODEL_OPENROUTER_GEMINI_FLASH_SUB),
            ProviderModel("google/gemini-3.1-flash-lite", CopyKeys.SETTINGS_MODEL_OPENROUTER_GEMINI_LITE_SUB),
            ProviderModel("nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", CopyKeys.SETTINGS_MODEL_OPENROUTER_NEMOTRON_SUB),
            ProviderModel("xiaomi/mimo-v2.5", CopyKeys.SETTINGS_MODEL_OPENROUTER_MIMO_SUB),
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
        keyConsoleFree = true,
        keyGuideStepsKey = CopyKeys.KEY_GUIDE_GEMINI_STEPS,
        // Google issues two Gemini key formats: AIza… (AI Studio classic) and AQ.… (Cloud v2 /
        // Vertex express). Show both so neither looks malformed.
        keyMask = "AIza•••••• / AQ.••••••",
        // Every current-gen REST audio model (docs check 2026-07). 2.5-series omitted (shutdown
        // 2026-10-16). Live models (gemini-*-live-*) are WebSocket-only — no one-shot
        // generateContent transcription — so they can never appear here.
        models = listOf(
            ProviderModel(GeminiTranscriptionClient.DEFAULT_MODEL, CopyKeys.SETTINGS_MODEL_GEMINI_LITE_SUB),
            ProviderModel("gemini-3.5-flash", CopyKeys.SETTINGS_MODEL_GEMINI_FLASH_SUB),
            ProviderModel("gemini-3-flash-preview", CopyKeys.SETTINGS_MODEL_GEMINI_PREVIEW_SUB),
            ProviderModel("gemini-3.1-pro-preview", CopyKeys.SETTINGS_MODEL_GEMINI_PRO_SUB, paidOnly = true),
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
