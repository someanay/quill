package com.tihor.quill.accessibility

import android.text.InputType
import com.tihor.quill.accessibility.SensitiveFieldClassifier.EditableFieldInfo
import com.tihor.quill.accessibility.SensitiveFieldClassifier.isSensitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 "write first" test: node properties → isSensitive (testing_plan.md §1). */
class SensitiveFieldClassifierTest {

    @Test
    fun passwordFlag_isSensitive_evenWithPlainInputType() {
        assertTrue(
            isSensitive(EditableFieldInfo(isPassword = true, inputType = InputType.TYPE_CLASS_TEXT)),
        )
    }

    @Test
    fun textPasswordVariations_areSensitive() {
        val base = InputType.TYPE_CLASS_TEXT
        assertTrue(isSensitive(field(base or InputType.TYPE_TEXT_VARIATION_PASSWORD)))
        assertTrue(isSensitive(field(base or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)))
        assertTrue(isSensitive(field(base or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    }

    @Test
    fun numberPassword_isSensitive() {
        assertTrue(
            isSensitive(field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)),
        )
    }

    @Test
    fun ordinaryText_isNotSensitive() {
        assertFalse(isSensitive(field(InputType.TYPE_CLASS_TEXT)))
        assertFalse(
            isSensitive(field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)),
        )
    }

    @Test
    fun ordinaryNumberAndPhone_areNotSensitive() {
        assertFalse(isSensitive(field(InputType.TYPE_CLASS_NUMBER)))
        assertFalse(isSensitive(field(InputType.TYPE_CLASS_PHONE)))
    }

    @Test
    fun zeroInputType_withoutPasswordFlag_isNotSensitive() {
        assertFalse(isSensitive(field(0)))
    }

    // --- Browser fields are NOT sensitive by default: only a positive password signal blocks
    //     dictation. Plain text / search boxes / address bars in a browser must be dictatable. ---

    @Test
    fun browser_plainText_isNotSensitive() {
        // A web <input type=text>, an address bar, or a search box reports TYPE_CLASS_TEXT — dictatable.
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun browser_nullType_isNotSensitive() {
        assertFalse(isSensitive(browserField(InputType.TYPE_NULL)))
    }

    @Test
    fun browser_number_isNotSensitive() {
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_NUMBER)))
    }

    @Test
    fun browser_email_url_phone_areNotSensitive() {
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_PHONE)))
    }

    @Test
    fun browser_webPassword_isSensitive_viaPasswordRule() {
        // The one browser case that still blocks: an explicitly-typed web password field.
        assertTrue(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    }

    @Test
    fun browser_passwordFlag_isSensitive() {
        assertTrue(isSensitive(EditableFieldInfo(isPassword = true, inputType = InputType.TYPE_CLASS_TEXT, packageName = "com.android.chrome")))
    }

    @Test
    fun anyApp_plainTextAndNull_areNotSensitive() {
        // Plain or untyped editable fields are fine to dictate into, browser or not.
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.whatsapp")))
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_NULL, packageName = "com.whatsapp")))
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.android.chrome")))
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.google.android.gm")))
    }

    private fun field(inputType: Int) = EditableFieldInfo(isPassword = false, inputType = inputType)
    private fun browserField(inputType: Int) =
        EditableFieldInfo(isPassword = false, inputType = inputType, packageName = "com.android.chrome")
}
