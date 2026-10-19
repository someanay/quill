package com.tihor.quill.stt

import java.io.File
import com.tihor.quill.errors.DictationIssue
import kotlinx.serialization.json.*

/** Options for a transcription request (spec §3.3). */
data class TranscriptionOptions(
    val model: String,
    val language: String? = null,
    val prompt: String? = null,
)

data class Transcript(val text: String)

/** Typed failures so the UI can show a specific message (PRD edge cases), not a generic error. */
sealed class TranscriptionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Explicit authentication rejection; 403 without an invalid-key code is access denied. */
    class BadKey : TranscriptionError("That API key was rejected. Check it in settings.")
    class Offline : TranscriptionError("No network connection is available.")
    class MissingKey : TranscriptionError("No key saved for the selected speech provider.")
    class InvalidKeyFormat : TranscriptionError("The saved key contains unsupported characters.")
    class InvalidEndpoint : TranscriptionError("Check the custom provider URL.")
    class MissingModel : TranscriptionError("Choose a speech model in settings.")
    class AudioUnavailable : TranscriptionError("The recording is missing or empty.")

    /** 429 — provider rate limit; retryable. */
    class RateLimited : TranscriptionError("Rate limited by the provider. Try again shortly.")

    /** Explicit exhausted-credit/quota codes or HTTP 402. Generic 429s remain rate limits. */
    class QuotaExhausted : TranscriptionError("API usage has run out.")

    companion object {
        /** 429-body markers that mean "plan exhausted", not "slow down" (verified per-provider 2026-07). */
        private val QUOTA_MARKERS = listOf("insufficient_quota", "quota_exceeded", "billing_hard_limit_reached", "credit_balance_too_low")

        /** Maps a 429 to [QuotaExhausted] when the body carries a quota marker, else [RateLimited]. */
        fun fromTooManyRequests(body: String?): TranscriptionError =
            if (markers(body).any { it in QUOTA_MARKERS }) {
                QuotaExhausted()
            } else {
                RateLimited()
            }

        /** Read only the error envelope; untrusted text is inspected locally and never retained. */
        private fun markers(body: String?): List<String> = runCatching {
            val error = Json.parseToJsonElement(body.orEmpty().take(16_384)).jsonObject["error"]?.jsonObject
            buildList {
                listOf("code", "type", "status").forEach { key ->
                    (error?.get(key) as? JsonPrimitive)?.contentOrNull?.lowercase()?.let(::add)
                }
                (error?.get("details") as? JsonArray)?.forEach { detail ->
                    ((detail as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull?.lowercase()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

        fun fromHttp(status: Int, body: String?): TranscriptionError {
            val codes = markers(body)
            if (status == 401 || codes.any { it in setOf("api_key_invalid", "invalid_api_key", "api_key_expired", "key_expired") }) return BadKey()
            if (status == 402) return QuotaExhausted()
            if (status == 429) return fromTooManyRequests(body)
            val issue = when {
                codes.any { it in setOf("model_not_found", "model_decommissioned", "model_not_available", "unsupported_model") } -> DictationIssue.MODEL
                codes.any { it in setOf("invalid_audio", "invalid_audio_format", "audio_too_short") } -> DictationIssue.AUDIO_FORMAT
                status == 403 -> DictationIssue.ACCESS_DENIED
                status in setOf(404, 405, 410) -> DictationIssue.ENDPOINT
                status in setOf(408, 504) -> DictationIssue.TIMEOUT
                status == 413 -> DictationIssue.AUDIO_TOO_LARGE
                status == 415 -> DictationIssue.AUDIO_FORMAT
                status == 503 -> DictationIssue.PROVIDER_BUSY
                status >= 500 -> DictationIssue.SERVER
                else -> DictationIssue.REQUEST_REJECTED
            }
            return Provider(status, issue = issue)
        }

        fun validate(audio: File, opts: TranscriptionOptions, key: String, baseUrl: String): TranscriptionError? = when {
            key.isBlank() -> MissingKey()
            key.any { it.code !in 33..126 } -> InvalidKeyFormat()
            opts.model.isBlank() -> MissingModel()
            !audio.isFile || audio.length() <= 44L -> AudioUnavailable()
            // Loopback HTTP is allowed for local development/tests; remote endpoints require TLS.
            !validEndpoint(baseUrl) -> InvalidEndpoint()
            else -> null
        }

        private fun validEndpoint(url: String): Boolean = runCatching {
            val uri = java.net.URI(url)
            uri.host != null && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.scheme == "https" || uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "::1"))
        }.getOrDefault(false)
    }

    /** A provider failure stores only a status and our own category, never response text. */
    class Provider(val httpCode: Int, @Suppress("UNUSED_PARAMETER") body: String? = null,
                   val issue: DictationIssue = when (httpCode) {
                       403 -> DictationIssue.ACCESS_DENIED
                       503 -> DictationIssue.PROVIDER_BUSY
                       in 500..599 -> DictationIssue.SERVER
                       else -> DictationIssue.REQUEST_REJECTED
                   }) : TranscriptionError("Provider error $httpCode (${issue.name})")

    /** No network / timeout / connection failure; retryable. */
    class Network(cause: Throwable) : TranscriptionError("Network error. Recording saved — retry.", cause)

    /** 2xx but the body didn't parse. */
    class Parse(cause: Throwable) : TranscriptionError("Couldn't read the provider's response.", cause)

    class EmptyResponse : TranscriptionError("The model returned no transcript. Try again or choose another model in Speech & model.")

    class BlockedResponse : TranscriptionError("The provider blocked this recording and returned no transcript.")

    class IncompleteResponse : TranscriptionError("The provider stopped before completing the transcript. Try a shorter recording.")
}

/**
 * Transcribes audio against the user's BYOK provider. The interface intentionally does **not** assume
 * request/response, so streaming (P2) can slot in later (spec, PRD §6 P2).
 */
interface TranscriptionClient {
    suspend fun transcribe(audio: File, opts: TranscriptionOptions): Result<Transcript>
}
