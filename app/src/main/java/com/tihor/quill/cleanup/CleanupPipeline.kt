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
 *
 * The pipeline is also where the [CleanupGuard] divergence check is **enforced**, so that no [clean]
 * implementation can bypass it — see [cleanedOrRaw].
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
        val tidied = if (cleanupEnabled) cleanedOrRaw(raw, clean) else raw
        return WordSubstitutions.apply(tidied, words)
    }

    /**
     * Run [clean] and keep its result **only if it still says what the user said** ([CleanupGuard]);
     * otherwise fall back to [raw], the same fail-soft path as a null/timeout/HTTP error.
     *
     * This check is enforced here, at the choke point every cleanup flows through, rather than only
     * inside a particular [clean] implementation. [clean] is an injected lambda — as the prompt it
     * builds grows (per-app writing style composes an extra directive into it), the odds of some
     * future variant returning an assistant reply go up, not down. Enforcing at the pipeline means the
     * guarantee holds for *any* lambda, including one that forgets to check for itself.
     * [CleanupClient] also checks before returning; both call the same pure predicate, so the two can
     * never disagree, and the earlier check just saves this one a wasted comparison.
     */
    private suspend fun cleanedOrRaw(raw: String, clean: suspend (String) -> String?): String {
        val cleaned = clean(raw) ?: return raw
        return if (CleanupGuard.preservesMeaning(raw, cleaned)) cleaned else raw
    }
}
