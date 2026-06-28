package com.tihor.quill.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.customWordsDataStore by preferencesDataStore(name = "quill_custom_words")

/**
 * Persists the user's [CustomWord] list (settings.words). Stored as a single JSON blob in DataStore
 * so order is preserved and entries are atomic. Reads expose a [Flow] for the UI; the overlay reads
 * a snapshot at transcription time to post-process the transcript.
 */
class CustomWordsStore(private val context: Context) {

    val words: Flow<List<CustomWord>> =
        context.customWordsDataStore.data.map { decode(it[KEY]) }

    suspend fun add(heard: String, replacement: String) {
        val h = heard.trim()
        val r = replacement.trim()
        if (h.isEmpty() || r.isEmpty()) return
        context.customWordsDataStore.edit { prefs ->
            val current = decode(prefs[KEY]).toMutableList()
            // Replace an existing entry with the same "heard" form (case-insensitive) rather than dup.
            current.removeAll { it.heard.equals(h, ignoreCase = true) }
            current.add(CustomWord(h, r))
            prefs[KEY] = JSON.encodeToString(current)
        }
    }

    suspend fun remove(word: CustomWord) {
        context.customWordsDataStore.edit { prefs ->
            val current = decode(prefs[KEY]).toMutableList()
            current.removeAll { it.heard.equals(word.heard, ignoreCase = true) && it.replacement == word.replacement }
            prefs[KEY] = JSON.encodeToString(current)
        }
    }

    /** Remove every saved word (for "clear all data"). */
    suspend fun clearAll() {
        context.customWordsDataStore.edit { it.clear() }
    }

    private fun decode(raw: String?): List<CustomWord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { JSON.decodeFromString<List<CustomWord>>(raw) }
            .onFailure { Log.w(TAG, "Failed to decode custom words: ${it.message}") }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val TAG = "Quill.Words"
        val KEY = stringPreferencesKey("entries")
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
