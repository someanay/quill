package com.tihor.quill.copy.ota

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches and accepts OTA copy bundles, **off the critical path** (M13a, spec §2.3).
 *
 * Lifecycle: called once unconditionally on app start (the OTA fetch is part of how the app works, not
 * a user setting). It runs on [Dispatchers.IO] and **never** blocks app start or the bubble. Offline /
 * fetch failure / a rejected bundle is silent — no user-facing error — and leaves the resolved copy at
 * cached-or-baked-in.
 *
 * Cheap-when-unchanged: the request sends `If-None-Match: <last ETag>`, so an unchanged bundle returns
 * `304 Not Modified` and downloads nothing. A `200` body is run through the full [CopyBundleVerifier]
 * gauntlet, then the **acceptance policy** here:
 *
 * - **monotonic revision** — accept only if `revision` strictly exceeds the stored applied revision
 *   (rollback guard, spec §2.3).
 *
 * On acceptance the validated raw bytes are cached atomically and the applied revision/ETag are stored.
 * The caller applies the cache **on foreground**: `MainActivity.onStart` calls
 * `CopyRepository.applyCachedOverride` on every resume and re-applies immediately when a fetch returns
 * [Outcome.Accepted], so an accepted bundle shows **live in the same session** — no cold restart needed.
 * Applying is a cheap idempotent re-verify of the cache (falls back to baked-in on any problem), so it
 * stays within the spec §2.3 offline/airplane guarantee.
 */
class CopyUpdater(
    private val verifier: CopyBundleVerifier,
    private val cache: CopyBundleCache,
    private val store: CopyUpdateStore,
    private val baseUrl: String = BASE_URL,
    private val httpClient: OkHttpClient = defaultClient(),
) {

    /** Fetch + validate + (maybe) cache a newer bundle. Safe to call on any dispatcher; never throws. */
    suspend fun checkForUpdate(): Outcome = withContext(Dispatchers.IO) {
        try {
            // Self-heal: if launch re-verification discarded a corrupt/tampered cache, the stored applied
            // revision/ETag are stale. Reset them so the current server revision re-downloads (otherwise
            // a 304 / non-newer revision could leave us stuck on baked-in).
            if (cache.read() == null && store.appliedRevision() > 0) store.reset()

            val applied = store.appliedRevision()
            val etag = store.etag()
            val url = "${baseUrl.trimEnd('/')}/strings.signed.json"
            val request = Request.Builder().url(url).get().apply {
                if (!etag.isNullOrBlank()) header("If-None-Match", etag)
            }.build()

            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> return@withContext Outcome.NotModified
                    !response.isSuccessful -> return@withContext Outcome.Failed("http ${response.code}")
                    else -> {
                        val bytes = response.body?.bytes()
                            ?: return@withContext Outcome.Failed("empty body")
                        val newEtag = response.header("ETag")
                        return@withContext accept(bytes, newEtag, applied)
                    }
                }
            }
        } catch (e: Exception) {
            // Offline, timeout, DNS, TLS — all silent. Keep cached/baked-in.
            Log.d(TAG, "copy update skipped: ${e.message}")
            Outcome.Failed(e.message ?: "io")
        }
    }

    private suspend fun accept(bytes: ByteArray, etag: String?, appliedRevision: Int): Outcome {
        when (val result = verifier.verify(bytes)) {
            is CopyBundleVerifier.Result.Rejected -> {
                Log.d(TAG, "copy bundle rejected: ${result.reason}")
                return Outcome.Rejected(result.reason)
            }
            is CopyBundleVerifier.Result.Valid -> {
                // Rollback guard: only a strictly newer revision is accepted.
                if (result.revision <= appliedRevision) {
                    return Outcome.Rejected("rollback: revision ${result.revision} <= applied $appliedRevision")
                }
                if (!cache.write(bytes)) return Outcome.Failed("cache write failed")
                store.setApplied(result.revision, etag)
                Log.d(TAG, "copy bundle accepted: revision ${result.revision} (applied on foreground)")
                return Outcome.Accepted(result.revision)
            }
        }
    }

    sealed interface Outcome {
        data class Accepted(val revision: Int) : Outcome
        object NotModified : Outcome
        data class Rejected(val reason: String) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    companion object {
        private const val TAG = "QuillCopyUpdate"

        /**
         * Static host for the signed copy bundle (spec §2.3). The bundle is a single flat file served at
         * `…/strings.signed.json` over HTTPS from the Supabase storage bucket (v1 ships `en` only).
         */
        const val BASE_URL =
            "https://tznxppfwlgdhbvsplewl.supabase.co/storage/v1/object/public/copy-bundles"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
