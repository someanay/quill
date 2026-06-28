package com.tihor.quill.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Best-effort deep-links to OEM auto-start / protected-apps managers (M11). These components are
 * undocumented and vary by brand/version, so each candidate is resolved against the package manager
 * and the caller MUST fall back to on-screen instructions when none resolves. We cannot grant
 * auto-start programmatically — this is guidance, not automation.
 */
object OemAutoStart {

    // Honor/MagicOS first (the daily-driver worst case), then Huawei, Xiaomi/MIUI, Oppo/ColorOS,
    // Vivo/Funtouch/iQOO, Samsung. First component that resolves on this device wins.
    private val CANDIDATES = listOf(
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupapp.StartupAppControlActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
    )

    /** The first OEM auto-start intent that actually resolves on this device, or null if none do. */
    fun bestEffortIntent(context: Context): Intent? {
        for ((pkg, cls) in CANDIDATES) {
            val intent = Intent()
                .setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        return null
    }
}
