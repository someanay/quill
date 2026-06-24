package com.tihor.quill.stt

import java.io.File

/** Options for a transcription request (spec §3.3). */
data class TranscriptionOptions(
    val model: String,
    val language: String? = null,
    val prompt: String? = null,
)

data class Transcript(val text: String)

/** Typed failures so the UI can show a specific message (PRD edge cases), not a generic error. */
sealed class TranscriptionError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 401/403 — bad or expired key; point the user at key settings. */
    class BadKey : TranscriptionError("That API key was rejected. Check it in settings.")

    /** 429 — provider rate limit; retryable. */
    class RateLimited : TranscriptionError("Rate limited by the provider. Try again shortly.")

    /** Other non-2xx from the provider. */
    class Provider(val httpCode: Int, body: String? = null) :
        TranscriptionError("Provider error $httpCode${body?.let { ": $it" } ?: ""}")

    /** No network / timeout / connection failure; retryable. */
    class Network(cause: Throwable) : TranscriptionError("Network error. Recording saved — retry.", cause)

    /** 2xx but the body didn't parse. */
    class Parse(cause: Throwable) : TranscriptionError("Couldn't read the provider's response.", cause)
}

/**
 * Transcribes audio against the user's BYOK provider. The interface intentionally does **not** assume
 * request/response, so streaming (P2) can slot in later (spec, PRD §6 P2).
 */
interface TranscriptionClient {
    suspend fun transcribe(audio: File, opts: TranscriptionOptions): Result<Transcript>
}
