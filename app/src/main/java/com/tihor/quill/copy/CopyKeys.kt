package com.tihor.quill.copy

/**
 * Typed copy keys (spec §2.3). Every user-facing string is read by one of these from the Copy
 * Catalog — no string literals in composables (CI guard enforces it).
 *
 * Compliance keys are excluded from source-editor changes. Every string is bundled
 * in the APK; no remote override mechanism exists.
 */
object CopyKeys {
    // --- Onboarding (overridable) ---
    const val SPLASH_TAGLINE = "onboarding.splash.tagline"
    const val WELCOME_TITLE = "onboarding.welcome.title"
    const val WELCOME_BODY = "onboarding.welcome.body"
    const val WELCOME_CTA = "onboarding.welcome.cta"
    const val WELCOME_ALREADY = "onboarding.welcome.already"
    const val WELCOME_OPEN = "onboarding.welcome.open"

    const val KEY_WARNING_FREE_TIER = "onboarding.key.warning.free_tier"
    const val KEY_WARNING_USAGE = "onboarding.key.warning.usage"
    const val KEY_WARNING_READING = "onboarding.key.warning.reading"
    const val KEY_WARNING_ACCEPT = "onboarding.key.warning.accept"

    const val HOWITWORKS_EYEBROW = "onboarding.howitworks.eyebrow"
    const val HOWITWORKS_TITLE = "onboarding.howitworks.title"
    const val HOWITWORKS_T1 = "onboarding.howitworks.t1"
    const val HOWITWORKS_T2 = "onboarding.howitworks.t2"
    const val HOWITWORKS_T3 = "onboarding.howitworks.t3"
    const val HOWITWORKS_STEP1 = "onboarding.howitworks.step1"
    const val HOWITWORKS_STEP2 = "onboarding.howitworks.step2"
    const val HOWITWORKS_STEP3 = "onboarding.howitworks.step3"
    const val HOWITWORKS_CTA = "onboarding.howitworks.cta"

    const val CHECKLIST_TITLE = "onboarding.checklist.title"
    const val CHECKLIST_PROGRESS = "onboarding.checklist.progress"
    const val CHECKLIST_KEY = "onboarding.checklist.key"
    const val CHECKLIST_KEY_SUB = "onboarding.checklist.key_sub"
    const val CHECKLIST_MIC = "onboarding.checklist.mic"
    const val CHECKLIST_MIC_SUB = "onboarding.checklist.mic_sub"
    const val CHECKLIST_OVERLAY = "onboarding.checklist.overlay"
    const val CHECKLIST_OVERLAY_SUB = "onboarding.checklist.overlay_sub"
    const val CHECKLIST_ACCESSIBILITY = "onboarding.checklist.accessibility"
    const val CHECKLIST_ACC_SUB = "onboarding.checklist.accessibility_sub"
    const val CHECKLIST_CTA = "onboarding.checklist.cta"

    const val KEY_TITLE = "onboarding.key.title"
    const val KEY_BODY = "onboarding.key.body"
    const val KEY_FIELD_LABEL = "onboarding.key.field_label"
    const val KEY_CTA = "onboarding.key.cta"
    const val KEY_WHERE = "onboarding.key.where_link"
    const val KEY_CONSOLE_BODY = "onboarding.key.console_body"
    const val KEY_CONSOLE_FREE = "onboarding.key.console_free"
    // Per-provider "how do I get this key" guide (shown before opening the provider's console).
    const val KEY_GUIDE_TITLE = "onboarding.key.guide.title"
    const val KEY_GUIDE_OPEN = "onboarding.key.guide.open"
    const val KEY_GUIDE_GROQ_STEPS = "onboarding.key.guide.groq_steps"
    const val KEY_GUIDE_OPENAI_STEPS = "onboarding.key.guide.openai_steps"
    const val KEY_GUIDE_OPENROUTER_STEPS = "onboarding.key.guide.openrouter_steps"
    const val KEY_GUIDE_GEMINI_STEPS = "onboarding.key.guide.gemini_steps"
    const val KEY_VERIFYING = "onboarding.key.verifying"
    const val KEY_VALID = "onboarding.key.valid"
    const val KEY_ERROR_INVALID = "key.error.invalid"
    const val KEY_ERROR_NETWORK = "key.error.network"

    const val MIC_TITLE = "onboarding.mic.title"
    const val MIC_CTA = "onboarding.mic.cta"
    const val MICTEST_TITLE = "onboarding.mictest.title"
    const val MICTEST_BODY = "onboarding.mictest.body"
    const val MICTEST_CTA = "onboarding.mictest.cta"
    const val MICTEST_HEARD = "onboarding.mictest.heard"

    const val OVERLAY_TITLE = "onboarding.overlay.title"
    const val OVERLAY_CTA = "onboarding.overlay.cta"

    const val ACC_ENABLE_TITLE = "onboarding.accessibility.enable_title"
    const val ACC_ENABLE_CTA = "onboarding.accessibility.enable_cta"

    const val ALLSET_TITLE = "onboarding.allset.title"
    const val ALLSET_BODY = "onboarding.allset.body"
    const val ALLSET_CTA = "onboarding.allset.cta"
    const val ALLSET_LOOK = "onboarding.allset.look"
    const val ALLSET_HOME_LINK = "onboarding.allset.home_link"

    const val ACTION_NOT_NOW = "action.not_now"
    const val ACTION_CONTINUE = "action.continue"
    const val ACTION_BACK = "action.back"
    const val ACTION_CANCEL = "action.cancel"
    const val STATUS_DONE = "status.done"

    // --- Navigation tab labels (overridable) ---
    const val NAV_HOME = "nav.home"
    const val NAV_HISTORY = "nav.history"
    const val NAV_SETTINGS = "nav.settings"

    // --- Accessibility labels (icon contentDescription, overridable) ---
    const val A11Y_BACK = "a11y.back"
    const val A11Y_SETTINGS = "a11y.settings"
    const val A11Y_SELECTED = "a11y.selected"
    const val A11Y_SHOW_KEY = "a11y.show_key"
    const val A11Y_HIDE_KEY = "a11y.hide_key"

    // --- Common (overridable) ---
    const val COMMON_COMING_SOON = "common.coming_soon"

