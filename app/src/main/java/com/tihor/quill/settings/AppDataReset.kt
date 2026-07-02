package com.tihor.quill.settings

import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.secrets.SttKeyStore

/**
 * Full data-and-keys wipe for the Advanced "Clear all data & keys" action. Clears every on-device
 * store the app owns: the Keystore-backed API keys, all settings (back to defaults), dictation
 * history, and the custom-word vocabulary.
 *
 * Pure orchestration over the store interfaces so it is unit-testable with a fake [SecretStore] —
 * the real Keystore-backed store needs a device, but the orchestration (and the data stores) do not.
 */
object AppDataReset {
    suspend fun clearAll(
        secretStore: SecretStore,
        settings: SettingsRepository,
        history: HistoryRepository,
        words: CustomWordsStore,
    ) {
        SttProvider.entries.forEach { secretStore.clear(SttKeyStore.alias(it)) } // per-provider STT keys
        secretStore.clear(SecretStore.ALIAS_STT_KEY) // legacy single-slot key (pre-migration installs)
        secretStore.clear(SecretStore.ALIAS_LLM_KEY)
        settings.clear()
        history.clearAll()
        words.clearAll()
    }
}
