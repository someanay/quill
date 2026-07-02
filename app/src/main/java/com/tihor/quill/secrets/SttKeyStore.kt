package com.tihor.quill.secrets

import com.tihor.quill.settings.SttProvider

/**
 * Per-provider STT keys (decision 3): each provider gets its own [SecretStore] alias
 * (`stt_key_<provider>`), so switching provider swaps which key is used and a key entered for
 * provider A is **never** sent to provider B.
 *
 * **Legacy migration (exactly once):** M7–M13 stored the single Groq-era key under
 * [SecretStore.ALIAS_STT_KEY]. On the first access after this update, that key moves to the Groq
 * alias and the legacy entry is deleted; if a Groq-alias key already exists the legacy value is
 * discarded (never overwrites). Migration is idempotent — once the legacy entry is gone it can
 * never run again. Keys are never logged (spec §6).
 */
class SttKeyStore(private val store: SecretStore) {

    fun get(provider: SttProvider): String? {
        migrateLegacyIfNeeded()
        return store.get(alias(provider))
    }

    fun put(provider: SttProvider, key: String) {
        migrateLegacyIfNeeded()
        store.put(alias(provider), key)
    }

    fun clear(provider: SttProvider) {
        migrateLegacyIfNeeded()
        store.clear(alias(provider))
    }

    fun has(provider: SttProvider): Boolean {
        migrateLegacyIfNeeded()
        return store.contains(alias(provider))
    }

    private fun migrateLegacyIfNeeded() {
        if (!store.contains(SecretStore.ALIAS_STT_KEY)) return
        val legacy = store.get(SecretStore.ALIAS_STT_KEY)
        if (legacy != null && !store.contains(alias(SttProvider.GROQ))) {
            store.put(alias(SttProvider.GROQ), legacy)
        }
        store.clear(SecretStore.ALIAS_STT_KEY)
    }

    companion object {
        fun alias(provider: SttProvider): String = "stt_key_${provider.id}"
    }
}
