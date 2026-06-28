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
    /** LLM cleanup pass before insertion (M10). Opt-in: default OFF (extra latency + a second BYOK call). */
    val cleanupEnabled: Boolean = false,
    val privateMode: Boolean = false,
    /** Save dictation history on device (M9). Default ON; [privateMode] hard-overrides it. */
    val historyEnabled: Boolean = true,
    /**
     * How long saved dictations are kept, in minutes (M9). Default 1 hour; older entries auto-delete.
     * [RETENTION_FOREVER] (0) disables time-based pruning (a count cap still backstops storage).
     */
    val historyRetentionMinutes: Int = RETENTION_DEFAULT_MINUTES,
    val sessionCapSeconds: Int = 300,
    val onboardingComplete: Boolean = false,
    /** Floating-bubble size: [BUBBLE_SIZE_SMALL] / [BUBBLE_SIZE_MEDIUM] / [BUBBLE_SIZE_LARGE]. */
    val bubbleSize: String = BUBBLE_SIZE_MEDIUM,
    /** App theme: [APP_THEME_SYSTEM] / [APP_THEME_LIGHT] / [APP_THEME_DARK] — drives the whole UI. */
    val appTheme: String = APP_THEME_SYSTEM,
    /**
     * Reliability/"keep alive" (M11). When on, Quill restores the bubble after reboot and after an
     * incidental process kill (only when foregrounded — never an illegal background start). Default ON;
     * it never fights a deliberate force-stop (the OS blocks that for us).
     */
    val bootRestore: Boolean = true,
    /**
     * Whether the user currently wants the bubble running (the Home master toggle). Persisted so boot
     * + foreground recovery know there is something to restore; cleared when the user turns it off.
     */
    val bubbleActive: Boolean = false,
    /**
     * Opt-in local-first crash reporting (M11). Default OFF (privacy posture): nothing is collected or
     * shared unless the user turns this on and explicitly shares a report.
     */
    val crashReportingEnabled: Boolean = false,
    /**
     * Set when foreground recovery finds the bubble was killed while the user wanted it on. Gates the
     * battery-exemption re-prompt (only re-ask once a kill has actually happened); cleared on grant.
     */
    val bubbleWasKilled: Boolean = false,
    /**
     * "Check for updated text" (M13a, spec §2.3). Default ON. When on, Quill fetches a signed copy
     * bundle from the static host in the background at launch (off the critical path). Off → no fetch;
     * cached/baked-in copy only. This is the one extra outbound call beyond the provider calls, and it
     * sends no user data or device identifier (spec §2.3 privacy delta).
     */
    val copyUpdatesEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_LANGUAGE = "en"
        const val LANGUAGE_AUTO = "auto"

        const val BUBBLE_SIZE_SMALL = "Small"
        const val BUBBLE_SIZE_MEDIUM = "Medium"
        const val BUBBLE_SIZE_LARGE = "Large"

        const val APP_THEME_SYSTEM = "Match system"
        const val APP_THEME_LIGHT = "Light"
        const val APP_THEME_DARK = "Dark"

        /** History retention sentinel: keep forever (no time-based pruning). */
        const val RETENTION_FOREVER = 0
        const val RETENTION_DEFAULT_MINUTES = 60 // 1 hour
        /** Selectable retention windows, in minutes (1h / 1d / 1w / 30d / forever). */
        val RETENTION_OPTIONS = listOf(60, 1_440, 10_080, 43_200, RETENTION_FOREVER)
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<QuillSettings> = context.settingsDataStore.data.map { p ->
        val provider = SttProvider.fromId(p[Keys.PROVIDER])
        QuillSettings(
            provider = provider,
            sttModel = p[Keys.STT_MODEL] ?: provider.defaultModel,
            languageHint = p[Keys.LANGUAGE_HINT]?.takeIf { it.isNotBlank() } ?: QuillSettings.DEFAULT_LANGUAGE,
            cleanupEnabled = p[Keys.CLEANUP_ENABLED] ?: false,
            privateMode = p[Keys.PRIVATE_MODE] ?: false,
            historyEnabled = p[Keys.HISTORY_ENABLED] ?: true,
            historyRetentionMinutes = p[Keys.HISTORY_RETENTION_MINUTES] ?: QuillSettings.RETENTION_DEFAULT_MINUTES,
            sessionCapSeconds = p[Keys.SESSION_CAP_SECONDS] ?: 300,
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
            bubbleSize = p[Keys.BUBBLE_SIZE] ?: QuillSettings.BUBBLE_SIZE_MEDIUM,
            appTheme = p[Keys.APP_THEME] ?: QuillSettings.APP_THEME_SYSTEM,
            bootRestore = p[Keys.BOOT_RESTORE] ?: true,
            bubbleActive = p[Keys.BUBBLE_ACTIVE] ?: false,
            crashReportingEnabled = p[Keys.CRASH_REPORTING] ?: false,
            bubbleWasKilled = p[Keys.BUBBLE_WAS_KILLED] ?: false,
            copyUpdatesEnabled = p[Keys.COPY_UPDATES_ENABLED] ?: true,
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

    suspend fun setHistoryEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.HISTORY_ENABLED] = enabled }

    suspend fun setHistoryRetentionMinutes(minutes: Int) =
        context.settingsDataStore.edit { it[Keys.HISTORY_RETENTION_MINUTES] = minutes }

    suspend fun setSessionCapSeconds(seconds: Int) =
        context.settingsDataStore.edit { it[Keys.SESSION_CAP_SECONDS] = seconds }

    suspend fun setOnboardingComplete(complete: Boolean) =
        context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }

    suspend fun setBubbleSize(size: String) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_SIZE] = size }

    suspend fun setAppTheme(theme: String) =
        context.settingsDataStore.edit { it[Keys.APP_THEME] = theme }

    suspend fun setBootRestore(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.BOOT_RESTORE] = enabled }

    suspend fun setBubbleActive(active: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_ACTIVE] = active }

    suspend fun setCrashReportingEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.CRASH_REPORTING] = enabled }

    suspend fun setBubbleWasKilled(killed: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_WAS_KILLED] = killed }

    suspend fun setCopyUpdatesEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.COPY_UPDATES_ENABLED] = enabled }

    /** Full wipe: every preference reverts to its default (incl. onboarding). For "clear all data". */
    suspend fun clear() = context.settingsDataStore.edit { it.clear() }

    /**
     * Reset preferences to defaults but **keep** onboarding status, so resetting settings doesn't
     * bounce the user back through onboarding. For the "Reset settings to default" action.
     */
    suspend fun resetToDefaults() = context.settingsDataStore.edit { prefs ->
        val onboarded = prefs[Keys.ONBOARDING_COMPLETE]
        prefs.clear()
        if (onboarded != null) prefs[Keys.ONBOARDING_COMPLETE] = onboarded
    }

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val LANGUAGE_HINT = stringPreferencesKey("language_hint")
        val CLEANUP_ENABLED = booleanPreferencesKey("cleanup_enabled")
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HISTORY_RETENTION_MINUTES = intPreferencesKey("history_retention_minutes")
        val SESSION_CAP_SECONDS = intPreferencesKey("session_cap_seconds")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val BUBBLE_SIZE = stringPreferencesKey("bubble_size")
        val APP_THEME = stringPreferencesKey("app_theme")
        val BOOT_RESTORE = booleanPreferencesKey("boot_restore")
        val BUBBLE_ACTIVE = booleanPreferencesKey("bubble_active")
        val CRASH_REPORTING = booleanPreferencesKey("crash_reporting")
        val BUBBLE_WAS_KILLED = booleanPreferencesKey("bubble_was_killed")
        val COPY_UPDATES_ENABLED = booleanPreferencesKey("copy_updates_enabled")
    }
}
