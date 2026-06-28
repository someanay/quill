package com.tihor.quill.settings

import kotlinx.serialization.Serializable

/**
 * One custom-word substitution (settings.words): when the transcript contains [heard] (the form the
 * STT model keeps producing) it is replaced with [replacement] (the correct spelling). Names and
 * domain terms that providers mishear are the motivating case (spec — P1 substitution map).
 */
@Serializable
data class CustomWord(
    val heard: String,
    val replacement: String,
)

/**
 * Pure transcript post-processor — kept Android-free so it is unit-testable on the JVM. Applies each
 * [CustomWord] as a whole-word, case-insensitive find/replace, in list order.
 */
object WordSubstitutions {

    fun apply(text: String, words: List<CustomWord>): String {
        if (text.isEmpty() || words.isEmpty()) return text
        var out = text
        for (w in words) {
            val heard = w.heard.trim()
            if (heard.isEmpty()) continue
            // Bound the match by non-letter/non-digit edges so "cat" doesn't fire inside "category",
            // while still matching multi-word phrases and trailing punctuation.
            val rx = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(heard) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE,
            )
            out = rx.replace(out) { w.replacement }
        }
        return out
    }
}
