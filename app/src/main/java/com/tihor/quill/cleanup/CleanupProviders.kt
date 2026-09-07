package com.tihor.quill.cleanup

import com.tihor.quill.settings.SttProvider
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Cleanup is selected independently of speech recognition; keys belong to providers. */
object CleanupProviders {
    fun defaultModel(provider: SttProvider): String = when (provider) {
        SttProvider.GROQ -> CleanupClient.MODEL
        SttProvider.OPENAI -> "gpt-4o-mini"
        SttProvider.OPENROUTER -> "openai/gpt-4o-mini"
        SttProvider.GEMINI -> "gemini-3.5-flash-lite"
        SttProvider.CUSTOM -> ""
    }

    fun baseUrl(provider: SttProvider, customBaseUrl: String): String {
        val value = when (provider) {
            SttProvider.GEMINI -> "${provider.baseUrl}/v1beta/openai"
            SttProvider.CUSTOM -> customBaseUrl.trim()
            else -> provider.baseUrl
        }
        val url = value.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null)
        return value.trimEnd('/')
    }

    fun key(provider: SttProvider, read: (SttProvider) -> String?): String? =
        read(provider)?.takeIf { it.isNotBlank() }
}
