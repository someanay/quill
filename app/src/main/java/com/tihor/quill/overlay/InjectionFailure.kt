package com.tihor.quill.overlay

import com.tihor.quill.accessibility.InsertResult

/**
 * Why an insertion couldn't land, and how the failure notification should behave (M11). Pure so the
 * "service revoked vs. nothing focused vs. sensitive field" distinction is unit-testable — only a
 * revoked service warrants the one-tap "Re-enable Quill" deep-link action.
 */
enum class InjectionFailure {
    /** Accessibility service isn't connected (revoked by the OS/OEM, or never enabled). */
    ServiceRevoked,

    /** A password/banking field is focused — we refuse to type into it. */
    SensitiveField,

    /** Nothing editable is focused. */
    NoField;

    /** Only a revoked service offers re-enable: a focused/sensitive field means the service is fine. */
    val offersReEnable: Boolean get() = this == ServiceRevoked

    companion object {
        /**
         * @param serviceConnected QuillAccessibilityService.instance != null at injection time.
         * @param result the injector's verdict, or null when there was no service to even try.
         */
        fun classify(serviceConnected: Boolean, result: InsertResult?): InjectionFailure = when {
            !serviceConnected -> ServiceRevoked
            result == InsertResult.RefusedSensitive -> SensitiveField
            else -> NoField
        }
    }
}
