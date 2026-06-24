package com.tihor.quill.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tihor.quill.BuildConfig
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.audio.StopReason
import java.io.File

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

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioRecorder by lazy { AudioRecorder(this) }
    private var currentRecording: File? = null
    private var debugReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        setForeground(microphone = false)
        addBubble()
        registerDebugReceiverIfDebug()
    }

    /** Dev-only: drive recording without tapping the (un-injectable) overlay — M4 verification. */
    private fun registerDebugReceiverIfDebug() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DEBUG_RECORD_START -> bubble?.let { startRecording(it) }
                    ACTION_DEBUG_RECORD_STOP -> audioRecorder.requestStop()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_RECORD_START)
            addAction(ACTION_DEBUG_RECORD_STOP)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quill bubble", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Keeps the dictation bubble available over other apps." },
        )
    }

    /**
     * Foreground-service promotion. `specialUse` keeps the bubble alive over other apps (M1); the
     * `microphone` type is added only while recording (M4) — the visible overlay is what legally lets
     * the mic FGS start from the background (spec §5). We drop the mic type when recording stops so
     * the mic-in-use indicator and capture don't linger.
     */
    private fun setForeground(microphone: Boolean) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (microphone) "Quill is listening" else "Quill bubble active")
            .setContentText(if (microphone) "Recording… tap the bubble to stop." else "Tap the bubble to dictate.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (microphone) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, notification, type)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && microphone ->
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else -> startForeground(NOTIFICATION_ID, notification)
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
     * M4 bubble behaviour: tap toggles recording. Tap (idle) → start the mic FGS + record; tap again
     * → stop. The transcript + injection get wired between "stop" and "idle" at M5–M6.
     */
    private fun onBubbleTap(view: BubbleView) {
        if (audioRecorder.isRecording) {
            audioRecorder.requestStop() // onFinished updates the UI
            return
        }
        startRecording(view)
    }

    private fun startRecording(view: BubbleView) {
        if (!audioRecorder.hasMicPermission()) {
            Log.w(TAG, "Mic permission not granted; cannot record (onboarding handles the grant).")
            flash(view, BubbleState.ERROR)
            return
        }
        val output = File(cacheDir, "quill_${System.currentTimeMillis()}.wav")
        currentRecording = output
        setForeground(microphone = true)
        view.state = BubbleState.RECORDING
        audioRecorder.start(
            output = output,
            onAmplitude = { rms -> mainHandler.post { bubble?.level = (rms / 8000f).coerceIn(0f, 1f) } },
            onWarning = { mainHandler.post { Log.i(TAG, "Approaching session cap.") } },
            onFinished = { file, reason -> mainHandler.post { onRecordingFinished(file, reason) } },
        )
    }

    private fun onRecordingFinished(file: File, reason: StopReason) {
        setForeground(microphone = false)
        bubble?.let { it.level = 0f }
        when (reason) {
            StopReason.ERROR -> flash(bubble, BubbleState.ERROR)
            else -> bubble?.state = BubbleState.IDLE // M6 will transcribe + inject here
        }
        Log.i(TAG, "WAV saved: ${file.absolutePath} (${file.length()} bytes), reason=$reason")
    }

    /** Briefly show [state], then return to [then] (default keeps [state]). */
    private fun flash(view: BubbleView?, state: BubbleState, then: BubbleState = state) {
        view ?: return
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
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        runCatching { audioRecorder.stop() }
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Quill.Overlay"
        private const val CHANNEL_ID = "quill_overlay"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.tihor.quill.overlay.STOP"
        const val ACTION_DEBUG_RECORD_START = "com.tihor.quill.DEBUG_RECORD_START"
        const val ACTION_DEBUG_RECORD_STOP = "com.tihor.quill.DEBUG_RECORD_STOP"

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
