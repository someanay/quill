package com.tihor.quill.overlay

import com.tihor.quill.accessibility.InsertResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11 revocation distinction: only a disconnected accessibility service offers the one-tap
 * "Re-enable Quill" action — a focused-but-sensitive field or no-field-focused must not.
 */
class InjectionFailureTest {

    @Test
    fun serviceNotConnected_isRevoked_andOffersReEnable() {
        val f = InjectionFailure.classify(serviceConnected = false, result = null)
        assertEquals(InjectionFailure.ServiceRevoked, f)
        assertTrue(f.offersReEnable)
    }

    @Test
    fun noEditableField_doesNotOfferReEnable() {
        val f = InjectionFailure.classify(serviceConnected = true, result = InsertResult.NoEditableField)
        assertEquals(InjectionFailure.NoField, f)
        assertFalse(f.offersReEnable)
    }

    @Test
    fun sensitiveField_doesNotOfferReEnable() {
        val f = InjectionFailure.classify(serviceConnected = true, result = InsertResult.RefusedSensitive)
        assertEquals(InjectionFailure.SensitiveField, f)
        assertFalse(f.offersReEnable)
    }
}
