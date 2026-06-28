package com.tihor.quill.cleanup

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * M10 LLM cleanup: send the raw transcript to Groq's `chat/completions` endpoint and get back a tidied
 * version (fixed capitalization/punctuation, obvious filler removed). Reuses the **same Groq base URL
 * and the same STT key** — there is deliberately no separate LLM key in v1. Pure text → text.
 *
 * **Fail-soft is the whole contract.** [clean] returns `null` on ANY problem — HTTP error, timeout,
 * empty or malformed response — so the caller falls back to the raw transcript. Cleanup is an
 * enhancement, never a dependency: it must never block, error, or empty a dictation. An independent
 * [timeoutMs] call timeout (separate from transcription) keeps it from noticeably extending the loop.
 *
 * The key is read lazily via [apiKey] so it lives in memory only for the call; it is never logged.
 */
class CleanupClient(
    private val baseUrl: String,
    private val apiKey: () -> String,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build(),
    private val json: Json = DEFAULT_JSON,
) {
    /** Tidied text, or `null` to signal "use the raw transcript" (fail-soft). */
    suspend fun clean(text: String): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null // nothing to clean — don't spend an API call
        try {
            val payload = json.encodeToString(
                ChatRequest.serializer(),
                ChatRequest(
                    model = MODEL,
                    messages = listOf(
                        ChatMessage(role = "system", content = SYSTEM_PROMPT),
                        ChatMessage(role = "user", content = text),
                    ),
                ),
            ).toRequestBody(JSON_MEDIA)

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer ${apiKey()}")
                .post(payload)
                .build()

            val cleaned = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                json.decodeFromString(ChatResponse.serializer(), body)
                    .choices.firstOrNull()?.message?.content?.trim()
            }
            cleaned?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e // let the dictation's own cancellation propagate; don't swallow it
        } catch (e: Exception) {
            Log.w(TAG, "Cleanup failed; using raw transcript: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "Quill.Cleanup"

        /** Hardcoded cleanup model (Groq). No user-facing picker in v1. */
        const val MODEL = "llama-3.3-70b-versatile"

        /** Independent of the transcription timeout; on expiry we fall back to the raw transcript. */
        const val DEFAULT_TIMEOUT_MS = 4_000L

        val SYSTEM_PROMPT =
            "You tidy up dictated text. Fix capitalization and punctuation, and remove obvious filler " +
                "words (\"um\", \"uh\", \"er\") and false starts. Preserve the original meaning, wording, " +
                "and names exactly. Keep Hinglish and other code-mixed words in their original Roman/Latin " +
                "script. Do not translate, summarize, answer, explain, or add anything. Return only the " +
                "corrected text, with no quotes or commentary."

        private val JSON_MEDIA = "application/json".toMediaType()
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }
    }
}
