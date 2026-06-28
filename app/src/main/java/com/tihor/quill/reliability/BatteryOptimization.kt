package com.tihor.quill.reliability

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimisation exemption helpers (M11/M12). On aggressive OEMs an exempt app is far less
 * likely to be killed in the background — but we can only point the user to the system settings list
 * and let them allow Quill themselves.
 *
 * We deliberately do **not** use the one-tap dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
 * or hold the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission: Play restricts those to an allowlist
 * a BYOK dictation app doesn't qualify for (likely rejection). Reading the current exemption state is
 * unrestricted (no permission) and stays.
 */
object BatteryOptimization {

    /** True if the app is already exempt (won't be doze-restricted). Unrestricted read — no permission. */
    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * The battery-optimisation settings **list** (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) — the
     * only route we use: the user finds Quill in the list and allows it. Not Play-restricted; always
     * resolvable on stock Android. Wrap the launch against [android.content.ActivityNotFoundException]
     * on unusual OEMs.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