    // --- History (M9, overridable) ---
    const val HISTORY_TITLE = "history.title"
    const val HISTORY_CLEAR_ALL = "history.clear_all"
    const val HISTORY_GROUP_TODAY = "history.group.today"
    const val HISTORY_GROUP_YESTERDAY = "history.group.yesterday"
    const val HISTORY_EMPTY_ON_TITLE = "history.empty.on_title"
    const val HISTORY_EMPTY_ON_BODY = "history.empty.on_body"
    const val HISTORY_EMPTY_ON_CTA = "history.empty.on_cta"
    const val HISTORY_EMPTY_OFF_TITLE = "history.empty.off_title"
    const val HISTORY_EMPTY_OFF_BODY = "history.empty.off_body"
    const val HISTORY_EMPTY_OFF_CTA = "history.empty.off_cta"
    const val HISTORY_SHEET_COPY = "history.sheet.copy"
    const val HISTORY_SHEET_SHARE = "history.sheet.share"
    const val HISTORY_SHEET_DELETE = "history.sheet.delete"
    const val HISTORY_CLEAR_TITLE = "history.clear.title"
    const val HISTORY_CLEAR_BODY = "history.clear.body"
    const val HISTORY_CLEAR_CONFIRM = "history.clear.confirm"

    // --- Home (overridable) ---
    const val HOME_BANNER_A11Y_OFF_TEXT = "home.banner.a11y_off_text"
    const val HOME_GROUP_TODAY = "home.group.today"
    const val HOME_TODAY_DICTATIONS = "home.today.dictations"
    const val HOME_TODAY_DICTATIONS_SUB = "home.today.dictations_sub"
    const val HOME_TODAY_LAST_USED = "home.today.last_used"
    const val HOME_TODAY_LAST_USED_SUB = "home.today.last_used_sub"
    const val HOME_GROUP_TIP = "home.group.tip"
    const val HOME_TIP_TITLE = "home.tip.title"
    const val HOME_TIP_TEXT = "home.tip.text"
    const val HOME_PAUSED_BANNER_TITLE = "home.paused.banner_title"
    const val HOME_PAUSED_BANNER_TEXT = "home.paused.banner_text"
    const val HOME_PAUSED_FOOTER = "home.paused.footer"
    const val HOME_TOGGLE_ON_TITLE = "home.toggle.on_title"
    const val HOME_TOGGLE_OFF_TITLE = "home.toggle.off_title"
    const val HOME_TOGGLE_ON_SUB = "home.toggle.on_sub"
    const val HOME_TOGGLE_OFF_SUB = "home.toggle.off_sub"

    // --- Settings (overridable) ---
    const val SETTINGS_TITLE = "settings.title"
    const val SETTINGS_GROUP_DICTATION = "settings.group.dictation"
    const val SETTINGS_GROUP_CAPTURE = "settings.group.capture"
    const val SETTINGS_GROUP_PRIVACY = "settings.group.privacy"
    const val SETTINGS_ROW_SPEECH_MODEL = "settings.row.speech_model"
    const val SETTINGS_ROW_API_KEYS = "settings.row.api_keys"
    const val SETTINGS_ROW_CLEANUP = "settings.row.cleanup"
    const val SETTINGS_ROW_CUSTOM_WORDS = "settings.row.custom_words"
    const val SETTINGS_ROW_WRITING_STYLE = "settings.row.writing_style"
    const val SETTINGS_KEYS_SET = "settings.keys.set"
    const val SETTINGS_ROW_AUDIO = "settings.row.audio"
    const val SETTINGS_ROW_BUBBLE = "settings.row.bubble"
    const val SETTINGS_ROW_PRIVACY = "settings.row.privacy"
    const val SETTINGS_ROW_ADVANCED = "settings.row.advanced"
    const val SETTINGS_ROW_ABOUT = "settings.row.about"

    const val SETTINGS_SPEECH_TITLE = "settings.speech.title"
    const val SETTINGS_SPEECH_PROVIDER = "settings.speech.provider"
    const val SETTINGS_SPEECH_PROVIDER_CUSTOM = "settings.speech.provider_custom"
    const val SETTINGS_SPEECH_MODEL = "settings.speech.model"
    const val SETTINGS_MODEL_TURBO_SUB = "settings.speech.model_turbo_sub"
    const val SETTINGS_MODEL_ACCURATE_SUB = "settings.speech.model_accurate_sub"
    const val SETTINGS_SPEECH_LANGUAGE = "settings.speech.language"
    const val SETTINGS_LANG_EN = "settings.speech.lang_en"
    const val SETTINGS_LANG_AUTO = "settings.speech.lang_auto"
    const val SETTINGS_LANG_HI = "settings.speech.lang_hi"
    const val SETTINGS_LANGUAGE_HELP = "settings.speech.language_help"
    // Multi-provider picker (decision 1/4d/6)
    const val SETTINGS_MODEL_OPENAI_WHISPER_SUB = "settings.speech.model_openai_whisper_sub"
    const val SETTINGS_MODEL_OPENAI_MINI_SUB = "settings.speech.model_openai_mini_sub"
    const val SETTINGS_MODEL_OPENAI_4O_SUB = "settings.speech.model_openai_4o_sub"
    const val SETTINGS_MODEL_OPENROUTER_WHISPER_TURBO_SUB = "settings.speech.model_openrouter_whisper_turbo_sub"
    const val SETTINGS_MODEL_OPENROUTER_VOXTRAL_SUB = "settings.speech.model_openrouter_voxtral_sub"
    const val SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_MINI_SUB = "settings.speech.model_openrouter_gpt_audio_mini_sub"
    const val SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_SUB = "settings.speech.model_openrouter_gpt_audio_sub"
    const val SETTINGS_MODEL_OPENROUTER_GEMINI_FLASH_SUB = "settings.speech.model_openrouter_gemini_flash_sub"
    const val SETTINGS_MODEL_OPENROUTER_GEMINI_LITE_SUB = "settings.speech.model_openrouter_gemini_lite_sub"
    const val SETTINGS_MODEL_OPENROUTER_NEMOTRON_SUB = "settings.speech.model_openrouter_nemotron_sub"
    const val SETTINGS_MODEL_OPENROUTER_MIMO_SUB = "settings.speech.model_openrouter_mimo_sub"
    const val SETTINGS_MODEL_GEMINI_LITE_SUB = "settings.speech.model_gemini_lite_sub"
    const val SETTINGS_MODEL_GEMINI_FLASH_SUB = "settings.speech.model_gemini_flash_sub"
    const val SETTINGS_MODEL_GEMINI_PREVIEW_SUB = "settings.speech.model_gemini_preview_sub"
    const val SETTINGS_MODEL_GEMINI_PRO_SUB = "settings.speech.model_gemini_pro_sub"
    const val SETTINGS_SPEECH_GEMINI_CAVEAT = "settings.speech.gemini_caveat"
    const val SETTINGS_SPEECH_KEY_REQUIRED = "settings.speech.key_required"
    const val SETTINGS_SPEECH_KEY_REQUIRED_CTA = "settings.speech.key_required_cta"
    const val SETTINGS_SPEECH_MODEL_FIELD_LABEL = "settings.speech.model_field_label"
    const val SETTINGS_SPEECH_MODEL_FIELD_HINT = "settings.speech.model_field_hint"
    const val SETTINGS_SPEECH_CUSTOM_URL_LABEL = "settings.speech.custom_url_label"
    const val SETTINGS_SPEECH_CUSTOM_URL_HINT = "settings.speech.custom_url_hint"
    const val SETTINGS_SPEECH_CUSTOM_URL_ERROR = "settings.speech.custom_url_error"
    const val SETTINGS_SPEECH_CUSTOM_URL_HELP = "settings.speech.custom_url_help"

