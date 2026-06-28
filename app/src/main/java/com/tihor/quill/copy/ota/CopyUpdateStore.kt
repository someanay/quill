package com.tihor.quill.copy.ota

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.copyUpdateDataStore by preferencesDataStore(name = "copy_update")

/**
 * Internal bookkeeping for the OTA copy pipeline (M13a, spec §4.4) — **not** user-facing settings, so
 * it lives in its own DataStore rather than [com.tihor.quill.settings.QuillSettings]:
 *
 * - **applied revision** — the monotonic rollback guard (spec §2.3): a fetched bundle is accepted only
 *   if its `revision` strictly exceeds this.
 * - **ETag** — the last bundle's HTTP ETag, sent as `If-None-Match` so an unchanged bundle costs a cheap
 *   `304 Not Modified` and no download.
 *
 * The DataStore dir is already backup-excluded (`datastore/` in the backup rules).
 */
class CopyUpdateStore(private val context: Context) {

    suspend fun appliedRevision(): Int =
        context.copyUpdateDataStore.data.map { it[Keys.APPLIED_REVISION] ?: 0 }.first()

    suspend fun etag(): String? =
        context.copyUpdateDataStore.data.map { it[Keys.ETAG] }.first()

    suspend fun setApplied(revision: Int, etag: String?) {
        context.copyUpdateDataStore.edit {
            it[Keys.APPLIED_REVISION] = revision
            if (etag.isNullOrBlank()) it.remove(Keys.ETAG) else it[Keys.ETAG] = etag
        }
    }

    /** Reset to baked-in baseline (used when a cached bundle fails re-verification). */
    suspend fun reset() {
        context.copyUpdateDataStore.edit {
            it.remove(Keys.APPLIED_REVISION)
            it.remove(Keys.ETAG)
        }
    }

    private object Keys {
        val APPLIED_REVISION = intPreferencesKey("applied_revision")
        val ETAG = stringPreferencesKey("etag")
    }
}
