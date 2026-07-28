package com.tihor.quill.style

/**
 * Per-app writing style (scope doc §3). A fixed set of three — fewer isn't worth the feature, more and
 * users can't tell them apart. The style is resolved per dictation from the app that was focused when
 * recording started ([WritingStyleResolver]) and composed into the M10 cleanup prompt
 * ([WritingStylePrompt]).
 *
 * **Style changes punctuation, capitalization, and contractions ONLY. It never changes the user's
 * words.** That boundary is spelled out to the model in [WritingStylePrompt.BOUNDS] and enforced
 * structurally afterwards by `CleanupGuard` — the prompt asks, the guard verifies.
 *
 * [id] is the stable persisted token: it goes into DataStore (global default + per-app overrides), so
 * it must not change even if the enum constants are renamed or reordered.
 */
enum class WritingStyle(val id: String) {
    /** Work, external comms. Full sentence case, terminal punctuation always, contractions expanded. */
    FORMAL("formal"),

    /** Default; personal messaging. Sentence case, standard punctuation, contractions as spoken. */
    NEUTRAL("neutral"),

    /** Social, quick chats. Relaxed capitalization, terminal punctuation optional, contractions preferred. */
    CASUAL("casual"),
    ;

    companion object {
        /** What the global default ships as (scope doc §5) — and the fallback for an unreadable id. */
        val DEFAULT = NEUTRAL

        /** Parse a persisted [id]; anything unknown or absent falls back to [DEFAULT] (never throws). */
        fun fromId(id: String?): WritingStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