    const val SETTINGS_KEYS_TITLE = "settings.keys.title"
    const val SETTINGS_KEYS_EMPTY_TITLE = "settings.keys.empty_title"
    const val SETTINGS_KEYS_EMPTY_BODY = "settings.keys.empty_body"
    const val SETTINGS_KEYS_ADD = "settings.keys.add"
    const val SETTINGS_KEYS_BANNER = "settings.keys.banner"
    const val SETTINGS_KEYS_SECTION = "settings.keys.section"
    const val SETTINGS_KEYS_ROLE_STT = "settings.keys.role_stt"
    const val SETTINGS_KEYS_HELP = "settings.keys.help"
    const val SETTINGS_KEYS_REPLACE = "settings.keys.replace"
    const val SETTINGS_KEYS_TEST = "settings.keys.test"
    const val SETTINGS_KEYS_REMOVE = "settings.keys.remove"

    const val SETTINGS_CLEANUP_TITLE = "settings.cleanup.title"
    const val SETTINGS_CLEANUP_TIDY_TITLE = "settings.cleanup.tidy_title"
    const val SETTINGS_CLEANUP_TIDY_SUB = "settings.cleanup.tidy_sub"
    const val SETTINGS_CLEANUP_WHAT = "settings.cleanup.what"
    const val SETTINGS_CLEANUP_BEFORE = "settings.cleanup.before"
    const val SETTINGS_CLEANUP_AFTER = "settings.cleanup.after"
    const val SETTINGS_CLEANUP_MODEL = "settings.cleanup.model"
    const val SETTINGS_CLEANUP_HELP = "settings.cleanup.provider_help"
    const val SETTINGS_CLEANUP_CUSTOM = "settings.cleanup.custom_endpoint"
    const val CLEANUP_AUTO = "settings.cleanup.automatic"
    const val CLEANUP_AUTO_HELP = "settings.cleanup.automatic_help"
    const val KEY_ACTIVE = "settings.keys.active"
    const val KEY_USE = "settings.keys.use"
    // Decision 5: cleanup is Groq-only — status lines when a non-Groq STT provider is active.
    const val SETTINGS_CLEANUP_GROQ_KEY = "settings.cleanup.groq_key"
    const val SETTINGS_CLEANUP_GROQ_MISSING = "settings.cleanup.groq_missing"

    // Per-app writing style (rides on the cleanup pass; all overridable — none of this is compliance
    // copy. The privacy/permission/BYOK promises stay locked to the binary.)
    const val SETTINGS_STYLE_TITLE = "settings.style.title"
    const val SETTINGS_STYLE_INTRO = "settings.style.intro"
    const val SETTINGS_STYLE_DEFAULT_SECTION = "settings.style.default_section"
    const val SETTINGS_STYLE_PER_APP_SECTION = "settings.style.per_app_section"
    const val SETTINGS_STYLE_FORMAL = "settings.style.formal"
    const val SETTINGS_STYLE_FORMAL_SUB = "settings.style.formal_sub"
    const val SETTINGS_STYLE_NEUTRAL = "settings.style.neutral"
    const val SETTINGS_STYLE_NEUTRAL_SUB = "settings.style.neutral_sub"
    const val SETTINGS_STYLE_CASUAL = "settings.style.casual"
    const val SETTINGS_STYLE_CASUAL_SUB = "settings.style.casual_sub"
    const val SETTINGS_STYLE_SEARCH_HINT = "settings.style.search_hint"
    const val SETTINGS_STYLE_ASSIGN_TITLE = "settings.style.assign_title"
    const val SETTINGS_STYLE_USE_DEFAULT = "settings.style.use_default"
    const val SETTINGS_STYLE_LOADING = "settings.style.loading"
    const val SETTINGS_STYLE_NO_RESULTS = "settings.style.no_results"
    // §7 availability: styling rides on the cleanup pass, so it does nothing without cleanup ON and a
    // Groq key. Surfaced rather than left silent — a feature that never fires reads as broken.
    const val SETTINGS_STYLE_NEEDS_CLEANUP = "settings.style.needs_cleanup"
    const val SETTINGS_STYLE_NEEDS_GROQ = "settings.style.needs_groq"
    const val SETTINGS_STYLE_WORDS_PROMISE = "settings.style.words_promise"
    const val SETTINGS_STYLE_HELP = "settings.style.help"

    const val SETTINGS_WORDS_TITLE = "settings.words.title"
    const val SETTINGS_WORDS_BODY = "settings.words.body"
    const val SETTINGS_WORDS_EMPTY_TITLE = "settings.words.empty_title"
    const val SETTINGS_WORDS_EMPTY_SUB = "settings.words.empty_sub"
    const val SETTINGS_WORDS_ADD = "settings.words.add"
    const val SETTINGS_WORDS_ADD_TITLE = "settings.words.add_title"
    const val SETTINGS_WORDS_HEARD_LABEL = "settings.words.heard_label"
    const val SETTINGS_WORDS_HEARD_HINT = "settings.words.heard_hint"
    const val SETTINGS_WORDS_REPLACEMENT_LABEL = "settings.words.replacement_label"
    const val SETTINGS_WORDS_REPLACEMENT_HINT = "settings.words.replacement_hint"
    const val SETTINGS_WORDS_SAVE = "settings.words.save"
    const val SETTINGS_WORDS_DELETE = "settings.words.delete"
    const val SETTINGS_WORDS_COUNT = "settings.words.count"
    const val SETTINGS_WORDS_RECORD = "settings.words.record"
    const val SETTINGS_WORDS_LISTENING = "settings.words.listening"
    const val SETTINGS_WORDS_TRANSCRIBING = "settings.words.transcribing"
    const val SETTINGS_WORDS_ERR_RECORD = "settings.words.err_record"
    const val SETTINGS_WORDS_ERR_KEY = "settings.words.err_key"
    const val SETTINGS_WORDS_ERR_NETWORK = "settings.words.err_network"
    const val SETTINGS_WORDS_ERR_PERMISSION = "settings.words.err_permission"

