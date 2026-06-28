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
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tihor.quill.BuildConfig
import com.tihor.quill.accessibility.InsertResult
import com.tihor.quill.accessibility.QuillAccessibilityService
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.audio.StopReason
import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.TranscriptionClientFactory
import com.tihor.quill.stt.RomanizePrompt
import com.tihor.quill.stt.TranscriptionOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private var caption: CaptionView? = null
    private lateinit var captionParams: WindowManager.LayoutParams
    private var recordSeconds = 0
    private var sensitiveActive = false

    private val density by lazy { resources.displayMetrics.density }
    private fun dpx(v: Float) = (v * density).toInt()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            recordSeconds++
            caption?.configure(CaptionView.Mode.RECORDING, bubble?.level ?: 0f, recordSeconds)
            positionCaption()
            mainHandler.postDelayed(this, 1000)
        }
    }
    private val audioRecorder by lazy { AudioRecorder(this) }
    private var currentRecording: File? = null
    private var debugReceiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val secretStore: SecretStore by lazy { KeystoreSecretStore(this) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val historyRepository by lazy { HistoryRepository(this) }

    /** Package of the field where the current recording started — saved as the history "target app". */
    private var recordingPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        setForeground(microphone = false)
        addBubble()
        // Board F4: dim the bubble while a password/banking field is focused.
        QuillAccessibilityService.sensitiveListener = { s -> mainHandler.post { onSensitiveField(s) } }
        onSensitiveField(QuillAccessibilityService.sensitiveFieldFocused)
        registerDebugReceiverIfDebug()
    }

    private fun onSensitiveField(sensitive: Boolean) {
        sensitiveActive = sensitive
        bubble?.alpha = if (sensitive) 0.45f else 1f
        if (sensitive) {
            android.widget.Toast.makeText(
                this, "Dictation is off in password fields — for your security.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Dev-only: drive recording without tapping the (un-injectable) overlay — M4 verification. */
    private fun registerDebugReceiverIfDebug() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DEBUG_RECORD_START -> bubble?.let { startRecording(it) }
                    ACTION_DEBUG_RECORD_STOP -> audioRecorder.requestStop()
                    ACTION_DEBUG_SET_KEY ->
                        intent.getStringExtra("key")?.let { secretStore.put(SecretStore.ALIAS_STT_KEY, it) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_RECORD_START)
            addAction(ACTION_DEBUG_RECORD_STOP)
            addAction(ACTION_DEBUG_SET_KEY)
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

        // Companion status caption (board E2–E5 / F3): hidden until a state needs it.
        val cap = CaptionView(this)
        captionParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        cap.visibility = View.GONE
        windowManager.addView(cap, captionParams)
        caption = cap

        Log.i(TAG, "Bubble added at x=${params.x} y=${params.y}")
    }

    /** Drive the bubble state and its caption together (board E). */
    private fun applyState(state: BubbleState) {
        bubble?.state = state
        val cap = caption ?: return
        when (state) {
            BubbleState.IDLE -> { stopTimer(); cap.visibility = View.GONE }
            BubbleState.RECORDING -> {
                recordSeconds = 0
                cap.configure(CaptionView.Mode.RECORDING, bubble?.level ?: 0f, 0)
                cap.visibility = View.VISIBLE
                positionCaption()
                mainHandler.postDelayed(timerTick, 1000)
            }
            BubbleState.TRANSCRIBING -> showCaption(CaptionView.Mode.TRANSCRIBING)
            BubbleState.PASTE -> showCaption(CaptionView.Mode.PASTE)
            BubbleState.ERROR -> showCaption(CaptionView.Mode.ERROR)
        }
    }

    private fun showCaption(mode: CaptionView.Mode) {
        stopTimer()
        val cap = caption ?: return
        cap.configure(mode)
        cap.visibility = View.VISIBLE
        positionCaption()
    }

    private fun stopTimer() = mainHandler.removeCallbacks(timerTick)

    /** Tuck the caption beside the visible bubble — left if the bubble hugs the right edge. */
    private fun positionCaption() {
        val view = bubble ?: return
        val cap = caption ?: return
        if (cap.visibility != View.VISIBLE) return
        val (sw, _) = screenSize()
        val w = cap.viewWidth
        val h = cap.viewHeight
        val pad = dpx(34f) // tuck under the bubble's halo/shadow padding
        val onRight = params.x + view.viewSize / 2 > sw / 2
        captionParams.x = if (onRight) (params.x - w + pad).coerceAtLeast(0) else params.x + view.viewSize - pad
        captionParams.y = params.y + (view.viewSize - h) / 2
        runCatching { windowManager.updateViewLayout(cap, captionParams) }
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
        if (sensitiveActive) {
            // Board F4: refuse to record into password/banking fields; the bubble is already dimmed.
            toast("Dictation is off in password fields — for your security.")
            return
        }
        // Warn up front (not after the recording) if there's nowhere to insert the result.
        if (QuillAccessibilityService.instance?.isEditableFocused() != true) {
            toast("No text input selected!")
            return
        }
        startRecording(view)
    }

    private fun toast(message: String) =
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()

    private fun startRecording(view: BubbleView) {
        if (!audioRecorder.hasMicPermission()) {
            Log.w(TAG, "Mic permission not granted; cannot record (onboarding handles the grant).")
            applyState(BubbleState.ERROR)
            return
        }
        val output = File(cacheDir, "quill_${System.currentTimeMillis()}.wav")
        currentRecording = output
        recordingPackage = QuillAccessibilityService.instance?.focusedPackageName()
        setForeground(microphone = true)
        applyState(BubbleState.RECORDING)
        audioRecorder.start(
            output = output,
            onAmplitude = { rms ->
                mainHandler.post {
                    val level = (rms / 8000f).coerceIn(0f, 1f)
                    bubble?.level = level
                    caption?.setLevel(level)
                }
            },
            onWarning = { mainHandler.post { Log.i(TAG, "Approaching session cap.") } },
            onFinished = { file, reason -> mainHandler.post { onRecordingFinished(file, reason) } },
        )
    }

    private fun onRecordingFinished(file: File, reason: StopReason) {
        setForeground(microphone = false)
        bubble?.let { it.level = 0f }
        Log.i(TAG, "WAV saved: ${file.name} (${file.length()} bytes), reason=$reason")
        if (reason == StopReason.ERROR) {
            applyState(BubbleState.ERROR)
            return
        }
        currentRecording = file
        transcribeAndInject(file)
    }

    /**
     * M6 end-to-end: transcribe the WAV against the user's BYOK provider, then inject the transcript
     * into the focused field. A dictation is never silently lost (PRD §5.7): on transcription failure
     * the temp WAV is kept for retry; on a successful transcript+insert it is deleted (spec §4.3).
     */
    private fun transcribeAndInject(wav: File) {
        applyState(BubbleState.TRANSCRIBING)
        scope.launch {
            val key = secretStore.get(SecretStore.ALIAS_STT_KEY)
            if (key.isNullOrBlank()) {
                Log.w(TAG, "No API key set — add one in Quill settings.")
                applyState(BubbleState.ERROR)
                return@launch
            }
            val settings = settingsRepository.settings.first()
            val client = TranscriptionClientFactory.create(settings.provider, apiKey = { key })
            // "auto" → null (let the provider detect). English seeds the Hinglish romanization prompt.
            val language = settings.languageHint.takeUnless { it == QuillSettings.LANGUAGE_AUTO }
            val result = client.transcribe(
                audio = wav,
                opts = TranscriptionOptions(
                    model = settings.sttModel,
                    language = language,
                    prompt = RomanizePrompt.promptFor(language),
                ),
            )
            result.fold(
                onSuccess = {
                    // Save on a successful transcription (gated), BEFORE insertion — a transcript that
                    // fails to land is exactly when a saved copy is the safety net (M9).
                    val pkg = recordingPackage
                    historyRepository.maybeSave(
                        historyEnabled = settings.historyEnabled,
                        privateMode = settings.privateMode,
                        retentionMinutes = settings.historyRetentionMinutes,
                        text = it.text,
                        packageName = pkg,
                        appLabel = appLabelFor(pkg),
                        timestamp = System.currentTimeMillis(),
                    )
                    onTranscript(wav, it.text)
                },
                onFailure = { err ->
                    Log.w(TAG, "Transcription failed: ${err.message}") // keep WAV for retry
                    applyState(BubbleState.ERROR)
                },
            )
        }
    }

    private fun onTranscript(wav: File, text: String) {
        if (text.isBlank()) {
            Log.i(TAG, "Empty transcript — didn't catch that.")
            applyState(BubbleState.ERROR)
            return
        }
        val a11y = QuillAccessibilityService.instance
        if (a11y == null) {
            Log.w(TAG, "Accessibility service not connected; cannot insert.")
            applyState(BubbleState.ERROR)
            return
        }
        when (a11y.injectText(text)) {
            InsertResult.Success -> {
                applyState(BubbleState.IDLE)
                wav.delete()
            }
            InsertResult.NeedsClipboardFallback -> {
                applyState(BubbleState.PASTE) // transcript is on the clipboard; tap to paste
                wav.delete()
            }
            InsertResult.RefusedSensitive, InsertResult.NoEditableField -> {
                applyState(BubbleState.ERROR)
                wav.delete()
            }
        }
    }

    /** Human-readable app name for [pkg] (the history "target app"); null/raw pkg if unresolvable. */
    private fun appLabelFor(pkg: String?): String? {
        pkg ?: return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull() ?: pkg
    }

    private fun moveBy(dxPx: Float, dyPx: Float) {
        val view = bubble ?: return
        val (sw, sh) = screenSize()
        params.x = BubbleGeometry.clamp(params.x + dxPx.toInt(), 0, (sw - view.viewSize).coerceAtLeast(0))
        params.y = BubbleGeometry.clampY(params.y + dyPx.toInt(), view.viewSize, sh)
        windowManager.updateViewLayout(view, params)
        positionCaption()
    }

    private fun snapToEdge() {
        val view = bubble ?: return
        val (sw, _) = screenSize()
        params.x = BubbleGeometry.snapTargetX(params.x, view.viewSize, sw)
        windowManager.updateViewLayout(view, params)
        positionCaption()
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
        runCatching { scope.cancel() }
        QuillAccessibilityService.sensitiveListener = null
        stopTimer()
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        runCatching { audioRecorder.stop() }
        caption?.let { runCatching { windowManager.removeView(it) } }
        caption = null
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
        const val ACTION_DEBUG_SET_KEY = "com.tihor.quill.DEBUG_SET_KEY"

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
