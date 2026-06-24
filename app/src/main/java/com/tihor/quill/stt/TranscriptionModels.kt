package com.tihor.quill.stt

import kotlinx.serialization.Serializable

/**
 * Response of `POST {baseUrl}/audio/transcriptions` (OpenAI / Groq compatible). kotlinx.serialization
 * (compile-time) keeps R8 keep-rules minimal (spec §1 / M5 R8 shift-left).
 */
@Serializable
data class TranscriptionResponse(val text: String)

/** Best-effort error envelope some providers return (`{"error": {"message": "..."}}`). */
@Serializable
data class ProviderErrorEnvelope(val error: ProviderErrorBody? = null)

@Serializable
data class ProviderErrorBody(val message: String? = null, val code: String? = null)
