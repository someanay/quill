package com.tihor.quill.secrets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device security probe for the BYOK key at rest (spec §6). The AndroidKeyStore AES-256-GCM path
 * can only run on a real device (not Robolectric), so this is the instrumented counterpart to the
 * JVM [AesGcmCipherTest]. Uses a throwaway alias so it never clobbers the user's real stt_api_key.
 */
@RunWith(AndroidJUnit4::class)
class SecretBlobIntegrityTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "probe_integrity_key"
    private val secret = "gsk_PROBE_ZZ_9f3a_DO_NOT_LOG_4242_secret"

    @After
    fun cleanup() {
        KeystoreSecretStore(ctx).clear(alias)
    }

    @Test
    fun keyRoundTrips_butOnDiskBlobHasNoPlaintext() {
        val store = KeystoreSecretStore(ctx)
        store.put(alias, secret)

        // Round-trips through the real Keystore-backed cipher.
        assertEquals(secret, store.get(alias))

        // The persisted blob is ciphertext+IV — the plaintext key must NOT appear in it.
        val blob = File(File(ctx.filesDir, "secrets"), "${alias}.bin").readBytes()
        val asLatin1 = String(blob, Charsets.ISO_8859_1)
        assertFalse("blob must not contain the plaintext key", asLatin1.contains(secret))
        assertFalse("blob must not contain even the key prefix", asLatin1.contains("gsk_PROBE"))
        assertTrue("blob should be IV(12)+ciphertext+tag, longer than plaintext", blob.size > secret.length)

        // Raw plaintext bytes are not a contiguous slice of the blob either.
        val plainBytes = secret.toByteArray(Charsets.UTF_8)
        assertFalse("plaintext bytes must not appear verbatim", indexOfSub(blob, plainBytes) >= 0)
    }

    private fun indexOfSub(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
