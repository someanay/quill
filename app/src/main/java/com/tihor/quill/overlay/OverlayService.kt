package com.tihor.quill.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private var cancelButton: CancelButtonView? = null
    private lateinit var cancelParams: WindowManager.LayoutParams
    private var recordSeconds = 0
    private var sensitiveActive = false

    /** True while a long-press dictation is in flight — the transcript goes to the clipboard, not a field. */
    private var clipboardMode = false

    /** The in-flight transcription, held so the Cancel button can abort it. */
    private var transcriptionJob: Job? = null

    /** Auto-clears the "Copied to clipboard" confirmation back to idle after a moment. */
    private val idleReset = Runnable { applyState(BubbleState.IDLE) }

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

    // Last bubble appearance from Visual preferences; re-applied whenever the bubble is (re)created.
    private var bubbleSize = QuillSettings.BUBBLE_SIZE_MEDIUM
    private var appTheme = QuillSettings.APP_THEME_SYSTEM

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
        observeAppearance()
        registerDebugReceiverIfDebug()
    }

    /** Live-apply bubble size/theme from Visual preferences (settings.bubble) as the user changes them. */
    private fun observeAppearance() {
        scope.launch {
            settingsRepository.settings
                .map { it.bubbleSize to it.appTheme }
                .distinctUntilChanged()
                .collect { (size, theme) ->
                    bubbleSize = size
                    appTheme = theme
                    applyAppearance()
                }
        }
    }

    private fun applyAppearance() {
        val view = bubble ?: return
        view.sizeScale = scaleFor(bubbleSize)
        view.darkTheme = isDarkTheme(appTheme)
        // A size change alters the view's footprint: re-layout, then keep it on-screen.
        runCatching { windowManager.updateViewLayout(view, params) }
        val (sw, _) = screenSize()
        params.x = BubbleGeometry.clamp(params.x, 0, (sw - view.viewSize).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
        positionCaption()
        positionCancelButton()
    }

    private fun scaleFor(size: String): Float = when (size) {
        QuillSettings.BUBBLE_SIZE_SMALL -> 0.82f
        QuillSettings.BUBBLE_SIZE_LARGE -> 1.22f
        else -> 1f
    }

    private fun isDarkTheme(theme: String): Boolean = when (theme) {
        QuillSettings.APP_THEME_LIGHT -> false
        QuillSettings.APP_THEME_DARK -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // "Match system" theme needs to follow day/night flips while the bubble is live.
        applyAppearance()
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
                    // Drive the real injection path (incl. the failure → clipboard + notification
                    // fallback) with a fake transcript — no mic/STT needed. Run it with no text field
                    // focused (e.g. on the home screen) to exercise the failure branch.
                    ACTION_DEBUG_FAKE_TRANSCRIPT -> {
                        val text = intent.getStringExtra("text") ?: "debug transcript"
                        onTranscript(File(cacheDir, "quill_debug_${System.currentTimeMillis()}.wav"), text)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_RECORD_START)
            addAction(ACTION_DEBUG_RECORD_STOP)
            addAction(ACTION_DEBUG_SET_KEY)
            addAction(ACTION_DEBUG_FAKE_TRANSCRIPT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        debugReceiver = receiver
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quill bubble", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Keeps the dictation bubble available over other apps." },
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Quill dictation alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when a transcript couldn't be inserted and was copied instead." },
        )
    }

    /**
     * Foreground-service promotion. `specialUse` keeps the bubble alive over other apps (M1); the
     * `microphone` type is added only while recording (M4) — the visible overlay is what legally lets
     * the mic FGS start from the background (spec §5). We drop the mic type when recording stops so
     * the mic-in-use indicator and capture don't linger.
     */
    private fun setForeground(microphone: Boolean) {
        // Long-press now dictates to the clipboard, so the bubble is dismissed from here instead.
        val stopIntent = Intent(this, OverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (microphone) "Quill is listening" else "Quill bubble active")
            .setContentText(if (microphone) "Recording… tap the bubble to stop." else "Tap the bubble to dictate.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(0, "Dismiss bubble", stopPending)
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
        view.onLongPress = { onBubbleLongPress(view) }  // long-press dictates to the clipboard
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
        applyAppearance() // honour the persisted size/theme on (re)creation

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

        // Cancel button (board E): floats above the bubble while transcribing / after a failure.
        val cancel = CancelButtonView(this, onTap = { onCancel() })
        cancelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        cancel.visibility = View.GONE
        windowManager.addView(cancel, cancelParams)
        cancelButton = cancel

        Log.i(TAG, "Bubble added at x=${params.x} y=${params.y}")
    }

    /** Drive the bubble state, its caption, and the Cancel button together (board E). */
    private fun applyState(state: BubbleState) {
        bubble?.state = state
        updateCancelButton(state)
        val cap = caption ?: return
        when (state) {
            BubbleState.IDLE -> {
                stopTimer()
                clipboardMode = false
                cap.clipboardTarget = false
                cap.visibility = View.GONE
            }
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

    /** The Cancel button is offered only while transcribing or after a failure (board E). */
    private fun updateCancelButton(state: BubbleState) {
        val btn = cancelButton ?: return
        val show = state == BubbleState.TRANSCRIBING || state == BubbleState.ERROR
        btn.visibility = if (show) View.VISIBLE else View.GONE
        if (show) positionCancelButton()
    }

    /** Cancel button: abort an in-flight transcription (or dismiss an error) and return to idle. */
    private fun onCancel() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        mainHandler.removeCallbacks(idleReset)
        applyState(BubbleState.IDLE)
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

    /** Centre the Cancel button over the bubble and tuck it just above (flips below near the top edge). */
    private fun positionCancelButton() {
        val view = bubble ?: return
        val btn = cancelButton ?: return
        if (btn.visibility != View.VISIBLE) return
        val (sw, _) = screenSize()
        val w = btn.viewWidth
        val centerX = params.x + view.viewSize / 2
        cancelParams.x = BubbleGeometry.clamp(centerX - w / 2, 0, (sw - w).coerceAtLeast(0))
        val gap = dpx(4f)
        val bubbleTop = params.y + view.contentInset
        var y = bubbleTop - (btn.viewHeight - btn.bottomInset) - gap
        if (y < 0) {
            // No room above — drop it just below the bubble instead.
            y = params.y + view.viewSize - view.contentInset - btn.bottomInset + gap
        }
        cancelParams.y = y
        runCatching { windowManager.updateViewLayout(btn, cancelParams) }
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
        // A tap on the "Copied to clipboard" confirmation just clears it.
        if (bubble?.state == BubbleState.PASTE && clipboardMode) {
            applyState(BubbleState.IDLE)
            return
        }
        if (sensitiveActive) {
            // Board F4: refuse to record into password/banking fields; the bubble is already dimmed.
            toast("Dictation is off in password fields — for your security.")
            return
        }
        // Warn up front (not after the recording) if there's nowhere to insert the result.
        if (QuillAccessibilityService.instance?.isEditableFocused() != true) {
            toast("No text input selected — long-press to dictate to the clipboard.")
            return
        }
        clipboardMode = false
        startRecording(view)
    }

    /**
     * Long-press dictates to the **clipboard** instead of a focused field (the user's explicit ask):
     * it records regardless of whether any text input is selected, transcribes, copies the result to
     * the clipboard, and shows a "Copied to clipboard" confirmation. A long-press while recording
     * simply stops the current take.
     */
    private fun onBubbleLongPress(view: BubbleView) {
        if (audioRecorder.isRecording) {
            audioRecorder.requestStop()
            return
        }
        clipboardMode = true
        caption?.clipboardTarget = true
        toast("Dictating to clipboard…")
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
        transcriptionJob = scope.launch {
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
        // Long-press path: the transcript belongs on the clipboard, not in a field. Skip injection
        // entirely (no focused field required) and show the "Copied to clipboard" confirmation.
        if (clipboardMode) {
            copyTranscriptToClipboard(text)
            bubble?.state = BubbleState.PASTE
            updateCancelButton(BubbleState.PASTE)
            caption?.let {
                it.clipboardTarget = true
                it.configure(CaptionView.Mode.PASTE)
                it.visibility = View.VISIBLE
            }
            stopTimer()
            positionCaption()
            mainHandler.removeCallbacks(idleReset)
            mainHandler.postDelayed(idleReset, 3500)
            wav.delete()
            return
        }
        val a11y = QuillAccessibilityService.instance
        if (a11y == null) {
            // We have a transcript but no way to inject it — never lose it: copy + notify.
            Log.w(TAG, "Accessibility service not connected; copying transcript to clipboard.")
            injectionFailed(text)
            wav.delete()
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
                // Injection failed (sensitive field / nothing focused). Per the "never fail to
                // transcribe — fail to inject" rule: keep the transcript on the clipboard and tell
                // the user, instead of silently dropping it.
                injectionFailed(text)
                wav.delete()
            }
        }
    }

    /** Couldn't insert: stash the transcript on the clipboard and post a heads-up notification. */
    private fun injectionFailed(text: String) {
        copyTranscriptToClipboard(text)
        notifyInjectionFailed(text)
        applyState(BubbleState.ERROR)
    }

    private fun copyTranscriptToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Quill dictation", text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
    }

    private fun notifyInjectionFailed(text: String) {
        val preview = text.trim().take(120)
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Copied to clipboard")
            .setContentText("Couldn't insert here — paste your dictation manually.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, notification)
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
        positionCancelButton()
    }

    private fun snapToEdge() {
        val view = bubble ?: return
        val (sw, _) = screenSize()
        params.x = BubbleGeometry.snapTargetX(params.x, view.viewSize, sw)
        windowManager.updateViewLayout(view, params)
        positionCaption()
        positionCancelButton()
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
        mainHandler.removeCallbacks(idleReset)
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        runCatching { audioRecorder.stop() }
        cancelButton?.let { runCatching { windowManager.removeView(it) } }
        cancelButton = null
        caption?.let { runCatching { windowManager.removeView(it) } }
        caption = null
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Quill.Overlay"
        private const val CHANNEL_ID = "quill_overlay"
        private const val ALERT_CHANNEL_ID = "quill_alerts"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.tihor.quill.overlay.STOP"
        const val ACTION_DEBUG_RECORD_START = "com.tihor.quill.DEBUG_RECORD_START"
        const val ACTION_DEBUG_RECORD_STOP = "com.tihor.quill.DEBUG_RECORD_STOP"
        const val ACTION_DEBUG_SET_KEY = "com.tihor.quill.DEBUG_SET_KEY"
        const val ACTION_DEBUG_FAKE_TRANSCRIPT = "com.tihor.quill.DEBUG_FAKE_TRANSCRIPT"

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
