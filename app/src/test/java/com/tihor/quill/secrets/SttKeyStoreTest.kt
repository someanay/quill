package com.tihor.quill.secrets

import com.tihor.quill.settings.SttProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-provider STT keys (decision 3): each provider gets its own Keystore alias, so switching
 * provider can never send a key entered for provider A to provider B. The single legacy
 * `ALIAS_STT_KEY` slot (M7, Groq-only era) migrates to the Groq alias exactly once.
 */
class SttKeyStoreTest {

    private class FakeSecretStore : SecretStore {
        val map = mutableMapOf<String, String>()
        override fun put(alias: String, secret: String) { map[alias] = secret }
        override fun get(alias: String): String? = map[alias]
        override fun clear(alias: String) { map.remove(alias) }
        override fun contains(alias: String): Boolean = alias in map
    }

    @Test
    fun keys_areIsolatedPerProvider() {
        val store = SttKeyStore(FakeSecretStore())
        store.put(SttProvider.GROQ, "gsk_groq")
        store.put(SttProvider.OPENAI, "sk-openai")
        store.put(SttProvider.GEMINI, "AIza-gemini")

        assertEquals("gsk_groq", store.get(SttProvider.GROQ))
        assertEquals("sk-openai", store.get(SttProvider.OPENAI))
        assertEquals("AIza-gemini", store.get(SttProvider.GEMINI))
        assertNull(store.get(SttProvider.OPENROUTER))
        assertNull(store.get(SttProvider.CUSTOM))
    }

    @Test
    fun clear_removesOnlyThatProvidersKey() {
        val store = SttKeyStore(FakeSecretStore())
        store.put(SttProvider.GROQ, "gsk_groq")
        store.put(SttProvider.OPENROUTER, "sk-or")

        store.clear(SttProvider.OPENROUTER)

        assertNull(store.get(SttProvider.OPENROUTER))
        assertEquals("gsk_groq", store.get(SttProvider.GROQ))
    }

    @Test
    fun has_reflectsStoredKeys() {
        val store = SttKeyStore(FakeSecretStore())
        assertFalse(store.has(SttProvider.GROQ))
        store.put(SttProvider.GROQ, "gsk")
        assertTrue(store.has(SttProvider.GROQ))
    }

    @Test
    fun legacyKey_migratesToGroqAlias_once() {
        val backing = FakeSecretStore().apply { put(SecretStore.ALIAS_STT_KEY, "gsk_legacy") }
        val store = SttKeyStore(backing)

        assertEquals("gsk_legacy", store.get(SttProvider.GROQ))
        // Legacy slot deleted after the move; a later write can't resurrect it.
        assertFalse(backing.contains(SecretStore.ALIAS_STT_KEY))

        store.put(SttProvider.GROQ, "gsk_new")
        assertEquals("gsk_new", store.get(SttProvider.GROQ))
        assertFalse(backing.contains(SecretStore.ALIAS_STT_KEY))
    }

    @Test
    fun legacyKey_migratesOnAnyAccess_notJustGroqReads() {
        val backing = FakeSecretStore().apply { put(SecretStore.ALIAS_STT_KEY, "gsk_legacy") }
        val store = SttKeyStore(backing)

        store.has(SttProvider.OPENAI) // any access triggers the one-time migration

        assertFalse(backing.contains(SecretStore.ALIAS_STT_KEY))
        assertEquals("gsk_legacy", store.get(SttProvider.GROQ))
    }

    @Test
    fun legacyKey_neverOverwritesAnExistingGroqKey() {
        val backing = FakeSecretStore().apply {
            put(SecretStore.ALIAS_STT_KEY, "gsk_stale_legacy")
            put(SttKeyStore.alias(SttProvider.GROQ), "gsk_current")
        }
        val store = SttKeyStore(backing)

        assertEquals("gsk_current", store.get(SttProvider.GROQ))
        assertFalse(backing.contains(SecretStore.ALIAS_STT_KEY))
    }

    @Test
    fun legacyMigration_doesNotTouchOtherProviders() {
        val backing = FakeSecretStore().apply { put(SecretStore.ALIAS_STT_KEY, "gsk_legacy") }
        val store = SttKeyStore(backing)

        assertNull(store.get(SttProvider.OPENAI))
        assertNull(store.get(SttProvider.GEMINI))
        assertEquals("gsk_legacy", store.get(SttProvider.GROQ))
    }
}
