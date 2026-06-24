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
 * on-device: `text="Message"`, `hintText=null`, `isShowingHintText=false` (no placeholder metadata
 * at all). The SET_TEXT payload must be exactly the dictated string — the whole field is replaced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintNodeInjectionTest {

    private fun payloadFor(node: AccessibilityNodeInfo, transcript: String): String =
        InsertionComposer.composeForInjection(existingText = node.text, transcript = transcript).newText

    @Test
    fun whatsAppLikeNode_textIsPlaceholder_noHintMetadata_replacesWithTranscript() {
        val node = AccessibilityNodeInfo().apply { text = "Message" } // hintText null, flag false

        // Guard the assumptions this test is built on (don't assume hint metadata exists).
        assertEquals("Message", node.text)
        assertNull(node.hintText)
        assertFalse(node.isShowingHintText)

        assertEquals("hello world", payloadFor(node, "hello world"))
    }

    @Test
    fun nodeWithRealContent_isAlsoReplaced_v1ReplaceSemantics() {
        val node = AccessibilityNodeInfo().apply { text = "half typed" }
        assertEquals("dictated text", payloadFor(node, "dictated text"))
    }
}