    // Contacts-imported vocabulary (Phase 1, feature #6 re-scoped: contacts only). Opt-in import,
    // on-device only; separate store from custom words.
    const val SETTINGS_ROW_CONTACTS_VOCAB = "settings.row.contacts_vocab"
    const val SETTINGS_VOCAB_TITLE = "settings.vocab.title"
    const val SETTINGS_VOCAB_BODY = "settings.vocab.body"
    const val SETTINGS_VOCAB_COUNT = "settings.vocab.count"
    const val SETTINGS_VOCAB_IMPORT = "settings.vocab.import"
    const val SETTINGS_VOCAB_IMPORTING = "settings.vocab.importing"
    const val SETTINGS_VOCAB_REIMPORT = "settings.vocab.reimport"
    const val SETTINGS_VOCAB_IMPORTED = "settings.vocab.imported"
    const val SETTINGS_VOCAB_IMPORT_FAILED = "settings.vocab.import_failed"
    const val SETTINGS_VOCAB_EMPTY_TITLE = "settings.vocab.empty_title"
    const val SETTINGS_VOCAB_EMPTY_SUB = "settings.vocab.empty_sub"
    const val SETTINGS_VOCAB_PERMISSION_NEEDED = "settings.vocab.permission_needed"
    const val SETTINGS_VOCAB_OPEN_SETTINGS = "settings.vocab.open_settings"
    const val SETTINGS_VOCAB_CLEAR = "settings.vocab.clear"
    const val SETTINGS_VOCAB_CLEAR_CONFIRM_TITLE = "settings.vocab.clear_confirm_title"
    const val SETTINGS_VOCAB_CLEAR_CONFIRM_BODY = "settings.vocab.clear_confirm_body"
    const val SETTINGS_VOCAB_CLEAR_CONFIRM_CTA = "settings.vocab.clear_confirm_cta"

    const val SETTINGS_AUDIO_TITLE = "settings.audio.title"
    const val SETTINGS_AUDIO_START_STOP = "settings.audio.start_stop"
    const val SETTINGS_AUDIO_TRIGGER = "settings.audio.trigger"
    const val SETTINGS_AUDIO_TRIGGER_SUB = "settings.audio.trigger_sub"
    const val SETTINGS_AUDIO_TRIGGER_VALUE = "settings.audio.trigger_value"
    const val SETTINGS_AUDIO_HOLD = "settings.audio.hold"
    const val SETTINGS_AUDIO_HOLD_SUB = "settings.audio.hold_sub"
    const val SETTINGS_AUDIO_MAX_LENGTH = "settings.audio.max_length"
    const val SETTINGS_AUDIO_AUTOSTOP = "settings.audio.autostop"
    const val SETTINGS_AUDIO_SILENCE = "settings.audio.silence"
    const val SETTINGS_AUDIO_SILENCE_SUB = "settings.audio.silence_sub"
    const val SETTINGS_AUDIO_SILENCE_BANNER = "settings.audio.silence_banner"
    const val SETTINGS_AUDIO_FORMAT = "settings.audio.format"
    const val SETTINGS_AUDIO_FORMAT_SUB = "settings.audio.format_sub"
    const val SETTINGS_AUDIO_FORMAT_VALUE = "settings.audio.format_value"

    const val SETTINGS_PRIVACY_TITLE = "settings.privacy.title"
    const val SETTINGS_PRIVACY_BANNER_TITLE = "settings.privacy.banner_title"
    const val SETTINGS_PRIVACY_BANNER_TEXT = "settings.privacy.banner_text"
    const val SETTINGS_PRIVACY_PRIVATE_MODE = "settings.privacy.private_mode"
    const val SETTINGS_PRIVACY_PRIVATE_MODE_SUB = "settings.privacy.private_mode_sub"
    const val SETTINGS_PRIVACY_HISTORY = "settings.privacy.history"
    const val SETTINGS_PRIVACY_HISTORY_SUB = "settings.privacy.history_sub"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION = "settings.privacy.history_retention"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION_HOUR = "settings.privacy.history_retention.hour"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION_DAY = "settings.privacy.history_retention.day"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION_WEEK = "settings.privacy.history_retention.week"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION_MONTH = "settings.privacy.history_retention.month"
    const val SETTINGS_PRIVACY_HISTORY_RETENTION_FOREVER = "settings.privacy.history_retention.forever"
    const val SETTINGS_PRIVACY_DIAGNOSTICS = "settings.privacy.diagnostics"
    const val SETTINGS_PRIVACY_CRASH = "settings.privacy.crash"
    const val SETTINGS_PRIVACY_CRASH_SUB = "settings.privacy.crash_sub"
    const val SETTINGS_PRIVACY_EXAMPLE = "settings.privacy.example"
    const val SETTINGS_PRIVACY_EXAMPLE_SUB = "settings.privacy.example_sub"
    const val SETTINGS_PRIVACY_DIAG_HELP = "settings.privacy.diag_help"
    const val SETTINGS_PRIVACY_PERMISSIONS = "settings.privacy.permissions"
    const val SETTINGS_PRIVACY_MIC = "settings.privacy.mic"
    const val SETTINGS_PRIVACY_ACCESSIBILITY = "settings.privacy.accessibility"

    const val SETTINGS_BUBBLE_TITLE = "settings.bubble.title"
    const val SETTINGS_BUBBLE_HELP = "settings.bubble.help"
    const val SETTINGS_BUBBLE_PREVIEW_LIGHT = "settings.bubble.preview_light"
    const val SETTINGS_BUBBLE_PREVIEW_DARK = "settings.bubble.preview_dark"
    const val SETTINGS_BUBBLE_SIZE = "settings.bubble.size"
    const val SETTINGS_BUBBLE_THEME = "settings.bubble.theme"

    const val SETTINGS_ADVANCED_TITLE = "settings.advanced.title"
    const val SETTINGS_ADVANCED_ENDPOINT = "settings.advanced.endpoint"
    const val SETTINGS_ADVANCED_ENDPOINT_HELP = "settings.advanced.endpoint_help"
    const val SETTINGS_ADVANCED_TIMEOUT = "settings.advanced.timeout"
    const val SETTINGS_ADVANCED_TIMEOUT_SUB = "settings.advanced.timeout_sub"
    const val SETTINGS_ADVANCED_TIMEOUT_VALUE = "settings.advanced.timeout_value"
    const val SETTINGS_ADVANCED_KEEPALIVE = "settings.advanced.keepalive"
    const val SETTINGS_ADVANCED_KEEPALIVE_SUB = "settings.advanced.keepalive_sub"
    const val SETTINGS_ADVANCED_BATTERY = "settings.advanced.battery"
    const val SETTINGS_ADVANCED_BATTERY_SUB = "settings.advanced.battery_sub"
    const val SETTINGS_ADVANCED_RESET = "settings.advanced.reset"
    const val SETTINGS_ADVANCED_RESET_SETTINGS = "settings.advanced.reset_settings"
    const val SETTINGS_ADVANCED_CLEAR = "settings.advanced.clear"
    const val SETTINGS_ADVANCED_CLEAR_CONFIRM_TITLE = "settings.advanced.clear_confirm_title"
    const val SETTINGS_ADVANCED_CLEAR_CONFIRM_BODY = "settings.advanced.clear_confirm_body"
    const val SETTINGS_ADVANCED_CLEAR_CONFIRM_CTA = "settings.advanced.clear_confirm_cta"
    const val SETTINGS_ADVANCED_RESET_CONFIRM_TITLE = "settings.advanced.reset_confirm_title"
    const val SETTINGS_ADVANCED_RESET_CONFIRM_BODY = "settings.advanced.reset_confirm_body"
    const val SETTINGS_ADVANCED_RESET_CONFIRM_CTA = "settings.advanced.reset_confirm_cta"

