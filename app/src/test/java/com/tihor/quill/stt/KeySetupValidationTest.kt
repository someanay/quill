package com.tihor.quill.stt

import com.tihor.quill.settings.SttProvider
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class KeySetupValidationTest {
    @Test fun rejectsWrongProviderMaskedAndIncompleteKeys() {
        assertTrue(KeySetupValidation.plausible(SttProvider.GEMINI, "AIza" + "x".repeat(35)))
        assertTrue(KeySetupValidation.plausible(SttProvider.GEMINI, "AQ." + "x".repeat(40)))
        assertTrue(KeySetupValidation.plausible(SttProvider.GROQ, "gsk_" + "x".repeat(40)))
        assertTrue(KeySetupValidation.plausible(SttProvider.OPENROUTER, "sk-or-v1-" + "x".repeat(40)))
        assertFalse(KeySetupValidation.plausible(SttProvider.GROQ, "AIza" + "x".repeat(35)))
        assertFalse(KeySetupValidation.plausible(SttProvider.GEMINI, "AIza****"))
        assertFalse(KeySetupValidation.plausible(SttProvider.GEMINI, "AIza" + "x".repeat(25) + "..."))
        assertFalse(KeySetupValidation.plausible(SttProvider.GROQ, "gsk_hello worldxxxxxxxxxxxx"))
        assertFalse(KeySetupValidation.plausible(SttProvider.OPENAI, "sk-" + "x".repeat(40)))
    }

    @Test fun routerChecksAuthenticatedAccountNotPublicModelCatalog() = runTest {
        val token = "sk-or-v1-" + "x".repeat(40)
        var request: Request? = null
        val client = fakeClient(200) { request = it }
        assertEquals(KeyValidation.Valid, KeySetupValidation.validate(SttProvider.OPENROUTER, token, client))
        assertEquals("/api/v1/key", request!!.url.encodedPath)
        assertEquals("GET", request!!.method)
        assertEquals("Bearer $token", request!!.header("Authorization"))
        assertNull(request!!.body)
        assertFalse(request!!.url.toString().contains(token))
    }

    @Test fun providerRejectionAndTemporaryFailureAreDistinct() = runTest {
        val token = "gsk_" + "x".repeat(40)
        assertEquals(KeyValidation.Rejected, KeySetupValidation.validate(SttProvider.GROQ, token, fakeClient(401)))
        assertTrue(KeySetupValidation.validate(SttProvider.GROQ, token, fakeClient(429)) is KeyValidation.Unverified)
        assertTrue(KeySetupValidation.validate(SttProvider.GROQ, token, fakeClient(503)) is KeyValidation.Unverified)
    }

    private fun fakeClient(code: Int, inspect: (Request) -> Unit = {}) = OkHttpClient.Builder().addInterceptor {
        inspect(it.request())
        Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body("{}".toResponseBody("application/json".toMediaType())).build()
    }.build()
}
