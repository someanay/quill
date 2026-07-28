package com.tihor.quill.cleanup

/**
 * Structural divergence guard for the M10 cleanup pass — the real fix for "imperative dictation gets
 * obeyed". Prompt wording alone (see [CleanupClient.SYSTEM_PROMPT]) can still leak: an LLM may treat
 * `"type my email"` as an instruction and answer `"I'm ready to type your email"`. Wording is
 * probabilistic and can't be trusted to hold every time, so we verify the *output* structurally.
 *
 * Cleanup is only ever allowed to **tidy** (capitalization, punctuation, filler removal) and — once
 * per-app writing style lands — to restyle punctuation and contractions. It must never **change what
 * the user said**. [preservesMeaning] returns `false` when the cleaned text stopped being a plausible
 * restyling of the raw transcript, and the caller then discards it and falls back to the RAW
 * transcript — the same fail-soft path as a timeout or HTTP error. Raw is always the safe value.
 *
 * ### Why token overlap and not a length ratio
 * An earlier version gated on `cleaned.length / raw.length` as well. That basis is wrong for a feature
 * that deliberately restyles: **formal styling expands contractions** ("don't" → "do not", *longer*)
 * and **casual styling drops punctuation** (*shorter*), so any length window either rejects legitimate
 * styling or gets widened until assistant replies fit through it. Both of those transformations
 * preserve essentially every token, while an assistant reply does not — so token overlap separates
 * the two cleanly and the length ratio is gone entirely.
 *
 * Two directions, both required:
 *  - **Precision** ([MIN_TOKEN_PRECISION]) — nearly every word in the *cleaned* text must already
 *    exist in the raw transcript. Cleanup may drop words; it may never invent them. Assistant replies
 *    pull in new words ("I'm", "ready", "your", "okay", "currently") and crater this.
 *  - **Recall** ([MIN_TOKEN_RECALL]) — enough of the *raw* text must survive into the cleaned one.
 *    This is what catches summarising, which precision alone would wave through (a summary invents
 *    nothing). The bar is deliberately lower because filler removal legitimately drops tokens.
 *
 * Both are **multiset** comparisons, not set comparisons: matching each occurrence against an unused
 * occurrence on the other side. That's what stops degenerate repetition ("Meet me at five. Meet me at
 * five.") from scoring a perfect 1.0 — and it removes the last reason to keep a length heuristic.
 *
 * **Known limitation:** bag-of-words comparison is blind to *order*, so a pure clause reordering would
 * pass. That's forbidden by the scope doc but has never been an observed failure mode, and checking it
 * would cost far more than it buys. The threat this guard is built for is the assistant reply.
 *
 * Pure (no Android, no network) so every threshold is directly unit-testable.
 */
object CleanupGuard {

    /**
     * Fraction of the cleaned text's tokens that must already appear in the raw transcript.
     *
     * Chosen to sit clearly above the assistant replies we've actually seen and clearly below
     * legitimate tidying. "what time is it" → "It's currently 3pm" scores 0.5 (rejected); a tidy that
     * introduces at most one word in four scores 0.75 (accepted). 0.7 leaves margin on both sides
     * rather than sitting on either boundary.
     */
    const val MIN_TOKEN_PRECISION = 0.7

    /**
     * Fraction of the raw transcript's tokens that must survive into the cleaned text — the
     * anti-summarising check. Low on purpose: filler removal is a legitimate, sanctioned deletion, and
     * a heavily-disfluent dictation ("um uh er so like you know…") can lose nearly half its tokens to
     * a correct tidy. A genuine summary of a long dictation lands far below this.
     */
    const val MIN_TOKEN_RECALL = 0.5

    /**
     * `true` if [cleaned] is a plausible tidy/restyle of [raw] (keep it); `false` if it diverged enough
     * to be a meaning change (discard it, fall back to raw). Defensively returns `false` for blank
     * inputs so a degenerate result can never slip through as "preserved".
     */
    fun preservesMeaning(raw: String, cleaned: String): Boolean {
        if (raw.isBlank() || cleaned.isBlank()) return false

        val rawTokens = tokenize(raw)
        val cleanedTokens = tokenize(cleaned)
        if (rawTokens.isEmpty() || cleanedTokens.isEmpty()) return false

        // Did cleanup invent content? (words in the output with no counterpart in the input)
        if (overlapFraction(of = cleanedTokens, against = rawTokens) < MIN_TOKEN_PRECISION) return false
        // Did cleanup throw content away? (words in the input with no counterpart in the output)
        return overlapFraction(of = rawTokens, against = cleanedTokens) >= MIN_TOKEN_RECALL
    }

