package com.tihor.quill

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Confirms the Robolectric harness is wired (M0). Pinned to a stable SDK rather than compileSdk 37
 * so the test does not depend on Robolectric shipping a shadow for the very latest API.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {
    @Test
    fun applicationContext_hasExpectedPackage() {
        assertEquals("com.tihor.quill", RuntimeEnvironment.getApplication().packageName)
    }
}
