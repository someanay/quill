package com.tihor.quill.settings

import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.style.WritingStyle
import com.tihor.quill.style.WritingStyleStore
import com.tihor.quill.vocab.ContactsVocabularyStore
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

/**
 * "Clear all data & keys" must wipe ALL six on-device stores (Pass 2 — data-deletion correctness):
 * API keys, settings (back to defaults), dictation history, the custom-word vocabulary, the
 * per-app writing-style overrides, and the contacts-imported vocabulary (Phase 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDataResetTest {

    /** In-memory [SecretStore]; the real Keystore-backed store needs a device. */
    private class FakeSecretStore : SecretStore {
        private val map = mutableMapOf<String, String>()
        override fun put(alias: String, secret: String) { map[alias] = secret }
        override fun get(alias: String): String? = map[alias]
        override fun clear(alias: String) { map.remove(alias) }
        override fun contains(alias: String): Boolean = map.containsKey(alias)
    }

    private val app get() = RuntimeEnvironment.getApplication()

    // The DataStore/SQLite singletons persist across methods in a Robolectric class — start clean.
    @Before
    fun reset() {
        runBlocking {
            SettingsRepository(app).clear()
            HistoryRepository(app).clearAll()
            CustomWordsStore(app).clearAll()
            WritingStyleStore(app).clearAll()
            ContactsVocabularyStore(app).clear()
        }
    }

    @Test
    fun clearAll_wipesKeysSettingsHistoryWordsAndStyleOverrides() = runTest {
        val secrets = FakeSecretStore().apply {
            put(SecretStore.ALIAS_STT_KEY, "sk-stt")
            put(SecretStore.ALIAS_LLM_KEY, "sk-llm")
        }
        val settings = SettingsRepository(app)
        val keys = com.tihor.quill.secrets.SttKeyStore(secrets)
        val drafts = com.tihor.quill.secrets.KeySetupDrafts(secrets)
        SttProvider.entries.forEach {
            keys.add(it, "saved-${it.id}")
            keys.add(it, "second-${it.id}")
            drafts.write(it, "unfinished-${it.id}")
        }
        val history = HistoryRepository(app)
        val words = CustomWordsStore(app)
        val styles = WritingStyleStore(app)
        val vocab = ContactsVocabularyStore(app)

        // Seed every store with non-default / non-empty data.
        settings.setPrivateMode(true)
        settings.setHistoryRetentionMinutes(QuillSettings.RETENTION_FOREVER)
        settings.setOnboardingComplete(true)
        history.maybeSave(true, false, QuillSettings.RETENTION_FOREVER, "hello", "com.x", "X", 1_000)
        words.add("clod", "Claude")
        styles.setOverride("com.example.app", WritingStyle.CASUAL)
        vocab.replaceAll(listOf("Rohit Tikmany", "Rohit", "Tikmany"))

        // Sanity: populated before the wipe.
        assertTrue(keys.has(SttProvider.GROQ)) // Reading the ring migrates the legacy alias.
        assertEquals(1, history.all().size)
        assertEquals(1, words.words.first().size)
        assertEquals(1, styles.overrides.first().size)
        assertEquals(3, vocab.terms.first().size)

        AppDataReset.clearAll(secrets, settings, history, words, styles, vocab)

        // Keys gone.
        assertFalse(secrets.contains(SecretStore.ALIAS_STT_KEY))
        assertFalse(secrets.contains(SecretStore.ALIAS_LLM_KEY))
        SttProvider.entries.forEach {
            assertTrue(keys.list(it).isEmpty())
            assertEquals("", drafts.read(it))
        }
        // Settings back to defaults (full wipe, incl. onboarding).
        val s = settings.settings.first()
        assertFalse(s.privateMode)
        assertTrue(s.historyEnabled)
        assertEquals(60, s.historyRetentionMinutes)
        assertEquals(SttProvider.GROQ, s.provider)
        assertFalse("full wipe resets onboarding", s.onboardingComplete)
        // History empty.
        assertTrue(history.all().isEmpty())
        // Custom words empty.
        assertTrue(words.words.first().isEmpty())
        // Per-app style overrides empty — every app falls back to its curated/global default again.
        assertTrue(styles.overrides.first().isEmpty())
        // Contacts-imported vocabulary empty.
        assertTrue(vocab.terms.first().isEmpty())
    }
}
