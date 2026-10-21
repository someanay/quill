package com.tihor.quill.overlay

import android.animation.Animator
import android.animation.ValueAnimator
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tihor.quill.BuildConfig
import com.tihor.quill.accessibility.AccessibilityUtil
import com.tihor.quill.accessibility.InsertResult
import com.tihor.quill.accessibility.QuillAccessibilityService
import com.tihor.quill.copy.Copy
import com.tihor.quill.copy.CopyKeys
import com.tihor.quill.audio.AudioRecorder
import com.tihor.quill.audio.StopReason
import com.tihor.quill.history.HistoryRepository
import com.tihor.quill.secrets.KeystoreSecretStore
import com.tihor.quill.secrets.SecretStore
import com.tihor.quill.secrets.SttKeyStore
import com.tihor.quill.settings.BubbleVisibilityStore
import com.tihor.quill.settings.CustomWordsStore
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.settings.SttProvider
import com.tihor.quill.settings.TranscriptionClientFactory
import com.tihor.quill.cleanup.CleanupClient
import com.tihor.quill.cleanup.CleanupKeyPolicy
import com.tihor.quill.style.WritingStyleResolver
import com.tihor.quill.style.WritingStyleStore
import com.tihor.quill.cleanup.CleanupPipeline
import com.tihor.quill.stt.OpenAiCompatibleTranscriptionClient
import com.tihor.quill.stt.RomanizePrompt
import com.tihor.quill.stt.TranscriptionError
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
import kotlinx.coroutines.runBlocking
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
    private var binTarget: BinTargetView? = null
    private lateinit var binParams: WindowManager.LayoutParams
    private var recordSeconds = 0
    private var sensitiveActive = false

    /** The running "fly into the bin" animation, held so a new drag / teardown can interrupt it. */
    private var dismissAnimator: ValueAnimator? = null

    /**
     * Set just before the [autoReturn] timer writes `visible = true`, so [applyVisibility] can tell an
     * automatic return from a user-initiated summon and explain itself with a toast. Consumed on read.
     */
    private var autoReturnFired = false

    /**
     * Set by [onBubbleDragEnd] just before it hides, so [applyVisibility] can tell a **transient**
     * drag-dismiss from a **deliberate** hide. Only the former arms [autoReturn]: flicking the bubble
     * into the bin means "not right now", but switching the Home master toggle off is a stated
     * preference, and a preference that silently flips itself back on ten minutes later isn't a
     * preference. Same respect-the-user's-intent line M11 draws around a force-stop. Consumed on read.
     */
    private var hiddenByDrag = false
    private var dragReturnMinutes = 10

    /** True while a long-press dictation is in flight — the transcript goes to the clipboard, not a field. */
    private var clipboardMode = false

    /** The in-flight transcription, held so the Cancel button can abort it. */
    private var transcriptionJob: Job? = null

    /** Auto-clears the "Copied to clipboard" confirmation back to idle after a moment. */
    private val idleReset = Runnable { applyState(BubbleState.IDLE) }

    /**
     * Sensitive-clip auto-expiry (M11): wipe a failed-injection transcript off the clipboard ~60 s
     * later, but only if it's still Quill's clip AND the same one (matched by embedded token) —
     * never clobber something the user copied since. Nulled when the user copies something new.
     */
    private var pendingClearToken: String? = null

    private val clearSensitiveClip = Runnable {
        val token = pendingClearToken ?: return@Runnable
        val cb = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return@Runnable
        if (ClipboardHygiene.shouldClear(cb.primaryClipDescription?.label, token)) {
            runCatching { cb.clearPrimaryClip() }
        }
        pendingClearToken = null
    }

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
    private val sttKeyStore: SttKeyStore by lazy { SttKeyStore(secretStore) }
    private val settingsRepository by lazy { SettingsRepository(this) }
    private val customWordsStore by lazy { CustomWordsStore(this) }
    private val writingStyleStore by lazy { WritingStyleStore(this) }
    private val historyRepository by lazy { HistoryRepository(this) }
    private val visibilityStore by lazy { BubbleVisibilityStore(this) }

    /**
     * v1.1 auto-return: a hidden bubble comes back on its own after [AutoReturn.DEFAULT_DELAY_MS].
     * Runs on the service's own [scope] — the FGS stays alive while hidden (Option A), so this needs
     * no AlarmManager and therefore no Play-restricted alarm permission (see [AutoReturn]).
     */
    private val autoReturn by lazy {
        AutoReturn(scope) {
            autoReturnFired = true
            settingsRepository.setBubbleVisible(true)
        }
    }

    // --- v1.1 shake-to-summon: only listening while the bubble is hidden ---
    private val sensorManager: SensorManager? by lazy { getSystemService(SensorManager::class.java) }
    private val shakeDetector = ShakeDetector()
    private var shakeListening = false
    private val shakeListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            // SensorEvent.timestamp is nanos on a monotonic clock; ShakeDetector wants millis.
            val whenMs = event.timestamp / 1_000_000L
            if (shakeDetector.onSample(event.values[0], event.values[1], event.values[2], whenMs)) {
                Log.i(TAG, "Shake detected — summoning the bubble.")
                scope.launch { settingsRepository.setBubbleVisible(true) }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Last-applied notification state, so [refreshNotification] re-posts the correct title/text. */
    private var micActive = false
    private var currentVisible = true

    // Last bubble appearance from Visual preferences; re-applied whenever the bubble is (re)created.
    private var bubbleSize = QuillSettings.BUBBLE_SIZE_MEDIUM
    private var appTheme = QuillSettings.APP_THEME_SYSTEM

    /** Package of the field where the current recording started — saved as the history "target app". */
    private var recordingPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        // Seed from the synchronous mirror so the first notification + bubble paint match the persisted
        // shown/hidden state (no flash) before the visibility Flow delivers its first value.
        currentVisible = visibilityStore.visible
        setForeground(microphone = false)
        addBubble()
        // Board F4: dim the bubble while a password/banking field is focused.
        QuillAccessibilityService.sensitiveListener = { s -> mainHandler.post { onSensitiveField(s) } }
        onSensitiveField(QuillAccessibilityService.sensitiveFieldFocused)
        observeAppearance()
        observeVisibility()
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

    /**
     * Observe the unified visibility state (the single source of truth, [QuillSettings.bubbleVisible]).
     * The Home toggle, drag-dismiss, and the notification "Show Quill" action all write this Flow via
     * [SettingsRepository.setBubbleVisible]; the service is the **one** place that applies it to the
     * actual bubble window — so all three entry points stay in sync without anyone poking the view directly.
     */
    private fun observeVisibility() {
        scope.launch {
            settingsRepository.settings
                .map { it.bubbleVisible }
                .distinctUntilChanged()
                .collect { applyVisibility(it) }
        }
    }

    /**
     * Apply the unified visibility state to the bubble window. Hiding is **not** stopping: the FGS and
     * its notification stay up (the notification's "Show Quill" action is the way back). Re-posts the
     * notification so its title/text reflect shown vs hidden.
     *
     * This is also the single arm/disarm point for both v1.1 return paths — the ~10-minute
     * [autoReturn] timer and the shake listener. Because *every* show/hide funnels through
     * [SettingsRepository.setBubbleVisible] → the visibility Flow → here, hooking them at this one
     * place means no entry point can leave a stacked timer or a sensor running behind it: showing
     * always cancels + unregisters, hiding always registers the sensor.
     *
     * The timer is the one asymmetric part: it arms only for a **transient** hide (a drag into the
     * bin, flagged by [hiddenByDrag]), never for a deliberate one (the Home master toggle). Shake
     * stays available either way — that's the user asking for it back, not the app deciding.
     */
    private fun applyVisibility(visible: Boolean) {
        currentVisible = visible
        bubble?.let { view ->
            if (visible) {
                // Undo any dismiss transform *before* showing: a summon that lands while a
                // fly-into-bin is still running would otherwise reveal a half-shrunk bubble.
                cancelDismissAnimation()
                resetDismissTransform()
                view.visibility = View.VISIBLE
                applyAppearance()
                applyState(BubbleState.IDLE) // summon/show returns to the idle baseline
            } else {
                mainHandler.removeCallbacks(idleReset)
                applyState(BubbleState.IDLE)
                view.visibility = View.GONE
                caption?.visibility = View.GONE
                cancelButton?.visibility = View.GONE
                // The bubble is off-screen now, so undoing the fly-into-bin transform can't flash.
                resetDismissTransform()
            }
        }
        if (visible) {
            autoReturn.cancel()   // any early summon (notification / Home toggle / shake) disarms it
            stopShakeListening()
            if (autoReturnFired) {
                autoReturnFired = false
                toast(Copy.repository(this).get(CopyKeys.BUBBLE_AUTORETURN_TOAST))
            }
        } else {
            val transient = hiddenByDrag
            hiddenByDrag = false
            // Drag-dismiss → come back on your own. Home toggle off (and a hidden-state restore after
            // an OEM kill, which also lands here with the flag clear) → stay gone until asked.
            if (transient) autoReturn.schedule(dragReturnMinutes * 60_000L) else autoReturn.cancel()
            startShakeListening()
        }
        hideBinTarget(animate = false)
        refreshNotification()
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
                    // Per-provider aliases (decision 3): optional --es provider <id> targets a
                    // specific provider's slot; default Groq (the historical single-key behaviour).
                    ACTION_DEBUG_SET_KEY ->
                        intent.getStringExtra("key")?.let {
                            sttKeyStore.put(SttProvider.fromId(intent.getStringExtra("provider") ?: SttProvider.GROQ.id), it)
                        }
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
        micActive = microphone
        val notification = buildNotification(microphone, currentVisible)
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

    /**
     * Re-post the FGS notification to reflect the current shown/hidden (and recording) state — keeps the
     * "Show Quill" action present and the title/text accurate when hidden, **without** touching the
     * foreground-service type (so a refresh on hide/show never drops the mic FGS).
     */
    private fun refreshNotification() {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(micActive, currentVisible))
        }
    }

    /**
     * Build the single FGS notification. Content reflects state (recording / hidden / active). Both
     * actions are always present: "Show Quill" ([ACTION_SHOW]) summons a hidden bubble back (no-op when
     * shown) — the reliable way back, no timer/sensor; "Dismiss bubble" ([ACTION_STOP]) is the explicit
     * full-stop (distinct from hide). **Tapping the body** also summons the bubble (same [ACTION_SHOW],
     * not a MainActivity launch) and must not dismiss the ongoing notification. All strings soft-coded.
     */
    private fun buildNotification(microphone: Boolean, visible: Boolean): android.app.Notification {
        val copy = Copy.repository(this)
        val (titleKey, textKey) = when {
            microphone -> CopyKeys.NOTIFICATION_TITLE_RECORDING to CopyKeys.NOTIFICATION_TEXT_RECORDING
            !visible -> CopyKeys.NOTIFICATION_TITLE_HIDDEN to CopyKeys.NOTIFICATION_TEXT_HIDDEN
            else -> CopyKeys.NOTIFICATION_TITLE_ACTIVE to CopyKeys.NOTIFICATION_TEXT_ACTIVE
        }
        val showPending = PendingIntent.getService(
            this, 2, Intent(this, OverlayService::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPending = PendingIntent.getService(
            this, 0, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Body tap → summon the bubble (same ACTION_SHOW), without opening the app. Distinct requestCode
        // from the action button (2) so the two PendingIntents don't collide.
        val bodyPending = PendingIntent.getService(
            this, 3, Intent(this, OverlayService::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(copy.get(titleKey))
            .setContentText(copy.get(textKey))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(bodyPending)
            .setAutoCancel(false) // ongoing FGS notification: a body tap summons, never dismisses it
            .addAction(0, copy.get(CopyKeys.NOTIFICATION_ACTION_SHOW), showPending)
            .addAction(0, copy.get(CopyKeys.NOTIFICATION_ACTION_STOP), stopPending)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Explicit full-stop ("Dismiss bubble"): clear both desired-state flags so neither boot nor
            // foreground recovery resurrects the service and the Home toggle reads "off", then tear down.
            // A deliberate teardown, so a brief blocking write is acceptable (mirrors BootReceiver).
            runBlocking {
                settingsRepository.setBubbleActive(false)
                settingsRepository.setBubbleVisible(false)
            }
            // A full-stop must not leave a v1.1 return path armed: the auto-return timer would try to
            // re-show a bubble the user explicitly dismissed, and the sensor would keep draining.
            // onDestroy repeats both (scope.cancel also kills the timer) — this just makes the
            // intent explicit at the one place "hidden" and "stopped" mean different things.
            autoReturn.cancel()
            stopShakeListening()
            stopSelf()
            return START_NOT_STICKY
        }
        // Every start() uses startForegroundService(), which arms the framework watchdog requiring a
        // startForeground() call on EVERY delivery — including when the service is already running (the
        // Home toggle off→on start()s the live service). Re-promote with the current mic state so a
        // re-delivery can't crash with ForegroundServiceDidNotStartInTimeException. onCreate/startRecording
        // also promote, so re-promoting here is idempotent and never changes the mic FGS type.
        setForeground(micActive)
        // Recreate the window if a re-start arrives after the system trimmed us.
        if (bubble == null) addBubble()
        // Notification "Show Quill": summon a hidden bubble back by writing the unified visibility state
        // (the observer re-shows it and the Home toggle reflects "on" live). No-op if already shown.
        if (intent?.action == ACTION_SHOW) scope.launch { settingsRepository.setBubbleVisible(true) }
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
        view.onDragStart = { onBubbleDragStart() }
        view.onDrag = { dx, dy -> moveBy(dx, dy); updateBinTarget() }
        view.onDragEnd = { onBubbleDragEnd() }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_SECURE,
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

        // Drag-to-dismiss bin target (v1.1). Added **once here** and removed in onDestroy alongside
        // the other overlay windows — never added/removed per drag — so there is no path (including
        // the service being killed mid-drag) that can leak the window. It stays GONE until a drag
        // brings the bubble near the bottom. NOT_TOUCHABLE as well as NOT_FOCUSABLE: it sits under
        // the user's finger during exactly the gesture it's reacting to and must never steal it.
        val copy = Copy.repository(this)
        val bin = BinTargetView(
            this,
            label = copy.get(CopyKeys.BUBBLE_BIN_LABEL),
            activeLabel = copy.get(CopyKeys.BUBBLE_BIN_LABEL_ACTIVE),
        )
        binParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((sw - bin.viewWidth) / 2).coerceAtLeast(0)   // bottom-centre
            y = (sh - dpx(BIN_BOTTOM_MARGIN_DP) - bin.viewHeight).coerceAtLeast(0)
        }
        bin.visibility = View.GONE
        windowManager.addView(bin, binParams)
        binTarget = bin

        // Honour the persisted visibility across a service restart (START_STICKY / boot / foreground
        // recovery): a bubble the user hid stays hidden until summoned. Read synchronously from the
        // mirror so there's no flash before the visibility Flow delivers its first value.
        currentVisible = visibilityStore.visible
        if (!currentVisible) view.visibility = View.GONE

        Log.i(TAG, "Bubble added at x=${params.x} y=${params.y} visible=$currentVisible")
    }

    /** A fresh drag always cancels a fly-into-bin still settling from the previous one. */
    private fun onBubbleDragStart() {
        cancelDismissAnimation()
        resetDismissTransform()
    }

    /**
     * The bubble's visible bottom edge in window coordinates — what the dismiss-zone / proximity maths
     * are measured against (excludes the transparent halo + shadow padding, so the threshold matches
     * what the user actually sees).
     */
    private fun bubbleBottomY(view: BubbleView): Int = params.y + view.viewSize - view.contentInset

    /**
     * Called on every drag move: fade/grow the bin target in as the bubble nears the bottom, and
     * "activate" it (red + pop) once releasing would genuinely hide the bubble — so the outcome is
     * visible *before* the user lets go.
     */
    private fun updateBinTarget() {
        val view = bubble ?: return
        val bin = binTarget ?: return
        val (_, sh) = screenSize()
        val bottom = bubbleBottomY(view)
        val proximity = BubbleGeometry.dismissProximity(
            bubbleBottomY = bottom,
            screenHeight = sh,
            thresholdPx = dpx(DISMISS_ZONE_DP),
            approachPx = dpx(BIN_APPROACH_DP),
        )
        bin.progress = proximity
        bin.active = BubbleGeometry.isInDismissZone(bottom, sh, dpx(DISMISS_ZONE_DP))
        bin.visibility = if (proximity > 0f) View.VISIBLE else View.GONE
    }

    /**
     * Drag-release decision (this service owns the window position): if the bubble's visible bottom
     * edge landed in the bottom dismiss zone, animate it into the bin and hide it (Wispr-Flow-style);
     * otherwise fade the bin out and edge-snap as usual.
     */
    private fun onBubbleDragEnd() {
        val view = bubble ?: return
        val (_, sh) = screenSize()
        if (BubbleGeometry.isInDismissZone(bubbleBottomY(view), sh, dpx(DISMISS_ZONE_DP))) {
            // Hide, don't stop: write the unified visibility state. The observer hides the view, the
            // Home toggle reflects "off" live, the FGS + notification stay up for summon, and
            // applyVisibility arms the auto-return timer + shake listener.
            animateIntoBin {
                hideBinTarget(animate = false)
                // Flag first: this is the *transient* hide, the only one that earns an auto-return.
                scope.launch {
                    dragReturnMinutes = settingsRepository.settings.first().bubbleReturnMinutes
                    val copy = Copy.repository(this@OverlayService)
                    toast(if (dragReturnMinutes == 0) copy.get(CopyKeys.BUBBLE_DISMISS_NEVER)
                        else copy.get(CopyKeys.BUBBLE_DISMISS_TIMED, dragReturnMinutes))
                    hiddenByDrag = true
                    settingsRepository.setBubbleVisible(false)
                }
            }
        } else {
            hideBinTarget(animate = true)
            snapToEdge()
        }
    }

    /** The bin target circle's centre in screen coordinates — where a dismissed bubble flies to. */
    private fun binCenter(): Pair<Float, Float> {
        val bin = binTarget
        val (sw, sh) = screenSize()
        if (bin == null) return sw / 2f to (sh - dpx(BIN_BOTTOM_MARGIN_DP)).toFloat()
        return (binParams.x + bin.viewWidth / 2f) to (binParams.y + bin.circleCenterY).toFloat()
    }

    /**
     * Short "sucked into the bin" animation: the bubble's window slides toward the bin centre while
     * the view scales down, then [onEnd] hides it for real.
     *
     * Two deliberate choices here. **Only scale + window position are animated — never `alpha`**: the
     * bubble's alpha already has an owner (the sensitive-field dimmer in [onSensitiveField] sets
     * 0.45), and a second consumer would fight it, e.g. leaving the bubble permanently at 45 % if a
     * password field gained focus mid-animation. Shrinking to nothing reads the same. And it's the
     * **window** that moves, not `translationX/Y`: the bubble's window is `WRAP_CONTENT`-sized to the
     * view, so a translation would simply clip at the window's edge, whereas scaling shrinks inward
     * and is safe.
     *
     * Interruptible: [cancelDismissAnimation] (a new drag, a show, teardown) still runs [onEnd]-free
     * and [resetDismissTransform] puts the bubble back, so a cancelled dismiss never strands a
     * half-shrunk bubble.
     */
    private fun animateIntoBin(onEnd: () -> Unit) {
        val view = bubble ?: return onEnd()
        cancelDismissAnimation()
        val startX = params.x
        val startY = params.y
        val (binCx, binCy) = binCenter()
        val targetX = (binCx - view.viewSize / 2f)
        val targetY = (binCy - view.viewSize / 2f)
        dismissAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DISMISS_ANIM_MS
            interpolator = AccelerateInterpolator(1.4f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                params.x = (startX + (targetX - startX) * t).toInt()
                params.y = (startY + (targetY - startY) * t).toInt()
                val scale = 1f - DISMISS_SHRINK * t
                view.scaleX = scale
                view.scaleY = scale
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    dismissAnimator = null
                    // Restore the pre-dismiss position (edge-snapped) so summoning brings the bubble
                    // back where the user left it rather than stranded at the bin. Safe to do while
                    // it's still visible-but-tiny: applyVisibility's GONE lands within a frame or two.
                    params.y = startY
                    params.x = BubbleGeometry.snapTargetX(startX, view.viewSize, screenSize().first)
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    private fun cancelDismissAnimation() {
        dismissAnimator?.cancel()
        dismissAnimator = null
    }

    /** Undo the fly-into-bin transform so the bubble is full-size again the next time it's shown. */
    private fun resetDismissTransform() {
        val view = bubble ?: return
        view.scaleX = 1f
        view.scaleY = 1f
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** Retire the bin target — fading out on a released-outside-the-zone drag, instantly otherwise. */
    private fun hideBinTarget(animate: Boolean) {
        val bin = binTarget ?: return
        bin.active = false
        if (!animate || bin.visibility != View.VISIBLE) {
            bin.progress = 0f
            bin.visibility = View.GONE
            return
        }
        val from = bin.progress
        ValueAnimator.ofFloat(from, 0f).apply {
            duration = BIN_FADE_MS
            addUpdateListener { bin.progress = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (bin.progress <= 0f) bin.visibility = View.GONE
                }
            })
            start()
        }
    }

    // --- v1.1 shake-to-summon ---

    /**
     * Start listening for a summon shake. Registered **only while the bubble is hidden** (see
     * [applyVisibility]) — an accelerometer left running while the bubble is perfectly visible would
     * be pure battery cost for nothing. `SENSOR_DELAY_UI` (~60 ms) is plenty to resolve a 3–5 Hz
     * shake; `SENSOR_DELAY_FASTEST` would just burn power.
     */
    private fun startShakeListening() {
        if (shakeListening) return
        val manager = sensorManager ?: return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.i(TAG, "No accelerometer on this device — shake-to-summon unavailable.")
            return
        }
        shakeDetector.reset() // never carry a half-finished sequence across a hide
        shakeListening = manager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopShakeListening() {
        if (!shakeListening) return
        runCatching { sensorManager?.unregisterListener(shakeListener) }
        shakeListening = false
        shakeDetector.reset()
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
        val accessibility = QuillAccessibilityService.instance
        if (accessibility == null) {
            toast("Quill accessibility is disconnected — open Accessibility settings and enable Quill.")
            return
        }
        val hasInput = accessibility.isEditableFocused()
        if (sensitiveActive) {
            // Board F4: refuse to record into password/banking fields; the bubble is already dimmed.
            toast("Dictation is off in password fields — for your security.")
            return
        }
        // Warn up front (not after the recording) if there's nowhere to insert the result.
        if (!hasInput) {
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
            // Include retries: a quiet take must never become provider-generated filler.
            val audible = try {
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    com.tihor.quill.audio.AudioSignal.hasAudibleSignal(wav.readBytes())
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                applyState(BubbleState.ERROR); return@launch
            }
            if (!audible) {
                wav.delete(); currentRecording = null; clipboardMode = false
                applyState(BubbleState.IDLE)
                toast(Copy.repository(this@OverlayService).get(CopyKeys.RECORDING_NO_SPEECH))
                return@launch
            }
            val settings = settingsRepository.settings.first()
            // Per-provider key (decision 3): the ACTIVE provider's own slot, never another provider's.
            val key = sttKeyStore.get(settings.provider)
            if (key.isNullOrBlank()) {
                Log.w(TAG, "No API key set for the active provider — add one in Quill settings.")
                applyState(BubbleState.ERROR)
                return@launch
            }
            val customWords = customWordsStore.words.first()
            val client = TranscriptionClientFactory.create(
                settings.provider,
                apiKey = { key },
                customBaseUrl = settings.customBaseUrl,
            )
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
                    // The app that was focused when recording STARTED — already captured for history's
                    // "target app". Per-app writing style reuses it rather than adding plumbing: the
                    // user was composing in that app's context when they spoke, so that is the context
                    // to style for, even if they've since switched apps (scope doc §6a).
                    val pkg = recordingPackage
                    // Resolve fresh per dictation: override → curated (in code) → global default.
                    // Always resolves; Neutral (the shipped default) composes to the unmodified M10
                    // prompt, so this is inert until the user opts in.
                    val style = WritingStyleResolver.resolve(
                        packageName = pkg,
                        overrides = writingStyleStore.overrides.first(),
                        globalDefault = settings.writingStyleDefault,
                    )
                    // M10: tidy the raw transcript with the LLM (gated by cleanupEnabled, fail-soft —
                    // any error/timeout/empty falls back to raw), THEN apply custom-word substitution
                    // so the deterministic vocabulary wins last (decision 3). The single result is what
                    // both history and the injector receive (decision 4), all within the existing
                    // TRANSCRIBING state — no new bubble state, no recorder/FGS changes.
                    val corrected = CleanupPipeline.process(
                        raw = it.text,
                        cleanupEnabled = settings.cleanupEnabled,
                        words = customWords,
                        clean = { text ->
                            val cleanupKey = com.tihor.quill.cleanup.CleanupProviders.key(settings.cleanupProvider) {
                                sttKeyStore.get(it)
                            }
                            if (cleanupKey == null) null else runCatching {
                                CleanupClient(
                                    baseUrl = com.tihor.quill.cleanup.CleanupProviders.baseUrl(settings.cleanupProvider, settings.customBaseUrl),
                                    apiKey = { cleanupKey },
                                    style = style,
                                    model = settings.cleanupModel,
                                    lowLatencyRouting = settings.cleanupProvider == SttProvider.OPENROUTER,
                                ).clean(text).also { cleaned ->
                                    Log.i(TAG, "Cleanup provider=${settings.cleanupProvider.id} style=${style.id} applied=${cleaned != null}")
                                }
                            }.getOrElse { error ->
                                if (error is kotlinx.coroutines.CancellationException) throw error
                                null
                            }
                        },
                    )
                    // Save on a successful transcription (gated), BEFORE insertion — a transcript that
                    // fails to land is exactly when a saved copy is the safety net (M9).
                    historyRepository.maybeSave(
                        historyEnabled = settings.historyEnabled,
                        privateMode = settings.privateMode,
                        retentionMinutes = settings.historyRetentionMinutes,
                        text = corrected,
                        packageName = pkg,
                        appLabel = appLabelFor(pkg),
                        timestamp = System.currentTimeMillis(),
                    )
                    onTranscript(wav, corrected)
                },
                onFailure = { err ->
                    Log.w(TAG, "Transcription failed: ${err.message}") // keep WAV for retry
                    // Exhausted plan/credits (and Groq's indistinguishable daily-cap 429) get an
                    // explicit toast — a silent ERROR bubble reads as "the app broke", not "top up".
                    if (err is TranscriptionError.QuotaExhausted || err is TranscriptionError.RateLimited) {
                        toast(Copy.repository(this@OverlayService).get(CopyKeys.STT_QUOTA_TOAST))
                    } else {
                        // Provider response bodies can echo user content; show only our own messages.
                        toast(if (err is TranscriptionError && err !is TranscriptionError.Provider) {
                            err.message ?: "Couldn't transcribe. Try again."
                        } else "The provider couldn't transcribe this recording. Check Speech & model settings.")
                    }
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
            // User explicitly asked for this on the clipboard to paste — do NOT auto-expire it.
            copyTranscriptToClipboard(text, autoExpire = false)
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
            // We have a transcript but no way to inject it — never lose it: copy + notify. A null
            // service means it was revoked (M11): the notification offers a one-tap re-enable.
            Log.w(TAG, "Accessibility service not connected; copying transcript to clipboard.")
            injectionFailed(text, InjectionFailure.ServiceRevoked)
            wav.delete()
            return
        }
        when (val result = a11y.injectText(text, expectedPackage = recordingPackage)) {
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
                // the user, instead of silently dropping it. Distinguish the cause so the message +
                // action are correct (no "re-enable" prompt when the service is healthy).
                injectionFailed(text, InjectionFailure.classify(serviceConnected = true, result = result))
                wav.delete()
            }
        }
    }

    /** Couldn't insert: stash the transcript on the clipboard (auto-expiring) and post a heads-up. */
    private fun injectionFailed(text: String, failure: InjectionFailure) {
        copyTranscriptToClipboard(text, autoExpire = true)
        notifyInjectionFailed(text, failure)
        applyState(BubbleState.ERROR)
    }

    /**
     * Put [text] on the clipboard marked sensitive. When [autoExpire], schedule a guarded ~60 s clear
     * (M11) so an involuntary failed-injection copy doesn't linger; user-initiated copies don't expire.
     */
    private fun copyTranscriptToClipboard(text: String, autoExpire: Boolean) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val label = ClipboardHygiene.tokenLabel()
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
        mainHandler.removeCallbacks(clearSensitiveClip)
        if (autoExpire) {
            pendingClearToken = label
            mainHandler.postDelayed(clearSensitiveClip, ClipboardHygiene.SENSITIVE_CLEAR_DELAY_MS)
        }
    }

    private fun notifyInjectionFailed(text: String, failure: InjectionFailure) {
        val preview = text.trim().take(120)
        val copy = Copy.repository(this)
        val revoked = failure.offersReEnable
        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(if (revoked) copy.get(CopyKeys.INJECT_REVOKED_TITLE) else copy.get(CopyKeys.INJECT_FAILED_TITLE))
            .setContentText(if (revoked) copy.get(CopyKeys.INJECT_REVOKED_TEXT) else copy.get(CopyKeys.INJECT_FAILED_TEXT))
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
        if (revoked) {
            // One-tap deep-link back to Accessibility settings to re-enable the service.
            val reenable = PendingIntent.getActivity(
                this, 1, AccessibilityUtil.disclosureIntent(this), PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, copy.get(CopyKeys.INJECT_REENABLE_ACTION), reenable)
        }
        runCatching {
            getSystemService(NotificationManager::class.java).notify(ALERT_NOTIFICATION_ID, builder.build())
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
        isRunning = false
        // v1.1 teardown first: disarm the auto-return, release the accelerometer, and stop any
        // in-flight dismiss animation before the windows it drives are torn down.
        autoReturn.cancel()
        stopShakeListening()
        cancelDismissAnimation()
        runCatching { scope.cancel() }
        QuillAccessibilityService.sensitiveListener = null
        stopTimer()
        mainHandler.removeCallbacks(idleReset)
        mainHandler.removeCallbacks(clearSensitiveClip)
        pendingClearToken = null
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        runCatching { audioRecorder.stop() }
        binTarget?.let { runCatching { windowManager.removeView(it) } }
        binTarget = null
        cancelButton?.let { runCatching { windowManager.removeView(it) } }
        cancelButton = null
        caption?.let { runCatching { windowManager.removeView(it) } }
        caption = null
        bubble?.let { runCatching { windowManager.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    companion object {
        /**
         * Liveness signal for foreground process-kill recovery (M11): true between [onCreate] and
         * [onDestroy]. Read by [com.tihor.quill.reliability.ForegroundRecovery] to tell an incidental
         * kill from a running service.
         */
        @Volatile
        var isRunning = false
            private set

        private const val TAG = "Quill.Overlay"
        private const val CHANNEL_ID = "quill_overlay"
        private const val ALERT_CHANNEL_ID = "quill_alerts"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.tihor.quill.overlay.STOP"
        const val ACTION_SHOW = "com.tihor.quill.overlay.SHOW"

        /** Drag-release within this many dp of the screen's bottom edge dismisses (hides) the bubble. */
        private const val DISMISS_ZONE_DP = 192f

        /**
         * The bin target starts fading/growing in once the bubble's bottom edge is within this many dp
         * of the screen bottom, and is fully drawn by [DISMISS_ZONE_DP]. Must exceed [DISMISS_ZONE_DP]
         * so the target announces itself *before* the drop would take effect.
         */
        private const val BIN_APPROACH_DP = 320f

        /** Gap between the screen's bottom edge and the bin target's own bottom. */
        private const val BIN_BOTTOM_MARGIN_DP = 22f

        /** "Fly into the bin" duration — short enough to stay responsive, long enough to read. */
        private const val DISMISS_ANIM_MS = 200L

        /** How far the bubble shrinks on its way into the bin (1 - this = final scale). */
        private const val DISMISS_SHRINK = 0.85f

        /** Bin fade-out when a drag is released outside the dismiss zone. */
        private const val BIN_FADE_MS = 150L
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
