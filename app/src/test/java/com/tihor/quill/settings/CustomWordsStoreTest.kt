package com.tihor.quill.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Custom-word substitution store (P1 vocabulary map). The persisted contract: entries round-trip,
 * are trimmed, blanks are rejected, the "heard" form is unique case-insensitively (re-adding replaces
 * rather than duplicates), and remove drops the matching entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomWordsStoreTest {

    private fun store() = CustomWordsStore(RuntimeEnvironment.getApplication())

    // Robolectric caches the DataStore singleton across methods in a class, so empty the store first
    // for true per-test isolation (mirrors HistoryRepositoryTest's clearAll()).
    @Before
    fun reset() {
        runBlocking {
            val s = store()
            s.words.first().forEach { s.remove(it) }
        }
    }

    @Test
    fun add_persistsAndRoundTrips() = runTest {
        val s = store()
        s.add("clod code", "Claude Code")
        val words = s.words.first()
        assertEquals(1, words.size)
        assertEquals("clod code", words.first().heard)
        assertEquals("Claude Code", words.first().replacement)
    }

    @Test
    fun add_trimsSurroundingWhitespace() = runTest {
        val s = store()
        s.add("  hey there  ", "  Hey There  ")
        val w = s.words.first().first()
        assertEquals("hey there", w.heard)
        assertEquals("Hey There", w.replacement)
    }

    @Test
    fun add_blankHeardOrReplacement_isIgnored() = runTest {
        val s = store()
        s.add("   ", "x")
        s.add("y", "   ")
        assertTrue(s.words.first().isEmpty())
    }

    @Test
    fun add_sameHeardCaseInsensitive_replacesInsteadOfDuplicating() = runTest {
        val s = store()
        s.add("gpt", "GPT")
        s.add("GPT", "ChatGPT")
        val words = s.words.first()
        assertEquals(1, words.size)
        assertEquals("ChatGPT", words.first().replacement)
    }

    @Test
    fun add_preservesInsertionOrder() = runTest {
        val s = store()
        s.add("one", "1")
        s.add("two", "2")
        s.add("three", "3")
        assertEquals(listOf("one", "two", "three"), s.words.first().map { it.heard })
    }

    @Test
    fun remove_dropsMatchingEntryOnly() = runTest {
        val s = store()
        s.add("a", "A")
        s.add("b", "B")
        s.remove(CustomWord("a", "A"))
        val words = s.words.first()
        assertEquals(1, words.size)
        assertEquals("b", words.first().heard)
    }

    @Test
    fun clearAll_emptiesStore() = runTest {
        val s = store()
        s.add("a", "A")
        s.add("b", "B")
        s.clearAll()
        assertTrue(s.words.first().isEmpty())
    }
}
