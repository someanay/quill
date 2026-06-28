package com.tihor.quill.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Node-level regression for the WhatsApp "Message" prefix bug, matching the **real** node observed
 * on-device: `text="Message"`, `hintText=null`, `isShowingHintText=false` (no placeholder metadata).
 * Because a placeholder only shows while the editable is empty, its caret is at 0 — so Quill replaces
 * rather than prepends. A field with real content (caret past 0) is preserved and inserted into.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintNodeInjectionTest {

    private fun payloadFor(node: AccessibilityNodeInfo, transcript: String): String =
        InsertionComposer.composeForInjection(
            existingText = node.text,
            transcript = transcript,
            selectionStart = node.textSelectionStart,
            selectionEnd = node.textSelectionEnd,
            isShowingHintText = node.isShowingHintText,
            hintText = node.hintText,
        ).newText

    @Test
    fun whatsAppLikeNode_placeholderCaretAtStart_replacesWithTranscript() {
        val node = AccessibilityNodeInfo().apply {
            text = "Message" // hintText null, flag false
            setTextSelection(0, 0) // empty editable ⇒ caret at 0
        }

        // Guard the assumptions this test is built on (no hint metadata).
        assertEquals("Message", node.text)
        assertNull(node.hintText)
        assertFalse(node.isShowingHintText)

        assertEquals("hello world", payloadFor(node, "hello world")) // not "Message hello world"
    }

    @Test
    fun nodeWithRealContent_caretAtEnd_isPreservedAndAppended() {
        val node = AccessibilityNodeInfo().apply {
            text = "half typed"
            setTextSelection(10, 10)
        }
        assertEquals("half typed dictated", payloadFor(node, "dictated"))
    }
}
