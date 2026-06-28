package com.tihor.quill.overlay

import android.content.Context

/**
 * Persists whether the user has dismissed (hidden) the floating bubble (Wispr-Flow-style hide/restore).
 *
 * Plain (non-encrypted) [android.content.SharedPreferences] on purpose: the flag isn't a secret, and a
 * **synchronous** read lets [OverlayService.addBubble] decide the bubble's visibility immediately when
 * the service is recreated (START_STICKY / foreground recovery) — a DataStore Flow can't be read inline
 * there. Persisting it (rather than an in-service boolean) means a deliberately-hidden bubble stays
 * hidden across a service restart instead of popping back; the notification's "Show Quill" action is the
 * way to bring it back.
 */
class BubbleVisibilityStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var hidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDDEN, value).apply()
        }

    private companion object {
        const val PREFS = "quill_overlay"
        const val KEY_HIDDEN = "bubble_hidden"
    }
}
