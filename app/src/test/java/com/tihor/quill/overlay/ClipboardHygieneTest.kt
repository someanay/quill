package com.tihor.quill.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M11 sensitive-clip auto-expiry guard: clear the staged transcript only if the clipboard still holds
 * Quill's clip AND it's the exact same one — never clobber something the user copied since, and
 * never clear a different Quill clip that arrived later. (The 60 s timing is exercised on a
 * real device; here we assert the guard + the window constant.)
 */
class ClipboardHygieneTest {

    @Test
    fun clears_whenClipIsStillQuillsAndTokenMatches() {
        val token = ClipboardHygiene.tokenLabel()
        assertTrue(ClipboardHygiene.shouldClear(token, token))
    }

    @Test
    fun doesNotClear_whenTokenMismatches_differentQuillClip() {
        val tokenA = ClipboardHygiene.tokenLabel()
        val tokenB = ClipboardHygiene.tokenLabel()
        // Clip B arrived after Clip A; A's clear callback must NOT clear B.
        assertFalse(ClipboardHygiene.shouldClear(tokenB, tokenA))
    }

    @Test
    fun doesNotClear_whenUserCopiedSomethingElse() {
        val token = ClipboardHygiene.tokenLabel()
        assertFalse(ClipboardHygiene.shouldClear("some other app's copy", token))
    }

    @Test
    fun doesNotClear_whenClipboardEmpty() {
        val token = ClipboardHygiene.tokenLabel()
        assertFalse(ClipboardHygiene.shouldClear(null, token))
    }

    @Test
    fun doesNotClear_whenExpectedTokenNull_userInitiated() {
        assertFalse(ClipboardHygiene.shouldClear(ClipboardHygiene.tokenLabel(), null))
    }

    @Test
    fun tokenLabel_isUniquePerCall() {
        val a = ClipboardHygiene.tokenLabel()
        val b = ClipboardHygiene.tokenLabel()
        assertTrue(a != b)
    }

    @Test
    fun tokenLabel_containsPrefix() {
        assertTrue(ClipboardHygiene.tokenLabel().startsWith(ClipboardHygiene.QUILL_CLIP_LABEL + "/"))
    }

    @Test
    fun expiryWindowIsSixtySeconds() {
        assertEquals(60_000L, ClipboardHygiene.SENSITIVE_CLEAR_DELAY_MS)
    }
}