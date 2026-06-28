package com.tihor.quill.reliability

import android.content.Context
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Process-kill recovery (M11). Called when the app comes to the foreground (legal place to start a
 * FGS). If the user wants the bubble, overlay is granted, recovery is on, and the service isn't
 * running, it was killed incidentally (memory/doze/OEM trim) — restart it, and remember the kill so
 * the battery-exemption re-prompt is earned. A deliberate force-stop is respected by the OS (the app
 * wouldn't be foregrounded without a manual relaunch, and the service won't auto-restart).
 */
object ForegroundRecovery {

    /** @return true if a killed bubble was restarted. Safe to call on every foreground. */
    suspend fun onForeground(context: Context): Boolean {
        val repo = SettingsRepository(context)
        val s = repo.settings.first()
        val recover = ReliabilityPolicy.shouldRecoverOnForeground(
            bootRestore = s.bootRestore,
            bubbleActive = s.bubbleActive,
            overlayGranted = OverlayPermission.isGranted(context),
            serviceRunning = OverlayService.isRunning,
        )
        if (!recover) return false
        runCatching { OverlayService.start(context) } // legal: app is foregrounded
        repo.setBubbleWasKilled(true)
        return true
    }
}
