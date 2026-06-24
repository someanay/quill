package com.tihor.quill.accessibility

import com.tihor.quill.accessibility.InsertionComposer.composeForInjection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1 REPLACE semantics (testing_plan.md §1): SET_TEXT writes exactly the transcript; existing field
 * content — including a placeholder surfaced as text (WhatsApp "Message") — is overwritten, never
 * appended.
 */
class InsertionComposerTest {

    @Test
    fun emptyField_resultIsTranscript() {
        val r = composeForInjection(existingText = "", transcript = "hello world")
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun nullExisting_resultIsTranscript() {
        val r = composeForInjection(existingText = null, transcript = "hello world")
        assertEquals("hello world", r.newText)
    }

    @Test
    fun placeholderSurfacedAsText_isOverwritten_noPrefix() {
        // The WhatsApp case: field "text" is the placeholder, no hint metadata.
        val r = composeForInjection(existingText = "Message", transcript = "hello world")
        assertEquals("hello world", r.newText) // not "Message hello world"
        assertEquals(11, r.newCaret)
    }

    @Test
    fun realContent_isAlsoReplaced_v1Tradeoff() {
        // Documented v1 trade-off: real existing content is overwritten (preserve-mode is P1).
        val r = composeForInjection(existingText = "Hello there", transcript = "world")
        assertEquals("world", r.newText)
        assertEquals(5, r.newCaret)
    }

    @Test
    fun caretIsAtEndOfTranscript() {
        assertEquals("hi".length, composeForInjection("anything", "hi").newCaret)
    }
}
