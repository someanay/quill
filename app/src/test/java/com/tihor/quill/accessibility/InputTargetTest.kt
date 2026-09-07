package com.tihor.quill.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputTargetTest {
    private fun resolve(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(root, true)
        return EditableNodeResolver.findInputTarget(root)
    }

    private fun field(focused: Boolean) = AccessibilityNodeInfo().apply {
        isEditable = true
        isFocused = focused
        viewIdResourceName = if (focused) "test:id/target" else "test:id/sibling"
    }

    @Test fun findsFocusedComposeFieldInsideContainer() {
        val root = AccessibilityNodeInfo()
        val wrapper = AccessibilityNodeInfo()
        val target = field(true)
        shadowOf(root).addChild(wrapper)
        shadowOf(wrapper).addChild(target)
        assertEquals("test:id/target", resolve(root)?.viewIdResourceName)
    }

    @Test fun neverChoosesUnfocusedSibling() {
        val root = AccessibilityNodeInfo()
        shadowOf(root).addChild(field(false))
        assertNull(resolve(root))
    }

    @Test fun passwordFocusIsReturnedForSafetyCheckInsteadOfAnotherField() {
        val root = AccessibilityNodeInfo()
        val password = field(true).apply { isPassword = true }
        shadowOf(root).addChild(field(false))
        shadowOf(root).addChild(password)
        assertTrue(resolve(root)!!.isPassword)
    }

    @Test fun rootFieldDoesNotNeedSetTextActionToBeDetected() {
        val target = field(true)
        assertFalse(EditableNodeResolver.supportsSetText(target))
        assertEquals("test:id/target", resolve(target)?.viewIdResourceName)
    }
}
