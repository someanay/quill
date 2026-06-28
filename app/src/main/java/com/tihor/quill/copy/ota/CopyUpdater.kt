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
 * Lifecycle: called once on app start (gated by the "Check for updated text" setting). It runs on
 * [Dispatchers.IO] and **never** blocks app start or the bubble. Offline / fetch failure / a rejected
 * bundle is silent — no user-facing error — and leaves the resolved copy at cached-or-baked-in.
 *
 * Cheap-when-unchanged: the request sends `If-None-Match: <last ETag>`, so an unchanged bundle returns
 * `304 Not Modified` and downloads nothing. A `200` body is run through the full [CopyBundleVerifier]
 * gauntlet, then the **acceptance policy** here:
 *
 * - **monotonic revision** — accept only if `revision` strictly exceeds the stored applied revision
 *   (rollback guard, spec §2.3).
 *
 * On acceptance the validated raw bytes are cached atomically and the applied revision/ETag are stored.
 * We deliberately **do not** live-apply: the new copy takes effect on the **next launch** (when
 * `CopyRepository.applyCachedOverride` re-verifies and applies the cache), so nothing flickers under a
 * screen the user is actively reading (spec §2.3 offline/airplane guarantee).
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
            val url = "${baseUrl.trimEnd('/')}/copy/$LOCALE.json"
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
                Log.d(TAG, "copy bundle accepted: revision ${result.revision} (applies next launch)")
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
        private const val LOCALE = "en" // v1 ships en only; the {locale} path keeps i18n a drop-in.

        /**
         * Static host for the signed copy bundle (spec §2.3). **Placeholder** — point this at the real
         * CDN before shipping OTA copy. Served as `…/copy/en.json` over HTTPS.
         */
        const val BASE_URL = "https://copy.tihor.com" // TODO(M13b): set to the production CDN origin.

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
