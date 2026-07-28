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
    fun rejects_grossExpansion_intoAParagraph() {
        // A short dictation ballooning into a paragraph is a rewrite. Caught by precision: almost
        // every word in the output has no counterpart in the two-word input.
        assertFalse(
            CleanupGuard.preservesMeaning(
                raw = "remind me",
                cleaned = "Sure, I'll remind you. What would you like me to remind you about, and when?",
            ),
        )
    }

    // --- Styling must survive the guard (scope doc §9.2) ---
    //
    // These are the cases a length-ratio guard cannot get right: formal styling EXPANDS contractions
    // (longer) and casual styling DROPS them (shorter), while both preserve every token. Contraction
    // normalisation is what makes the two sides compare equal.

    @Test
    fun accepts_formalStyling_expandingContractions() {
        assertTrue(CleanupGuard.preservesMeaning(raw = "dont worry", cleaned = "Do not worry."))
        assertTrue(CleanupGuard.preservesMeaning("i cant make it wont be there", "I can not make it. I will not be there."))
        assertTrue(CleanupGuard.preservesMeaning("its fine and thats ok", "It is fine, and that is ok."))
    }

    @Test
    fun accepts_casualStyling_droppingContractionsAndPunctuation() {
        assertTrue(CleanupGuard.preservesMeaning(raw = "I do not think so.", cleaned = "i dont think so"))
        assertTrue(CleanupGuard.preservesMeaning("It is not going to work.", "its not going to work"))
        assertTrue(CleanupGuard.preservesMeaning("We are running late, I am afraid.", "were running late im afraid"))
    }

    @Test
    fun contractionNormalisation_isSymmetric() {
        // Whichever direction the styling goes, the pair compares equal.
        assertTrue(CleanupGuard.preservesMeaning("you're right", "You are right."))
        assertTrue(CleanupGuard.preservesMeaning("You are right.", "youre right"))
    }

    // --- The new basis: token overlap in BOTH directions ---

    @Test
    fun rejects_assistantReply_thatAnswersAQuestion() {
        // The §-required case: an answer reuses "it"/"is" but invents the rest.
        assertFalse(CleanupGuard.preservesMeaning(raw = "what time is it", cleaned = "It's currently 3pm"))
        assertFalse(CleanupGuard.preservesMeaning("whats the weather", "The weather today is sunny and warm."))
        assertFalse(CleanupGuard.preservesMeaning("remind me to call mum", "I've set a reminder for you to call your mum."))
    }

    @Test
    fun rejects_summarising_whichPrecisionAloneWouldAllow() {
        // Invents nothing (perfect precision) but throws most of the dictation away — only the recall
        // direction catches this, which is why the guard checks both.
        assertFalse(
            CleanupGuard.preservesMeaning(
                raw = "so i went to the shop this morning and picked up some milk and bread and then " +
                    "i walked back home through the park because the weather was really nice",
                cleaned = "I went to the shop.",
            ),
        )
    }

    @Test
    fun rejects_degenerateRepetition() {
        // Multiset (not set) overlap is what catches this: the second copy of every token has no
        // unused counterpart in the raw, so precision halves. A set comparison would score 1.0.
        assertFalse(
            CleanupGuard.preservesMeaning(
                raw = "meet me at five",
                cleaned = "Meet me at five. Meet me at five. Meet me at five.",
            ),
        )
    }

    @Test
    fun accepts_genuineRepetition_presentInTheRaw() {
        // The flip side: repetition the user actually spoke must survive, so the multiset budget has
        // to allow as many copies as the raw contains.
        assertTrue(CleanupGuard.preservesMeaning("no no no thats wrong", "No, no, no, that is wrong."))
    }

    @Test
    fun accepts_heavyFillerRemoval() {
        // Filler removal is sanctioned deletion and can strip a large fraction of tokens — the recall
        // floor has to stay below what a legitimately disfluent dictation loses.
        assertTrue(
            CleanupGuard.preservesMeaning(
                raw = "um so like you know i think uh we should probably meet tomorrow",
                cleaned = "So I think we should probably meet tomorrow.",
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
