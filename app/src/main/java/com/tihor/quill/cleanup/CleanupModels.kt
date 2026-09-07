package com.tihor.quill.cleanup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Request/response for `POST {baseUrl}/chat/completions` (OpenAI / Groq chat-completions shape).
 * kotlinx.serialization (compile-time) keeps R8 keep-rules minimal, matching the STT models.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double,
    val provider: ProviderRouting? = null,
)

@Serializable
data class ProviderRouting(val sort: String)

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatResponse(val choices: List<ChatChoice> = emptyList())

@Serializable
data class ChatChoice(val message: ChatMessage? = null, @SerialName("finish_reason") val finishReason: String? = null)
