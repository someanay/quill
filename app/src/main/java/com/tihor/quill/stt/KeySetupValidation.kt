package com.tihor.quill.stt

import com.tihor.quill.models.ModelCatalog
import com.tihor.quill.models.ModelUse
import com.tihor.quill.settings.SttProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object KeySetupValidation {
    fun plausible(provider: SttProvider, value: String): Boolean {
        val key = value.trim()
        val prefix = when (provider) {
            SttProvider.GEMINI -> key.startsWith("AIza") || key.startsWith("AQ.")
            SttProvider.GROQ -> key.startsWith("gsk_")
            SttProvider.OPENROUTER -> key.startsWith("sk-or-")
            else -> false
        }
        return prefix && key.length in 20..4096 && key.all { it.isLetterOrDigit() || it in "-_." } &&
            !key.contains("...")
    }

    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    /** Authenticate without spending transcription tokens or uploading a silent recording. */
    suspend fun validate(provider: SttProvider, key: String, httpClient: OkHttpClient = client): KeyValidation = withContext(Dispatchers.IO) {
        if (!plausible(provider, key)) return@withContext KeyValidation.Rejected
        val url = if (provider == SttProvider.OPENROUTER) "https://openrouter.ai/api/v1/key"
            else ModelCatalog.endpoint(provider, ModelUse.DICTATION).toString()
        val request = Request.Builder().url(url)
            .header(if (provider == SttProvider.GEMINI) "x-goog-api-key" else "Authorization",
                if (provider == SttProvider.GEMINI) key else "Bearer $key").build()
        val result = try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> KeyValidation.Valid
                    response.code in listOf(400, 401, 403) -> KeyValidation.Rejected
                    else -> KeyValidation.Unverified("provider unavailable")
                }
            }
        } catch (_: java.io.IOException) { KeyValidation.Unverified("network unavailable") }
        ensureActive()
        result
    }
}
