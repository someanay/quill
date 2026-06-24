package com.tihor.quill.accessibility

import android.text.InputType

/**
 * Decides whether a focused editable field is **sensitive** — a password (or banking-style secret)
 * field that Quill must neither read nor write (PRD §4 edge case, P0-5; spec §5 step B.5).
 *
 * Native fields are trusted to report `isPassword` / password input types. **Browser/WebView fields
 * are not:** Chrome can report a web `<input type=password>` as `isPassword=false` with a plain
 * `TYPE_CLASS_TEXT` input type — indistinguishable from a normal web text field. So in a browser
 * context we invert the default: a field is sensitive **unless** its type *positively* confirms a
 * non-secret kind (email / url / phone / date). Plain text, number, or no-type can't be confirmed
 * safe, so we refuse them rather than rely on a flag Chrome doesn't set.
 *
 * Pure and Android-constant-only so it is unit-testable on the JVM.
 */
object SensitiveFieldClassifier {

    /** The subset of node properties that determine sensitivity. */
    data class EditableFieldInfo(
        val isPassword: Boolean,
        val inputType: Int,
        val className: CharSequence? = null,
        val packageName: CharSequence? = null,
    )

    fun isSensitive(field: EditableFieldInfo): Boolean {
        // The node's own password flag / password input type is authoritative when present.
        if (field.isPassword) return true
        if (isPasswordInputType(field.inputType)) return true

        // Browser context: refuse unless the type positively confirms a non-secret field.
        if (isBrowserContext(field.packageName) && !confirmsNonSensitiveType(field.inputType)) {
            return true
        }
        return false
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * In a browser, only these clearly-non-secret kinds are trusted. Plain text/number/datetime-less
     * and `TYPE_NULL` are deliberately *not* confirmed (a password or PIN can masquerade as them).
     */
    private fun confirmsNonSensitiveType(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_URI

            InputType.TYPE_CLASS_PHONE -> true
            InputType.TYPE_CLASS_DATETIME -> true
            else -> false
        }
    }

    private fun isBrowserContext(packageName: CharSequence?): Boolean {
        val pkg = packageName?.toString() ?: return false
        if (pkg in BROWSER_PACKAGES) return true
        // Heuristic for the long tail of browsers / WebView-host packages.
        return pkg.contains("browser") || pkg.contains("chrome")
    }

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.focus",
        "com.microsoft.emmx", "com.brave.browser",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser", "com.vivaldi.browser", "com.UCMobile.intl",
    )
}
