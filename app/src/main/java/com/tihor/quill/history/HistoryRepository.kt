package com.tihor.quill.history

import android.content.Context
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
     * Prunes to [CAP] newest rows after insert. Blank text is never saved.
     */
    suspend fun maybeSave(
        historyEnabled: Boolean,
        privateMode: Boolean,
        text: String,
        packageName: String?,
        appLabel: String?,
        timestamp: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!historyEnabled || privateMode || text.isBlank()) return@withContext false
        store.insert(text, timestamp, packageName, appLabel)
        store.pruneTo(CAP)
        true
    }

    suspend fun all(): List<HistoryEntry> = withContext(Dispatchers.IO) { store.queryAll() }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { store.delete(id) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { store.clearAll() }

    companion object {
        /** Retention cap; oldest are pruned on insert (spec §4 / M9). */
        const val CAP = 1000
    }
}
