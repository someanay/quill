package com.tihor.quill.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tihor.quill.style.WritingStyle
import com.tihor.quill.stt.OpenRouterTranscriptionClient
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
    /** Custom provider only: user-supplied OpenAI-compatible base URL (https, validated by [CustomEndpoint]). */
    val customBaseUrl: String = "",
    /** Custom provider only: user-supplied model id. Mirrored into [sttModel] while CUSTOM is active. */
    val customModel: String = "",
    val languageHint: String = DEFAULT_LANGUAGE, // ISO code, or LANGUAGE_AUTO for auto-detect
    /** LLM cleanup pass before insertion (M10). Opt-in: default OFF (extra latency + a second BYOK call). */
    val cleanupEnabled: Boolean = false,
    /**
     * Global writing-style default — the last tier of [com.tihor.quill.style.WritingStyleResolver],
     * used for any app with neither a user override nor a curated default. Ships Neutral, which
     * composes to the unmodified cleanup prompt, so the feature is inert until someone opts in.
     */
    val writingStyleDefault: WritingStyle = WritingStyle.DEFAULT,
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
    /**
     * True once the accessibility service has connected at least once. Distinguishes "was enabled,
     * now revoked" (show the recovery banner) from "never enabled" (a user who declined it in
     * onboarding must NOT see a revocation prompt). Set in [com.tihor.quill.accessibility.QuillAccessibilityService].
     */
    val accessibilityEnabledOnce: Boolean = false,
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
     * Whether Quill's overlay **service is desired** (alive). Set true when the bubble is first started
     * (Home toggle-on / onboarding); set false **only** by the explicit full-stop ("Dismiss bubble").
     * Gates M11 boot + foreground recovery — distinct from [bubbleVisible], so a service the user hid
     * (but didn't fully stop) is still restored after an OEM kill, then re-hidden.
     */
    val bubbleActive: Boolean = false,
    /**
     * The single source of truth for "is the bubble **shown vs hidden**" — the unified state the Home
     * master toggle, drag-to-dismiss, and the notification "Show Quill" action all read and write. true =
     * shown, false = hidden. Hiding (toggle-off or drag-dismiss) keeps the FGS + notification alive (≠
     * stop); only [bubbleActive]=false fully stops. Mirrored synchronously by [BubbleVisibilityStore] so
     * `addBubble` can read it inline on a cold-start/recovery restart (see [setBubbleVisible]).
     */
    val bubbleVisible: Boolean = true,
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
     * Provider ids whose stored key was probed as **free-tier-only** at key-save time (no paid
     * access). The model picker hides [ProviderModel.paidOnly] models for these providers.
     */
    val freeTierProviders: Set<String> = emptySet(),
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

    /** Synchronous on-disk mirror of [QuillSettings.bubbleVisible], kept in lockstep by [setBubbleVisible]. */
    private val visibilityMirror by lazy { BubbleVisibilityStore(context) }

    val settings: Flow<QuillSettings> = context.settingsDataStore.data.map { p ->
        val provider = SttProvider.fromId(p[Keys.PROVIDER])
        val storedModel = p[Keys.STT_MODEL]
        val activeModel = when {
            provider == SttProvider.OPENROUTER && storedModel == OpenRouterTranscriptionClient.LEGACY_DEFAULT_MODEL -> provider.defaultModel
            storedModel.isNullOrBlank() -> provider.defaultModel
            else -> storedModel
        }
        QuillSettings(
            provider = provider,
            sttModel = activeModel,
            customBaseUrl = p[Keys.CUSTOM_BASE_URL].orEmpty(),
            customModel = p[Keys.CUSTOM_MODEL].orEmpty(),
            languageHint = p[Keys.LANGUAGE_HINT]?.takeIf { it.isNotBlank() } ?: QuillSettings.DEFAULT_LANGUAGE,
            cleanupEnabled = p[Keys.CLEANUP_ENABLED] ?: false,
            writingStyleDefault = WritingStyle.fromId(p[Keys.WRITING_STYLE_DEFAULT]),
            privateMode = p[Keys.PRIVATE_MODE] ?: false,
            historyEnabled = p[Keys.HISTORY_ENABLED] ?: true,
            historyRetentionMinutes = p[Keys.HISTORY_RETENTION_MINUTES] ?: QuillSettings.RETENTION_DEFAULT_MINUTES,
            sessionCapSeconds = p[Keys.SESSION_CAP_SECONDS] ?: 300,
            onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
            accessibilityEnabledOnce = p[Keys.ACCESSIBILITY_ENABLED_ONCE] ?: false,
            bubbleSize = p[Keys.BUBBLE_SIZE] ?: QuillSettings.BUBBLE_SIZE_MEDIUM,
            appTheme = p[Keys.APP_THEME] ?: QuillSettings.APP_THEME_SYSTEM,
            bootRestore = p[Keys.BOOT_RESTORE] ?: true,
            bubbleActive = p[Keys.BUBBLE_ACTIVE] ?: false,
            bubbleVisible = p[Keys.BUBBLE_VISIBLE] ?: true,
            crashReportingEnabled = p[Keys.CRASH_REPORTING] ?: false,
            bubbleWasKilled = p[Keys.BUBBLE_WAS_KILLED] ?: false,
            freeTierProviders = p[Keys.FREE_TIER_PROVIDERS] ?: emptySet(),
        )
    }

    suspend fun setProvider(provider: SttProvider) = context.settingsDataStore.edit {
        it[Keys.PROVIDER] = provider.id
        // Reset the model to the new provider's default. CUSTOM's "default" is whatever the user
        // configured last (customModel), so switching away and back never loses it.
        it[Keys.STT_MODEL] =
            if (provider == SttProvider.CUSTOM) it[Keys.CUSTOM_MODEL].orEmpty() else provider.defaultModel
    }

    suspend fun setSttModel(model: String) = context.settingsDataStore.edit { it[Keys.STT_MODEL] = model }

    suspend fun setCustomBaseUrl(url: String) =
        context.settingsDataStore.edit { it[Keys.CUSTOM_BASE_URL] = url.trim() }

    /** Persist the Custom provider's model; keeps the active [QuillSettings.sttModel] in sync while on CUSTOM. */
    suspend fun setCustomModel(model: String) = context.settingsDataStore.edit {
        it[Keys.CUSTOM_MODEL] = model.trim()
        if (SttProvider.fromId(it[Keys.PROVIDER]) == SttProvider.CUSTOM) it[Keys.STT_MODEL] = model.trim()
    }

    /** Persist the spoken-language choice: an ISO code ("en", "hi") or [QuillSettings.LANGUAGE_AUTO]. */
    suspend fun setLanguageHint(code: String) = context.settingsDataStore.edit {
        if (code.isBlank()) it.remove(Keys.LANGUAGE_HINT) else it[Keys.LANGUAGE_HINT] = code
    }

    suspend fun setCleanupEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.CLEANUP_ENABLED] = enabled }

    /** Persist the global writing-style default (the resolver's last tier). Stored as [WritingStyle.id]. */
    suspend fun setWritingStyleDefault(style: WritingStyle) =
        context.settingsDataStore.edit { it[Keys.WRITING_STYLE_DEFAULT] = style.id }

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

    /** Record that the accessibility service has connected at least once (gates the revocation banner). */
    suspend fun setAccessibilityEnabledOnce(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.ACCESSIBILITY_ENABLED_ONCE] = enabled }

    suspend fun setBubbleSize(size: String) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_SIZE] = size }

    suspend fun setAppTheme(theme: String) =
        context.settingsDataStore.edit { it[Keys.APP_THEME] = theme }

    suspend fun setBootRestore(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.BOOT_RESTORE] = enabled }

    suspend fun setBubbleActive(active: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_ACTIVE] = active }

    /**
     * Set the unified bubble-visibility state. Writes the synchronous mirror **first**, then DataStore,
     * keeping the two in lockstep — this is the single writer of [QuillSettings.bubbleVisible]. The Home
     * toggle, drag-dismiss, and notification-summon all funnel through here; the
     * [com.tihor.quill.overlay.OverlayService] observes the Flow and the Home screen reflects it, so all
     * three entry points stay in sync live.
     */
    suspend fun setBubbleVisible(visible: Boolean) {
        visibilityMirror.visible = visible
        context.settingsDataStore.edit { it[Keys.BUBBLE_VISIBLE] = visible }
    }

    suspend fun setCrashReportingEnabled(enabled: Boolean) =
        context.settingsDataStore.edit { it[Keys.CRASH_REPORTING] = enabled }

    suspend fun setBubbleWasKilled(killed: Boolean) =
        context.settingsDataStore.edit { it[Keys.BUBBLE_WAS_KILLED] = killed }

    /**
     * Record the key-save probe's verdict on paid access for [provider] (see
     * `KeyValidator.hasPaidAccess`; callers skip this entirely on an inconclusive probe). Marking a
     * provider free-tier-only also deselects a now-hidden paid-only model, falling back to the
     * provider default so the active model is never one the picker refuses to show.
     */
    suspend fun setFreeTierOnly(provider: SttProvider, freeOnly: Boolean) =
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.FREE_TIER_PROVIDERS] ?: emptySet()
            prefs[Keys.FREE_TIER_PROVIDERS] = if (freeOnly) current + provider.id else current - provider.id
            if (freeOnly && SttProvider.fromId(prefs[Keys.PROVIDER]) == provider &&
                provider.models.any { it.paidOnly && it.id == prefs[Keys.STT_MODEL] }
            ) {
                prefs[Keys.STT_MODEL] = provider.defaultModel
            }
        }

    /** Full wipe: every preference reverts to its default (incl. onboarding). For "clear all data". */
    suspend fun clear() {
        visibilityMirror.visible = true // keep the mirror in lockstep with the DataStore default
        context.settingsDataStore.edit { it.clear() }
    }

    /**
     * Reset preferences to defaults but **keep** onboarding status, so resetting settings doesn't
     * bounce the user back through onboarding. For the "Reset settings to default" action.
     */
    suspend fun resetToDefaults() {
        visibilityMirror.visible = true // keep the mirror in lockstep with the DataStore default
        context.settingsDataStore.edit { prefs ->
            val onboarded = prefs[Keys.ONBOARDING_COMPLETE]
            val a11yEnabledOnce = prefs[Keys.ACCESSIBILITY_ENABLED_ONCE]
            prefs.clear()
            if (onboarded != null) prefs[Keys.ONBOARDING_COMPLETE] = onboarded
            // Historical fact, not a preference — a settings reset must not resurrect the revocation
            // banner for a user who legitimately had accessibility enabled.
            if (a11yEnabledOnce != null) prefs[Keys.ACCESSIBILITY_ENABLED_ONCE] = a11yEnabledOnce
        }
    }

    private object Keys {
        val PROVIDER = stringPreferencesKey("provider")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val LANGUAGE_HINT = stringPreferencesKey("language_hint")
        val CLEANUP_ENABLED = booleanPreferencesKey("cleanup_enabled")
        val WRITING_STYLE_DEFAULT = stringPreferencesKey("writing_style_default")
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val HISTORY_RETENTION_MINUTES = intPreferencesKey("history_retention_minutes")
        val SESSION_CAP_SECONDS = intPreferencesKey("session_cap_seconds")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ACCESSIBILITY_ENABLED_ONCE = booleanPreferencesKey("accessibility_enabled_once")
        val BUBBLE_SIZE = stringPreferencesKey("bubble_size")
        val APP_THEME = stringPreferencesKey("app_theme")
        val BOOT_RESTORE = booleanPreferencesKey("boot_restore")
        val BUBBLE_ACTIVE = booleanPreferencesKey("bubble_active")
        val BUBBLE_VISIBLE = booleanPreferencesKey("bubble_visible")
        val CRASH_REPORTING = booleanPreferencesKey("crash_reporting")
        val BUBBLE_WAS_KILLED = booleanPreferencesKey("bubble_was_killed")
        val FREE_TIER_PROVIDERS = stringSetPreferencesKey("free_tier_providers")
    }
}
