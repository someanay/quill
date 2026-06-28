package com.tihor.quill.copy.ota

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64

/**
 * Test-only Ed25519 signer (M13a). The production verifier ships only the **public** key; we don't have
 * the production **private** key (it's an M13b CI secret), so bundle-level tests generate an ephemeral
 * keypair from a fixed seed, sign with the private half, and verify against the matching public half.
 *
 * This is genuine Ed25519: a signature produced here by the bundled lib is verified by
 * [Ed25519Verifier] through an *independently constructed* engine — proving the verifier accepts real
 * signatures on the JVM with no platform "Ed25519" provider involved.
 */
class TestEd25519Signer private constructor(
    val rawPublicKey: ByteArray,
    private val privateKey: EdDSAPrivateKey,
) {
    fun sign(message: ByteArray): ByteArray {
        val engine = EdDSAEngine(MessageDigest.getInstance(SPEC.hashAlgorithm))
        engine.initSign(privateKey)
        engine.update(message)
        return engine.sign()
    }

    /** Sign the canonical bytes of (revision, strings) and emit the JSON wire bundle. */
    fun bundleBytes(revision: Int, strings: Map<String, String>): ByteArray {
        val sig = sign(CopyBundleCanonical.bytes(revision, strings))
        val bundle = SignedCopyBundle(revision, strings, Base64.getEncoder().encodeToString(sig))
        return Json.encodeToString(SignedCopyBundle.serializer(), bundle).toByteArray(Charsets.UTF_8)
    }

    companion object {
        private val SPEC = EdDSANamedCurveTable.getByName("Ed25519")

        /** Deterministic keypair from a 32-byte [seed] (pad/truncate a label for convenience). */
        fun fromSeed(seed: ByteArray = "quill-m13a-test-seed-0123456789!!".toByteArray()): TestEd25519Signer {
            val s = seed.copyOf(32)
            val privSpec = EdDSAPrivateKeySpec(s, SPEC)
            val priv = EdDSAPrivateKey(privSpec)
            val rawPub = EdDSAPublicKey(EdDSAPublicKeySpec(privSpec.a, SPEC)).abyte
            return TestEd25519Signer(rawPub, priv)
        }
    }
}

/** Re-encode a bundle with a single field tampered (signature left stale → must fail verification). */
internal fun tamperedBundle(
    block: (SignedCopyBundle) -> SignedCopyBundle,
    revision: Int,
    strings: Map<String, String>,
    signer: TestEd25519Signer,
): ByteArray {
    val sig = signer.sign(CopyBundleCanonical.bytes(revision, strings))
    val good = SignedCopyBundle(revision, strings, Base64.getEncoder().encodeToString(sig))
    val bad = block(good)
    return Json.encodeToString(SignedCopyBundle.serializer(), bad).toByteArray(Charsets.UTF_8)
}
