package com.tihor.quill

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Trivial JVM test so the suite (and CI) actually runs from M0. Real unit tests — the
 * insert-at-cursor composition, WAV header, transcription parsing, secret round-trip, copy-catalog
 * resolution — arrive with their milestones per testing_plan.md §1.
 */
class SmokeTest {
    @Test
    fun arithmetic_sanity() {
        assertEquals(4, 2 + 2)
    }
}
