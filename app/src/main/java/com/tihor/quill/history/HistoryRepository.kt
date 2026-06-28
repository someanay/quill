package com.tihor.quill.history

import android.content.Context
import com.tihor.quill.settings.QuillSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dictation history (M9). The write gate lives here ([maybeSave]) so it is unit-testable without the
 * overlay service: nothing is written unless history is enabled and private mode is off. Toggling
 * history off simply stops new writes — it never deletes existing rows (the user clears explicitly).
 */
class HistoryRepository(context: Context) {

    private val store = HistoryStore(context.applicationContext)

    /**
     * Save a transcript on a successful transcription, gated by the flags. Returns true if written.
     * After insert, prunes entries past the [retentionMinutes] window (relative to [timestamp]) and
     * past the [CAP] backstop. Blank text is never saved.
     */
    suspend fun maybeSave(
        historyEnabled: Boolean,
        privateMode: Boolean,
        retentionMinutes: Int,
        text: String,
        packageName: String?,
        appLabel: String?,
        timestamp: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!historyEnabled || privateMode || text.isBlank()) return@withContext false
        store.insert(text, timestamp, packageName, appLabel)
        pruneLocked(retentionMinutes, timestamp)
        true
    }

    /**
     * Drop entries older than the [retentionMinutes] window (relative to [now]) and past the [CAP]
     * backstop. Call this before reading so the screen reflects the window even with no new writes.
     * [QuillSettings.RETENTION_FOREVER] skips the time window (only the count cap applies).
     */
    suspend fun prune(retentionMinutes: Int, now: Long) = withContext(Dispatchers.IO) {
        pruneLocked(retentionMinutes, now)
    }

    private fun pruneLocked(retentionMinutes: Int, now: Long) {
        if (retentionMinutes > QuillSettings.RETENTION_FOREVER) {
            store.pruneOlderThan(now - retentionMinutes * 60_000L)
        }
        store.pruneTo(CAP)
    }

    suspend fun all(): List<HistoryEntry> = withContext(Dispatchers.IO) { store.queryAll() }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { store.delete(id) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { store.clearAll() }

    companion object {
        /** Storage backstop; bounds the DB even when retention is set to forever (spec §4 / M9). */
        const val CAP = 1000
    }
}
