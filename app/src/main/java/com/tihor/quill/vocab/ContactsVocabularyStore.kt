package com.tihor.quill.vocab

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.contactsVocabDataStore by preferencesDataStore(name = "quill_contacts_vocab")

/**
 * Persists the contacts-imported candidate vocabulary (Phase 1) as a single JSON list in DataStore —
 * same storage pattern as [com.tihor.quill.settings.CustomWordsStore], but a deliberately **separate
 * store**: imported contact names and user-authored word substitutions are different data and must
 * stay distinguishable (clear-one must never clear the other; only "Clear all data & keys" wipes
 * both, via [com.tihor.quill.settings.AppDataReset]).
 *
 * Nothing in this store is ever sent anywhere in Phase 1 — it is read only by the settings UI. A
 * later phase that feeds it into STT/cleanup needs its own explicit disclosure.
 */
class ContactsVocabularyStore(private val context: Context) {

    val terms: Flow<List<String>> =
        context.contactsVocabDataStore.data.map { decode(it[KEY]) }

    /** Replace the whole vocabulary with a fresh import (re-import replaces, never appends). */
    suspend fun replaceAll(newTerms: List<String>) {
        context.contactsVocabDataStore.edit { prefs ->
            prefs[KEY] = JSON.encodeToString(newTerms)
        }
    }

    /** Remove every imported term ("clear imported vocabulary" and "Clear all data & keys"). */
    suspend fun clear() {
        context.contactsVocabDataStore.edit { it.clear() }
    }

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { JSON.decodeFromString<List<String>>(raw) }
            .onFailure { Log.w(TAG, "Failed to decode contacts vocabulary: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "Quill.ContactsVocab"
        val KEY = stringPreferencesKey("terms")
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
