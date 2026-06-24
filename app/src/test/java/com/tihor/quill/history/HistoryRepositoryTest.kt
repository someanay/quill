package com.tihor.quill.history

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * M9 "write first" (testing_plan.md): the write gate lives in the repository — no rows when the flag
 * is off or private mode is on, blanks are dropped, and toggling off never deletes existing rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRepositoryTest {

    private suspend fun repo() = HistoryRepository(RuntimeEnvironment.getApplication()).also { it.clearAll() }

    @Test
    fun saves_whenEnabled() = runTest {
        val r = repo()
        assertTrue(r.maybeSave(historyEnabled = true, privateMode = false, "hi", "com.a", "App A", 1_000))
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("hi", all.first().text)
        assertEquals("App A", all.first().appLabel)
    }

    @Test
    fun noRows_whenFlagOff() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(historyEnabled = false, privateMode = false, "hi", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun noRows_inPrivateMode() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(historyEnabled = true, privateMode = true, "hi", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun blankTranscript_notSaved() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(historyEnabled = true, privateMode = false, "   ", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun toggleOff_stopsWrites_withoutDeleting() = runTest {
        val r = repo()
        assertTrue(r.maybeSave(historyEnabled = true, privateMode = false, "kept", null, null, 1_000))
        assertFalse(r.maybeSave(historyEnabled = false, privateMode = false, "dropped", null, null, 2_000))
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("kept", all.first().text)
    }

    @Test
    fun delete_andClearAll() = runTest {
        val r = repo()
        r.maybeSave(historyEnabled = true, privateMode = false, "a", null, null, 1_000)
        r.maybeSave(historyEnabled = true, privateMode = false, "b", null, null, 2_000)
        val first = r.all().first()
        r.delete(first.id)
        assertEquals(1, r.all().size)
        r.clearAll()
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun retention_prunesOldestBeyondCap() = runTest {
        val r = repo()
        val n = HistoryRepository.CAP + 5
        for (i in 1..n) r.maybeSave(historyEnabled = true, privateMode = false, "t$i", null, null, i.toLong())
        val all = r.all()
        assertEquals(HistoryRepository.CAP, all.size)
        assertEquals("newest kept", n.toLong(), all.first().createdAt)
        assertTrue("oldest 5 pruned", all.none { it.createdAt <= 5L })
    }
}
