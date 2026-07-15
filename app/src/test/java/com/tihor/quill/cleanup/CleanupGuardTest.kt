package com.tihor.quill.cleanup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The structural divergence guard — the real fix for "imperative dictation gets obeyed". A normal tidy
 * (filler removal, punctuation, capitalization) must be ACCEPTED; an assistant-style rewrite/expansion
 * must be REJECTED so the caller falls back to the raw transcript (fail-soft). Pure — no network.
 */
class CleanupGuardTest {

    @Test
    fun rejects_assistantExpansion_ofImperative() {
        // The reported bug: cleanup obeyed the dictation instead of cleaning it.
        assertFalse(
            CleanupGuard.preservesMeaning(
                raw = "type my email",
                cleaned = "I'm ready to type your email",
            ),
        )
    }

    @Test
    fun accepts_normalTidy_fillerAndPunctuation() {
        assertTrue(
            CleanupGuard.preservesMeaning(
                raw = "um so i think we should uh meet tomorrow",
                cleaned = "So I think we should meet tomorrow.",
            ),
        )
    }

    @Test
    fun commandLike_verbatimCleaned_isAccepted() {
        // The fix must NOT block legitimate cleanup of command-shaped dictation — only divergence.
        assertTrue(CleanupGuard.preservesMeaning("send this message", "Send this message."))
        assertTrue(CleanupGuard.preservesMeaning("what time is it", "What time is it?"))
        assertTrue(CleanupGuard.preservesMeaning("delete everything", "Delete everything."))
    }

    @Test
    fun commandLike_assistantReply_isRejected() {
        // Same dictations, but the model obeyed them — must be rejected so raw is used instead.
        assertFalse(CleanupGuard.preservesMeaning("send this message", "Okay, I'll send this message now."))
        assertFalse(CleanupGuard.preservesMeaning("what time is it", "It's 3 PM."))
        assertFalse(CleanupGuard.preservesMeaning("delete everything", "I can't delete everything for you."))
    }

    @Test
    fun rejects_grossExpansion_byLength() {
        // A short dictation ballooning into a paragraph is a rewrite, regardless of token overlap.
        assertFalse(
            CleanupGuard.preservesMeaning(
                raw = "remind me",
                cleaned = "Sure, I'll remind you. What would you like me to remind you about, and when?",
            ),
        )
    }

    @Test
    fun accepts_capitalizationAndPunctuationOnly() {
        assertTrue(CleanupGuard.preservesMeaning("hello world how are you", "Hello world, how are you?"))
    }

    @Test
    fun accepts_hinglish_keptInRomanScript() {
        // Code-mixed content tidied in place — same words, fixed casing/punctuation.
        assertTrue(CleanupGuard.preservesMeaning("kal main office nahi aaunga", "Kal main office nahi aaunga."))
    }

    @Test
    fun blankInputs_areRejected_defensively() {
        assertFalse(CleanupGuard.preservesMeaning("", "Hello."))
        assertFalse(CleanupGuard.preservesMeaning("hello", ""))
        assertFalse(CleanupGuard.preservesMeaning("   ", "   "))
    }
}
