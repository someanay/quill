package com.tihor.quill.accessibility

import com.tihor.quill.accessibility.InsertionComposer.compose
import org.junit.Assert.assertEquals
import org.junit.Test

/** M3 "write first" test: insert-at-cursor composition incl. spacing rules (testing_plan.md §1). */
class InsertionComposerTest {

    @Test
    fun insertIntoEmptyField() {
        val r = compose("", 0, 0, "hello world")
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun appendAfterWord_addsSingleLeadingSpace() {
        val r = compose("hello", 5, 5, "world")
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun appendAfterExistingSpace_doesNotDoubleSpace() {
        val r = compose("hello ", 6, 6, "world")
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun transcriptWithLeadingSpace_isNotDoubled() {
        val r = compose("hello", 5, 5, " world")
        assertEquals("hello world", r.newText)
    }

    @Test
    fun insertInMiddle_addsLeadingAndTrailingSpaces() {
        // caret between "hello" and "there": "hello|there"
        val r = compose("hellothere", 5, 5, "world")
        assertEquals("hello world there", r.newText)
        // caret right after "...world", before the inserted trailing space's following text
        assertEquals("hello world".length, r.newCaret)
    }

    @Test
    fun insertBeforeWhitespace_noTrailingSpaceAdded() {
        // "hello| world" inserting "there" -> "hello there world"
        val r = compose("hello world", 5, 5, "there")
        assertEquals("hello there world", r.newText)
    }

    @Test
    fun replacesSelection() {
        // select "world" in "hello world" and insert "there"
        val r = compose("hello world", 6, 11, "there")
        assertEquals("hello there", r.newText)
        assertEquals("hello there".length, r.newCaret)
    }

    @Test
    fun unsetSelection_appendsAtEnd() {
        val r = compose("hello", -1, -1, "world")
        assertEquals("hello world", r.newText)
    }

    @Test
    fun outOfRangeSelection_treatedAsEnd() {
        val r = compose("hello", 999, 999, "world")
        assertEquals("hello world", r.newText)
    }

    @Test
    fun emptyTranscript_collapsesSelectionNoChange() {
        val r = compose("hello world", 0, 5, "")
        assertEquals(" world", r.newText)
        assertEquals(0, r.newCaret)
    }
}
