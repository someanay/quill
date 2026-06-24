package com.tihor.quill.secrets

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keystore-backed secret storage (spec §3.2 / §6). API keys never appear in logs or backups. */
interface SecretStore {
    fun put(alias: String, secret: String)
    fun get(alias: String): String?
    fun clear(alias: String)
    fun contains(alias: String): Boolean

    companion object {
        const val ALIAS_STT_KEY = "stt_api_key"
        const val ALIAS_LLM_KEY = "llm_api_key" // P1 (cleanup pass)
    }
}

/**
 * AES-256-GCM with a **non-exportable** key in the AndroidKeyStore (spec §6). Ciphertext + IV are
 * written to app-private files (excluded from Auto Backup via `data_extraction_rules.xml`), so on a
 * restore the Keystore key won't exist and the ciphertext is undecryptable garbage. We deliberately
 * do **not** use the deprecated `EncryptedSharedPreferences` (spec §6 note).
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val dir = File(context.filesDir, "secrets").apply { mkdirs() }

    override fun put(alias: String, secret: String) {
        File(dir, fileName(alias)).writeBytes(AesGcmCipher.encrypt(secretKey(), secret))
    }

    override fun get(alias: String): String? {
        val file = File(dir, fileName(alias))
        if (!file.exists()) return null
        return runCatching { AesGcmCipher.decrypt(secretKey(), file.readBytes()) }.getOrNull()
    }

    override fun clear(alias: String) {
        File(dir, fileName(alias)).delete()
    }

    override fun contains(alias: String): Boolean = File(dir, fileName(alias)).exists()

    private fun fileName(alias: String): String = "${alias.filter { it.isLetterOrDigit() || it == '_' }}.bin"

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "quill_secret_master_key"
    }
}

/**
 * AES-256-GCM encrypt/decrypt with the 12-byte IV prepended to the ciphertext. Provider-agnostic
 * (plain JDK crypto) so it is unit-testable with a software key, independent of the AndroidKeyStore.
 */
object AesGcmCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, plaintext: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + ciphertext
    }

    fun decrypt(key: SecretKey, blob: ByteArray): String {
        require(blob.size > IV_LENGTH) { "blob too short" }
        val iv = blob.copyOfRange(0, IV_LENGTH)
        val ciphertext = blob.copyOfRange(IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
