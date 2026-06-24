package com.tihor.quill.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** M7 "write first": settings persist and read back (testing_plan.md §1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private fun repo() = SettingsRepository(RuntimeEnvironment.getApplication())

    @Test
    fun defaults_areGroqAndSensible() = runTest {
        val s = repo().settings.first()
        assertEquals(SttProvider.GROQ, s.provider)
        assertEquals(SttProvider.GROQ.defaultModel, s.sttModel)
        assertNull(s.languageHint)
        assertFalse(s.privateMode)
        assertTrue(s.cleanupEnabled)
        assertEquals(300, s.sessionCapSeconds)
        assertFalse(s.onboardingComplete)
    }

    @Test
    fun setValues_roundTrip() = runTest {
        val r = repo()
        r.setPrivateMode(true)
        r.setLanguageHint("en")
        r.setSessionCapSeconds(120)
        r.setCleanupEnabled(false)
        r.setOnboardingComplete(true)

        val s = r.settings.first()
        assertTrue(s.privateMode)
        assertEquals("en", s.languageHint)
        assertEquals(120, s.sessionCapSeconds)
        assertFalse(s.cleanupEnabled)
        assertTrue(s.onboardingComplete)
    }

    @Test
    fun blankLanguageHint_isStoredAsNull() = runTest {
        val r = repo()
        r.setLanguageHint("fr")
        assertEquals("fr", r.settings.first().languageHint)
        r.setLanguageHint("")
        assertNull(r.settings.first().languageHint)
    }
}
