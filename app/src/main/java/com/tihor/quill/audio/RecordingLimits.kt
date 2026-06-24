package com.tihor.quill.audio

/**
 * The hard session cap (PRD decision: 300 s) that bounds a runaway mic FGS, plus the pre-limit
 * warning point. Pure → unit-tested.
 */
object RecordingLimits {

    const val DEFAULT_CAP_MS = 300_000L          // 5 minutes
    const val DEFAULT_WARN_LEAD_MS = 15_000L     // warn 15 s before the cap

    fun reachedCap(elapsedMs: Long, capMs: Long = DEFAULT_CAP_MS): Boolean =
        elapsedMs >= capMs

    fun shouldWarn(
        elapsedMs: Long,
        capMs: Long = DEFAULT_CAP_MS,
        warnLeadMs: Long = DEFAULT_WARN_LEAD_MS,
    ): Boolean = !reachedCap(elapsedMs, capMs) && elapsedMs >= capMs - warnLeadMs
}
