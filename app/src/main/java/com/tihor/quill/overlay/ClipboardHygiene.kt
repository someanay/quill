package com.tihor.quill.overlay

import java.util.UUID

/**
 * Pure helpers for the sensitive-clipboard auto-expiry (M11), so the "is it still ours?" guard is
 * unit-testable. After staging a failed-injection transcript to the clipboard (marked
 * EXTRA_IS_SENSITIVE), Quill schedules a clear ~60 s later — but only if the clip is *still* the one
 * Quill staged, so it never clobbers something the user copied in the meantime. Each clip carries a
 * unique token in its label so two rapid Quill clips won't race each other's clear callbacks.
 */
object ClipboardHygiene {
    /** ClipData label prefix used for every transcript Quill puts on the clipboard. */
    const val QUILL_CLIP_LABEL = "Quill dictation"

    /** Delay before a sensitive Quill clip is auto-cleared. */
    const val SENSITIVE_CLEAR_DELAY_MS = 60_000L

    /** Produce a unique clip label with an embedded token so two clips don't alias. */
    fun tokenLabel(): String = "$QUILL_CLIP_LABEL/${UUID.randomUUID()}"

    /**
     * Clear only when the current clip is still Quill's AND carries the exact [expectedToken].
     * If [expectedToken] is null this is a user-initiated copy (no auto-expiry) — always match.
     */
    fun shouldClear(currentLabel: CharSequence?, expectedToken: String?): Boolean {
        if (expectedToken == null) return false // never auto-clear user-initiated copies
        return currentLabel?.toString() == expectedToken
    }
}
