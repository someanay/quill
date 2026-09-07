package com.tihor.quill.copy.ota

import com.tihor.quill.copy.CopyKeys
import com.tihor.quill.copy.CopyRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * End-to-end OTA pipeline tests (M13a, spec §2.3 / §6): persistence across a simulated restart, the
 * compliance lock surviving a signed bundle, tampered-cache rejection, the monotonic rollback guard,
 * and silent offline handling. Robolectric supplies a real [android.content.Context] (assets + filesDir
 * + DataStore).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyOtaPipelineTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val signer = TestEd25519Signer.fromSeed()
    private val server = MockWebServer()

    private fun cache() = CopyBundleCache(File(context.filesDir, CopyRepository.OTA_CACHE_DIR))
    private fun testEd25519() = Ed25519Verifier(signer.rawPublicKey)

    @After
    fun tearDown() {
        cache().clear()
        runCatching { server.shutdown() }
        runBlocking { CopyUpdateStore(context).reset() }
    }

    @Test
    fun appliedOverride_survivesSimulatedRestart() {
        // Write a validated bundle to the cache, then a *fresh* repository re-reads + applies it at launch.
        cache().write(signer.bundleBytes(2, mapOf(CopyKeys.WELCOME_TITLE to "Bienvenue")))

        val restarted = CopyRepository(context)
        assertEquals("Dictate into any app you use", restarted.get(CopyKeys.WELCOME_TITLE)) // baked-in pre-apply
        restarted.applyCachedOverride(context, restarted.verifierWith(testEd25519()))
        assertEquals("Bienvenue", restarted.get(CopyKeys.WELCOME_TITLE))
    }

    @Test
    fun cachedBundle_cannotChangeComplianceKey() {
        val baked = CopyRepository(context).get(CopyKeys.RATIONALE_ACCESSIBILITY)
        cache().write(
            signer.bundleBytes(
                2,
                mapOf(
                    CopyKeys.WELCOME_TITLE to "Overridden",
                    CopyKeys.RATIONALE_ACCESSIBILITY to "MALICIOUS DISCLOSURE", // compliance key — must be ignored
                ),
            ),
        )
        val repo = CopyRepository(context)
        repo.applyCachedOverride(context, repo.verifierWith(testEd25519()))

        assertEquals("Overridden", repo.get(CopyKeys.WELCOME_TITLE))
        assertEquals("compliance copy stays binary-only", baked, repo.get(CopyKeys.RATIONALE_ACCESSIBILITY))
    }

    @Test
    fun tamperedCache_isRejectedAtLaunch_andCleared() {
        val tampered = tamperedBundle(
            { it.copy(signature = "AAAA" + it.signature.drop(4)) },
            2, mapOf(CopyKeys.WELCOME_TITLE to "Bienvenue"), signer,
        )
        cache().write(tampered)

        val repo = CopyRepository(context)
        repo.applyCachedOverride(context, repo.verifierWith(testEd25519()))

        // Falls back to baked-in, and the bad cache file is discarded.
        assertEquals("Dictate into any app you use", repo.get(CopyKeys.WELCOME_TITLE))
        assertFalse(File(File(context.filesDir, CopyRepository.OTA_CACHE_DIR), "override.json").exists())
    }

    @Test
    fun fetch_rejectsNonNewerRevision_rollbackGuard() = runBlocking {
        val store = CopyUpdateStore(context)
        val c = cache()
        // Already at revision 5 (cache present so self-heal won't reset).
        c.write(signer.bundleBytes(5, mapOf(CopyKeys.WELCOME_TITLE to "Five")))
        store.setApplied(5, null)

        server.enqueue(MockResponse().setResponseCode(200).setBody(bodyString(3, "Three")))
        server.start()

        val updater = CopyUpdater(
            verifier = CopyRepository(context).verifierWith(testEd25519()),
            cache = c, store = store, baseUrl = server.url("/").toString(),
        )
        val outcome = updater.checkForUpdate()

        assertTrue("expected rollback rejection, got $outcome", outcome is CopyUpdater.Outcome.Rejected)
        assertEquals(5, store.appliedRevision()) // unchanged
    }

    @Test
    fun fetch_acceptsNewerRevision_cachesForNextLaunch() = runBlocking {
        val store = CopyUpdateStore(context)
        val c = cache()
        server.enqueue(MockResponse().setResponseCode(200).setBody(bodyString(2, "Fresh")))
        server.start()

        val updater = CopyUpdater(
            verifier = CopyRepository(context).verifierWith(testEd25519()),
            cache = c, store = store, baseUrl = server.url("/").toString(),
        )
        val outcome = updater.checkForUpdate()

        // The bundle is fetched as a flat file at /strings.signed.json (no /copy/{locale}.json path).
        assertEquals("/strings.signed.json", server.takeRequest().path)
        assertTrue("expected acceptance, got $outcome", outcome is CopyUpdater.Outcome.Accepted)
        assertEquals(2, store.appliedRevision())
        // The accepted bundle is now cached and would apply on next launch.
        assertNotNull(c.read())
        val repo = CopyRepository(context)
        repo.applyCachedOverride(context, repo.verifierWith(testEd25519()))
        assertEquals("Fresh", repo.get(CopyKeys.WELCOME_TITLE))
    }

    @Test
    fun fetch_failure_isSilent_keepsBakedIn_noCrash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.start()

        val store = CopyUpdateStore(context)
        val updater = CopyUpdater(
            verifier = CopyRepository(context).verifierWith(testEd25519()),
            cache = cache(), store = store, baseUrl = server.url("/").toString(),
        )
        val outcome = updater.checkForUpdate()

        assertTrue("expected failure outcome, got $outcome", outcome is CopyUpdater.Outcome.Failed)
        assertEquals(0, store.appliedRevision())
        // Resolved copy is untouched — a fresh repo still renders baked-in.
        assertEquals("Dictate into any app you use", CopyRepository(context).get(CopyKeys.WELCOME_TITLE))
    }

    @Test
    fun overlappingForegroundAndScheduledChecksCannotRollBackCache() = runBlocking {
        server.enqueue(MockResponse().setBody(bodyString(7, "Seven")))
        server.enqueue(MockResponse().setBody(bodyString(6, "Six")))
        server.start()
        val store = CopyUpdateStore(context)
        val c = cache()
        val outcomes = (1..2).map {
            async {
                CopyUpdater(
                    verifier = CopyRepository(context).verifierWith(testEd25519()),
                    cache = c, store = store, baseUrl = server.url("/").toString(),
                ).checkForUpdate()
            }
        }.awaitAll()
        assertEquals(1, outcomes.count { it is CopyUpdater.Outcome.Accepted })
        assertEquals(1, outcomes.count { it is CopyUpdater.Outcome.Rejected })
        assertEquals(7, store.appliedRevision())
        val repo = CopyRepository(context)
        repo.applyCachedOverride(context, repo.verifierWith(testEd25519()))
        assertEquals("Seven", repo.get(CopyKeys.WELCOME_TITLE))
    }

    private fun bodyString(revision: Int, title: String): String =
        signer.bundleBytes(revision, mapOf(CopyKeys.WELCOME_TITLE to title)).toString(Charsets.UTF_8)
}
