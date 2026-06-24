package com.tihor.quill.secrets

/**
 * **Temporary** process-memory holder for the BYOK key so M6 can run end-to-end before secure
 * storage exists. Replaced at M7 by `SecretStore` (Android Keystore AES-256-GCM, backup-excluded).
 * Deliberately in-memory only — no plaintext key is written to disk at this stage. Never logged.
 */
object TempApiKeyHolder {
    @Volatile
    var key: String? = null

    fun isSet(): Boolean = !key.isNullOrBlank()
}
