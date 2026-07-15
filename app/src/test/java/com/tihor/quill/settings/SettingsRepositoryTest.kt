package com.tihor.quill.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    // Robolectric caches the DataStore singleton across methods in a class, so reset to a known clean
    // baseline before each test (the suite previously passed only by lucky method ordering).
    @Before
    fun reset() {
        runBlocking {
            val r = repo()
            r.setProvider(SttProvider.GROQ) // also resets sttModel to the provider default
            r.setLanguageHint("en")
            r.setCleanupEnabled(false) // M10: cleanup is opt-in; baseline mirrors the default-off
            r.setPrivateMode(false)
            r.setHistoryEnabled(true)
            r.setHistoryRetentionMinutes(QuillSettings.RETENTION_DEFAULT_MINUTES)
            r.setSessionCapSeconds(300)
            r.setOnboardingComplete(false)
            r.setBubbleSize(QuillSettings.BUBBLE_SIZE_MEDIUM)
            r.setAppTheme(QuillSettings.APP_THEME_SYSTEM)
            r.setBubbleActive(false)
            r.setBubbleVisible(true)
            r.setCustomBaseUrl("")
            r.setCustomModel("")
            r.setFreeTierOnly(SttProvider.GEMINI, freeOnly = false)
        }
    }

    @Test
    fun freeTierOnly_roundTrips_andDeselectsHiddenPaidModel() = runTest {
        val r = repo()
        assertTrue(r.settings.first().freeTierProviders.isEmpty())

        // A free-tier verdict while a paid-only model is selected must also fix the selection —
        // the picker hides that model, so it can't stay active.
        r.setProvider(SttProvider.GEMINI)
        r.setSttModel("gemini-3.1-pro-preview")
        r.setFreeTierOnly(SttProvider.GEMINI, freeOnly = true)
        val s = r.settings.first()
        assertTrue(SttProvider.GEMINI.id in s.freeTierProviders)
        assertEquals(SttProvider.GEMINI.defaultModel, s.sttModel)

        // A later paid verdict (e.g. the user upgraded and re-saved the key) clears the flag.
        r.setFreeTierOnly(SttProvider.GEMINI, freeOnly = false)
        assertTrue(r.settings.first().freeTierProviders.isEmpty())
    }

    @Test
    fun defaults_areGroqAndSensible() = runTest {
        val s = repo().settings.first()
        assertEquals(SttProvider.GROQ, s.provider)
        assertEquals(SttProvider.GROQ.defaultModel, s.sttModel)
        // Default is English so mixed-language (Hinglish) dictation comes out in Roman script.
        assertEquals("en", s.languageHint)
        assertFalse(s.privateMode)
        // M10: LLM cleanup is opt-in — default OFF (extra latency + a second BYOK call).
        assertFalse(s.cleanupEnabled)
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

    @Test
    fun quillSettings_dataClassDefaults_matchSpec() {
        // Leak-immune: assert the documented defaults on the data class itself (spec §4.1 / prd / M9).
        val d = QuillSettings()
        assertEquals(SttProvider.DEFAULT, d.provider)
        assertEquals("en", d.languageHint)
        // M10: cleanup is opt-in, so the documented default is OFF (asserted, not incidental).
        assertFalse(d.cleanupEnabled)
        assertFalse(d.privateMode)
        assertEquals(300, d.sessionCapSeconds)
        assertFalse(d.onboardingComplete)
        // M9: history ON by default, kept 1 hour.
        assertTrue(d.historyEnabled)
        assertEquals(60, d.historyRetentionMinutes)
        assertEquals(QuillSettings.RETENTION_DEFAULT_MINUTES, d.historyRetentionMinutes)
        assertEquals(QuillSettings.BUBBLE_SIZE_MEDIUM, d.bubbleSize)
        assertEquals(QuillSettings.APP_THEME_SYSTEM, d.appTheme)
    }

    @Test
    fun history_enabledAndRetention_roundTrip() = runTest {
        val r = repo()
        r.setHistoryEnabled(false)
        r.setHistoryRetentionMinutes(QuillSettings.RETENTION_FOREVER)
        var s = r.settings.first()
        assertFalse(s.historyEnabled)
        assertEquals(QuillSettings.RETENTION_FOREVER, s.historyRetentionMinutes)

        r.setHistoryEnabled(true)
        r.setHistoryRetentionMinutes(1_440)
        s = r.settings.first()
        assertTrue(s.historyEnabled)
        assertEquals(1_440, s.historyRetentionMinutes)
    }

    @Test
    fun bubbleSizeAndTheme_defaultsAndRoundTrip() = runTest {
        val r = repo()
        val d = r.settings.first()
        assertEquals(QuillSettings.BUBBLE_SIZE_MEDIUM, d.bubbleSize)
        assertEquals(QuillSettings.APP_THEME_SYSTEM, d.appTheme)

        r.setBubbleSize(QuillSettings.BUBBLE_SIZE_LARGE)
        r.setAppTheme(QuillSettings.APP_THEME_DARK)
        val s = r.settings.first()
        assertEquals(QuillSettings.BUBBLE_SIZE_LARGE, s.bubbleSize)
        assertEquals(QuillSettings.APP_THEME_DARK, s.appTheme)
    }

    @Test
    fun bubbleVisible_defaultsToShown() {
        // The unified visibility state defaults to shown (the bubble appears once the service starts).
        assertTrue(QuillSettings().bubbleVisible)
    }

    @Test
    fun bubbleVisible_roundTrips_throughFlowAndSynchronousMirror() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val r = SettingsRepository(app)
        // The single writer updates both the observable Flow (what Home + the service collect) and the
        // synchronous mirror (what addBubble reads inline) — they must never diverge.
        r.setBubbleVisible(false)
        assertFalse("Flow reflects hidden", r.settings.first().bubbleVisible)
        assertFalse("mirror is in lockstep with the Flow", BubbleVisibilityStore(app).visible)

        r.setBubbleVisible(true)
        assertTrue("Flow reflects shown", r.settings.first().bubbleVisible)
        assertTrue("mirror is in lockstep with the Flow", BubbleVisibilityStore(app).visible)
    }

    @Test
    fun bubbleVisible_andBubbleActive_areIndependent() = runTest {
        // Hiding (toggle-off / drag-dismiss) must not clear "service desired": the FGS stays alive for
        // summon, and M11 recovery still restores it (then re-hides). Distinct flags, distinct meaning.
        val r = repo()
        r.setBubbleActive(true)
        r.setBubbleVisible(false)
        var s = r.settings.first()
        assertTrue("hiding the bubble keeps the service desired", s.bubbleActive)
        assertFalse(s.bubbleVisible)

        // And the full-stop path clears the service-desired flag.
        r.setBubbleActive(false)
        s = r.settings.first()
        assertFalse(s.bubbleActive)
    }

    @Test
    fun clear_resetsVisibilityMirrorToShown() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val r = SettingsRepository(app)
        r.setBubbleVisible(false)
        r.clear()
        assertTrue("a full wipe restores the mirror to its default", BubbleVisibilityStore(app).visible)
        assertTrue(r.settings.first().bubbleVisible)
    }

    @Test
    fun setProvider_switchesProviderAndResetsModelToProviderDefault() = runTest {
        val r = repo()
        // Pick a non-default model first, then switching provider must reset the model.
        r.setSttModel("custom-model")
        r.setProvider(SttProvider.OPENAI)
        val s = r.settings.first()
        assertEquals(SttProvider.OPENAI, s.provider)
        assertEquals(SttProvider.OPENAI.defaultModel, s.sttModel)
    }

    @Test
    fun customEndpoint_roundTrips() = runTest {
        val r = repo()
        r.setCustomBaseUrl("https://api.example.com/v1")
        r.setCustomModel("my-whisper")
        val s = r.settings.first()
        assertEquals("https://api.example.com/v1", s.customBaseUrl)
        assertEquals("my-whisper", s.customModel)
    }

    @Test
    fun switchingToCustom_usesTheSavedCustomModel_andItSurvivesRoundTrips() = runTest {
        val r = repo()
        r.setCustomModel("my-whisper")
        r.setProvider(SttProvider.CUSTOM)
        var s = r.settings.first()
        assertEquals(SttProvider.CUSTOM, s.provider)
        assertEquals("the active model is the saved custom model, not a blank", "my-whisper", s.sttModel)

        // Leaving CUSTOM resets the active model to the new provider's default…
        r.setProvider(SttProvider.GROQ)
        s = r.settings.first()
        assertEquals(SttProvider.GROQ.defaultModel, s.sttModel)

        // …but the custom model isn't lost: coming back restores it.
        r.setProvider(SttProvider.CUSTOM)
        s = r.settings.first()
        assertEquals("my-whisper", s.sttModel)
    }

    @Test
    fun setCustomModel_whileOnCustom_alsoUpdatesTheActiveModel() = runTest {
        val r = repo()
        r.setProvider(SttProvider.CUSTOM)
        r.setCustomModel("model-v2")
        val s = r.settings.first()
        assertEquals("model-v2", s.customModel)
        assertEquals("model-v2", s.sttModel)
    }

    @Test
    fun clear_resetsEverythingToDefaultsInclOnboarding() = runTest {
        val r = repo()
        r.setPrivateMode(true)
        r.setOnboardingComplete(true)
        r.setHistoryRetentionMinutes(QuillSettings.RETENTION_FOREVER)
        r.clear()
        val s = r.settings.first()
        assertFalse(s.privateMode)
        assertFalse("full wipe resets onboarding", s.onboardingComplete)
        assertEquals(60, s.historyRetentionMinutes)
        assertEquals(SttProvider.GROQ, s.provider)
    }

    @Test
    fun resetToDefaults_resetsPrefsButKeepsOnboarding() = runTest {
        val r = repo()
        r.setOnboardingComplete(true)
        r.setPrivateMode(true)
        r.setHistoryRetentionMinutes(QuillSettings.RETENTION_FOREVER)
        r.resetToDefaults()
        val s = r.settings.first()
        assertTrue("onboarding is preserved across a settings reset", s.onboardingComplete)
        assertFalse(s.privateMode)
        assertEquals(60, s.historyRetentionMinutes)
    }
}
