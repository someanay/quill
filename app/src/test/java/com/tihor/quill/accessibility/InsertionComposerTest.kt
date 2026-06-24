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

    // --- Hint/placeholder handling (the WhatsApp "Message" prefix bug) ---

    @Test
    fun showingHint_flagTrue_resultIsExactlyTranscript_noPrefix() {
        // WhatsApp-style: text equals the hint "Message" and isShowingHintText=true.
        val r = InsertionComposer.composeForField(
            fieldText = "Message", hintText = "Message", isShowingHintText = true,
            selStart = 0, selEnd = 0, transcript = "hello world",
        )
        assertEquals("hello world", r.newText)
        assertEquals(11, r.newCaret)
    }

    @Test
    fun hintShownAsTextWithoutFlag_textEqualsHint_isTreatedAsEmpty() {
        // The app surfaces the hint as text but doesn't set isShowingHintText (the actual bug case).
        val r = InsertionComposer.composeForField(
            fieldText = "Message", hintText = "Message", isShowingHintText = false,
            selStart = 7, selEnd = 7, transcript = "hello world",
        )
        assertEquals("hello world", r.newText)
    }

    @Test
    fun realContentEqualToNothingSpecial_isPreserved_notTreatedAsHint() {
        val r = InsertionComposer.composeForField(
            fieldText = "Hello", hintText = "Message", isShowingHintText = false,
            selStart = 5, selEnd = 5, transcript = "world",
        )
        assertEquals("Hello world", r.newText)
    }

    @Test
    fun nullHint_realContent_appendsNormally() {
        val r = InsertionComposer.composeForField(
            fieldText = "Hello", hintText = null, isShowingHintText = false,
            selStart = 5, selEnd = 5, transcript = "world",
        )
        assertEquals("Hello world", r.newText)
    }

    @Test
    fun showingHint_selectionSpanningHintIsIgnored() {
        val r = InsertionComposer.composeForField(
            fieldText = "Message", hintText = "Message", isShowingHintText = true,
            selStart = 0, selEnd = 7, transcript = "hi",
        )
        assertEquals("hi", r.newText)
    }

    @Test
    fun emptyFieldWithHint_butUserTypedSameAsHint_edgeCase_treatedAsHintWhenFlagSet() {
        // If the user literally typed the hint word, the flag is the tie-breaker: flag=false +
        // text==hint is rare and we accept treating it as empty (safer than prefixing a placeholder).
        val flagged = InsertionComposer.composeForField("Message", "Message", true, 0, 0, "x")
        assertEquals("x", flagged.newText)
    }
}
