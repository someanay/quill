package com.tihor.quill.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/** Helpers to detect whether Quill's accessibility service is enabled and to open its settings. */
object AccessibilityUtil {

    /**
     * True if [QuillAccessibilityService] is in the system's enabled-accessibility-services list.
     * Read from `Settings.Secure` rather than `AccessibilityManager` so it works as a plain check
     * from any context (and on app-foreground re-checks for the M11 revocation detection).
     */
    fun isServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, QuillAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component == expected) return true
        }
        return false
    }

    fun disclosureIntent(context: Context): Intent =
        Intent(context, AccessibilityDisclosureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}
