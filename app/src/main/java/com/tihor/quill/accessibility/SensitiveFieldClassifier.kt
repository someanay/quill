package com.tihor.quill.accessibility

import android.text.InputType

/**
 * Decides whether a focused editable field is **sensitive** — a password (or banking-style secret)
 * field that Quill must neither read nor write (PRD §4 edge case, P0-5; spec §5 step B.5).
 *
 * A field is sensitive when it carries a **positive** password signal: the node's `isPassword` flag,
 * or a password input-type variation (including the web-password variation Chrome reports for
 * `<input type=password>`). Everything else — plain text, email, search boxes, address bars — is
 * dictatable, in browsers and native apps alike.
 *
 * We deliberately do **not** treat every browser text field as sensitive. That blanket rule made
 * normal web forms (and address bars, search boxes, comment fields) undictatable, which is the
 * common case. The residual risk — a site stripping the password variation off a real password box —
 * is covered downstream: a dictation that can't be safely inserted is never lost, it falls back to
 * the clipboard with a notification rather than being written into the wrong field.
 *
 * Pure and Android-constant-only so it is unit-testable on the JVM.
 */
object SensitiveFieldClassifier {

    /** The subset of node properties that determine sensitivity. */
    data class EditableFieldInfo(
        val isPassword: Boolean,
        val inputType: Int,
        val className: CharSequence? = null,
        val packageName: CharSequence? = null,
    )

    fun isSensitive(field: EditableFieldInfo): Boolean {
        // The node's own password flag / password input type is authoritative.
        if (field.isPassword) return true
        if (isPasswordInputType(field.inputType)) return true
        return false
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
