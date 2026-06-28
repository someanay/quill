package com.tihor.quill.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11 sensitive-clip auto-expiry guard: clear the staged transcript only if the clipboard still holds
 * Quill's clip — never clobber something the user copied since. (The 60 s timing is exercised on a
 * real device; here we assert the guard + the window constant.)
 */
class ClipboardHygieneTest {

    @Test
    fun clears_whenClipIsStillQuills() {
        assertTrue(ClipboardHygiene.shouldClear(ClipboardHygiene.QUILL_CLIP_LABEL))
    }

    @Test
    fun doesNotClear_whenUserCopiedSomethingElse() {
        assertFalse(ClipboardHygiene.shouldClear("some other app's copy"))
    }

    @Test
    fun doesNotClear_whenClipboardEmpty() {
        assertFalse(ClipboardHygiene.shouldClear(null))
    }

    @Test
    fun expiryWindowIsSixtySeconds() {
        assertEquals(60_000L, ClipboardHygiene.SENSITIVE_CLEAR_DELAY_MS)
    }
}
