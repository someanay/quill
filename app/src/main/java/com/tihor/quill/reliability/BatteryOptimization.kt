package com.tihor.quill.reliability

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimisation exemption helpers (M11). On aggressive OEMs an unrestricted/exempt app is far
 * less likely to be killed in the background. We can only *ask* — the user grants it. Every intent is
 * built so the caller can wrap the launch against [android.content.ActivityNotFoundException].
 */
object BatteryOptimization {

    /** True if the app is already exempt from battery optimisation (won't be doze-restricted). */
    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The direct "Allow?" dialog (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS). Requires the declared
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission. If this doesn't resolve, fall back to
     * [settingsIntent].
     */
    fun requestIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The battery-optimisation list (always resolvable on stock Android) as a safe fallback. */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
