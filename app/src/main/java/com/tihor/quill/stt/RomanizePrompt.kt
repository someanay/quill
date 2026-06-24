package com.tihor.quill.stt

/**
 * The Hinglish romanization prompt seed. When the effective language is English, Whisper is biased to
 * keep Hindi (and other code-mixed) words in Roman/Latin script — "aaj ka", not "आज का".
 *
 * It is sent **only** for the English/romanize default. For an explicit non-English language or
 * auto-detect, no prompt is sent (it would contradict the user's choice).
 */
object RomanizePrompt {
    const val HINGLISH: String =
        "The speaker mixes English and Hindi (Hinglish) and writes Hindi words in Roman/Latin " +
            "script. Example: Aaj ka plan kya hai? Let's meet kal, theek hai."

    /** The prompt for [language] (an ISO code, or null for auto-detect): Hinglish for "en", else none. */
    fun promptFor(language: String?): String? =
        if (language.equals("en", ignoreCase = true)) HINGLISH else null
}
