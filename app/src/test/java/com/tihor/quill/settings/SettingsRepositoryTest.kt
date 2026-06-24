package com.tihor.quill.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Default is English so mixed-language (Hinglish) dictation comes out in Roman script.
        assertEquals("en", s.languageHint)
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
    fun languageHint_roundTripsCodesAndAutoSentinel() = runTest {
        val r = repo()
        r.setLanguageHint("hi")
        assertEquals("hi", r.settings.first().languageHint)
        // Auto-detect is an explicit choice, distinct from the "en" default.
        r.setLanguageHint(QuillSettings.LANGUAGE_AUTO)
        assertEquals(QuillSettings.LANGUAGE_AUTO, r.settings.first().languageHint)
        // Clearing falls back to the English default.
        r.setLanguageHint("")
        assertEquals("en", r.settings.first().languageHint)
    }
}
