package com.tihor.quill.copy.ota

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.security.MessageDigest

/**
 * Verifies Ed25519 (RFC 8032, PureEdDSA over edwards25519 / SHA-512) signatures (M13a, spec §6).
 *
 * **Why a bundled pure-Java verifier and not the platform provider:** `Signature.getInstance("Ed25519")`
 * only exists on **API 33+**, but Quill's minSdk is **29** — on API 29–32 it throws
 * `NoSuchAlgorithmException`. This class uses `net.i2p.crypto:eddsa` uniformly across API 29–37 and the
 * test JVM. It **never** calls the JCA `Signature.getInstance("Ed25519")` factory, so there is no
 * platform fallback to version-branch and no behavioural difference by API level.
 *
 * The public key is the **raw 32-byte** Ed25519 public key (the encoded curve point), constructed via
 * [EdDSAPublicKeySpec] — **not** a PEM / X.509 `SubjectPublicKeyInfo` (the pinned constant is raw bytes,
 * not DER).
 *
 * @param publicKey the raw 32-byte Ed25519 public key. Defaults to the pinned production key.
 */
class Ed25519Verifier(publicKey: ByteArray = PINNED_PUBLIC_KEY) {

    init {
        require(publicKey.size == 32) {
            "Ed25519 public key must be 32 raw bytes, got ${publicKey.size}"
        }
    }

    // Curve params + parsed key are immutable and cheap to hold; EdDSAEngine instances are created per
    // verify (Signature objects are not thread-safe).
    private val curve = EdDSANamedCurveTable.getByName(CURVE_NAME)
    private val key = EdDSAPublicKey(EdDSAPublicKeySpec(publicKey.copyOf(), curve))

    /**
     * @return true iff [signature] is a valid Ed25519 signature of [message] under the pinned key.
     * Never throws for a malformed signature/message — a bad input simply returns false (treated as a
     * rejected, possibly-hostile bundle).
     */
    fun verify(message: ByteArray, signature: ByteArray): Boolean = try {
        if (signature.size != SIGNATURE_LEN) {
            false
        } else {
            val engine = EdDSAEngine(MessageDigest.getInstance(curve.hashAlgorithm)) // "SHA-512"
            engine.initVerify(key)
            engine.update(message)
            engine.verify(signature)
        }
    } catch (_: Exception) {
        // Any parsing/format/crypto error => not a valid signature. Fail closed.
        false
    }

    companion object {
        private const val CURVE_NAME = "Ed25519"
        private const val SIGNATURE_LEN = 64

        /**
         * Pinned Ed25519 **public** key (base64, decodes to exactly 32 raw bytes). The app trusts ONLY
         * copy bundles whose signature verifies against this key (spec §2.3 / §6). The matching private
         * key is held only as an M13b CI secret and never ships in the binary.
         */
        const val PINNED_PUBLIC_KEY_B64 = "bds0/2n1NYbYv7yvxwW3LI+uxGBeuf4KS7LBxHHB8hA="

        // java.util.Base64 (API 26+, minSdk is 29) — not android.util.Base64 — so the verifier loads on
        // a plain JVM in unit tests without Robolectric or the Android framework.
        val PINNED_PUBLIC_KEY: ByteArray = java.util.Base64.getDecoder().decode(PINNED_PUBLIC_KEY_B64)
    }
}