    /**
     * Fraction of [of]'s token *occurrences* that can be matched to a distinct, not-yet-consumed
     * occurrence in [against]. Multiset semantics: a token repeated three times on one side needs
     * three occurrences on the other to score fully.
     */
    private fun overlapFraction(of: List<String>, against: List<String>): Double {
        val budget = HashMap<String, Int>(against.size)
        for (token in against) budget[token] = (budget[token] ?: 0) + 1
        var matched = 0
        for (token in of) {
            val remaining = budget[token] ?: 0
            if (remaining > 0) {
                budget[token] = remaining - 1
                matched++
            }
        }
        return matched.toDouble() / of.size
    }

    /**
     * Lowercased word tokens with contractions expanded to a canonical form — punctuation and casing
     * are exactly what cleanup is allowed to change, so they're normalised away before comparing.
     */
    private fun tokenize(text: String): List<String> =
        expandContractions(text.lowercase().replace('’', '\''))
            .split(TOKEN_SPLIT)
            .filter { it.isNotEmpty() }

    /** Rewrite each whole word through [CONTRACTIONS], leaving anything unlisted untouched. */
    private fun expandContractions(text: String): String =
        WORD.replace(text) { m -> CONTRACTIONS[m.value] ?: m.value }

    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    /** A word, optionally carrying one internal apostrophe (so "don't" is one token, not two). */
    private val WORD = Regex("\\p{L}+(?:'\\p{L}+)?")

    /**
     * Contraction → expanded form, applied to **both** sides before comparing. This is what lets the
     * two legitimate style transformations through: formal expansion ("dont" → "Do not") and casual
     * contraction ("I do not think so." → "i dont think so") both collapse to identical token streams
     * instead of looking like invented/deleted words.
     *
     * Apostrophe-less spellings are included deliberately, because dropping apostrophes is exactly what
     * casual styling does — omitting them would false-reject the casual style wholesale. Some of those
     * spellings collide with real words ("its", "were", "ill", "lets", "id"), which is harmless here:
     * the output of this function is a **comparison key, never displayed text**, and the same mapping
     * is applied to both sides, so a collision can only ever make two strings compare *equal* — it can
     * never manufacture a difference. ("we were" normalising to "we we are" is nonsense to read and
     * perfectly fine to compare.)
     */
    private val CONTRACTIONS: Map<String, String> = mapOf(
        // negations — the "n't" family
        "can't" to "can not", "cant" to "can not", "cannot" to "can not",
        "won't" to "will not", "wont" to "will not",
        "shan't" to "shall not",
        "don't" to "do not", "dont" to "do not",
        "doesn't" to "does not", "doesnt" to "does not",
        "didn't" to "did not", "didnt" to "did not",
        "isn't" to "is not", "isnt" to "is not",
        "aren't" to "are not", "arent" to "are not",
        "wasn't" to "was not", "wasnt" to "was not",
        "weren't" to "were not", "werent" to "were not",
        "hasn't" to "has not", "hasnt" to "has not",
        "haven't" to "have not", "havent" to "have not",
        "hadn't" to "had not", "hadnt" to "had not",
        "couldn't" to "could not", "couldnt" to "could not",
        "wouldn't" to "would not", "wouldnt" to "would not",
        "shouldn't" to "should not", "shouldnt" to "should not",
        "mustn't" to "must not", "mustnt" to "must not",
        "needn't" to "need not", "neednt" to "need not",
        // pronoun + auxiliary
        "i'm" to "i am", "im" to "i am",
        "i've" to "i have", "ive" to "i have",
        "i'll" to "i will", "ill" to "i will",
        "i'd" to "i would", "id" to "i would",
        "you're" to "you are", "youre" to "you are",
        "you've" to "you have", "youve" to "you have",
        "you'll" to "you will", "youll" to "you will",
        "you'd" to "you would", "youd" to "you would",
        "we're" to "we are", "were" to "we are",
        "we've" to "we have", "weve" to "we have",
        // NB: "we'll" only — bare "well" is deliberately absent. It's common filler that cleanup is
        // *supposed* to delete, and expanding it to two tokens would inflate the raw side's token
        // count and false-reject ordinary filler removal.
        "we'll" to "we will",
        "we'd" to "we would",
        "they're" to "they are", "theyre" to "they are",
        "they've" to "they have", "theyve" to "they have",
        "they'll" to "they will", "theyll" to "they will",
        "they'd" to "they would",
        "he's" to "he is", "hes" to "he is",
        "she's" to "she is", "shes" to "she is",
        "it's" to "it is", "its" to "it is",
        // demonstratives / interrogatives
        "that's" to "that is", "thats" to "that is",
        "there's" to "there is", "theres" to "there is",
        "here's" to "here is", "heres" to "here is",
        "what's" to "what is", "whats" to "what is",
        "who's" to "who is", "whos" to "who is",
        "let's" to "let us", "lets" to "let us",
    )
}