    const val SETTINGS_ABOUT_TITLE = "settings.about.title"
    const val SETTINGS_ABOUT_VERSION = "settings.about.version"
    const val SETTINGS_ABOUT_PRIVACY_POLICY = "settings.about.privacy_policy"
    const val SETTINGS_ABOUT_TERMS = "settings.about.terms"
    const val SETTINGS_ABOUT_LICENSES = "settings.about.licenses"
    const val SETTINGS_ABOUT_FEEDBACK = "settings.about.feedback"
    const val SETTINGS_ABOUT_FOOTER = "settings.about.footer"

    // --- System state (overridable; recovery title shared with Home) ---
    const val SYSTEM_RECOVERY_TITLE = "system.recovery.title"
    const val SYSTEM_RECOVERY_BANNER_TEXT = "system.recovery.banner_text"
    const val SYSTEM_RECOVERY_HEADING = "system.recovery.heading"
    const val SYSTEM_RECOVERY_BODY = "system.recovery.body"
    const val SYSTEM_RECOVERY_CTA = "system.recovery.cta"
    const val SYSTEM_RECOVERY_WHY = "system.recovery.why"
    const val SYSTEM_CRASH_TITLE = "system.crash.title"
    const val SYSTEM_CRASH_BODY = "system.crash.body"
    const val SYSTEM_CRASH_BANNER = "system.crash.banner"
    const val SYSTEM_CRASH_ALWAYS = "system.crash.always"
    const val SYSTEM_CRASH_SEND = "system.crash.send"
    const val SYSTEM_CRASH_DONT_SEND = "system.crash.dont_send"

    // --- M11 reliability / OEM survival (overridable) ---
    const val RELIABILITY_BOOT_CHANNEL = "reliability.boot.channel"
    const val RELIABILITY_BOOT_TITLE = "reliability.boot.title"
    const val RELIABILITY_BOOT_TEXT = "reliability.boot.text"
    const val INJECT_FAILED_TITLE = "inject.failed.title"
    const val INJECT_FAILED_TEXT = "inject.failed.text"
    const val INJECT_REVOKED_TITLE = "inject.revoked.title"
    const val INJECT_REVOKED_TEXT = "inject.revoked.text"
    const val INJECT_REENABLE_ACTION = "inject.reenable.action"
    const val HOME_BATTERY_TITLE = "home.battery.title"
    const val HOME_BATTERY_TEXT = "home.battery.text"
    const val SETTINGS_SURVIVAL_ROW = "settings.survival.row"
    const val SETTINGS_SURVIVAL_TITLE = "settings.survival.title"
    const val SETTINGS_SURVIVAL_BODY = "settings.survival.body"
    const val SETTINGS_SURVIVAL_STEPS = "settings.survival.steps"
    const val SETTINGS_SURVIVAL_OPEN = "settings.survival.open"
    const val SETTINGS_SURVIVAL_FALLBACK = "settings.survival.fallback"

    // --- STT failure feedback (overridable) ---
    const val STT_QUOTA_TOAST = "stt.quota.toast"

    // --- Overlay bubble + FGS notification (overridable) ---
    const val NOTIFICATION_ACTION_SHOW = "notification.action.show"
    const val NOTIFICATION_ACTION_STOP = "notification.action.stop"
    const val NOTIFICATION_TITLE_ACTIVE = "notification.title.active"
    const val NOTIFICATION_TEXT_ACTIVE = "notification.text.active"
    const val NOTIFICATION_TITLE_HIDDEN = "notification.title.hidden"
    const val NOTIFICATION_TEXT_HIDDEN = "notification.text.hidden"
    const val NOTIFICATION_TITLE_RECORDING = "notification.title.recording"
    const val NOTIFICATION_TEXT_RECORDING = "notification.text.recording"
    const val BUBBLE_DISMISS_TOAST = "bubble.dismiss.toast"
    // Drag-to-dismiss bin target (v1.1): the caption under the bin, resting vs "release now".
    const val BUBBLE_BIN_LABEL = "bubble.bin.label"
    const val BUBBLE_BIN_LABEL_ACTIVE = "bubble.bin.label_active"
    // Shown when the ~10-minute auto-return brings the bubble back on its own, so its reappearance
    // reads as designed behaviour rather than the hide having failed.
    const val BUBBLE_AUTORETURN_TOAST = "bubble.autoreturn.toast"

    // --- Demo-video carousel (overridable) ---
    // Home section header (baked default "See Quill in action"). Onboarding surfaces carry no header.
    const val CAROUSEL_HOME_HEADER = "carousel.home.header"
    // Generic accessibility fallbacks when a clip has no caption.
    const val CAROUSEL_CARD_A11Y = "carousel.card.a11y"
    const val CAROUSEL_PLAY_A11Y = "carousel.play.a11y"

    /**
     * Up to 6 per-clip caption keys per surface (`carousel.<surface>.<1..6>.caption`), pre-registered
     * so a clip can carry a caption without a code change. Their baked defaults are **empty** — an
     * empty caption renders no caption row ([OPTIONAL_BLANK] exempts them from the non-blank check).
     */
    val CAROUSEL_CAPTIONS_HOME: List<String> = (1..6).map { "carousel.home.$it.caption" }
    val CAROUSEL_CAPTIONS_ACCESSIBILITY: List<String> = (1..6).map { "carousel.accessibility.$it.caption" }
    val CAROUSEL_CAPTIONS_KEY: List<String> = (1..6).map { "carousel.key.$it.caption" }
    val CAROUSEL_CAPTIONS: List<String> =
        CAROUSEL_CAPTIONS_HOME + CAROUSEL_CAPTIONS_ACCESSIBILITY + CAROUSEL_CAPTIONS_KEY

    // --- Shared values (overridable) ---
    const val VALUE_ON = "value.on"
    const val VALUE_OFF = "value.off"
    const val VALUE_NONE = "value.none"
    const val VALUE_GRANTED = "value.granted"
    const val VALUE_VALID = "value.valid"
    const val VALUE_CHECK = "value.check"

