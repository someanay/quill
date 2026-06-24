package com.tihor.quill.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Node-level regression for the WhatsApp "Message" hint-prefix bug: a real [AccessibilityNodeInfo]
 * whose `text` equals its `hintText` must inject **exactly** the dictated string, no placeholder
 * prefix. Exercises the same property reads the injector uses (`text`, `hintText`,
 * `isShowingHintText`, selection) through [InsertionComposer.composeForField].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintNodeInjectionTest {

    private fun compose(node: AccessibilityNodeInfo, transcript: String) =
        InsertionComposer.composeForField(
            fieldText = node.text,
            hintText = node.hintText,
            isShowingHintText = node.isShowingHintText,
            selStart = node.textSelectionStart,
            selEnd = node.textSelectionEnd,
            transcript = transcript,
        )

    @Test
    fun nodeShowingHintAsText_injectsExactlyTranscript_noPrefix() {
        val node = AccessibilityNodeInfo().apply {
            text = "Message"      // WhatsApp surfaces the hint as the node text…
            hintText = "Message"  // …matching the hint, without setting isShowingHintText.
        }
        val result = compose(node, "hello world")
        assertEquals("hello world", result.newText)
    }

    @Test
    fun nodeWithRealContent_appendsAtCursor_preservesText() {
        val node = AccessibilityNodeInfo().apply {
            text = "Hello"
            hintText = "Message"
        }
        val result = compose(node, "world")
        assertEquals("Hello world", result.newText)
    }
}
