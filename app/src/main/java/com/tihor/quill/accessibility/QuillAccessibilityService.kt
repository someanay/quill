package com.tihor.quill.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.os.Build
import android.view.inputmethod.EditorInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.tihor.quill.BuildConfig
import com.tihor.quill.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tracks the focused editable field + foreground app (spec §3.1) and performs text insertion
 * (M3, spec §5). Exposes a process-wide [instance] so the overlay bubble (a separate service) can
 * ask it to inject into the currently focused field on tap.
 *
 * Not `isAccessibilityTool` — productivity use, so the disclosure + Play-declaration route (spec §8).
 */
class QuillAccessibilityService : AccessibilityService() {

    private val injector by lazy { AccessibilityTextInjector(this) }
    private var debugReceiver: BroadcastReceiver? = null

    /**
     * The editable node currently focused, captured from focus **events** (`event.source`). This is
     * more reliable than `findFocus(FOCUS_INPUT)` at inject time — for WebViews the latter returns
     * the container, losing which input (and whether it's a password) is actually focused.
     */
    @Volatile
    private var focusedEditable: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        registerDebugTriggerIfDebug()
        // Persist that accessibility has been enabled at least once, so the Home revocation banner can
        // tell "was enabled, now revoked" apart from "never enabled" (a user who declined it during
        // onboarding must not be shown a revocation/recovery prompt).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SettingsRepository(applicationContext).setAccessibilityEnabledOnce(true) }
        }
        Log.i(TAG, "Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> updateFocusedEditable(event)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Keyboard, dialogs and overlays also emit window events. Resolve the current
                // application field instead of treating each event as leaving the app.
                focusedEditable = currentInputFocus()
                updateSensitive(focusedEditable?.let(::isSensitive) == true)
            }
        }
    }

    private fun updateFocusedEditable(event: AccessibilityEvent) {
        val source = event.source
        if (source != null && source.isEditable) {
            focusedEditable = source
            val sensitive = isSensitive(source)
            updateSensitive(sensitive)
            Log.i(
                TAG,
                "Editable focused: pkg=${source.packageName} class=${source.className} " +
                    "isPassword=${source.isPassword} inputType=0x${source.inputType.toString(16)} " +
                    "sensitive=$sensitive" + if (sensitive) " -> dictation DISABLED here" else "",
            )
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            // Focus moved to a non-editable view — forget the previous field.
            focusedEditable = null
            updateSensitive(false)
            Log.d(TAG, "Focus in ${event.packageName}: non-editable ${source?.className}")
        }
    }

    /**
     * Insert [text] into the currently focused field. Called from the bubble (via [instance]) and,
     * in debug builds, from the adb broadcast trigger. Resolves focus *now* rather than from a
     * stored node, since the bubble is `FLAG_NOT_FOCUSABLE` and doesn't move input focus.
     */
    fun injectText(text: String, expectedPackage: String? = null): InsertResult {
        if (expectedPackage != null && focusedPackageName() != expectedPackage) return InsertResult.NoEditableField
        // Prefer the node captured from focus events (reliable for WebViews); refresh it for current
        // text/selection. Fall back to findFocus only if we have no tracked field.
        val focus = currentInputFocus()
        if (focus == null) {
            val editor = activeEditor()
            if (editor != null) {
                if (editorSensitive(editor)) return InsertResult.RefusedSensitive
                if (Build.VERSION.SDK_INT >= 33) {
                    val connection = inputMethod?.currentInputConnection
                    if (connection != null) {
                        return runCatching {
                            connection.commitText(text, 1, null)
                            val surrounding = connection.getSurroundingText(text.length.coerceAtMost(16000), 0, 0)
                            val confirmed = surrounding?.text?.toString()?.endsWith(text) == true
                            Log.i(TAG, "Editor connection insertion confirmed=$confirmed")
                            if (confirmed) InsertResult.Success else InsertResult.NoEditableField
                        }.getOrDefault(InsertResult.NoEditableField)
                    }
                }
            }
            Log.i(TAG, "injectText: no focused field.")
            return InsertResult.NoEditableField
        }
        val result = injector.insert(focus, text)
        Log.i(TAG, "injectText -> $result")
        return result
    }

    /** Perform the user-initiated clipboard paste into the focused field (Tap-to-Paste rung). */
    fun pasteIntoFocused(): Boolean {
        val focus = currentInputFocus()
        return focus != null && injector.paste(focus)
    }

    /** Package of the currently focused editable field (M9 history "target app"); null if none. */
    fun focusedPackageName(): String? = currentInputFocus()?.packageName?.toString() ?: activeEditor()?.packageName

    /** True if an editable text field is focused right now — used to warn before recording starts. */
    fun isEditableFocused(): Boolean {
        val focus = currentInputFocus()
        focusedEditable = focus
        val editor = activeEditor()
        updateSensitive(focus?.let(::isSensitive) == true || editor?.let(::editorSensitive) == true)
        return focus != null || editor != null
    }

    /** API 33+ exposes editors such as Gmail's body even when their WebView has no editable node. */
    private fun activeEditor(): EditorInfo? {
        if (Build.VERSION.SDK_INT < 33) return null
        val method = inputMethod ?: return null
        if (!method.currentInputStarted || method.currentInputConnection == null) return null
        val editor = method.currentInputEditorInfo ?: return null
        val foreground = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
            .maxByOrNull { it.layer }?.root?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()
        return editor.takeIf { it.inputType != 0 && it.packageName == foreground }
    }

    private fun editorSensitive(editor: EditorInfo): Boolean = SensitiveFieldClassifier.isSensitive(
        SensitiveFieldClassifier.EditableFieldInfo(false, editor.inputType, null, editor.packageName),
    )

    private fun currentInputFocus(): AccessibilityNodeInfo? = runCatching {
        // Only the current application window may provide a target. Never choose a field
        // in the keyboard, floating overlay, or an inactive app behind a dialog.
        val applicationWindows = windows.filter {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive)
        }.sortedByDescending { it.layer }
        val root = applicationWindows.firstOrNull()?.root
        if (root != null) return@runCatching EditableNodeResolver.findInputTarget(root)
        // Some OEMs omit the window list. The active root is the only safe fallback;
        // do not revive a cached node whose focus or window may have changed.
        EditableNodeResolver.findInputTarget(rootInActiveWindow)
    }.getOrNull()

    private fun isSensitive(node: AccessibilityNodeInfo): Boolean =
        SensitiveFieldClassifier.isSensitive(
            SensitiveFieldClassifier.EditableFieldInfo(
                node.isPassword, node.inputType, node.className, node.packageName,
            ),
        )

    /** Dev-only: `adb shell am broadcast -a com.tihor.quill.DEBUG_INJECT --es text "hello world"`. */
    private fun registerDebugTriggerIfDebug() {
        if (!BuildConfig.DEBUG) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DEBUG_INJECT ->
                        injectText(intent.getStringExtra("text") ?: "hello world")
                    ACTION_DEBUG_PASTE -> pasteIntoFocused()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_DEBUG_INJECT)
            addAction(ACTION_DEBUG_PASTE)
        }
        // RECEIVER_EXPORTED so `adb shell am broadcast` (a different uid) can reach it in debug.
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        debugReceiver = receiver
        Log.i(TAG, "Debug inject trigger registered.")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        debugReceiver?.let { runCatching { unregisterReceiver(it) } }
        debugReceiver = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Quill.A11y"
        const val ACTION_DEBUG_INJECT = "com.tihor.quill.DEBUG_INJECT"
        const val ACTION_DEBUG_PASTE = "com.tihor.quill.DEBUG_PASTE"

        /** Set while the service is connected; used by the overlay bubble to trigger injection. */
        @Volatile
        var instance: QuillAccessibilityService? = null
            private set

        /** True while a password/banking field is focused — the overlay dims the bubble (board F4). */
        @Volatile
        var sensitiveFieldFocused: Boolean = false
            private set

        /** Observer (the overlay) notified when the focused field's sensitivity changes. */
        @Volatile
        var sensitiveListener: ((Boolean) -> Unit)? = null

        private fun updateSensitive(value: Boolean) {
            if (sensitiveFieldFocused == value) return
            sensitiveFieldFocused = value
            sensitiveListener?.invoke(value)
        }
    }
}
