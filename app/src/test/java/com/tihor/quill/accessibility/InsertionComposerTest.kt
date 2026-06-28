package com.tihor.quill.accessibility

import com.tihor.quill.accessibility.InsertionComposer.composeForInjection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Insert-at-caret semantics (testing_plan.md §1): the transcript is spliced into the existing field
 * content at the caret. A field reads as empty/placeholder — and is set to exactly the transcript —
 * when it shows its hint, has no text, its text equals the hint, OR the caret is at the start (which
 * is the universal "placeholder surfaced as text" signal, since a placeholder shows only when empty).
 */
class InsertionComposerTest {

    @Test
    fun emptyField_resultIsTranscript() {
        val r = composeForInjection(existingText = "", transcript = "hello world", selectionEnd = 0)
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun nullExisting_resultIsTranscript() {
        assertEquals("hello world", composeForInjection(existingText = null, transcript = "hello world").newText)
    }

    @Test
    fun showingHintText_replacesWithTranscript() {
        val r = composeForInjection(
            existingText = "Message", transcript = "hello world",
            selectionStart = 7, selectionEnd = 7, isShowingHintText = true,
        )
        assertEquals("hello world", r.newText)
    }

    @Test
    fun textEqualsHint_replacesWithTranscript() {
        val r = composeForInjection(
            existingText = "Search", transcript = "cats", selectionStart = 6, selectionEnd = 6, hintText = "Search",
        )
        assertEquals("cats", r.newText)
    }

    @Test
    fun placeholderSurfacedAsText_caretAtStart_isReplaced() {
        // The WhatsApp case: text="Message", no hint metadata, caret at 0 (field is really empty).
        val r = composeForInjection(
            existingText = "Message", transcript = "hello world", selectionStart = 0, selectionEnd = 0,
        )
        assertEquals("hello world", r.newText) // not "Message hello world"
    }

    @Test
    fun unknownSelection_isReplaced() {
        // No caret info (-1) ⇒ can't trust the text isn't a placeholder ⇒ replace.
        val r = composeForInjection(existingText = "Message", transcript = "hello world")
        assertEquals("hello world", r.newText)
    }

    @Test
    fun realContent_caretAtEnd_appendsWithSpace() {
        val r = composeForInjection(
            existingText = "Hello", transcript = "world", selectionStart = 5, selectionEnd = 5,
        )
        assertEquals("Hello world", r.newText)
        assertEquals("Hello world".length, r.newCaret)
    }

    @Test
    fun realContent_caretInMiddle_insertsThereNoExtraSpace() {
        val r = composeForInjection(
            existingText = "ab cd", transcript = "X", selectionStart = 3, selectionEnd = 3,
        )
        assertEquals("ab Xcd", r.newText)
        assertEquals(4, r.newCaret)
    }

    @Test
    fun selectionRange_isOverwrittenByTranscript() {
        val r = composeForInjection(
            existingText = "hello world", transcript = "hi", selectionStart = 0, selectionEnd = 5,
        )
        assertEquals("hi world", r.newText)
        assertEquals(2, r.newCaret)
    }

    @Test
    fun appendDoesNotDoubleSpace_whenExistingEndsWithSpace() {
        val r = composeForInjection(
            existingText = "hello ", transcript = "world", selectionStart = 6, selectionEnd = 6,
        )
        assertEquals("hello world", r.newText)
    }
}
