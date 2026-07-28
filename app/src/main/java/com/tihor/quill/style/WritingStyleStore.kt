package com.tihor.quill.style

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.writingStyleDataStore by preferencesDataStore(name = "quill_writing_style")

/**
 * Persists **only the user's explicit per-app style overrides** (scope doc §5) as a single JSON blob
 * (package name → [WritingStyle.id]), the same storage shape as `CustomWordsStore`.
 *
 * Curated defaults are deliberately **not** stored. Snapshotting them would freeze every user on the
 * guess that shipped with the version they installed, and would make "reset to default" ambiguous
 * (reset to *which* default?). Keeping only overrides means this map stays tiny — usually empty —
 * [clearOverride] is a true reset, and improved curation reaches existing users on update.
 *
 * Style ids (not enum ordinals) are the persisted form, so reordering the enum can't silently
 * repoint a user's choices; an unreadable blob or an unknown id degrades to "no override" rather than
 * throwing, keeping the fail-soft contract intact all the way down to storage.
 */
class WritingStyleStore(private val context: Context) {

    /** Explicit per-app overrides. Absent packages are not "unstyled" — they resolve from the other tiers. */
    val overrides: Flow<Map<String, WritingStyle>> =
        context.writingStyleDataStore.data.map { decode(it[KEY]) }

    /** Assign [style] to [packageName], replacing any previous override for it. */
    suspend fun setOverride(packageName: String, style: WritingStyle) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        context.writingStyleDataStore.edit { prefs ->
            prefs[KEY] = JSON.encodeToString(decodeRaw(prefs[KEY]) + (pkg to style.id))
        }
    }

    /** Drop [packageName]'s override — it falls back to its curated default, else the global default. */
    suspend fun clearOverride(packageName: String) {
        context.writingStyleDataStore.edit { prefs ->
            prefs[KEY] = JSON.encodeToString(decodeRaw(prefs[KEY]) - packageName.trim())
        }
    }

    /** Remove every override (for "clear all data" / "reset settings"). */
    suspend fun clearAll() {
        context.writingStyleDataStore.edit { it.clear() }
    }

    private fun decode(raw: String?): Map<String, WritingStyle> =
        decodeRaw(raw).mapValues { (_, id) -> WritingStyle.fromId(id) }

    private fun decodeRaw(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { JSON.decodeFromString<Map<String, String>>(raw) }
            .onFailure { Log.w(TAG, "Failed to decode writing-style overrides: ${it.message}") }
            .getOrDefault(emptyMap())
    }

    private companion object {
        const val TAG = "Quill.Style"
        val KEY = stringPreferencesKey("overrides")
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
