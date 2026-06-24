package com.tihor.quill.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tihor.quill.accessibility.InsertResult
import com.tihor.quill.accessibility.QuillAccessibilityService

/**
 * Hosts the floating bubble in a [WindowManager] overlay window (spec §3.1). M1 is a plain started
 * service: a visible `TYPE_APPLICATION_OVERLAY` window keeps the process perceptible, so the bubble
 * survives the app being backgrounded without a foreground notification yet. At M4 this becomes a
 * `foregroundServiceType="microphone"` FGS that also owns the recorder — the visible overlay is
 * what will legally let that mic FGS start from the background (spec §5).
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubble: BubbleView? = null
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        goForeground()
        addBubble()
    }

    /**
     * Promote to a foreground service so the bubble survives the app being backgrounded. On
     * MagicOS a plain *started* service (and its overlay) is trimmed the moment another app comes
     * forward — defeating the whole "float over other apps" purpose. M1 uses the `specialUse` type
     * since the recorder (and the microphone type) arrive at M4.
     */
    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quill bubble", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Keeps the dictation bubble available over other apps." },
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Quill bubble active")
            .setContentText("Tap the bubble to dictate.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Recreate the window if a re-start arrives after the system trimmed us.
        if (bubble == null) addBubble()
        return START_STICKY
    }

    private fun addBubble() {
        if (!OverlayPermission.isGranted(this)) {
            Log.w(TAG, "Overlay permission not granted; cannot show bubble.")
            stopSelf()
            return
        }
        val view = BubbleView(this)
        view.onTap = { onBubbleTap(view) }
        view.onLongPress = { stopSelf() }               // long-press dismisses
        view.onDragStart = {}
        view.onDrag = { dx, dy -> moveBy(dx, dy) }
        view.onDragEnd = { snapToEdge() }

        val (sw, sh) = screenSize()
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sw - view.viewSize          // start snapped to the right edge
            y = sh / 3
        }
        windowManager.addView(view, params)
        bubble = view
        Log.i(TAG, "Bubble added at x=${params.x} y=${params.y}")
    }

    /**
     * M3 bubble behaviour: a tap inserts a hardcoded "hello world" into the focused field via the
     * accessibility service (the recorder + real transcript arrive at M4–M6). In the PASTE state a
     * tap performs the clipboard paste (the "Tap to Paste" rung). The bubble state reflects the
     * outcome.
     */
    private fun onBubbleTap(view: BubbleView) {
        val a11y = QuillAccessibilityService.instance
        if (a11y == null) {
            Log.w(TAG, "Accessibility service not connected; cannot inject.")
            flash(view, BubbleState.ERROR)
            return
        }
        if (view.state == BubbleState.PASTE) {
            val pasted = a11y.pasteIntoFocused()
            view.state = if (pasted) BubbleState.IDLE else BubbleState.ERROR
            return
        }
        when (a11y.injectText("hello world")) {
            InsertResult.Success -> flash(view, BubbleState.TRANSCRIBING, then = BubbleState.IDLE)
            InsertResult.NeedsClipboardFallback -> view.state = BubbleState.PASTE
            InsertResult.RefusedSensitive -> flash(view, BubbleState.ERROR)
            InsertResult.NoEditableField -> flash(view, BubbleState.ERROR)
        }
    }

    /** Briefly show [state], then return to [then] (default keeps [state]). */
    private fun flash(view: BubbleView, state: BubbleState, then: BubbleState = state) {
        view.state = state
        if (then != state) {
            view.postDelayed({ if (bubble === view) view.state = then }, 700)
        }
    }

    private fun moveBy(dxPx: Float, dyPx: Float) {
        val view = bubble ?: return
        val (sw, sh) = screenSize()
        params.x = BubbleGeometry.clamp(params.x + dxPx.toInt(), 0, (sw - view.viewSize).coerceAtLeast(0))
        params.y = BubbleGeometry.clampY(params.y + dyPx.toInt(), view.viewSize, sh)
        windowManager.updateViewLayout(view, params)
    }

    private fun snapToEdge() {
        val view = bubble ?: return
        val (sw, _) = screenSize()
        params.x = BubbleGeometry.snapTargetX(params.x, view.viewSize, sw)
        windowManager.updateViewLayout(view, params)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = windowManager.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Quill.Overlay"
        private const val CHANNEL_ID = "quill_overlay"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.tihor.quill.overlay.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
