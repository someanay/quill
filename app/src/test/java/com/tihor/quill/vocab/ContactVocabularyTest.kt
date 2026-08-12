package com.tihor.quill.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure contacts-name → candidate-vocabulary filter (Phase 1, contacts only). The contract:
 * junk is dropped (blanks, digits/emails/handles, punctuation-only, excessive length), multi-word
 * names contribute both the full name and each constituent word, dedupe is case-insensitive keeping
 * first-seen casing and order, and the result is capped as a backstop.
 */
class ContactVocabularyTest {

    @Test
    fun emptyInput_returnsEmpty() {
        assertTrue(ContactVocabulary.extract(emptyList()).isEmpty())
    }

    @Test
    fun blankAndWhitespaceEntries_areDropped() {
        assertTrue(ContactVocabulary.extract(listOf("", "   ", "\n\t")).isEmpty())
    }

    @Test
    fun duplicates_caseInsensitive_keepFirstSeenCasingAndOrder() {
        val out = ContactVocabulary.extract(listOf("Priya", "PRIYA", "priya", "Rohit"))
        assertEquals(listOf("Priya", "Rohit"), out)
    }

    @Test
    fun singleWordName_isKeptAsIs() {
        assertEquals(listOf("Madonna"), ContactVocabulary.extract(listOf("Madonna")))
    }

    @Test
    fun multiWordName_contributesFullNameAndEachWord() {
        val out = ContactVocabulary.extract(listOf("Rohit Tikmany"))
        assertEquals(listOf("Rohit Tikmany", "Rohit", "Tikmany"), out)
    }

    @Test
    fun multiWordParts_dedupeAgainstOtherContacts() {
        val out = ContactVocabulary.extract(listOf("Rohit Tikmany", "Rohit Sharma"))
        // "Rohit" appears once, in first-seen position.
        assertEquals(listOf("Rohit Tikmany", "Rohit", "Tikmany", "Rohit Sharma", "Sharma"), out)
    }

    @Test
    fun entriesWithDigits_areDropped() {
        assertTrue(ContactVocabulary.extract(listOf("Rohit 2", "9876543210", "Agent 47")).isEmpty())
    }

    @Test
    fun emailsAndHandles_areDropped() {
        assertTrue(ContactVocabulary.extract(listOf("rohit@gmail.com", "@ynamkit", "https://x.io")).isEmpty())
    }

    @Test
    fun punctuationOnlyEntries_areDropped() {
        assertTrue(ContactVocabulary.extract(listOf("...", "---", "'''", ". - .")).isEmpty())
    }

    @Test
    fun excessivelyLongEntries_areDropped() {
        val long = "A".repeat(ContactVocabulary.MAX_TERM_LENGTH + 1)
        assertTrue(ContactVocabulary.extract(listOf(long)).isEmpty())
    }

    @Test
    fun singleCharacterWords_areNotAddedAsTerms() {
        // An initial alone ("J") is too short to be a useful vocabulary term; the full name is kept.
        val out = ContactVocabulary.extract(listOf("J R Tolkien"))
        assertEquals(listOf("J R Tolkien", "Tolkien"), out)
    }

    @Test
    fun namePunctuation_apostrophesHyphensPeriods_areKept() {
        val out = ContactVocabulary.extract(listOf("O'Brien", "Anne-Marie", "Dr. Smith"))
        assertEquals(listOf("O'Brien", "Anne-Marie", "Dr. Smith", "Dr.", "Smith"), out)
    }

    @Test
    fun realisticMixedSample_filtersAndDedupes() {
        val raw = listOf(
            "Rohit Tikmany", "rohit.tikmany@gmail.com", "", "Priya Nair", "PRIYA NAIR",
            "Mom ❤\uFE0F", "9876543210", "O'Brien", "A", "Jean-Luc Picard", "X".repeat(60),
        )
        val out = ContactVocabulary.extract(raw)
        assertEquals(
            listOf(
                "Rohit Tikmany", "Rohit", "Tikmany",
                "Priya Nair", "Priya", "Nair",
                "O'Brien",
                "Jean-Luc Picard", "Jean-Luc", "Picard",
            ),
            out,
        )
    }

    @Test
    fun resultCapped_atMaxTerms() {
        // Alphabetic uniquifier (digits would be filtered as junk): "Fam aaa", "Fam aab", ...
        fun alpha(i: Int): String {
            var n = i
            var s = ""
            repeat(3) { s = ('a' + n % 26) + s; n /= 26 }
            return s
        }
        val names = (0 until ContactVocabulary.MAX_TERMS).map { "Fam ${alpha(it)}" }
        // Each name contributes its full form + a unique second token, so the cap binds.
        assertEquals(ContactVocabulary.MAX_TERMS, ContactVocabulary.extract(names).size)
    }
}
