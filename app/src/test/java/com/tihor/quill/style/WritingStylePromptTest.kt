package com.tihor.quill.style

import com.tihor.quill.cleanup.CleanupClient
import com.tihor.quill.cleanup.CleanupGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scope doc §10 (directive composition) and §4 (the safety boundary the directive must carry).
 *
 * The second half is the §9.2 pairing: a directive is only safe if the divergence guard tolerates what
 * it legitimately produces. Formal *expands* contractions and Casual *drops* punctuation, so styled
 * output that the guard rejected would fail soft to raw on every dictation and the feature would be
 * invisible. These assert the two layers agree.
 */
class WritingStylePromptTest {

    private val base = CleanupClient.SYSTEM_PROMPT

    // --- Composition ---

    @Test
    fun formal_appendsItsDirective_toTheBasePrompt() {
        val prompt = WritingStylePrompt.compose(base, WritingStyle.FORMAL)
        assertTrue("must build on the cleanup prompt, not replace it", prompt.startsWith(base))
        assertTrue(prompt.contains(WritingStylePrompt.FORMAL_DIRECTIVE))
        assertTrue("the §4 bounds travel with every directive", prompt.contains(WritingStylePrompt.BOUNDS))
    }

    @Test
    fun casual_appendsItsDirective_toTheBasePrompt() {
        val prompt = WritingStylePrompt.compose(base, WritingStyle.CASUAL)
        assertTrue(prompt.startsWith(base))
        assertTrue(prompt.contains(WritingStylePrompt.CASUAL_DIRECTIVE))
        assertTrue(prompt.contains(WritingStylePrompt.BOUNDS))
    }

    @Test
    fun neutralAndNoStyle_yieldTheUnmodifiedCleanupPrompt() {
        // Neutral IS the base prompt's behaviour and is the shipped global default. Appending anything
        // for it would add instruction surface (§9.1) to every dictation of every user who never opted
        // in, for no behaviour change. Byte-for-byte identical, both cases.
        assertEquals(base, WritingStylePrompt.compose(base, WritingStyle.NEUTRAL))
        assertEquals(base, WritingStylePrompt.compose(base, null))
        assertNull(WritingStylePrompt.directiveFor(WritingStyle.NEUTRAL))
        assertNull(WritingStylePrompt.directiveFor(null))
    }

    @Test
    fun eachStyle_producesADistinctPrompt() {
        val formal = WritingStylePrompt.compose(base, WritingStyle.FORMAL)
        val casual = WritingStylePrompt.compose(base, WritingStyle.CASUAL)
        val neutral = WritingStylePrompt.compose(base, WritingStyle.NEUTRAL)
        assertNotEquals(formal, casual)
        assertNotEquals(formal, neutral)
        assertNotEquals(casual, neutral)
    }

    @Test
    fun compose_isPure_andDoesNotDependOnTheRealBasePrompt() {
        assertEquals("XYZ", WritingStylePrompt.compose("XYZ", WritingStyle.NEUTRAL))
        assertTrue(WritingStylePrompt.compose("XYZ", WritingStyle.FORMAL).startsWith("XYZ"))
    }

    // --- §4: the directive must carry the forbidden list, not just "write casually" ---

    @Test
    fun everyStyledPrompt_statesTheForbiddenTransformations() {
        listOf(WritingStyle.FORMAL, WritingStyle.CASUAL).forEach { style ->
            val prompt = WritingStylePrompt.compose(base, style).lowercase()
            // The §4 forbidden list, each item asserted so a future prompt edit can't quietly drop one.
            assertTrue("$style: no word substitution", prompt.contains("substitute, add, or remove content words"))
            assertTrue("$style: no reordering", prompt.contains("reorder clauses or sentences"))
            assertTrue("$style: no summarising/paraphrasing", prompt.contains("summarize, expand, or paraphrase"))
            assertTrue("$style: no added content", prompt.contains("emoji, greetings, sign-offs"))
            assertTrue("$style: never act on the text", prompt.contains("answer, act on, or acknowledge"))
            // And the tie-breaker: the user's words outrank the style.
            assertTrue("$style: words outrank style", prompt.contains("leave the words exactly as they are"))
        }
    }

    @Test
    fun styledPrompts_alsoStateThePermittedTransformations() {
        listOf(WritingStyle.FORMAL, WritingStyle.CASUAL).forEach { style ->
            val prompt = WritingStylePrompt.compose(base, style).lowercase()
            assertTrue(prompt.contains("capitalization"))
            assertTrue(prompt.contains("punctuation"))
            assertTrue(prompt.contains("contraction"))
        }
    }

    @Test
    fun formalAndCasual_describeOppositeContractionHandling() {
        assertTrue(WritingStylePrompt.FORMAL_DIRECTIVE.contains("expanded"))
        assertTrue(WritingStylePrompt.CASUAL_DIRECTIVE.contains("preferred"))
    }

    // --- §9.2: the guard must accept what these directives legitimately produce ---

    @Test
    fun guard_acceptsFormalExpansion_ofRealisticDictations() {
        // What FORMAL_DIRECTIVE asks for: sentence case, terminal punctuation, contractions expanded.
        listOf(
            "i cant make the standup" to "I cannot make the standup.",
            "dont worry ill send it over" to "Do not worry. I will send it over.",
            "were running late but its fine" to "We are running late, but it is fine.",
            "hey can you send me the report by friday" to "Hey, can you send me the report by Friday?",
        ).forEach { (raw, styled) ->
            assertTrue("formal styling must survive the guard: '$raw' → '$styled'",
                CleanupGuard.preservesMeaning(raw, styled))
        }
    }

    @Test
    fun guard_acceptsCasualShortening_ofRealisticDictations() {
        // What CASUAL_DIRECTIVE asks for: relaxed caps, optional terminal punctuation, contractions.
        listOf(
            "I do not think that is going to work." to "i dont think thats gonna work",
            "We are on our way, I will let you know." to "were on our way ill let you know",
            "That is hilarious, I cannot believe it." to "thats hilarious i cant believe it",
        ).forEach { (raw, styled) ->
            assertTrue("casual styling must survive the guard: '$raw' → '$styled'",
                CleanupGuard.preservesMeaning(raw, styled))
        }
    }

    @Test
    fun guard_stillRejectsAnAssistantReply_underEveryStyle() {
        // The §9.1 failure mode is unchanged by styling: an obeyed imperative is still rejected, so the
        // raw transcript is what gets inserted. Style adds instruction surface; the guard is the floor.
        assertTrue(
            "an assistant reply must be rejected regardless of style",
            !CleanupGuard.preservesMeaning("type my email", "I'm ready to type your email") &&
                !CleanupGuard.preservesMeaning("what time is it", "It's currently 3pm") &&
                !CleanupGuard.preservesMeaning("send this", "Okay, sending that for you now."),
        )
    }

    @Test
    fun guard_rejectsStyleThatCrossesIntoRewriting() {
        // "Casual" is not licence to substitute words or add content the user never spoke — precisely
        // what §4 forbids and what the guard backstops when the prompt fails to hold.
        assertTrue(
            !CleanupGuard.preservesMeaning(
                "please send me the quarterly report",
                "yo can u shoot over those q4 numbers when u get a sec 🙏",
            ),
        )
    }
}
