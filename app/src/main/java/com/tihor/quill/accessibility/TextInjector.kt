package com.tihor.quill.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/** Outcome of an insertion attempt (drives the bubble state). */
sealed interface InsertResult {
    /** Text was written into the field via `ACTION_SET_TEXT`. */
    data object Success : InsertResult

    /** SET_TEXT was unavailable/failed; the transcript is on the clipboard — show "Tap to Paste". */
    data object NeedsClipboardFallback : InsertResult

    /** Focused field is a password/banking field — Quill refuses to touch it (P0-5). */
    data object RefusedSensitive : InsertResult

    /** No editable field is focused. */
    data object NoEditableField : InsertResult
}

/**
 * Inserts dictated text into the focused field, with the SET_TEXT → clipboard fallback ladder of
 * spec §5C. The IME escape hatch (rung 4) is intentionally out of v1 but the interface keeps that
 * door open.
 */
interface TextInjector {

    /** Resolve the true editable node from [focus] and insert [text]. */
    fun insert(focus: AccessibilityNodeInfo, text: String): InsertResult

    /**
     * Perform the user-initiated clipboard paste (the "Tap to Paste" rung) into [focus], confirm it
     * landed, and only then restore the user's previous clipboard. Returns true if the paste was
     * confirmed.
     */
    fun paste(focus: AccessibilityNodeInfo): Boolean
}
