package com.tihor.quill.stt

import com.tihor.quill.settings.SttProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * Key validation against the **selected** provider (behavior requirement): the tiny-silent-WAV probe
 * goes through the same factory as dictation, so BadKey → Rejected maps identically per provider
 * (Gemini's 400 API_KEY_INVALID arrives here already typed as [TranscriptionError.BadKey] by its
 * client). A fake factory keeps this a pure JVM test.
 */
class KeyValidatorTest {

    private class FakeClient(private val result: Result<Transcript>) : TranscriptionClient {
        var lastModel: String? = null
        override suspend fun transcribe(audio: File, opts: TranscriptionOptions): Result<Transcript> {
            lastModel = opts.model
            return result
        }
    }

    private fun validator(client: TranscriptionClient, onCreate: (SttProvider) -> Unit = {}) =
        KeyValidator(cacheDir = File(System.getProperty("java.io.tmpdir"))) { provider, _, _ ->
            onCreate(provider)
            client
        }

    @Test
    fun success_isValid_andUsesSelectedProvider() = runTest {
        var seen: SttProvider? = null
        val client = FakeClient(Result.success(Transcript("")))
        val result = validator(client) { seen = it }.validate(SttProvider.GEMINI, "AIza-key")
        assertEquals(KeyValidation.Valid, result)
        assertEquals(SttProvider.GEMINI, seen)
        assertEquals(SttProvider.GEMINI.defaultModel, client.lastModel)
    }

    @Test
    fun badKey_isRejected_perProvider() = runTest {
        val client = FakeClient(Result.failure(TranscriptionError.BadKey()))
        SttProvider.entries.filter { it != SttProvider.CUSTOM }.forEach { provider ->
            assertEquals(
                "provider $provider must map BadKey → Rejected",
                KeyValidation.Rejected,
                validator(client).validate(provider, "bad-key"),
            )
        }
    }

    @Test
    fun networkFailure_isUnverified_notRejected() = runTest {
        val client = FakeClient(Result.failure(TranscriptionError.Network(IOException("offline"))))
        val result = validator(client).validate(SttProvider.OPENROUTER, "sk-or")
        assertTrue(result is KeyValidation.Unverified)
    }

    @Test
    fun nonKeyProviderError_isUnverified() = runTest {
        // e.g. Gemini returning Parse on the silent clip: the key authenticated, the clip just had no
        // speech — save as unverified, never block the user.
        val client = FakeClient(Result.failure(TranscriptionError.Parse(RuntimeException("empty"))))
        val result = validator(client).validate(SttProvider.GEMINI, "AIza-key")
        assertTrue(result is KeyValidation.Unverified)
    }

    @Test
    fun paidAccessProbe_success_isTrue() = runTest {
        val client = FakeClient(Result.success(Transcript("ok")))
        val has = validator(client).hasPaidAccess(SttProvider.GEMINI, "AIza-key", "gemini-3.1-pro-preview")
        assertEquals(true, has)
        assertEquals("gemini-3.1-pro-preview", client.lastModel)
    }

    @Test
    fun paidAccessProbe_quotaExhausted_isFalse() = runTest {
        // A free-tier key calling a paid-only model gets 429 RESOURCE_EXHAUSTED (limit 0).
        val client = FakeClient(Result.failure(TranscriptionError.QuotaExhausted()))
        assertEquals(false, validator(client).hasPaidAccess(SttProvider.GEMINI, "AIza-key", "gemini-3.1-pro-preview"))
    }

    @Test
    fun paidAccessProbe_inconclusive_isNull() = runTest {
        // Offline or a provider hiccup proves nothing — the caller must keep the stored verdict.
        val offline = FakeClient(Result.failure(TranscriptionError.Network(IOException("offline"))))
        assertEquals(null, validator(offline).hasPaidAccess(SttProvider.GEMINI, "AIza-key", "gemini-3.1-pro-preview"))
        val hiccup = FakeClient(Result.failure(TranscriptionError.Provider(503)))
        assertEquals(null, validator(hiccup).hasPaidAccess(SttProvider.GEMINI, "AIza-key", "gemini-3.1-pro-preview"))
    }

    @Test
    fun custom_usesConfiguredModelAndEndpoint() = runTest {
        val client = FakeClient(Result.success(Transcript("ok")))
        val result = validator(client).validate(
            SttProvider.CUSTOM, "sk-custom",
            model = "my-model", customBaseUrl = "https://api.example.com/v1",
        )
        assertEquals(KeyValidation.Valid, result)
        assertEquals("my-model", client.lastModel)
    }

    @Test
    fun custom_withoutModelOrEndpoint_isUnverified_withoutNetworkCall() = runTest {
        var created = false
        val client = FakeClient(Result.success(Transcript("ok")))
        val v = validator(client) { created = true }
        // Missing model…
        assertTrue(v.validate(SttProvider.CUSTOM, "sk", customBaseUrl = "https://x.example") is KeyValidation.Unverified)
        // …and missing base URL both refuse to probe.
        assertTrue(v.validate(SttProvider.CUSTOM, "sk", model = "my-model") is KeyValidation.Unverified)
        assertTrue("no client should be built without an endpoint/model to probe", !created)
    }
}
