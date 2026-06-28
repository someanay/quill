package com.tihor.quill.settings

import android.content.Context
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.audio.StopReason
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.stt.RomanizePrompt
import com.tihor.quill.stt.TranscriptionOptions
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Captures a short voice sample and transcribes it with the user's BYOK provider, used by the
 * custom-words dialog to fill the "Heard as" form automatically. Reuses the exact dictation pipeline
 * ([AudioRecorder] → [com.tihor.quill.stt.TranscriptionClient]) so the captured text matches what the
 * model actually produces while dictating.
 *
 * Recording runs from a foreground Activity (no mic FGS needed — the app is visible). Silence
 * auto-stop ends the clip when the user stops talking; an 8 s cap bounds a stray recording.
 */
class VoiceWordCapture(private val context: Context) {

    /** Why a [transcribe] call failed — distinct enough for the UI to give a useful message. */
    sealed interface Failure {
        data object NoKey : Failure
        data object Network : Failure
    }

    private val recorder = AudioRecorder(context)
    private val secretStore: SecretStore = KeystoreSecretStore(context)
    private val settingsRepo = SettingsRepository(context)

    fun hasMicPermission(): Boolean = recorder.hasMicPermission()

    val isRecording: Boolean get() = recorder.isRecording

    /** Start capturing to a temp WAV; [onFinished] fires on the recording thread when it stops. */
    fun startRecording(onFinished: (File, StopReason) -> Unit) {
        val out = File(context.cacheDir, "quill_word_${System.currentTimeMillis()}.wav")
        recorder.start(output = out, capMs = CAP_MS, onFinished = onFinished)
    }

    fun stopRecording() = recorder.requestStop()

    /** Transcribe [wav] against the user's STT provider; returns the trimmed text or a [Failure]. */
    suspend fun transcribe(wav: File): Result<String> {
        val key = secretStore.get(SecretStore.ALIAS_STT_KEY)
        if (key.isNullOrBlank()) return Result.failure(FailureException(Failure.NoKey))
        val settings = settingsRepo.settings.first()
        val client = TranscriptionClientFactory.create(settings.provider, apiKey = { key })
        val language = settings.languageHint.takeUnless { it == QuillSettings.LANGUAGE_AUTO }
        return client.transcribe(
            audio = wav,
            opts = TranscriptionOptions(
                model = settings.sttModel,
                language = language,
                prompt = RomanizePrompt.promptFor(language),
            ),
        ).map { it.text.trim() }
            .recoverCatching { throw FailureException(Failure.Network) }
    }

    /** Carries a typed [Failure] out through [Result.failure]. */
    class FailureException(val failure: Failure) : Exception()

    private companion object {
        const val CAP_MS = 8_000L
    }
}
