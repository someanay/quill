package com.tihor.quill.style

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The persisted half of scope doc §5: **only explicit overrides are stored**. Curated defaults must
 * never be snapshotted in here — that is what keeps "reset to default" a true reset and lets improved
 * curation reach existing users on update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WritingStyleStoreTest {

    private fun store() = WritingStyleStore(RuntimeEnvironment.getApplication())

    // Robolectric caches the DataStore singleton across methods in a class — start each test empty.
    @Before
    fun reset() = runBlocking { store().clearAll() }

    @Test
    fun setOverride_persistsAndRoundTrips() = runTest {
        val s = store()
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        assertEquals(mapOf("com.Slack" to WritingStyle.CASUAL), s.overrides.first())
    }

    @Test
    fun setOverride_replacesAPreviousChoiceForTheSameApp() = runTest {
        val s = store()
        s.setOverride("com.whatsapp", WritingStyle.FORMAL)
        s.setOverride("com.whatsapp", WritingStyle.CASUAL)
        assertEquals(1, s.overrides.first().size)
        assertEquals(WritingStyle.CASUAL, s.overrides.first()["com.whatsapp"])
    }

    @Test
    fun multipleApps_areIndependent() = runTest {
        val s = store()
        s.setOverride("com.whatsapp", WritingStyle.FORMAL)
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        val o = s.overrides.first()
        assertEquals(WritingStyle.FORMAL, o["com.whatsapp"])
        assertEquals(WritingStyle.CASUAL, o["com.Slack"])
    }

    @Test
    fun clearOverride_removesOnlyThatApp() = runTest {
        val s = store()
        s.setOverride("com.whatsapp", WritingStyle.FORMAL)
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        s.clearOverride("com.whatsapp")
        val o = s.overrides.first()
        assertNull(o["com.whatsapp"])
        assertEquals(WritingStyle.CASUAL, o["com.Slack"])
    }

    @Test
    fun clearOverride_thenResolve_landsOnTheCuratedDefault() = runTest {
        // The end-to-end reset contract (§10): a Slack override, then reset, must resolve to Slack's
        // curated FORMAL — not to the override that was just deleted, and not to the global default.
        val s = store()
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        assertEquals(
            WritingStyle.CASUAL,
            WritingStyleResolver.resolve("com.Slack", s.overrides.first(), WritingStyle.NEUTRAL),
        )

        s.clearOverride("com.Slack")
        assertEquals(
            WritingStyle.FORMAL,
            WritingStyleResolver.resolve("com.Slack", s.overrides.first(), WritingStyle.NEUTRAL),
        )
    }

    @Test
    fun curatedDefaults_areNeverWrittenToStorage() = runTest {
        // Reading and resolving a curated app must leave the store empty — nothing is snapshotted.
        val s = store()
        WritingStyleResolver.resolve("com.Slack", s.overrides.first(), WritingStyle.NEUTRAL)
        WritingStyleResolver.resolve("com.instagram.android", s.overrides.first(), WritingStyle.NEUTRAL)
        assertTrue("only explicit overrides may be persisted", s.overrides.first().isEmpty())

        // And an override for one app doesn't drag its neighbours' curated values into storage either.
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        assertEquals(setOf("com.Slack"), s.overrides.first().keys)
    }

    @Test
    fun clearOverride_onAnAppWithNoOverride_isANoOp() = runTest {
        val s = store()
        s.setOverride("com.Slack", WritingStyle.CASUAL)
        s.clearOverride("com.never.set")
        assertEquals(setOf("com.Slack"), s.overrides.first().keys)
    }

    @Test
    fun blankPackage_isRejected() = runTest {
        val s = store()
        s.setOverride("   ", WritingStyle.FORMAL)
        s.setOverride("", WritingStyle.FORMAL)
        assertTrue(s.overrides.first().isEmpty())
    }

    @Test
    fun clearAll_emptiesTheStore() = runTest {
        val s = store()
        s.setOverride("com.a", WritingStyle.FORMAL)
        s.setOverride("com.b", WritingStyle.CASUAL)
        s.clearAll()
        assertTrue(s.overrides.first().isEmpty())
    }

    @Test
    fun emptyStore_yieldsNoOverrides_notAnError() = runTest {
        assertTrue(store().overrides.first().isEmpty())
    }

    @Test
    fun overridesSurviveANewStoreInstance() = runTest {
        store().setOverride("com.discord", WritingStyle.FORMAL)
        // A fresh instance over the same DataStore — this is the cold-start read the overlay does.
        assertEquals(WritingStyle.FORMAL, store().overrides.first()["com.discord"])
    }

    @Test
    fun globalDefault_shipsNeutral_andPersistsWhenChanged() = runTest {
        val repo = com.tihor.quill.settings.SettingsRepository(RuntimeEnvironment.getApplication())
        repo.clear()
        assertEquals(WritingStyle.NEUTRAL, repo.settings.first().writingStyleDefault)
        repo.setWritingStyleDefault(WritingStyle.FORMAL)
        assertEquals(WritingStyle.FORMAL, repo.settings.first().writingStyleDefault)
        // A wipe puts it back to the shipped default rather than leaving a stale choice behind.
        repo.clear()
        assertEquals(WritingStyle.NEUTRAL, repo.settings.first().writingStyleDefault)
    }

    @Test
    fun styleIsInertUntilOptedIn() = runTest {
        // Fresh install: no overrides, Neutral global default → an uncurated app resolves Neutral,
        // which composes to the unmodified M10 prompt. The feature ships doing nothing.
        val s = store()
        val resolved = WritingStyleResolver.resolve("com.some.app", s.overrides.first(), WritingStyle.DEFAULT)
        assertEquals(WritingStyle.NEUTRAL, resolved)
        assertFalse(
            "Neutral must not append a directive",
            WritingStylePrompt.compose("BASE", resolved) != "BASE",
        )
    }
}
