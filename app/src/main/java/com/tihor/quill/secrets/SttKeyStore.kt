package com.tihor.quill.secrets

import com.tihor.quill.settings.SttProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

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
    @Serializable data class SavedKey(val id: String, val key: String)
    @Serializable private data class KeyRing(val keys: List<SavedKey>, val activeId: String)

    private fun ring(provider: SttProvider): KeyRing {
        migrateLegacyIfNeeded()
        store.get("${alias(provider)}_ring")?.let { return Json.decodeFromString<KeyRing>(it) }
        val existing = store.get(alias(provider)) ?: return KeyRing(emptyList(), "")
        return KeyRing(listOf(SavedKey("primary", existing)), "primary")
    }

    fun list(provider: SttProvider): List<SavedKey> = synchronized(LOCK) { ring(provider).keys }
    fun activeId(provider: SttProvider): String = synchronized(LOCK) { ring(provider).activeId }

    private fun save(provider: SttProvider, ring: KeyRing) {
        store.put("${alias(provider)}_ring", Json.encodeToString(KeyRing.serializer(), ring))
        val active = ring.keys.firstOrNull { it.id == ring.activeId }
        if (active == null) store.clear(alias(provider)) else store.put(alias(provider), active.key)
    }

    fun add(provider: SttProvider, key: String) = synchronized(LOCK) {
        val ring = ring(provider)
        val entry = ring.keys.firstOrNull { it.key == key.trim() } ?: SavedKey(UUID.randomUUID().toString(), key.trim())
        require(entry.key.isNotBlank())
        save(provider, KeyRing(ring.keys.filterNot { it.id == entry.id } + entry, entry.id))
    }

    fun select(provider: SttProvider, id: String) = synchronized(LOCK) {
        val ring = ring(provider)
        require(ring.keys.any { it.id == id })
        save(provider, ring.copy(activeId = id))
    }

    fun remove(provider: SttProvider, id: String) = synchronized(LOCK) {
        val ring = ring(provider)
        val remaining = ring.keys.filterNot { it.id == id }
        save(provider, KeyRing(remaining, if (ring.activeId == id) remaining.firstOrNull()?.id.orEmpty() else ring.activeId))
    }

    fun get(provider: SttProvider): String? {
        migrateLegacyIfNeeded()
        return store.get(alias(provider))
    }

    fun put(provider: SttProvider, key: String) {
        synchronized(LOCK) {
            val ring = ring(provider)
            val id = ring.activeId.ifBlank { "primary" }
            save(provider, KeyRing(ring.keys.filterNot { it.id == id } + SavedKey(id, key), id))
        }
    }

    fun clear(provider: SttProvider) {
        migrateLegacyIfNeeded()
        store.clear(alias(provider))
        store.clear("${alias(provider)}_ring")
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
        private val LOCK = Any()
        fun alias(provider: SttProvider): String = "stt_key_${provider.id}"
    }
}
