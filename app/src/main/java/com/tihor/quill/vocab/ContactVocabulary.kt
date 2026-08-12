package com.tihor.quill.vocab

/**
 * Pure contacts-name → candidate-vocabulary filter (Phase 1: contacts only, nothing leaves the
 * device). Given raw `ContactsContract` display names, produce a deduplicated, cleaned candidate
 * vocabulary list.
 *
 * Kept Android-free so every rule is JVM-unit-testable — same pattern as
 * [com.tihor.quill.settings.WordSubstitutions] and `CleanupGuard`.
 *
 * ### Filtering rules (deliberate, documented)
 * 1. **Blank / whitespace-only entries are dropped.**
 * 2. **A name is kept only if every whitespace-separated token is a plausible name token** —
 *    letters (any script) with optional intra-word apostrophes/hyphens (`O'Brien`, `Anne-Marie`)
 *    and one optional trailing period (`Dr.`). Any other character anywhere in the entry — digits,
 *    `@`, `/`, emoji, symbols — rejects the **whole** entry. This drops phone-number-ish, email,
 *    handle, and emoji-decorated contact rows wholesale rather than guessing which part is a name.
 * 3. **Length bounds**: the full name must be 2..[MAX_TERM_LENGTH] chars; longer reads as junk
 *    (notes typed into the name field), shorter can't be a name.
 * 4. **Token-count bound**: at most [MAX_NAME_TOKENS] tokens; more reads as a note, not a name.
 * 5. **Multi-word names contribute the full name AND each constituent word** ([MIN_TERM_LENGTH]+
 *    chars), because STT mishears first/last names independently. Single-character tokens
 *    (initials) are kept inside the full name but not added as standalone terms.
 * 6. **Dedupe is case-insensitive**, keeping the first-seen casing and first-seen order
 *    (deterministic output for a deterministic input).
 * 7. **[MAX_TERMS] backstop cap** so a pathological address book can't bloat storage; truncation
 *    keeps the earliest (most-likely-used) terms.
 *
 * Nothing here decides how the vocabulary is later *used* (a follow-up phase); this only produces a
 * clean candidate list.
 */
object ContactVocabulary {

    /** Full-name length bounds (rule 3). */
    const val MAX_TERM_LENGTH = 48
    const val MIN_TERM_LENGTH = 2

    /** Most tokens a kept full name may have (rule 4). */
    const val MAX_NAME_TOKENS = 4

    /** Backstop cap on the produced list (rule 7). */
    const val MAX_TERMS = 1_000

    /**
     * A plausible name token: one or more letters, optionally joined by apostrophes/hyphens to more
     * letters, with one optional trailing period (an honorific/abbreviation like "Dr."). Any other
     * shape (contains digits, symbols, emoji, slashes) is not a name token.
     */
    private val NAME_TOKEN = Regex("""^\p{L}+(?:['\-]\p{L}+)*\.?$""")

    /** Extract the cleaned candidate vocabulary from raw contact display names (rules above). */
    fun extract(displayNames: List<String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()

        fun offer(term: String) {
            if (out.size >= MAX_TERMS) return
            if (seen.add(term.lowercase())) out.add(term)
        }

        for (raw in displayNames) {
            if (out.size >= MAX_TERMS) break
            val name = raw.trim().replace(Regex("\\s+"), " ")
            if (name.length < MIN_TERM_LENGTH || name.length > MAX_TERM_LENGTH) continue
            val tokens = name.split(' ')
            if (tokens.isEmpty() || tokens.size > MAX_NAME_TOKENS) continue
            if (tokens.any { !NAME_TOKEN.matches(it) }) continue

            offer(name)
            if (tokens.size > 1) {
                for (token in tokens) {
                    if (token.length >= MIN_TERM_LENGTH) offer(token)
                }
            }
        }
        return out
    }
}
