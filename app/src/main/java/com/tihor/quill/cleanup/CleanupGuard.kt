package com.tihor.quill.cleanup

/**
 * Structural divergence guard for the M10 cleanup pass — the real fix for "imperative dictation gets
 * obeyed". Prompt wording alone (see [CleanupClient.SYSTEM_PROMPT]) can still leak: an LLM may treat
 * `"type my email"` as an instruction and answer `"I'm ready to type your email"`. Wording can't be
 * trusted to hold every time, so we verify the *output* structurally.
 *
 * Cleanup is only ever allowed to **tidy** (fix capitalization/punctuation, drop filler/false starts).
 * It must never **change what the user said**. [preservesMeaning] checks that the cleaned text is a
 * plausible tidy of the raw transcript and not an assistant-style rewrite/expansion. When it returns
 * `false`, the caller discards the cleanup and falls back to the RAW transcript — the same fail-soft
 * path as a timeout or HTTP error. The raw transcript is always the safe value; cleanup is an
 * enhancement, never allowed to overwrite the user's words.
 *
 * Two cheap, language-agnostic signals (both must pass):
 *  - **Length ratio** — a real tidy stays close in length. Filler removal shrinks a little; punctuation
 *    grows a little. A gross expansion/contraction is a rewrite. Accept only [MIN_LENGTH_RATIO]..[MAX_LENGTH_RATIO].
 *  - **Token coverage** — tidying removes words but must not *introduce* content words. So nearly every
 *    word in the cleaned text should already appear in the raw transcript. Assistant replies pull in
 *    new words ("I'm", "ready", "your", "okay", "now") and crater coverage. Require ≥ [MIN_TOKEN_COVERAGE].
 *
 * Pure (no Android, no network) so the threshold is directly unit-testable.
 */
object CleanupGuard {
    /** Cleaned text shorter than half the raw is a contraction, not a tidy. */
    const val MIN_LENGTH_RATIO = 0.5

    /** Cleaned text longer than double the raw is an expansion (assistant reply), not a tidy. */
    const val MAX_LENGTH_RATIO = 2.0

    /** At least this fraction of the cleaned text's distinct words must already exist in the raw. */
    const val MIN_TOKEN_COVERAGE = 0.6

    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /**
     * `true` if [cleaned] is a plausible tidy of [raw] (keep it); `false` if it diverged enough to be a
     * meaning change (discard it, fall back to raw). Defensively returns `false` for blank inputs so a
     * degenerate result can never slip through as "preserved".
     */
    fun preservesMeaning(raw: String, cleaned: String): Boolean {
        if (raw.isBlank() || cleaned.isBlank()) return false

        val ratio = cleaned.trim().length.toDouble() / raw.trim().length
        if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) return false

        val rawTokens = tokenize(raw).toSet()
        val cleanedTokens = tokenize(cleaned).distinct()
        if (cleanedTokens.isEmpty()) return false

        val coverage = cleanedTokens.count { it in rawTokens }.toDouble() / cleanedTokens.size
        return coverage >= MIN_TOKEN_COVERAGE
    }

    /** Lowercased alphanumeric word tokens; punctuation/casing are exactly what cleanup is allowed to change. */
    private fun tokenize(s: String): List<String> =
        s.lowercase().split(TOKEN_SPLIT).filter { it.isNotEmpty() }
}
