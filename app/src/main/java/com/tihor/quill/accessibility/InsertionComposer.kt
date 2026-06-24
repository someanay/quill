package com.tihor.quill.accessibility

/**
 * Computes the field's new contents and caret position when inserting a transcript at the cursor —
 * the read-compose-set step of spec §5B that prevents the overwrite bug (`ACTION_SET_TEXT` replaces
 * the *whole* field, so we must rebuild before+transcript+after ourselves).
 *
 * Spacing (PRD §5 decision 2: "insert, never replace; a leading/trailing space is added only when
 * the adjacent character requires it"): a single space is added between the transcript and an
 * adjacent non-whitespace character, and never doubled. Pure → unit-tested.
 */
object InsertionComposer {

    data class Composition(val newText: String, val newCaret: Int)

    /**
     * Hint-aware [compose]. A field that is only showing its placeholder/hint is treated as **empty**
     * so `ACTION_SET_TEXT` replaces it with exactly the transcript — never appends the placeholder
     * (the WhatsApp "Message" prefix bug). A field counts as showing its hint when
     * [isShowingHintText] is true (the documented signal) **or** its [fieldText] equals its
     * [hintText] (some apps, e.g. WhatsApp, surface the hint as text without setting the flag).
     */
    fun composeForField(
        fieldText: CharSequence?,
        hintText: CharSequence?,
        isShowingHintText: Boolean,
        selStart: Int,
        selEnd: Int,
        transcript: String,
    ): Composition {
        val text = fieldText?.toString() ?: ""
        val hint = hintText?.toString()
        val showingHint = isShowingHintText || (!hint.isNullOrEmpty() && text == hint)
        return if (showingHint) {
            compose(existing = "", selStart = -1, selEnd = -1, transcript = transcript)
        } else {
            compose(existing = text, selStart = selStart, selEnd = selEnd, transcript = transcript)
        }
    }

    /**
     * @param existing current field text
     * @param selStart selection/caret start; <0 or out-of-range is treated as end-of-text
     * @param selEnd   selection end; the [selStart, selEnd] range is replaced (Android selection)
     * @param transcript text to insert
     */
    fun compose(existing: String, selStart: Int, selEnd: Int, transcript: String): Composition {
        val len = existing.length
        var start = if (selStart in 0..len) selStart else len
        var end = if (selEnd in 0..len) selEnd else len
        if (start > end) {
            val t = start; start = end; end = t
        }

        val before = existing.substring(0, start)
        val after = existing.substring(end)

        if (transcript.isEmpty()) {
            // Nothing to insert; collapse any selection to its start.
            return Composition(before + after, before.length)
        }

        val needsLeading =
            before.isNotEmpty() && !before.last().isWhitespace() && !transcript.first().isWhitespace()
        val needsTrailing =
            after.isNotEmpty() && !after.first().isWhitespace() && !transcript.last().isWhitespace()

        val leading = if (needsLeading) " " else ""
        val trailing = if (needsTrailing) " " else ""

        val inserted = leading + transcript + trailing
        val newText = before + inserted + after
        // Caret hugs the end of the transcript (before any added trailing space), so continued
        // dictation flows naturally from the last inserted word.
        val newCaret = before.length + leading.length + transcript.length
        return Composition(newText, newCaret)
    }
}
