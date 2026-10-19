package com.tihor.quill.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

/**
 * One client covers **both** default providers: OpenAI (`https://api.openai.com/v1`) and Groq
 * (`https://api.groq.com/openai/v1`) share the same multipart `audio/transcriptions` shape — only the
 * base URL + model differ (spec §1). Deepgram/AssemblyAI can be added behind [TranscriptionClient].
 *
 * The key is read lazily via [apiKey] (from `SecretStore` at M7) so it lives in memory only for the
 * call and is never stored on the client; it is never logged (spec §6).
 */
class OpenAiCompatibleTranscriptionClient(
    private val baseUrl: String,
    private val apiKey: () -> String,
    private val httpClient: OkHttpClient = defaultClient(),
    private val json: Json = DEFAULT_JSON,
) : TranscriptionClient {

    override suspend fun transcribe(
        audio: File,
        opts: TranscriptionOptions,
    ): Result<Transcript> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey()
            TranscriptionError.validate(audio, opts, key, baseUrl)?.let { return@withContext Result.failure(it) }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", opts.model)
                .addFormDataPart("file", audio.name, audio.asRequestBody(AUDIO_WAV))
                .apply {
                    opts.language?.let { addFormDataPart("language", it) }
                    opts.prompt?.let { addFormDataPart("prompt", it) }
                }
                .build()

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
                .addHeader("Authorization", "Bearer $key")
                .post(body)
                .build()

            httpClient.newCall(request).executeCancellable { response ->
                val payload = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> parse(payload)
                    else -> Result.failure(TranscriptionError.fromHttp(response.code, payload))
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Transcription transport failed (${e.javaClass.simpleName})")
            Result.failure(TranscriptionError.Network(e))
        } catch (e: IllegalArgumentException) {
            // Request construction can throw for a malformed key (a pasted value with a newline or
            // non-ASCII char that OkHttp forbids in a header) or a bad custom base URL. Return a Result
            // so the "never throw" contract holds and KeyValidator/dictation surface an error, not a crash.
            Log.w(TAG, "Transcription request configuration is invalid")
            Result.failure(TranscriptionError.InvalidEndpoint())
        }
    }

    private fun parse(payload: String): Result<Transcript> =
        try {
            // Whisper/OpenAI-compatible endpoints return the transcript with a leading space (and
            // sometimes trailing whitespace). Trim it so inserted/clipboard text never starts with a
            // stray gap. Interior spacing is untouched.
            val text = json.decodeFromString<TranscriptionResponse>(payload).text.trim()
            if (text.isBlank()) Result.failure(TranscriptionError.EmptyResponse()) else Result.success(Transcript(text))
        } catch (e: Exception) {
            Result.failure(TranscriptionError.Parse(e))
        }

    companion object {
        private const val TAG = "Quill.Stt"
        private val AUDIO_WAV = "audio/wav".toMediaType()
        val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

        const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
        const val GROQ_DEFAULT_MODEL = "whisper-large-v3"
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
