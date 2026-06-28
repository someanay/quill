package com.tihor.quill.cleanup

import com.tihor.quill.settings.CustomWord
import com.tihor.quill.settings.WordSubstitutions

/**
 * The post-transcription text pipeline (M10). The order is fixed (decision 3):
 *
 *   raw transcript → LLM cleanup (gated + fail-soft) → custom-word substitution → final text
 *
 * Cleanup runs **first** so the deterministic vocabulary always wins **last** — a custom word applied
 * after cleanup can never be undone by the LLM. The single returned string is what goes to BOTH the
 * injector and history (decision 4), so they can never diverge.
 *
 * Pure orchestration (no Android, no networking) so the ordering/off/fail-soft guarantees are
 * unit-testable: [clean] is the only side-effecting input and it is injected.
 */
object CleanupPipeline {
    /**
     * @param raw the raw transcript from STT.
     * @param cleanupEnabled when false, [clean] is never invoked (no chat call) — raw passes straight
     *   to substitution.
     * @param words custom-word substitutions, applied last.
     * @param clean the cleanup step; returns the tidied text, or null to fall back to [raw] (fail-soft).
     * @return the final text to insert and to save to history.
     */
    suspend fun process(
        raw: String,
        cleanupEnabled: Boolean,
        words: List<CustomWord>,
        clean: suspend (String) -> String?,
    ): String {
        val tidied = if (cleanupEnabled) (clean(raw) ?: raw) else raw
        return WordSubstitutions.apply(tidied, words)
    }
}
