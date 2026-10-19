package com.tihor.quill.errors

import com.tihor.quill.stt.TranscriptionError
import kotlinx.coroutines.CancellationException
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Safe, bundled messages only. Never render exception messages or provider response bodies. */
enum class DictationIssue(val canRetryAudio: Boolean = false, private val setup: Recovery? = null) {
    MIC_PERMISSION(setup = Recovery.APP_PERMISSIONS),
    MIC_PRIVACY(setup = Recovery.PRIVACY_SETTINGS),
    OVERLAY_PERMISSION(setup = Recovery.OVERLAY_SETTINGS),
    ACCESSIBILITY(setup = Recovery.ACCESSIBILITY_SETTINGS),
    BACKGROUND_RECORDING(setup = Recovery.OPEN_QUILL),
    MIC_UNAVAILABLE,
    MIC_INTERRUPTED,
    AUDIO_STORAGE,
    AUDIO_MISSING,
    AUDIO_EMPTY,
    NO_KEY(true, Recovery.PROVIDER_SETTINGS),
    KEY_UNREADABLE(true, Recovery.PROVIDER_SETTINGS),
    KEY_FORMAT(true, Recovery.PROVIDER_SETTINGS),
    BAD_KEY(true, Recovery.PROVIDER_SETTINGS),
    ACCESS_DENIED(true, Recovery.PROVIDER_SETTINGS),
    QUOTA(true, Recovery.PROVIDER_SETTINGS),
    RATE_LIMIT(true),
    MODEL(true, Recovery.PROVIDER_SETTINGS),
    ENDPOINT(true, Recovery.PROVIDER_SETTINGS),
    AUDIO_TOO_LARGE,
    AUDIO_FORMAT,
    REQUEST_REJECTED(true, Recovery.PROVIDER_SETTINGS),
    OFFLINE(true),
    TIMEOUT(true),
    DNS(true),
    CONNECTION(true),
    CONNECTION_LOST(true),
    SECURE_CONNECTION(true),
    SERVER(true),
    PROVIDER_BUSY(true),
    RESPONSE_INVALID(true),
    NO_SPEECH,
    BLOCKED,
    INCOMPLETE,
    SETTINGS_READ(true, Recovery.OPEN_QUILL),
    UNKNOWN(true),
    SENSITIVE_FIELD(setup = Recovery.DISMISS),
    NO_FIELD(setup = Recovery.DISMISS),
    COPIED_NO_FIELD(setup = Recovery.DISMISS),
    COPIED_SENSITIVE(setup = Recovery.DISMISS),
    COPIED_ACCESSIBILITY(setup = Recovery.ACCESSIBILITY_SETTINGS),
    CLIPBOARD_UNAVAILABLE(true, Recovery.OPEN_QUILL);

    val titleKey: String get() = "dictation.error.${name.lowercase(java.util.Locale.ROOT)}.title"
    val messageKey: String get() = "dictation.error.${name.lowercase(java.util.Locale.ROOT)}.message"

    fun recovery(hasAudio: Boolean): Recovery = setup ?: if (canRetryAudio && hasAudio) Recovery.RETRY else Recovery.RECORD_AGAIN

    companion object {
        fun preflight(microphone: Boolean, privacyBlocked: Boolean, overlay: Boolean, accessibility: Boolean, clipboard: Boolean): DictationIssue? = when {
            !microphone -> MIC_PERMISSION
            privacyBlocked -> MIC_PRIVACY
            !overlay -> OVERLAY_PERMISSION
            !clipboard && !accessibility -> ACCESSIBILITY
            else -> null
        }

        fun from(error: Throwable): DictationIssue = when (error) {
            is CancellationException -> throw error
            is TranscriptionError.Offline -> OFFLINE
            is TranscriptionError.MissingKey -> NO_KEY
            is TranscriptionError.InvalidKeyFormat -> KEY_FORMAT
            is TranscriptionError.InvalidEndpoint -> ENDPOINT
            is TranscriptionError.MissingModel -> MODEL
            is TranscriptionError.AudioUnavailable -> AUDIO_MISSING
            is TranscriptionError.BadKey -> BAD_KEY
            is TranscriptionError.RateLimited -> RATE_LIMIT
            is TranscriptionError.QuotaExhausted -> QUOTA
            is TranscriptionError.Provider -> error.issue
            is TranscriptionError.Network -> when (error.cause) {
                is SSLException -> SECURE_CONNECTION
                is InterruptedIOException -> TIMEOUT
                is UnknownHostException -> DNS
                is ConnectException -> CONNECTION
                is FileNotFoundException -> AUDIO_MISSING
                else -> CONNECTION_LOST
            }
            is TranscriptionError.Parse -> RESPONSE_INVALID
            is TranscriptionError.EmptyResponse -> NO_SPEECH
            is TranscriptionError.BlockedResponse -> BLOCKED
            is TranscriptionError.IncompleteResponse -> INCOMPLETE
            else -> UNKNOWN
        }
    }
}

enum class Recovery {
    RETRY, RECORD_AGAIN, APP_PERMISSIONS, PRIVACY_SETTINGS, OVERLAY_SETTINGS, ACCESSIBILITY_SETTINGS, PROVIDER_SETTINGS, OPEN_QUILL, DISMISS;
    val labelKey: String get() = "dictation.recovery.${name.lowercase(java.util.Locale.ROOT)}"
}
