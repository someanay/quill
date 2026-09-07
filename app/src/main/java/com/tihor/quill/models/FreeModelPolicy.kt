package com.tihor.quill.models

import com.tihor.quill.cleanup.CleanupProviders
import com.tihor.quill.settings.SttProvider

/** Free-tier defaults checked against provider pricing, September 2026. Quotas still apply. */
object FreeModelPolicy {
    fun cleanup(provider: SttProvider): String? = when (provider) {
        SttProvider.GEMINI, SttProvider.GROQ, SttProvider.OPENROUTER -> CleanupProviders.defaultModel(provider)
        SttProvider.OPENAI, SttProvider.CUSTOM -> null
    }

    fun knownFree(provider: SttProvider, id: String): Boolean = when (provider) {
        SttProvider.GEMINI -> id in setOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash",
            "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.5-transcribe")
        SttProvider.GROQ -> id in setOf("whisper-large-v3-turbo", "whisper-large-v3", "llama-3.3-70b-versatile")
        SttProvider.OPENROUTER -> id == "openrouter/free" || id.endsWith(":free")
        else -> false
    }

    fun preferred(models: List<ModelOption>, fallback: String, use: ModelUse): String =
        ModelCatalog.rank(models, use).firstOrNull { it.isFree }?.id ?: fallback
}
