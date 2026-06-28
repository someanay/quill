package com.tihor.quill.settings

import android.content.Context

/**
 * Synchronous, on-disk **mirror** of [QuillSettings.bubbleVisible] — the single "is the bubble shown
 * vs hidden" state. DataStore is the source of truth and the observable Flow ([SettingsRepository.settings]),
 * but it can't be read inline; [com.tihor.quill.overlay.OverlayService.addBubble] needs the value
 * synchronously on a cold start / recovery restart to paint the bubble in the right state without a flash.
 *
 * Lockstep invariant: the **only** writer is [SettingsRepository.setBubbleVisible], which writes this
 * mirror and the DataStore key together. Nothing else writes it, so the mirror never drifts from the Flow.
 * Plain (non-encrypted) SharedPreferences on purpose — this is a UI visibility flag, not a secret.
 */
class BubbleVisibilityStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True = bubble shown; false = hidden (drag-dismissed or toggled off). Default shown. */
    var visible: Boolean
        get() = prefs.getBoolean(KEY_VISIBLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_VISIBLE, value).apply()
        }

    private companion object {
        const val PREFS = "quill_overlay"
        const val KEY_VISIBLE = "bubble_visible"
    }
}
