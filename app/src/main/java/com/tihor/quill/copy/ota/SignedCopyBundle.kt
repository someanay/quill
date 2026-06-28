package com.tihor.quill.copy.ota

import kotlinx.serialization.Serializable

/**
 * The signed OTA copy bundle wire format (M13a, spec §2.3).
 *
 * ```json
 * {
 *   "revision": 42,
 *   "strings": { "<overridable key>": "<value>", ... },
 *   "signature": "<base64 Ed25519 signature over the canonical bytes of {revision, strings}>"
 * }
 * ```
 *
 * The signature covers the **canonical** reconstruction of `{revision, strings}` (see
 * [CopyBundleCanonical]) — not these raw wire bytes — so the authoring side (M13b) and the device agree
 * on exactly what is signed regardless of JSON whitespace or key ordering on the wire.
 */
@Serializable
data class SignedCopyBundle(
    val revision: Int,
    val strings: Map<String, String> = emptyMap(),
    val signature: String = "",
)
