package com.tihor.quill.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4 "write first": session-cap + pre-limit warning timing (testing_plan.md §1). */
class RecordingLimitsTest {

    @Test
    fun reachedCap_atAndAfterCap() {
        assertFalse(RecordingLimits.reachedCap(299_999, capMs = 300_000))
        assertTrue(RecordingLimits.reachedCap(300_000, capMs = 300_000))
        assertTrue(RecordingLimits.reachedCap(305_000, capMs = 300_000))
    }

    @Test
    fun shouldWarn_onlyInTheLeadWindowBeforeCap() {
        val cap = 300_000L
        val lead = 15_000L
        assertFalse(RecordingLimits.shouldWarn(284_999, cap, lead)) // before the window
        assertTrue(RecordingLimits.shouldWarn(285_000, cap, lead))  // window opens
        assertTrue(RecordingLimits.shouldWarn(299_000, cap, lead))
        assertFalse(RecordingLimits.shouldWarn(300_000, cap, lead)) // capped, not "warning" anymore
    }
}
