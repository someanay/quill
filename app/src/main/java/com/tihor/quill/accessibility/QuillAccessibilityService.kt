package com.tihor.quill.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Tracks the currently focused editable field and the foreground app (spec §3.1). M2 only
 * **observes and logs** — it resolves the focused node, classifies sensitivity, and records the
 * package/class. The real node resolution + text insertion ladder lands at M3 (spec §5 blueprint).
 *
 * Not declared `isAccessibilityTool` (Quill is a productivity tool, not a disability aid) — so the
 * prominent-disclosure + consent screen must run before the user enables this (spec §8).
 */
class QuillAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            -> logFocusedEditable(event)
        }
    }

    private fun logFocusedEditable(event: AccessibilityEvent) {
        // Prefer the actual input-focused node; fall back to the event source.
        val node: AccessibilityNodeInfo? =
            findInputFocus() ?: event.source

        if (node == null) {
            Log.d(TAG, "Focus change in ${event.packageName}: no node.")
            return
        }

        if (!node.isEditable) {
            Log.d(TAG, "Focus in ${event.packageName}: non-editable ${node.className}")
            return
        }

        val info = SensitiveFieldClassifier.EditableFieldInfo(
            isPassword = node.isPassword,
            inputType = node.inputType,
            className = node.className,
        )
        val sensitive = SensitiveFieldClassifier.isSensitive(info)
        Log.i(
            TAG,
            "Editable focused: pkg=${node.packageName} class=${node.className} " +
                "isPassword=${node.isPassword} inputType=0x${node.inputType.toString(16)} " +
                "sensitive=$sensitive" + if (sensitive) " -> dictation DISABLED here" else "",
        )
    }

    private fun findInputFocus(): AccessibilityNodeInfo? =
        runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted.")
    }

    companion object {
        private const val TAG = "Quill.A11y"
    }
}
