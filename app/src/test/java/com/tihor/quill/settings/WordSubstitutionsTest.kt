package com.tihor.quill.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** Custom-word post-processing (settings.words): whole-word, case-insensitive, order-preserving. */
class WordSubstitutionsTest {

    @Test
    fun emptyList_returnsTextUnchanged() {
        assertEquals("hello world", WordSubstitutions.apply("hello world", emptyList()))
    }

    @Test
    fun replacesWholeWord_caseInsensitive() {
        val words = listOf(CustomWord(heard = "clod code", replacement = "Claude Code"))
        assertEquals("Open Claude Code now", WordSubstitutions.apply("Open clod code now", words))
        assertEquals("Open Claude Code now", WordSubstitutions.apply("Open CLOD CODE now", words))
    }

    @Test
    fun doesNotReplaceInsideLongerWord() {
        val words = listOf(CustomWord(heard = "cat", replacement = "dog"))
        assertEquals("category catalog", WordSubstitutions.apply("category catalog", words))
        assertEquals("a dog sat", WordSubstitutions.apply("a cat sat", words))
    }

    @Test
    fun matchesAcrossPunctuation() {
        val words = listOf(CustomWord(heard = "tihor", replacement = "Tihor"))
        assertEquals("(Tihor).", WordSubstitutions.apply("(tihor).", words))
    }

    @Test
    fun appliesEntriesInOrder() {
        val words = listOf(
            CustomWord(heard = "a", replacement = "b"),
            CustomWord(heard = "b", replacement = "c"),
        )
        // "a" -> "b" then "b" -> "c": chained.
        assertEquals("c", WordSubstitutions.apply("a", words))
    }

    @Test
    fun blankHeard_isSkipped() {
        val words = listOf(CustomWord(heard = "   ", replacement = "x"))
        assertEquals("untouched", WordSubstitutions.apply("untouched", words))
    }

    @Test
    fun replacementWithDollarSign_isLiteral() {
        // Regex.replace treats $ specially; we must insert the replacement verbatim.
        val words = listOf(CustomWord(heard = "price", replacement = "\$5"))
        assertEquals("it is \$5", WordSubstitutions.apply("it is price", words))
    }
}
