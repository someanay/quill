package com.tihor.quill.accessibility

/**
 * Computes the string Quill writes into the focused field via `ACTION_SET_TEXT`.
 *
 * **Insert-at-caret (preserve content).** `ACTION_SET_TEXT` replaces the *entire* field, so to keep
 * what the user already typed we read the current text + caret, splice the transcript in at the
 * cursor, and hand back the full new string (plus where to put the caret afterwards). A non-empty
 * selection is overwritten by the transcript (standard insert-over-selection).
 *
 * **Telling a placeholder from real content — without per-app metadata.** Some apps (e.g. WhatsApp)
 * surface their placeholder as the field's `text` with no hint flags, which is indistinguishable from
 * real content *by text alone*. The reliable, universal signal is the **caret**: a placeholder is only
 * ever shown while the editable is genuinely empty, so the caret sits at position 0. Real content that
 * the user is dictating into has a caret *after* some text (position > 0). So we **replace** whenever
 * the caret is at the start (or unknown), and **insert** only when the caret is past position 0. This
 * kills the placeholder-prepend in every app, not just a hand-picked list.
 *
 * Trade-off (owned): dictating into real text with the caret deliberately at the very start replaces
 * instead of prepends. That's far rarer than appending to typed text, and never loses data silently —
 * the field still ends up as the transcript.
 *
 * Pure → unit-tested.
 */
object InsertionComposer {

    data class Composition(val newText: String, val newCaret: Int)

    /**
     * The full field text + caret after splicing [transcript] into [existingText] at
     * [selectionStart]..[selectionEnd]. Replaces the whole field (transcript only) when it reads as
     * empty/placeholder: showing its hint, no text, text equal to [hintText], or the caret at the
     * start ([selectionEnd] <= 0). [isShowingHintText]/[hintText] are belt-and-suspenders extras.
     */
    fun composeForInjection(
        existingText: CharSequence?,
        transcript: String,
        selectionStart: Int = -1,
        selectionEnd: Int = -1,
        isShowingHintText: Boolean = false,
        hintText: CharSequence? = null,
    ): Composition {
        val existing = existingText?.toString() ?: ""
        val emptyOrPlaceholder = isShowingHintText ||
            existing.isEmpty() ||
            (hintText != null && existing == hintText.toString()) ||
            selectionEnd <= 0 // caret at start / unknown ⇒ field is empty (text is a placeholder)
        if (emptyOrPlaceholder) {
            return Composition(newText = transcript, newCaret = transcript.length)
        }

        // Caret is past position 0 here, so there is real content to preserve.
        val len = existing.length
        val end = selectionEnd.coerceIn(0, len)
        val rawStart = if (selectionStart in 0..len) selectionStart else end
        val start = minOf(rawStart, end)
        val hi = maxOf(rawStart, end)

        // Appending right after a non-space char: add a separator so the words don't fuse.
        val appendingAtEnd = start == len && start == hi
        val needsSpace = appendingAtEnd && start > 0 &&
            !existing[start - 1].isWhitespace() &&
            transcript.isNotEmpty() && !transcript.first().isWhitespace()
        val payload = if (needsSpace) " $transcript" else transcript

        val newText = existing.substring(0, start) + payload + existing.substring(hi)
        return Composition(newText = newText, newCaret = start + payload.length)
    }
}
