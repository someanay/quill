package com.tihor.quill.settings

import android.content.Context
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.audio.StopReason
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.stt.RomanizePrompt
import com.tihor.quill.stt.TranscriptionOptions
import com.tihor.quill.stt.TranscriptionError
import com.tihor.quill.errors.DictationIssue
import com.tihor.quill.errors.NetworkStatus
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

    private val recorder = AudioRecorder(context)
    private val sttKeyStore = SttKeyStore(KeystoreSecretStore(context))
    private val settingsRepo = SettingsRepository(context)

    fun hasMicPermission(): Boolean = recorder.hasMicPermission()

    val isRecording: Boolean get() = recorder.isRecording
    val recordingIssue: DictationIssue? get() = recorder.lastIssue

    /** Start capturing to a temp WAV; [onFinished] fires on the recording thread when it stops. */
    fun startRecording(onFinished: (File, StopReason) -> Unit) {
        val out = File(context.cacheDir, "quill_word_${System.currentTimeMillis()}.wav")
        recorder.start(output = out, capMs = CAP_MS, onFinished = onFinished)
    }

    fun stopRecording() = recorder.requestStop()

    /** Transcribe [wav] against the user's STT provider; returns the trimmed text or the original typed failure. */
    suspend fun transcribe(wav: File): Result<String> {
        val settings = settingsRepo.settings.first()
        // Per-provider key (decision 3): the active provider's own slot.
        val key = sttKeyStore.get(settings.provider)
        if (key.isNullOrBlank()) return Result.failure(TranscriptionError.MissingKey())
        val client = TranscriptionClientFactory.create(
            settings.provider,
            apiKey = { key },
            customBaseUrl = settings.customBaseUrl,
        )
        val language = settings.languageHint.takeUnless { it == QuillSettings.LANGUAGE_AUTO }
        if (NetworkStatus.isOffline(context)) return Result.failure(TranscriptionError.Offline())
        return client.transcribe(
            audio = wav,
            opts = TranscriptionOptions(
                model = settings.sttModel,
                language = language,
                prompt = RomanizePrompt.promptFor(language),
            ),
        ).map { it.text.trim() }

    }

    private companion object {
        const val CAP_MS = 8_000L
    }
}
