package com.tihor.quill.stt

import com.tihor.quill.audio.WavHeader
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.settings.TranscriptionClientFactory
import java.io.File

/** Result of validating a BYOK key by a real (tiny) test transcription. */
sealed interface KeyValidation {
    data object Valid : KeyValidation
    data object Rejected : KeyValidation // bad key (401/403; Gemini: 400 API_KEY_INVALID)
    data class Unverified(val reason: String) : KeyValidation // network/other — saved but not confirmed
}

/**
 * Validates a key by sending a short silent clip to the **selected** provider (M7, extended for the
 * multi-provider picker). A BadKey means the key is bad — each client already maps its provider's
 * key-rejection shape (Gemini's 400 `API_KEY_INVALID` included) to [TranscriptionError.BadKey], so
 * the mapping here is provider-uniform. Success means it works; anything else (the key authenticated
 * but the request failed for another reason — e.g. Gemini returning nothing for silence — or no
 * network) is reported as unverified rather than blocking the user.
 *
 * [clientFactory] is injectable for tests; production uses [TranscriptionClientFactory].
 */
class KeyValidator(
    private val cacheDir: File,
    private val clientFactory: (SttProvider, () -> String, String?) -> TranscriptionClient =
        { provider, key, customBaseUrl -> TranscriptionClientFactory.create(provider, key, customBaseUrl) },
) {

    /**
     * [model] defaults to the provider's default; the Custom provider has none, so callers pass the
     * user's configured model (+ [customBaseUrl]). An unconfigured Custom endpoint can't be probed —
     * that returns [KeyValidation.Unverified] without any network call.
     */
    suspend fun validate(
        provider: SttProvider,
        key: String,
        model: String = provider.defaultModel,
        customBaseUrl: String? = null,
    ): KeyValidation {
        if (model.isBlank() || (provider == SttProvider.CUSTOM && customBaseUrl.isNullOrBlank())) {
            return KeyValidation.Unverified("endpoint not configured")
        }
        val wav = tinySilentWav()
        return try {
            val client = clientFactory(provider, { key }, customBaseUrl)
            val result = client.transcribe(wav, TranscriptionOptions(model = model))
            when {
                result.isSuccess -> KeyValidation.Valid
                result.exceptionOrNull() is TranscriptionError.BadKey -> KeyValidation.Rejected
                result.exceptionOrNull() is TranscriptionError.Network ->
                    KeyValidation.Unverified("no network")
                else -> KeyValidation.Unverified(result.exceptionOrNull()?.message ?: "couldn't verify")
            }
        } finally {
            wav.delete()
        }
    }

    private fun tinySilentWav(): File {
        val sampleRate = 16000
        val durationMs = 400
        val dataBytes = sampleRate * 2 * durationMs / 1000 // 16-bit mono
        val file = File.createTempFile("quill_keytest", ".wav", cacheDir)
        file.outputStream().use { out ->
            out.write(WavHeader.build(sampleRate, 1, 16, dataBytes))
            out.write(ByteArray(dataBytes)) // silence
        }
        return file
    }
}
