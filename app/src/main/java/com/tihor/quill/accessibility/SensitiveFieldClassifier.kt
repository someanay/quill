package com.tihor.quill.accessibility

import android.text.InputType

/**
 * Decides whether a focused editable field is **sensitive** — a password (or banking-style secret)
 * field that Quill must neither read nor write (PRD §4 edge case, P0-5; spec §5 step B.5).
 *
 * Pure and Android-constant-only so it is unit-testable on the JVM (the `InputType` values are
 * compile-time constants). The real [QuillAccessibilityService] maps an `AccessibilityNodeInfo`
 * onto [EditableFieldInfo] and calls [isSensitive].
 */
object SensitiveFieldClassifier {

    /** The subset of node properties that determine sensitivity. */
    data class EditableFieldInfo(
        val isPassword: Boolean,
        val inputType: Int,
        val className: CharSequence? = null,
    )

    fun isSensitive(field: EditableFieldInfo): Boolean {
        // The node's own password flag is authoritative when set.
        if (field.isPassword) return true

        val variation = field.inputType and InputType.TYPE_MASK_VARIATION
        return when (field.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

            else -> false
        }
    }
}