    // --- Compliance (binary-only; NEVER overridable) ---
    const val RATIONALE_MIC = "compliance.rationale.mic"
    const val RATIONALE_OVERLAY = "compliance.rationale.overlay"
    const val RATIONALE_ACCESSIBILITY = "compliance.rationale.accessibility"
    const val PRIVACY_PROMISE = "compliance.privacy.promise"
    const val KEY_REQUIRED_WARNING_TITLE = "compliance.key.required_title"
    const val KEY_REQUIRED_WARNING_BODY = "compliance.key.required_body"
    const val WELCOME_PRIVACY_PILL = "compliance.privacy.welcome_pill"

    /**
     * Compliance keys — locked to the binary, **excluded** from [OVERRIDABLE]. The local source editor
     * rejects edits to these (spec §2.3 / §6).
     */
    val COMPLIANCE: Set<String> = setOf(
        RATIONALE_MIC, RATIONALE_OVERLAY, RATIONALE_ACCESSIBILITY,
        PRIVACY_PROMISE, KEY_REQUIRED_WARNING_TITLE, KEY_REQUIRED_WARNING_BODY,
        WELCOME_PRIVACY_PILL,
    )

    /**
     * Overridable keys whose baked-in default is intentionally **blank** (the carousel captions).
     * Present in the catalog for completeness + source-editor support, but exempt from the "no blank
     * default" check — a blank caption simply renders no caption row.
     */
    val OPTIONAL_BLANK: Set<String> = CAROUSEL_CAPTIONS.toSet()

    /** Keys the local source editor may change; all changes require a new app build. */
    const val MODELS_SAVE = "models.save"
    const val MODELS_CUSTOM_REFRESH = "models.custom_refresh"
    const val MODELS_SEARCH = "models.search"
    const val MODELS_EMPTY = "models.empty"
    const val MODELS_LOADING = "models.loading"
    const val MODELS_FALLBACK = "models.fallback"
    const val MODELS_FRESH = "models.fresh"
    const val MODELS_REFRESH = "models.refresh"
    const val MODELS_RANKING = "models.ranking"
    const val MODELS_MANUAL = "models.manual"

    val OVERRIDABLE: Set<String> = ALL - COMPLIANCE

