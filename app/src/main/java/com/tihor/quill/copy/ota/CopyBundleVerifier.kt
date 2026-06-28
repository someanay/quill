package com.tihor.quill.copy.ota

import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Verifies a raw OTA copy bundle against the integrity gauntlet (M13a, spec §2.3 / §6) **except** the
 * monotonic-revision rollback check, which is an *acceptance policy* owned by [CopyUpdater] /
 * [com.tihor.quill.copy.CopyRepository] (the rollback guard differs between accepting a fetched bundle —
 * strictly greater — and re-applying an already-accepted cache — equal). What lives here is everything
 * that makes a bundle *authentic and safe to apply*:
 *
 * 1. Parse the JSON wire bundle. Malformed -> reject.
 * 2. **Ed25519 signature** verifies against the pinned key over the canonical bytes of `{revision,
 *    strings}` ([CopyBundleCanonical]). Fail -> reject (possible attack).
 * 3. **Schema**: every key exists in [allowedKeys] (= `CopyKeys.ALL`). Unknown key -> reject the whole
 *    bundle.
 * 4. **Placeholder parity**: every value's `%…` format placeholders match the baked-in template for that
 *    key ([Placeholders]). Any mismatch -> reject the whole bundle (atomic; never half-apply).
 *
 * On success the caller still applies the rollback policy, then hands `strings` to
 * `CopyRepository.applyOverride`, where compliance keys are already ignored (compliance lock untouched).
 */
class CopyBundleVerifier(
    private val bakedIn: Map<String, String>,
    private val allowedKeys: Set<String>,
    private val verifier: Ed25519Verifier = Ed25519Verifier(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    sealed interface Result {
        data class Valid(val revision: Int, val strings: Map<String, String>) : Result
        data class Rejected(val reason: String) : Result
    }

    /** Run steps 1–4 over [rawBundleBytes]. Never throws; a failure is a [Result.Rejected]. */
    fun verify(rawBundleBytes: ByteArray): Result {
        // 1. Parse.
        val bundle = try {
            json.decodeFromString(SignedCopyBundle.serializer(), rawBundleBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            return Result.Rejected("parse: ${e.message}")
        }

        // 2. Signature over the canonical bytes of {revision, strings}.
        val signature = try {
            Base64.getDecoder().decode(bundle.signature)
        } catch (e: Exception) {
            return Result.Rejected("signature: not valid base64")
        }
        val canonical = CopyBundleCanonical.bytes(bundle.revision, bundle.strings)
        if (!verifier.verify(canonical, signature)) {
            return Result.Rejected("signature: Ed25519 verification failed")
        }

        // 3. Schema — every key must be one the app knows.
        val unknown = bundle.strings.keys.firstOrNull { it !in allowedKeys }
        if (unknown != null) {
            return Result.Rejected("schema: unknown key '$unknown'")
        }

        // 4. Placeholder parity vs the baked-in template for each key.
        for ((key, value) in bundle.strings) {
            val baked = bakedIn[key] ?: return Result.Rejected("schema: no baked-in template for '$key'")
            if (!Placeholders.parityMatches(baked, value)) {
                return Result.Rejected("parity: placeholder mismatch on '$key'")
            }
        }

        return Result.Valid(bundle.revision, bundle.strings)
    }
}
