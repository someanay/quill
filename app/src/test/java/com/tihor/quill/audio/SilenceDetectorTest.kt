package com.tihor.quill.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4 "write first": silence auto-stop fires after a sustained quiet window, not on brief pauses. */
class SilenceDetectorTest {

    private fun detector() = SilenceDetector(thresholdRms = 500.0, quietWindowMs = 3000, graceMs = 1000)

    @Test
    fun sustainedSilence_afterGrace_autoStops() {
        val d = detector()
        d.start(0)
        // During grace: quiet but no stop.
        assertFalse(d.onFrame(rms = 10.0, nowMs = 500))
        // After grace, quiet accrues; stop once the quiet window elapses.
        assertFalse(d.onFrame(rms = 10.0, nowMs = 1500)) // quiet starts here
        assertFalse(d.onFrame(rms = 10.0, nowMs = 4000)) // 2.5 s quiet < 3 s
        assertTrue(d.onFrame(rms = 10.0, nowMs = 4600))  // 3.1 s quiet
    }

    @Test
    fun midSentencePause_doesNotCutOff() {
        val d = detector()
        d.start(0)
        assertFalse(d.onFrame(rms = 8000.0, nowMs = 1500)) // talking
        assertFalse(d.onFrame(rms = 10.0, nowMs = 3000))   // pause begins
        assertFalse(d.onFrame(rms = 9000.0, nowMs = 4500)) // talking again — resets quiet
        assertFalse(d.onFrame(rms = 10.0, nowMs = 6000))   // new pause
        assertFalse(d.onFrame(rms = 10.0, nowMs = 8000))   // 2 s quiet < 3 s window
    }

    @Test
    fun loudThroughout_neverStops() {
        val d = detector()
        d.start(0)
        for (t in longArrayOf(1500, 3000, 6000, 9000, 20000)) {
            assertFalse(d.onFrame(rms = 5000.0, nowMs = t))
        }
    }

    @Test
    fun initialSilenceWithinGrace_doesNotStopImmediately() {
        val d = detector()
        d.start(0)
        assertFalse(d.onFrame(rms = 0.0, nowMs = 100))
        assertFalse(d.onFrame(rms = 0.0, nowMs = 900))
    }
}