    /** Every key the app references — CI checks `strings.default.json` is complete against this. */
    val ALL: Set<String>
        get() = listOf(
            MODELS_SAVE, MODELS_CUSTOM_REFRESH,
            MODELS_SEARCH, MODELS_EMPTY, MODELS_LOADING, MODELS_FALLBACK, MODELS_FRESH, MODELS_REFRESH, MODELS_RANKING, MODELS_MANUAL,
            SPLASH_TAGLINE, WELCOME_TITLE, WELCOME_BODY, WELCOME_CTA, WELCOME_ALREADY, WELCOME_OPEN,
            KEY_WARNING_FREE_TIER, KEY_WARNING_USAGE, KEY_WARNING_READING, KEY_WARNING_ACCEPT,
            HOWITWORKS_EYEBROW, HOWITWORKS_TITLE, HOWITWORKS_T1, HOWITWORKS_T2, HOWITWORKS_T3,
            HOWITWORKS_STEP1, HOWITWORKS_STEP2, HOWITWORKS_STEP3, HOWITWORKS_CTA,
            CHECKLIST_TITLE, CHECKLIST_PROGRESS,
            CHECKLIST_KEY, CHECKLIST_KEY_SUB, CHECKLIST_MIC, CHECKLIST_MIC_SUB,
            CHECKLIST_OVERLAY, CHECKLIST_OVERLAY_SUB, CHECKLIST_ACCESSIBILITY, CHECKLIST_ACC_SUB, CHECKLIST_CTA,
            KEY_TITLE, KEY_BODY, KEY_FIELD_LABEL, KEY_CTA, KEY_WHERE, KEY_CONSOLE_BODY, KEY_CONSOLE_FREE,
            KEY_GUIDE_TITLE, KEY_GUIDE_OPEN, KEY_GUIDE_GROQ_STEPS, KEY_GUIDE_OPENAI_STEPS,
            KEY_GUIDE_OPENROUTER_STEPS, KEY_GUIDE_GEMINI_STEPS,
            KEY_VERIFYING, KEY_VALID, KEY_ERROR_INVALID, KEY_ERROR_NETWORK,
            MIC_TITLE, MIC_CTA, MICTEST_TITLE, MICTEST_BODY, MICTEST_CTA, MICTEST_HEARD,
            OVERLAY_TITLE, OVERLAY_CTA, ACC_ENABLE_TITLE, ACC_ENABLE_CTA,
            ALLSET_TITLE, ALLSET_BODY, ALLSET_CTA, ALLSET_LOOK, ALLSET_HOME_LINK,
            ACTION_NOT_NOW, ACTION_CONTINUE, ACTION_BACK, ACTION_CANCEL, STATUS_DONE,
            NAV_HOME, NAV_HISTORY, NAV_SETTINGS,
            A11Y_BACK, A11Y_SETTINGS, A11Y_SELECTED, A11Y_SHOW_KEY, A11Y_HIDE_KEY,
            COMMON_COMING_SOON,
            HISTORY_TITLE, HISTORY_CLEAR_ALL, HISTORY_GROUP_TODAY, HISTORY_GROUP_YESTERDAY,
            HISTORY_EMPTY_ON_TITLE, HISTORY_EMPTY_ON_BODY, HISTORY_EMPTY_ON_CTA,
            HISTORY_EMPTY_OFF_TITLE, HISTORY_EMPTY_OFF_BODY, HISTORY_EMPTY_OFF_CTA,
            HISTORY_SHEET_COPY, HISTORY_SHEET_SHARE, HISTORY_SHEET_DELETE,
            HISTORY_CLEAR_TITLE, HISTORY_CLEAR_BODY, HISTORY_CLEAR_CONFIRM,
            HOME_BANNER_A11Y_OFF_TEXT, HOME_GROUP_TODAY, HOME_TODAY_DICTATIONS, HOME_TODAY_DICTATIONS_SUB,
            HOME_TODAY_LAST_USED, HOME_TODAY_LAST_USED_SUB, HOME_GROUP_TIP, HOME_TIP_TITLE, HOME_TIP_TEXT,
            HOME_PAUSED_BANNER_TITLE, HOME_PAUSED_BANNER_TEXT, HOME_PAUSED_FOOTER,
            HOME_TOGGLE_ON_TITLE, HOME_TOGGLE_OFF_TITLE, HOME_TOGGLE_ON_SUB, HOME_TOGGLE_OFF_SUB,
            SETTINGS_TITLE, SETTINGS_GROUP_DICTATION, SETTINGS_GROUP_CAPTURE, SETTINGS_GROUP_PRIVACY,
            SETTINGS_ROW_SPEECH_MODEL, SETTINGS_ROW_API_KEYS, SETTINGS_ROW_CLEANUP, SETTINGS_ROW_CUSTOM_WORDS,
            SETTINGS_ROW_WRITING_STYLE,
            SETTINGS_KEYS_SET, SETTINGS_ROW_AUDIO, SETTINGS_ROW_BUBBLE, SETTINGS_ROW_PRIVACY,
            SETTINGS_ROW_ADVANCED, SETTINGS_ROW_ABOUT,
            SETTINGS_SPEECH_TITLE, SETTINGS_SPEECH_PROVIDER, SETTINGS_SPEECH_PROVIDER_CUSTOM,
            SETTINGS_SPEECH_MODEL, SETTINGS_MODEL_TURBO_SUB, SETTINGS_MODEL_ACCURATE_SUB,
            SETTINGS_SPEECH_LANGUAGE, SETTINGS_LANG_EN, SETTINGS_LANG_AUTO, SETTINGS_LANG_HI,
            SETTINGS_LANGUAGE_HELP,
            SETTINGS_MODEL_OPENAI_WHISPER_SUB, SETTINGS_MODEL_OPENAI_MINI_SUB, SETTINGS_MODEL_OPENAI_4O_SUB,
            SETTINGS_MODEL_OPENROUTER_WHISPER_TURBO_SUB, SETTINGS_MODEL_OPENROUTER_VOXTRAL_SUB, SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_MINI_SUB,
            SETTINGS_MODEL_OPENROUTER_GPT_AUDIO_SUB, SETTINGS_MODEL_OPENROUTER_GEMINI_FLASH_SUB,
            SETTINGS_MODEL_OPENROUTER_GEMINI_LITE_SUB, SETTINGS_MODEL_OPENROUTER_NEMOTRON_SUB,
            SETTINGS_MODEL_OPENROUTER_MIMO_SUB, SETTINGS_MODEL_GEMINI_LITE_SUB, SETTINGS_MODEL_GEMINI_FLASH_SUB,
            SETTINGS_MODEL_GEMINI_PREVIEW_SUB, SETTINGS_MODEL_GEMINI_PRO_SUB,
            SETTINGS_SPEECH_GEMINI_CAVEAT, SETTINGS_SPEECH_KEY_REQUIRED, SETTINGS_SPEECH_KEY_REQUIRED_CTA,
            SETTINGS_SPEECH_MODEL_FIELD_LABEL, SETTINGS_SPEECH_MODEL_FIELD_HINT,
            SETTINGS_SPEECH_CUSTOM_URL_LABEL, SETTINGS_SPEECH_CUSTOM_URL_HINT,
            SETTINGS_SPEECH_CUSTOM_URL_ERROR, SETTINGS_SPEECH_CUSTOM_URL_HELP,
            SETTINGS_KEYS_TITLE, SETTINGS_KEYS_EMPTY_TITLE, SETTINGS_KEYS_EMPTY_BODY, SETTINGS_KEYS_ADD,
            SETTINGS_KEYS_BANNER, SETTINGS_KEYS_SECTION, SETTINGS_KEYS_ROLE_STT, SETTINGS_KEYS_HELP,
            SETTINGS_KEYS_REPLACE, SETTINGS_KEYS_TEST, SETTINGS_KEYS_REMOVE,
            SETTINGS_CLEANUP_TITLE, SETTINGS_CLEANUP_TIDY_TITLE, SETTINGS_CLEANUP_TIDY_SUB,
            SETTINGS_CLEANUP_WHAT, SETTINGS_CLEANUP_BEFORE, SETTINGS_CLEANUP_AFTER,
            SETTINGS_CLEANUP_MODEL, SETTINGS_CLEANUP_HELP, SETTINGS_CLEANUP_CUSTOM,
            CLEANUP_AUTO, CLEANUP_AUTO_HELP, KEY_ACTIVE, KEY_USE,
            SETTINGS_CLEANUP_GROQ_KEY, SETTINGS_CLEANUP_GROQ_MISSING,
            SETTINGS_STYLE_TITLE, SETTINGS_STYLE_INTRO, SETTINGS_STYLE_DEFAULT_SECTION,
            SETTINGS_STYLE_PER_APP_SECTION, SETTINGS_STYLE_FORMAL, SETTINGS_STYLE_FORMAL_SUB,
            SETTINGS_STYLE_NEUTRAL, SETTINGS_STYLE_NEUTRAL_SUB, SETTINGS_STYLE_CASUAL,
            SETTINGS_STYLE_CASUAL_SUB, SETTINGS_STYLE_SEARCH_HINT, SETTINGS_STYLE_ASSIGN_TITLE,
            SETTINGS_STYLE_USE_DEFAULT, SETTINGS_STYLE_LOADING, SETTINGS_STYLE_NO_RESULTS,
            SETTINGS_STYLE_NEEDS_CLEANUP, SETTINGS_STYLE_NEEDS_GROQ, SETTINGS_STYLE_WORDS_PROMISE,
            SETTINGS_STYLE_HELP,
            SETTINGS_WORDS_TITLE, SETTINGS_WORDS_BODY, SETTINGS_WORDS_EMPTY_TITLE, SETTINGS_WORDS_EMPTY_SUB,
            SETTINGS_WORDS_ADD, SETTINGS_WORDS_ADD_TITLE, SETTINGS_WORDS_HEARD_LABEL, SETTINGS_WORDS_HEARD_HINT,
            SETTINGS_WORDS_REPLACEMENT_LABEL, SETTINGS_WORDS_REPLACEMENT_HINT, SETTINGS_WORDS_SAVE,
            SETTINGS_WORDS_DELETE, SETTINGS_WORDS_COUNT, SETTINGS_WORDS_RECORD, SETTINGS_WORDS_LISTENING,
            SETTINGS_WORDS_TRANSCRIBING, SETTINGS_WORDS_ERR_RECORD, SETTINGS_WORDS_ERR_KEY,
            SETTINGS_WORDS_ERR_NETWORK, SETTINGS_WORDS_ERR_PERMISSION,
            SETTINGS_ROW_CONTACTS_VOCAB, SETTINGS_VOCAB_TITLE, SETTINGS_VOCAB_BODY,
            SETTINGS_VOCAB_COUNT, SETTINGS_VOCAB_IMPORT, SETTINGS_VOCAB_IMPORTING,
            SETTINGS_VOCAB_REIMPORT, SETTINGS_VOCAB_IMPORTED, SETTINGS_VOCAB_IMPORT_FAILED,
            SETTINGS_VOCAB_EMPTY_TITLE, SETTINGS_VOCAB_EMPTY_SUB, SETTINGS_VOCAB_PERMISSION_NEEDED,
            SETTINGS_VOCAB_OPEN_SETTINGS, SETTINGS_VOCAB_CLEAR, SETTINGS_VOCAB_CLEAR_CONFIRM_TITLE,
            SETTINGS_VOCAB_CLEAR_CONFIRM_BODY, SETTINGS_VOCAB_CLEAR_CONFIRM_CTA,
            SETTINGS_AUDIO_TITLE, SETTINGS_AUDIO_START_STOP, SETTINGS_AUDIO_TRIGGER, SETTINGS_AUDIO_TRIGGER_SUB,
            SETTINGS_AUDIO_TRIGGER_VALUE, SETTINGS_AUDIO_HOLD, SETTINGS_AUDIO_HOLD_SUB,
            SETTINGS_AUDIO_MAX_LENGTH, SETTINGS_AUDIO_AUTOSTOP, SETTINGS_AUDIO_SILENCE,
            SETTINGS_AUDIO_SILENCE_SUB, SETTINGS_AUDIO_SILENCE_BANNER, SETTINGS_AUDIO_FORMAT,
            SETTINGS_AUDIO_FORMAT_SUB, SETTINGS_AUDIO_FORMAT_VALUE,
            SETTINGS_PRIVACY_TITLE, SETTINGS_PRIVACY_BANNER_TITLE, SETTINGS_PRIVACY_BANNER_TEXT,
            SETTINGS_PRIVACY_PRIVATE_MODE, SETTINGS_PRIVACY_PRIVATE_MODE_SUB, SETTINGS_PRIVACY_HISTORY,
            SETTINGS_PRIVACY_HISTORY_SUB, SETTINGS_PRIVACY_HISTORY_RETENTION,
            SETTINGS_PRIVACY_HISTORY_RETENTION_HOUR, SETTINGS_PRIVACY_HISTORY_RETENTION_DAY,
            SETTINGS_PRIVACY_HISTORY_RETENTION_WEEK, SETTINGS_PRIVACY_HISTORY_RETENTION_MONTH,
            SETTINGS_PRIVACY_HISTORY_RETENTION_FOREVER,
            SETTINGS_PRIVACY_DIAGNOSTICS, SETTINGS_PRIVACY_CRASH,
            SETTINGS_PRIVACY_CRASH_SUB, SETTINGS_PRIVACY_EXAMPLE, SETTINGS_PRIVACY_EXAMPLE_SUB,
            SETTINGS_PRIVACY_DIAG_HELP, SETTINGS_PRIVACY_PERMISSIONS, SETTINGS_PRIVACY_MIC,
            SETTINGS_PRIVACY_ACCESSIBILITY,
            SETTINGS_BUBBLE_TITLE, SETTINGS_BUBBLE_HELP, SETTINGS_BUBBLE_PREVIEW_LIGHT,
            SETTINGS_BUBBLE_PREVIEW_DARK, SETTINGS_BUBBLE_SIZE, SETTINGS_BUBBLE_THEME,
            SETTINGS_ADVANCED_TITLE, SETTINGS_ADVANCED_ENDPOINT, SETTINGS_ADVANCED_ENDPOINT_HELP,
            SETTINGS_ADVANCED_TIMEOUT, SETTINGS_ADVANCED_TIMEOUT_SUB, SETTINGS_ADVANCED_TIMEOUT_VALUE,
            SETTINGS_ADVANCED_KEEPALIVE, SETTINGS_ADVANCED_KEEPALIVE_SUB,
            SETTINGS_ADVANCED_BATTERY,
            SETTINGS_ADVANCED_BATTERY_SUB, SETTINGS_ADVANCED_RESET, SETTINGS_ADVANCED_RESET_SETTINGS,
            SETTINGS_ADVANCED_CLEAR, SETTINGS_ADVANCED_CLEAR_CONFIRM_TITLE,
            SETTINGS_ADVANCED_CLEAR_CONFIRM_BODY, SETTINGS_ADVANCED_CLEAR_CONFIRM_CTA,
            SETTINGS_ADVANCED_RESET_CONFIRM_TITLE, SETTINGS_ADVANCED_RESET_CONFIRM_BODY,
            SETTINGS_ADVANCED_RESET_CONFIRM_CTA,
            SETTINGS_ABOUT_TITLE, SETTINGS_ABOUT_VERSION, SETTINGS_ABOUT_PRIVACY_POLICY,
            SETTINGS_ABOUT_TERMS, SETTINGS_ABOUT_LICENSES, SETTINGS_ABOUT_FEEDBACK, SETTINGS_ABOUT_FOOTER,
            SYSTEM_RECOVERY_TITLE, SYSTEM_RECOVERY_BANNER_TEXT, SYSTEM_RECOVERY_HEADING, SYSTEM_RECOVERY_BODY,
            SYSTEM_RECOVERY_CTA, SYSTEM_RECOVERY_WHY, SYSTEM_CRASH_TITLE, SYSTEM_CRASH_BODY,
            SYSTEM_CRASH_BANNER, SYSTEM_CRASH_ALWAYS, SYSTEM_CRASH_SEND, SYSTEM_CRASH_DONT_SEND,
            RELIABILITY_BOOT_CHANNEL, RELIABILITY_BOOT_TITLE, RELIABILITY_BOOT_TEXT,
            INJECT_FAILED_TITLE, INJECT_FAILED_TEXT, INJECT_REVOKED_TITLE, INJECT_REVOKED_TEXT,
            INJECT_REENABLE_ACTION, HOME_BATTERY_TITLE, HOME_BATTERY_TEXT,
            SETTINGS_SURVIVAL_ROW, SETTINGS_SURVIVAL_TITLE, SETTINGS_SURVIVAL_BODY,
            SETTINGS_SURVIVAL_STEPS, SETTINGS_SURVIVAL_OPEN, SETTINGS_SURVIVAL_FALLBACK,
            STT_QUOTA_TOAST,
            NOTIFICATION_ACTION_SHOW, NOTIFICATION_ACTION_STOP,
            NOTIFICATION_TITLE_ACTIVE, NOTIFICATION_TEXT_ACTIVE,
            NOTIFICATION_TITLE_HIDDEN, NOTIFICATION_TEXT_HIDDEN,
            NOTIFICATION_TITLE_RECORDING, NOTIFICATION_TEXT_RECORDING, BUBBLE_DISMISS_TOAST,
            BUBBLE_BIN_LABEL, BUBBLE_BIN_LABEL_ACTIVE, BUBBLE_AUTORETURN_TOAST,
            CAROUSEL_HOME_HEADER, CAROUSEL_CARD_A11Y, CAROUSEL_PLAY_A11Y,
            VALUE_ON, VALUE_OFF, VALUE_NONE, VALUE_GRANTED, VALUE_VALID, VALUE_CHECK,
            RATIONALE_MIC, RATIONALE_OVERLAY, RATIONALE_ACCESSIBILITY,
            PRIVACY_PROMISE, KEY_REQUIRED_WARNING_TITLE, KEY_REQUIRED_WARNING_BODY, WELCOME_PRIVACY_PILL,
        ).toSet() + CAROUSEL_CAPTIONS
}
