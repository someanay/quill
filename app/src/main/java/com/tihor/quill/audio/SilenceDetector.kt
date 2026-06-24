package com.tihor.quill.audio

/**
 * Silence auto-stop safety net (spec §2.2): with tap-to-toggle a user can forget to stop, so a
 * sustained quiet window auto-stops recording. Deliberately conservative — a *fixed absolute RMS*
 * threshold and a long quiet window so mid-sentence pauses don't cut people off — and a startup
 * grace so an initial pause before speaking doesn't end the session immediately. Pure → unit-tested.
 *
 * Known v1 limitation (owned in the spec): in a high-noise environment the RMS may never fall below
 * the fixed threshold, so this won't fire — the 5-minute session cap is the backstop there.
 */
class SilenceDetector(
    private val thresholdRms: Double = DEFAULT_THRESHOLD_RMS,
    private val quietWindowMs: Long = DEFAULT_QUIET_WINDOW_MS,
    private val graceMs: Long = DEFAULT_GRACE_MS,
) {
    private var startMs = 0L
    private var quietSinceMs = -1L

    fun start(nowMs: Long) {
        startMs = nowMs
        quietSinceMs = -1L
    }

    /** Feed one frame's RMS; returns true when recording should auto-stop. */
    fun onFrame(rms: Double, nowMs: Long): Boolean {
        if (rms >= thresholdRms) {
            quietSinceMs = -1L
            return false
        }
        // Below threshold (quiet).
        if (nowMs - startMs < graceMs) return false // still in the startup grace window
        if (quietSinceMs < 0L) quietSinceMs = nowMs
        return nowMs - quietSinceMs >= quietWindowMs
    }

    companion object {
        // 16-bit PCM amplitude is ±32767; this is a low absolute floor (quiet room) tuned to err
        // toward *not* cutting people off. Re-tune empirically (testing_plan.md §2 M4 step 5).
        const val DEFAULT_THRESHOLD_RMS = 600.0
        const val DEFAULT_QUIET_WINDOW_MS = 3_500L
        const val DEFAULT_GRACE_MS = 2_000L
    }
}
