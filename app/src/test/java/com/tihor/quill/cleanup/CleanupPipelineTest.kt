package com.tihor.quill.cleanup

import com.tihor.quill.settings.CustomWord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * M10 "write first": the post-transcription pipeline order (decision 3) and the off/fail-soft/single-
 * output contracts (decisions 4 & 5). Pure — no Android, no network; the cleanup step is injected.
 */
class CleanupPipelineTest {

    private val clodToClaude = listOf(CustomWord("clod", "Claude"))

    @Test
    fun cleanupRunsBeforeSubstitution_substitutionWinsLast() = runTest {
        // The LLM tidies capitalization/punctuation; the custom word is applied to the CLEANED text.
        val result = CleanupPipeline.process(
            raw = "um hello clod",
            cleanupEnabled = true,
            words = clodToClaude,
            clean = { "Hello clod." },
        )
        assertEquals("Hello Claude.", result)
    }

    @Test
    fun substitutionAppliesToCleanedText_notRaw() = runTest {
        // The word "magic" exists only AFTER cleanup — proving substitution sees the cleaned string.
        val result = CleanupPipeline.process(
            raw = "say the word",
            cleanupEnabled = true,
            words = listOf(CustomWord("magic", "MAGIC")),
            clean = { "say the magic word" },
        )
        assertEquals("say the MAGIC word", result)
    }

    @Test
    fun cleanupDisabled_passesRawThroughSubstitutionOnly_noCleanCall() = runTest {
        var called = false
        val result = CleanupPipeline.process(
            raw = "hello clod",
            cleanupEnabled = false,
            words = clodToClaude,
            clean = { called = true; "SHOULD NOT BE USED" },
        )
        assertFalse("cleanup must not be invoked when disabled (no chat call)", called)
        assertEquals("hello Claude", result)
    }

    @Test
    fun cleanupFailsSoft_nullFallsBackToRawPlusSubstitution() = runTest {
        val result = CleanupPipeline.process(
            raw = "hello clod",
            cleanupEnabled = true,
            words = clodToClaude,
            clean = { null }, // simulated failure / timeout / empty
        )
        assertEquals("hello Claude", result)
    }

    @Test
    fun finalString_isOneValue_forBothHistoryAndInjector() = runTest {
        // OverlayService hands this single return value to BOTH history and the injector (decision 4),
        // so they cannot diverge. Verify the on and off cases each produce the expected final string.
        val on = CleanupPipeline.process("um hello clod", cleanupEnabled = true, clodToClaude) { "Hello clod." }
        assertEquals("Hello Claude.", on)
        val off = CleanupPipeline.process("um hello clod", cleanupEnabled = false, clodToClaude) { "x" }
        assertEquals("um hello Claude", off)
    }
}
