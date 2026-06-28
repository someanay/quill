package com.tihor.quill.copy.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The integrity gauntlet (M13a, spec §2.3 / §6): signature, schema, and placeholder parity. The
 * monotonic-revision rollback step is tested at the [CopyUpdater] level (it's an acceptance policy).
 */
class CopyBundleVerifierTest {

    private val signer = TestEd25519Signer.fromSeed()

    // A small baked-in template set; one key carries a %1$d placeholder so parity has teeth.
    private val baked = mapOf(
        "onboarding.welcome.title" to "Welcome",
        "settings.keys.set" to "%1\$d set",
        "compliance.privacy.promise" to "Your data stays on device",
    )
    private val allowed = baked.keys

    private fun verifier(verifier: Ed25519Verifier = Ed25519Verifier(signer.rawPublicKey)) =
        CopyBundleVerifier(bakedIn = baked, allowedKeys = allowed, verifier = verifier)

    private fun rejected(r: CopyBundleVerifier.Result) =
        (r as? CopyBundleVerifier.Result.Rejected)?.reason ?: error("expected Rejected, got $r")

    @Test
    fun validBundle_verifies() {
        val strings = mapOf("onboarding.welcome.title" to "Hello there")
        val result = verifier().verify(signer.bundleBytes(7, strings))
        assertTrue(result is CopyBundleVerifier.Result.Valid)
        result as CopyBundleVerifier.Result.Valid
        assertEquals(7, result.revision)
        assertEquals("Hello there", result.strings["onboarding.welcome.title"])
    }

    @Test
    fun tamperedStrings_rejected() {
        val strings = mapOf("onboarding.welcome.title" to "Hello")
        val bytes = tamperedBundle(
            { it.copy(strings = mapOf("onboarding.welcome.title" to "EVIL")) },
            7, strings, signer,
        )
        assertTrue(rejected(verifier().verify(bytes)).startsWith("signature"))
    }

    @Test
    fun tamperedRevision_rejected() {
        val strings = mapOf("onboarding.welcome.title" to "Hello")
        val bytes = tamperedBundle({ it.copy(revision = 999) }, 7, strings, signer)
        assertTrue(rejected(verifier().verify(bytes)).startsWith("signature"))
    }

    @Test
    fun tamperedSignature_rejected() {
        val strings = mapOf("onboarding.welcome.title" to "Hello")
        val bytes = tamperedBundle({ it.copy(signature = "AAAA" + it.signature.drop(4)) }, 7, strings, signer)
        assertTrue(rejected(verifier().verify(bytes)).startsWith("signature"))
    }

    @Test
    fun signedByDifferentKey_rejectedAgainstPinnedVerifier() {
        // Authentic signature, but from the wrong key → the pinned verifier rejects it.
        val bytes = signer.bundleBytes(7, mapOf("onboarding.welcome.title" to "Hello"))
        val pinned = verifier(Ed25519Verifier()) // production pinned key, not the test key
        assertTrue(rejected(pinned.verify(bytes)).startsWith("signature"))
    }

    @Test
    fun unknownKey_rejectsWholeBundle() {
        val bytes = signer.bundleBytes(7, mapOf("totally.unknown.key" to "x"))
        assertTrue(rejected(verifier().verify(bytes)).startsWith("schema"))
    }

    @Test
    fun placeholderShapeChange_rejectsWholeBundle() {
        // baked: "%1$d set"  →  override changes %d to %s: String.format would crash, so reject.
        val bytes = signer.bundleBytes(7, mapOf("settings.keys.set" to "%1\$s set"))
        assertTrue(rejected(verifier().verify(bytes)).startsWith("parity"))
    }

    @Test
    fun benignTextChange_withSamePlaceholders_isApplied() {
        val bytes = signer.bundleBytes(7, mapOf("settings.keys.set" to "%1\$d configured"))
        assertTrue(verifier().verify(bytes) is CopyBundleVerifier.Result.Valid)
    }

    @Test
    fun compliancePresent_passesGauntlet_butIsIgnoredAtApply() {
        // The verifier doesn't reject a compliance key with matching parity; the compliance LOCK is
        // enforced later in CopyRepository.applyOverride (covered by CopyOtaPipelineTest).
        val bytes = signer.bundleBytes(7, mapOf("compliance.privacy.promise" to "We sell your data"))
        assertTrue(verifier().verify(bytes) is CopyBundleVerifier.Result.Valid)
    }

    @Test
    fun malformedJson_rejected() {
        assertTrue(rejected(verifier().verify("{not json".toByteArray())).startsWith("parse"))
    }
}
