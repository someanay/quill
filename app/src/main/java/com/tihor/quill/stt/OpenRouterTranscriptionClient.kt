package com.tihor.quill.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * OpenRouter STT. Unlike the OpenAI-compatible providers, OpenRouter's dedicated
 * `POST /api/v1/audio/transcriptions` takes a **JSON body with base64-encoded audio** — the
 * `input_audio` contract — not multipart form-data (verified against openrouter.ai/docs, 2026-07):
 *
 * ```json
 * {"model": "openai/whisper-large-v3-turbo",
 *  "input_audio": {"data": "<base64 raw bytes>", "format": "wav"},
 *  "language": "en"}   // optional ISO-639-1; omitted for auto-detect
 * ```
 *
 * Response is `{"text": "...", "usage": {...}}` — parsed with the shared [TranscriptionResponse].
 * Model ids are namespaced (`openai/whisper-large-v3-turbo`). [TranscriptionOptions.prompt] is not
 * part of the top-level contract and is ignored. The key is read lazily via [apiKey] so it lives in
 * memory only for the call and is never logged (spec §6).
 */
class OpenRouterTranscriptionClient(
    private val apiKey: () -> String,
    private val baseUrl: String = BASE_URL,
    private val httpClient: OkHttpClient = defaultClient(),
    private val json: Json = OpenAiCompatibleTranscriptionClient.DEFAULT_JSON,
) : TranscriptionClient {

    @Serializable
    private data class InputAudio(val data: String, val format: String)

    @Serializable
    private data class TranscriptionRequest(
        val model: String,
        @SerialName("input_audio") val inputAudio: InputAudio,
        val language: String? = null, // omitted (not null) when unset — encodeDefaults is off
    )

    override suspend fun transcribe(
        audio: File,
        opts: TranscriptionOptions,
    ): Result<Transcript> = withContext(Dispatchers.IO) {
        try {
            val payload = json.encodeToString(
                TranscriptionRequest.serializer(),
                TranscriptionRequest(
                    model = opts.model,
                    inputAudio = InputAudio(
                        data = Base64.getEncoder().encodeToString(audio.readBytes()),
                        format = "wav",
                    ),
                    language = opts.language,
                ),
            ).toRequestBody(JSON_MEDIA)

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
                .addHeader("Authorization", "Bearer ${apiKey()}")
                .post(payload)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parse(body)
                    response.code == 401 || response.code == 403 -> Result.failure(TranscriptionError.BadKey())
                    // OpenRouter returns 402 Payment Required when the account's credits run out.
                    response.code == 402 -> Result.failure(TranscriptionError.QuotaExhausted())
                    response.code == 429 -> Result.failure(TranscriptionError.fromTooManyRequests(body))
                    else -> Result.failure(TranscriptionError.Provider(response.code, providerMessage(body)))
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "OpenRouter transcription network error: ${e.message}")
            Result.failure(TranscriptionError.Network(e))
        }
    }

    private fun parse(payload: String): Result<Transcript> =
        try {
            Result.success(Transcript(json.decodeFromString<TranscriptionResponse>(payload).text.trim()))
        } catch (e: Exception) {
            Result.failure(TranscriptionError.Parse(e))
        }

    private fun providerMessage(payload: String): String? =
        runCatching { json.decodeFromString<ProviderErrorEnvelope>(payload).error?.message }.getOrNull()

    companion object {
        private const val TAG = "Quill.Stt"
        private val JSON_MEDIA = "application/json".toMediaType()

        const val BASE_URL = "https://openrouter.ai/api/v1"

        // OpenRouter's dedicated STT catalog restored this namespaced Whisper model; verified
        // against the live transcription endpoint documentation in 2026-08.
        const val DEFAULT_MODEL = "openai/whisper-large-v3-turbo"
        const val LEGACY_DEFAULT_MODEL = "mistralai/voxtral-small-24b-2507"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
