package com.tihor.quill.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.tihor.quill.BuildConfig

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
        Log.i(TAG, "Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            -> updateFocusedEditable(event)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                focusedEditable = null // left the field / switched app — drop the stale node
                updateSensitive(false)
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
    fun injectText(text: String): InsertResult {
        // Prefer the node captured from focus events (reliable for WebViews); refresh it for current
        // text/selection. Fall back to findFocus only if we have no tracked field.
        val tracked = focusedEditable?.also { runCatching { it.refresh() } }
        val focus = tracked ?: currentInputFocus()
        if (focus == null) {
            Log.i(TAG, "injectText: no focused field.")
            return InsertResult.NoEditableField
        }
        val result = injector.insert(focus, text)
        Log.i(TAG, "injectText -> $result")
        return result
    }

    /** Perform the user-initiated clipboard paste into the focused field (Tap-to-Paste rung). */
    fun pasteIntoFocused(): Boolean {
        val focus = focusedEditable?.also { runCatching { it.refresh() } } ?: currentInputFocus()
        return focus != null && injector.paste(focus)
    }

    /** Package of the currently focused editable field (M9 history "target app"); null if none. */
    fun focusedPackageName(): String? = focusedEditable?.packageName?.toString()

    /** True if an editable text field is focused right now — used to warn before recording starts. */
    fun isEditableFocused(): Boolean {
        val tracked = focusedEditable?.also { runCatching { it.refresh() } }
        return (tracked ?: currentInputFocus())?.isEditable == true
    }

    private fun currentInputFocus(): AccessibilityNodeInfo? =
        runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()

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
