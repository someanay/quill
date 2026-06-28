package com.tihor.quill.copy

import android.content.Context
import com.tihor.quill.copy.ota.CopyBundleCache
import com.tihor.quill.copy.ota.CopyBundleVerifier
import com.tihor.quill.copy.ota.CopyUpdateStore
import com.tihor.quill.copy.ota.CopyUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Resolves UI copy by key (spec §2.3). v1 is offline-first: the complete baked-in catalog
 * (`assets/copy/strings.default.json`) is the source of truth and always-available fallback. The OTA
 * override layer (M13) calls [applyOverride], which **only** touches [CopyKeys.OVERRIDABLE] keys —
 * compliance copy is locked to the baked-in binary value and can never be changed over the air.
 */
class CopyRepository(context: Context) {

    private val bakedIn: Map<String, String> = loadBakedIn(context)
    private val _resolved = MutableStateFlow(bakedIn)
    val resolved: StateFlow<Map<String, String>> = _resolved

    /** Resolve [key], formatting with [args] if given. Missing keys fall back to the key itself. */
    fun get(key: String, vararg args: Any?): String {
        val template = _resolved.value[key] ?: key
        return if (args.isEmpty()) template else template.format(*args)
    }

    /**
     * Apply a validated remote override (M13). Overrides are restricted to overridable keys; any
     * compliance key in [override] is ignored so it stays at its baked-in value.
     */
    fun applyOverride(override: Map<String, String>) {
        val merged = bakedIn.toMutableMap()
        override.forEach { (k, v) -> if (k in CopyKeys.OVERRIDABLE) merged[k] = v }
        _resolved.value = merged
    }

    /**
     * Layer 2 of the 3-layer resolution (spec §2.3): re-verify and apply the cached OTA bundle at
     * launch, **before** any network. The cached raw signed bytes are re-run through the full
     * [CopyBundleVerifier] gauntlet (Ed25519 signature against the pinned key + schema + placeholder
     * parity) every launch, so a tampered cache file is rejected and we fall back to baked-in.
     *
     * The monotonic-revision step is intentionally **not** applied here: the cached bundle was already
     * accepted (its revision equals the stored applied revision), and re-verifying the signature still
     * proves authenticity — so re-applying an equal revision is not a rollback. (The strictly-greater
     * guard is enforced only when *accepting a freshly fetched* bundle, in `CopyUpdater`.)
     *
     * Synchronous and cheap (a small file read + one Ed25519 verify); safe to call once at process start.
     * A rejected/corrupt cache is cleared so the next fetch self-heals.
     */
    fun applyCachedOverride(
        context: Context,
        verifier: CopyBundleVerifier = CopyBundleVerifier(bakedIn = bakedIn, allowedKeys = CopyKeys.ALL),
    ) {
        val cache = CopyBundleCache(File(context.filesDir, OTA_CACHE_DIR))
        val bytes = cache.read() ?: return // no cached bundle → baked-in stands
        when (val result = verifier.verify(bytes)) {
            is CopyBundleVerifier.Result.Valid -> applyOverride(result.strings)
            is CopyBundleVerifier.Result.Rejected -> cache.clear() // discard; CopyUpdater re-fetches
        }
    }

    /** Build a [CopyBundleVerifier] wired to this catalog's baked-in templates (test/seam helper). */
    fun verifierWith(ed25519: com.tihor.quill.copy.ota.Ed25519Verifier): CopyBundleVerifier =
        CopyBundleVerifier(bakedIn = bakedIn, allowedKeys = CopyKeys.ALL, verifier = ed25519)

    /**
     * Build the launch-time OTA fetcher (layer 3, spec §2.3). Wired to the baked-in templates + key
     * allowlist so the verifier can enforce schema/parity. Caller fires it off-main, gated by the
     * "Check for updated text" setting; an accepted bundle is cached and applied on the next launch.
     */
    fun newUpdater(context: Context): CopyUpdater {
        val cache = CopyBundleCache(File(context.filesDir, OTA_CACHE_DIR))
        val verifier = CopyBundleVerifier(bakedIn = bakedIn, allowedKeys = CopyKeys.ALL)
        return CopyUpdater(verifier, cache, CopyUpdateStore(context))
    }

    /** True if the baked-in catalog contains every key the code references (CI completeness check). */
    fun isComplete(): Boolean = CopyKeys.ALL.all { bakedIn.containsKey(it) }

    fun bakedInValue(key: String): String? = bakedIn[key]

    private fun loadBakedIn(context: Context): Map<String, String> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return JSON.decodeFromString(CopyCatalog.serializer(), text).strings
    }

    companion object {
        private const val ASSET_PATH = "copy/strings.default.json"

        /** App-private dir holding the cached OTA bundle (`filesDir/copy`); backup-excluded. */
        const val OTA_CACHE_DIR = "copy"

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
