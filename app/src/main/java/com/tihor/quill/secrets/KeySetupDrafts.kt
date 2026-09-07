package com.tihor.quill.secrets

import com.tihor.quill.settings.SttProvider

/** Unfinished keys use the same encrypted storage as saved keys, never Android saved state. */
class KeySetupDrafts(private val store: SecretStore) {
    fun read(provider: SttProvider): String = store.get(alias(provider)).orEmpty()
    fun write(provider: SttProvider, value: String) {
        if (value.isEmpty()) clear(provider) else store.put(alias(provider), value)
    }
    fun clear(provider: SttProvider) = store.clear(alias(provider))
    private fun alias(provider: SttProvider) = "key_setup_draft_${provider.id}"
}
