package com.tihor.quill.copy

/**
 * Typed copy keys (spec §2.3). Every user-facing string is read by one of these from the Copy
 * Catalog — no string literals in composables (CI guard enforces it).
 *
 * **Compliance keys ([COMPLIANCE]) are binary-only and excluded from [OVERRIDABLE]** — the OTA layer
 * (M13) may only ever touch [OVERRIDABLE] keys, so the accessibility disclosure/consent, permission
 * rationales, privacy promises, and the BYOK key warning can never be changed over the air
 * (CLAUDE.md hard rule).
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
    const val STATUS_DONE = "status.done"

    // --- Compliance (binary-only; NEVER overridable) ---
    const val DISCLOSURE_TITLE = "compliance.accessibility.disclosure_title"
    const val DISCLOSURE_BODY = "compliance.accessibility.disclosure_body"
    const val DISCLOSURE_ACCEPT = "compliance.accessibility.disclosure_accept"
    const val DISCLOSURE_DECLINE = "compliance.accessibility.disclosure_decline"
    const val RATIONALE_MIC = "compliance.rationale.mic"
    const val RATIONALE_OVERLAY = "compliance.rationale.overlay"
    const val RATIONALE_ACCESSIBILITY = "compliance.rationale.accessibility"
    const val PRIVACY_PROMISE = "compliance.privacy.promise"
    const val KEY_REQUIRED_WARNING_TITLE = "compliance.key.required_title"
    const val KEY_REQUIRED_WARNING_BODY = "compliance.key.required_body"
    const val WELCOME_PRIVACY_PILL = "compliance.privacy.welcome_pill"

    /**
     * Compliance keys — locked to the binary, **excluded** from [OVERRIDABLE]. The OTA override layer
     * physically ignores these (spec §2.3 / §6).
     */
    val COMPLIANCE: Set<String> = setOf(
        DISCLOSURE_TITLE, DISCLOSURE_BODY, DISCLOSURE_ACCEPT, DISCLOSURE_DECLINE,
        RATIONALE_MIC, RATIONALE_OVERLAY, RATIONALE_ACCESSIBILITY,
        PRIVACY_PROMISE, KEY_REQUIRED_WARNING_TITLE, KEY_REQUIRED_WARNING_BODY,
        WELCOME_PRIVACY_PILL,
    )

    /** Keys an OTA bundle may override = everything that is **not** compliance. Enforced in M13. */
    val OVERRIDABLE: Set<String> = ALL - COMPLIANCE

    /** Every key the app references — CI checks `strings.default.json` is complete against this. */
    val ALL: Set<String>
        get() = listOf(
            SPLASH_TAGLINE, WELCOME_TITLE, WELCOME_BODY, WELCOME_CTA, WELCOME_ALREADY, WELCOME_OPEN,
            KEY_WARNING_FREE_TIER, KEY_WARNING_USAGE, KEY_WARNING_READING, KEY_WARNING_ACCEPT,
            HOWITWORKS_EYEBROW, HOWITWORKS_TITLE, HOWITWORKS_T1, HOWITWORKS_T2, HOWITWORKS_T3,
            HOWITWORKS_STEP1, HOWITWORKS_STEP2, HOWITWORKS_STEP3, HOWITWORKS_CTA,
            CHECKLIST_TITLE, CHECKLIST_PROGRESS,
            CHECKLIST_KEY, CHECKLIST_KEY_SUB, CHECKLIST_MIC, CHECKLIST_MIC_SUB,
            CHECKLIST_OVERLAY, CHECKLIST_OVERLAY_SUB, CHECKLIST_ACCESSIBILITY, CHECKLIST_ACC_SUB, CHECKLIST_CTA,
            KEY_TITLE, KEY_BODY, KEY_FIELD_LABEL, KEY_CTA, KEY_WHERE, KEY_VERIFYING, KEY_VALID,
            KEY_ERROR_INVALID, KEY_ERROR_NETWORK,
            MIC_TITLE, MIC_CTA, MICTEST_TITLE, MICTEST_BODY, MICTEST_CTA, MICTEST_HEARD,
            OVERLAY_TITLE, OVERLAY_CTA, ACC_ENABLE_TITLE, ACC_ENABLE_CTA,
            ALLSET_TITLE, ALLSET_BODY, ALLSET_CTA, ALLSET_LOOK, ALLSET_HOME_LINK,
            ACTION_NOT_NOW, ACTION_CONTINUE, ACTION_BACK, STATUS_DONE,
            DISCLOSURE_TITLE, DISCLOSURE_BODY, DISCLOSURE_ACCEPT, DISCLOSURE_DECLINE,
            RATIONALE_MIC, RATIONALE_OVERLAY, RATIONALE_ACCESSIBILITY,
            PRIVACY_PROMISE, KEY_REQUIRED_WARNING_TITLE, KEY_REQUIRED_WARNING_BODY, WELCOME_PRIVACY_PILL,
        ).toSet()
}
