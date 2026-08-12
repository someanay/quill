package com.tihor.quill.vocab

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
 * Contacts-imported vocabulary store (Phase 1). Kept deliberately separate from
 * [com.tihor.quill.settings.CustomWordsStore]: imported contact names and user-authored word
 * substitutions are different data. The persisted contract: a replace-all import round-trips,
 * re-importing replaces rather than appends, and clear empties the store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContactsVocabularyStoreTest {

    private fun store() = ContactsVocabularyStore(RuntimeEnvironment.getApplication())

    // Robolectric caches the DataStore singleton across methods in a class — start clean
    // (mirrors CustomWordsStoreTest).
    @Before
    fun reset() {
        runBlocking { store().clear() }
    }

    @Test
    fun emptyByDefault() = runTest {
        assertTrue(store().terms.first().isEmpty())
    }

    @Test
    fun replaceAll_persistsAndRoundTrips() = runTest {
        val s = store()
        s.replaceAll(listOf("Rohit Tikmany", "Rohit", "Tikmany"))
        assertEquals(listOf("Rohit Tikmany", "Rohit", "Tikmany"), s.terms.first())
    }

    @Test
    fun replaceAll_replacesRatherThanAppends() = runTest {
        val s = store()
        s.replaceAll(listOf("Aarav"))
        s.replaceAll(listOf("Priya", "Nair"))
        assertEquals(listOf("Priya", "Nair"), s.terms.first())
    }

    @Test
    fun clear_emptiesStore() = runTest {
        val s = store()
        s.replaceAll(listOf("Rohit", "Tikmany"))
        s.clear()
        assertTrue(s.terms.first().isEmpty())
    }
}
