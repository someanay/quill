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

    private fun field(inputType: Int) = EditableFieldInfo(isPassword = false, inputType = inputType)
}
