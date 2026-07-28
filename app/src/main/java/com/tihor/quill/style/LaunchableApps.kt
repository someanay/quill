package com.tihor.quill.style

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/** One user-launchable app, as offered in the per-app style picker. */
data class LaunchableApp(val packageName: String, val label: String)

/**
 * Lists the apps the user can assign a style to (scope doc §8.1).
 *
 * **Deliberately not `QUERY_ALL_PACKAGES`.** That permission is Play-restricted and granted narrowly
 * (launchers, security tools, file managers); a BYOK dictation app does not qualify, and M12 already
 * dropped one restricted permission specifically to keep review friction off this app. Instead the
 * manifest declares a `<queries>` element with a MAIN/LAUNCHER intent filter, which makes exactly the
 * user-launchable apps visible through package-visibility filtering — and that set *is* the set you
 * can dictate into, so it costs nothing in coverage.
 *
 * Quill itself is excluded (you cannot dictate into the settings screen you are configuring), results
 * are de-duplicated by package (an app may declare several launcher activities), and sorting is by
 * display label so the list reads the way the launcher does. Any [PackageManager] failure yields an
 * empty list rather than throwing — a style picker with nothing in it is a bad screen, not a crash.
 */
object LaunchableApps {

    fun query(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.applicationInfo }
                .filter { it.packageName != context.packageName }
                .associate { it.packageName to pm.getApplicationLabel(it).toString() } // de-dupes
                .map { (pkg, label) -> LaunchableApp(pkg, label.ifBlank { pkg }) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        }.onFailure {
            Log.w(TAG, "Could not list launchable apps: ${it.message}")
        }.getOrDefault(emptyList())
    }

    private const val TAG = "Quill.Style"
}
