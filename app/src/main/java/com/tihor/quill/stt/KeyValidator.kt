package com.tihor.quill.stt

import com.tihor.quill.audio.WavHeader
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.settings.TranscriptionClientFactory
import java.io.File

/** Result of validating a BYOK key by a real (tiny) test transcription. */
sealed interface KeyValidation {
    data object Valid : KeyValidation
    data object Rejected : KeyValidation // 401/403 — bad key
    data class Unverified(val reason: String) : KeyValidation // network/other — saved but not confirmed
}

/**
 * Validates a key by sending a short silent clip to the provider (M7). A 401/403 means the key is
 * bad; success means it works; anything else (the key authenticated but the request failed for
 * another reason, or no network) is reported as unverified rather than blocking the user.
 */
class KeyValidator(private val cacheDir: File) {

    suspend fun validate(provider: SttProvider, key: String): KeyValidation {
        val wav = tinySilentWav()
        return try {
            val client = TranscriptionClientFactory.create(provider) { key }
            val result = client.transcribe(wav, TranscriptionOptions(model = provider.defaultModel))
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
