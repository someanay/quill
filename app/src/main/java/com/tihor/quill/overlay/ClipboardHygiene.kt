package com.tihor.quill.overlay

/**
 * Pure helpers for the sensitive-clipboard auto-expiry (M11), so the "is it still ours?" guard is
 * unit-testable. After staging a failed-injection transcript to the clipboard (marked
 * EXTRA_IS_SENSITIVE), Quill schedules a clear ~60 s later — but only if the clip is *still* the one
 * Quill staged, so it never clobbers something the user copied in the meantime.
 */
object ClipboardHygiene {
    /** ClipData label used for every transcript Quill puts on the clipboard. */
    const val QUILL_CLIP_LABEL = "Quill dictation"

    /** Delay before a sensitive Quill clip is auto-cleared. */
    const val SENSITIVE_CLEAR_DELAY_MS = 60_000L

    /** Clear only when the current clip is still Quill's (matched by [QUILL_CLIP_LABEL]). */
    fun shouldClear(currentLabel: CharSequence?): Boolean =
        currentLabel?.toString() == QUILL_CLIP_LABEL
}
