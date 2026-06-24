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

    // --- Browser-context safety rule: Chrome can report a web password as plain text with
    //     isPassword=false, so in a browser we refuse anything not positively confirmed safe. ---

    @Test
    fun browser_plainText_isSensitive_couldBeMasqueradingPassword() {
        // A web <input type=text> reports 0x1 (TYPE_CLASS_TEXT) — same as a stripped web password.
        assertTrue(isSensitive(browserField(InputType.TYPE_CLASS_TEXT)))
    }

    @Test
    fun browser_nullType_isSensitive() {
        assertTrue(isSensitive(browserField(InputType.TYPE_NULL)))
    }

    @Test
    fun browser_number_isSensitive_couldBePin() {
        assertTrue(isSensitive(browserField(InputType.TYPE_CLASS_NUMBER)))
    }

    @Test
    fun browser_email_url_phone_areConfirmedSafe() {
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)))
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)))
        assertFalse(isSensitive(browserField(InputType.TYPE_CLASS_PHONE)))
    }

    @Test
    fun browser_webPassword_isSensitive_viaPasswordRule() {
        assertTrue(isSensitive(browserField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)))
    }

    @Test
    fun nonBrowser_plainTextAndNull_areNotSensitive() {
        // Native fields are trusted: a plain or untyped native EditText is fine to dictate into.
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.whatsapp")))
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_NULL, packageName = "com.whatsapp")))
    }

    @Test
    fun browserDetection_byKnownPackageAndHeuristic() {
        assertTrue(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.android.chrome")))
        assertTrue(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.sec.android.app.sbrowser")))
        assertTrue(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.acme.coolbrowser")))
        // Not a browser → plain text allowed.
        assertFalse(isSensitive(EditableFieldInfo(false, InputType.TYPE_CLASS_TEXT, packageName = "com.google.android.gm")))
    }

    private fun field(inputType: Int) = EditableFieldInfo(isPassword = false, inputType = inputType)
    private fun browserField(inputType: Int) =
        EditableFieldInfo(isPassword = false, inputType = inputType, packageName = "com.android.chrome")
}
