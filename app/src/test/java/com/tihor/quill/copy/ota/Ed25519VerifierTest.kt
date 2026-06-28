package com.tihor.quill.copy.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Ed25519 verifier correctness on the **plain JVM** (M13a). minSdk is 29 and the JCA "Ed25519"
 * Signature algorithm only exists on API 33+, so these prove the bundled pure-Java verifier
 * ([Ed25519Verifier], net.i2p.crypto:eddsa) works uniformly without the platform provider.
 */
class Ed25519VerifierTest {

    private val signer = TestEd25519Signer.fromSeed()
    private val verifier = Ed25519Verifier(signer.rawPublicKey)

    @Test
    fun genuineSignature_verifies() {
        val msg = "the quick brown fox".toByteArray()
        val sig = signer.sign(msg)
        // A genuine Ed25519 signature, verified by an independently-constructed engine.
        assertTrue(verifier.verify(msg, sig))
    }

    @Test
    fun tamperedMessage_isRejected() {
        val sig = signer.sign("approve transfer of \$10".toByteArray())
        assertFalse(verifier.verify("approve transfer of \$1000".toByteArray(), sig))
    }

    @Test
    fun tamperedSignature_isRejected() {
        val msg = "hello".toByteArray()
        val sig = signer.sign(msg)
        sig[0] = (sig[0].toInt() xor 0x01).toByte() // flip one bit
        assertFalse(verifier.verify(msg, sig))
    }

    @Test
    fun wrongKey_isRejected() {
        val msg = "hello".toByteArray()
        val sig = signer.sign(msg)
        val otherKey = TestEd25519Signer.fromSeed("a-totally-different-32-byte-seed!".toByteArray()).rawPublicKey
        assertFalse(Ed25519Verifier(otherKey).verify(msg, sig))
    }

    @Test
    fun malformedSignature_returnsFalse_doesNotThrow() {
        assertFalse(verifier.verify("x".toByteArray(), ByteArray(0)))
        assertFalse(verifier.verify("x".toByteArray(), ByteArray(10)))
        assertFalse(verifier.verify("x".toByteArray(), ByteArray(64))) // all-zero 64 bytes
    }

    @Test
    fun determinism_sameMessageSameSignature() {
        // Ed25519 is deterministic — a property the verifier relies on and a sanity check on the lib.
        val msg = "deterministic".toByteArray()
        assertArrayEqualsHex(signer.sign(msg), signer.sign(msg))
    }

    @Test
    fun pinnedPublicKey_decodesTo32RawBytes() {
        // The pinned constant must be raw 32 bytes (not PEM/X.509), as the verifier requires.
        val raw = Base64.getDecoder().decode(Ed25519Verifier.PINNED_PUBLIC_KEY_B64)
        assertEquals(32, raw.size)
        // Constructing the production verifier against the pinned raw key must not throw.
        Ed25519Verifier()
    }

    @Test
    fun bundleSignedByNonPinnedKey_failsAgainstPinnedVerifier() {
        // A bundle signed by anyone other than the pinned private key is rejected by the prod verifier —
        // i.e. the app trusts ONLY the pinned key (no platform-provider fallback that might accept it).
        val msg = "attacker payload".toByteArray()
        val sig = signer.sign(msg)
        assertFalse(Ed25519Verifier().verify(msg, sig))
    }

    private fun assertArrayEqualsHex(a: ByteArray, b: ByteArray) =
        assertEquals(a.joinToString("") { "%02x".format(it) }, b.joinToString("") { "%02x".format(it) })
}
