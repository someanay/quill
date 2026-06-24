package com.tihor.quill.secrets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * M7 "write first": the crypto round-trips and ciphertext ≠ plaintext (testing_plan.md §1). Tests the
 * provider-agnostic [AesGcmCipher] with a software AES-256 key; the AndroidKeyStore integration +
 * file persistence are verified on-device (key survives restart).
 */
class AesGcmCipherTest {

    private fun key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun roundTrip_returnsOriginal() {
        val k = key()
        val secret = "gsk_live_abc123_DEF456"
        val blob = AesGcmCipher.encrypt(k, secret)
        assertEquals(secret, AesGcmCipher.decrypt(k, blob))
    }

    @Test
    fun ciphertext_isNotThePlaintext() {
        val k = key()
        val secret = "super-secret-key"
        val blob = AesGcmCipher.encrypt(k, secret)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains(secret))
    }

    @Test
    fun eachEncryption_usesAFreshIv_soCiphertextDiffers() {
        val k = key()
        val a = AesGcmCipher.encrypt(k, "same input")
        val b = AesGcmCipher.encrypt(k, "same input")
        assertNotEquals(String(a, Charsets.ISO_8859_1), String(b, Charsets.ISO_8859_1))
    }

    @Test
    fun wrongKey_failsToDecrypt() {
        val blob = AesGcmCipher.encrypt(key(), "secret")
        assertThrows(AEADBadTagException::class.java) { AesGcmCipher.decrypt(key(), blob) }
    }
}
