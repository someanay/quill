package com.tihor.quill.reliability

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tihor.quill.MainActivity
import com.tihor.quill.copy.Copy
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.overlay.OverlayPermission
import com.tihor.quill.overlay.OverlayService
import com.tihor.quill.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Reboot survival (M11). On BOOT_COMPLETED / QUICKBOOT_POWERON, [ReliabilityPolicy.bootAction]
 * decides whether to restore the bubble. We never force-start the microphone FGS from boot and never
 * crash if a start is refused: a background FGS start is illegal on modern Android, so a refusal
 * falls back to a low-priority "tap to re-activate" notification (tapping opens the app, where the
 * foreground recovery path can start it legally). Gated on the user's keep-alive setting.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON, ACTION_HTC_QUICKBOOT_POWERON -> Unit
            else -> return
        }
        // Never let a boot broadcast crash the app — every branch is wrapped.
        runCatching {
            val settings = runBlocking { SettingsRepository(context).settings.first() }
            val action = ReliabilityPolicy.bootAction(
                bootRestore = settings.bootRestore,
                bubbleActive = settings.bubbleActive,
                overlayGranted = OverlayPermission.isGranted(context),
            )
            ReliabilityPolicy.executeBoot(
                action = action,
                // specialUse bubble only — never the mic FGS. A background FGS start is illegal on
                // modern Android, so a refusal falls back to the notification (never crashes).
                restore = { OverlayService.start(context) },
                notify = { postReactivate(context) },
            )
        }.onFailure { Log.w(TAG, "Boot handling failed", it) }
    }

    private fun postReactivate(context: Context) {
        runCatching {
            val copy = Copy.repository(context)
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    copy.get(K.RELIABILITY_BOOT_CHANNEL),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val tap = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(copy.get(K.RELIABILITY_BOOT_TITLE))
                .setContentText(copy.get(K.RELIABILITY_BOOT_TEXT))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "Quill.Boot"
        private const val CHANNEL_ID = "quill_reliability"
        private const val NOTIFICATION_ID = 3
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
