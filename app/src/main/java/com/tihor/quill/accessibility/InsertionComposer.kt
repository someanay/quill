package com.tihor.quill.accessibility

/**
 * Computes the string Quill writes into the focused field via `ACTION_SET_TEXT`.
 *
 * **v1 decision — REPLACE the whole field with the transcript.** `ACTION_SET_TEXT` replaces the
 * entire field, and we deliberately do **not** preserve existing text: apps like WhatsApp expose
 * their placeholder *as the field's text* with no hint metadata (observed: `text="Message"`,
 * `hintText=null`, `isShowingHintText=false`), so a placeholder is indistinguishable from real
 * content. Reading "existing" text therefore risks prepending the placeholder ("Message" + dictation).
 * Replacing guarantees the field ends up as exactly the transcript.
 *
 * Trade-off (owned): dictating into a field that already holds *real* half-typed text overwrites it.
 * The PRD §5.2 "insert at cursor, preserve content" behaviour is a deferred P1 — it needs reliable
 * per-app emptiness detection (which the accessibility tree doesn't give us here) and is the natural
 * home for a future "insert vs replace" setting. Pure → unit-tested.
 */
object InsertionComposer {

    data class Composition(val newText: String, val newCaret: Int)

    /**
     * The full replacement text + caret for inserting [transcript]. [existingText] is accepted for
     * logging / the deferred preserve-content mode but is intentionally **ignored** in v1 (replace).
     */
    fun composeForInjection(existingText: CharSequence?, transcript: String): Composition =
        Composition(newText = transcript, newCaret = transcript.length)
}
