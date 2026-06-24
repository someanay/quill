package com.tihor.quill.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Helpers for the "display over other apps" (SYSTEM_ALERT_WINDOW) special permission. */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Intent that opens the system screen to grant the overlay permission for this app. */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
}
