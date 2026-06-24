package com.tihor.quill.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "quill_settings")

/**
 * User settings (spec §4.1). Defaults: Groq, cleanup on, private mode off, 300 s cap.
 *
 * [languageHint] defaults to English ("en") so mixed-language (Hinglish) dictation comes out in
 * Roman/Latin script out of the box; it stays user-changeable. [LANGUAGE_AUTO] is the explicit
 * auto-detect choice (native-script escape hatch) — distinct from the "en" default.
 */
data class QuillSettings(
    val provider: SttProvider = SttProvider.DEFAULT,
    val sttModel: String = SttProvider.DEFAULT.defaultModel,
    val languageHint: String = DEFAULT_LANGUAGE, // ISO code, or LANGUAGE_AUTO for auto-detect
    val cleanupEnabled: Boolean = true,
    val privateMode: Boolean = false,
    val sessionCapSeconds: Int = 300,
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en"
        const val LANGUAGE_AUTO = "auto"
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<QuillSettings> = context.settingsDataStore.data.map { p ->
        val provider = SttProvider.fromId(p[Keys.PROVIDER])
        QuillSettings(
            provider = provider,
            sttModel = p[Keys.STT_MODEL] ?: provider.defaultModel,
            languageHint = p[Keys.LANGUAGE_HINT]?.takeIf { it.isNotBlank() } ?: QuillSettings.DEFAULT_LANGUAGE,
            cleanupEnabled = p[Keys.CLEANUP_ENABLED] ?: true,
            privateMode = p[Keys.PRIVATE_MODE] ?: false,
            sessionCapSeconds = p[Keys.SESSION_CAP_SECONDS] ?: 300,
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
        )
    }

    suspend fun setProvider(provider: SttProvider) = context.settingsDataStore.edit {
        it[Keys.PROVIDER] = provider.id
        // Reset the model to the new provider's default unless the user set one explicitly.
        it[Keys.STT_MODEL] = provider.defaultModel
    }

    suspend fun setSttModel(model: String) = context.settingsDataStore.edit { it[Keys.STT_MODEL] = model }

    /** Persist the spoken-language choice: an ISO code ("en", "hi") or [QuillSettings.LANGUAGE_AUTO]. */
    suspend fun setLanguageHint(code: String) = context.settingsDataStore.edit {
        if (code.isBlank()) it.remove(Keys.LANGUAGE_HINT) else it[Keys.LANGUAGE_HINT] = code
    }

    suspend fun setCleanupEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.CLEANUP_ENABLED] = enabled }

    suspend fun setPrivateMode(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.PRIVATE_MODE] = enabled }

    suspend fun setSessionCapSeconds(seconds: Int) =
        context.settingsDataStore.edit { it[Keys.SESSION_CAP_SECONDS] = seconds }

    suspend fun setOnboardingComplete(complete: Boolean) =
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val LANGUAGE_HINT = stringPreferencesKey("language_hint")
        val CLEANUP_ENABLED = booleanPreferencesKey("cleanup_enabled")
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val SESSION_CAP_SECONDS = intPreferencesKey("session_cap_seconds")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }
}
