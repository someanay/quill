package com.tihor.quill.history

import com.tihor.quill.settings.QuillSettings.Companion.RETENTION_FOREVER
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
 * History is on by default; entries auto-delete past the retention window (default 1 hour).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRepositoryTest {

    private val hour = 3_600_000L

    private suspend fun repo() = HistoryRepository(RuntimeEnvironment.getApplication()).also { it.clearAll() }

    @Test
    fun saves_whenEnabled() = runTest {
        val r = repo()
        assertTrue(r.maybeSave(true, false, RETENTION_FOREVER, "hi", "com.a", "App A", 1_000))
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("hi", all.first().text)
        assertEquals("App A", all.first().appLabel)
    }

    @Test
    fun noRows_whenFlagOff() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(false, false, RETENTION_FOREVER, "hi", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun noRows_inPrivateMode() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(true, true, RETENTION_FOREVER, "hi", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun blankTranscript_notSaved() = runTest {
        val r = repo()
        assertFalse(r.maybeSave(true, false, RETENTION_FOREVER, "   ", null, null, 1_000))
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun toggleOff_stopsWrites_withoutDeleting() = runTest {
        val r = repo()
        assertTrue(r.maybeSave(true, false, RETENTION_FOREVER, "kept", null, null, 1_000))
        assertFalse(r.maybeSave(false, false, RETENTION_FOREVER, "dropped", null, null, 2_000))
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("kept", all.first().text)
    }

    @Test
    fun delete_andClearAll() = runTest {
        val r = repo()
        r.maybeSave(true, false, RETENTION_FOREVER, "a", null, null, 1_000)
        r.maybeSave(true, false, RETENTION_FOREVER, "b", null, null, 2_000)
        val first = r.all().first()
        r.delete(first.id)
        assertEquals(1, r.all().size)
        r.clearAll()
        assertTrue(r.all().isEmpty())
    }

    @Test
    fun retention_prunesEntriesPastWindowOnInsert() = runTest {
        val r = repo()
        // 60-minute window: the old entry falls outside it once the new one lands two hours later.
        assertTrue(r.maybeSave(true, false, 60, "old", null, null, 0L))
        assertTrue(r.maybeSave(true, false, 60, "new", null, null, 2 * hour))
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("new", all.first().text)
    }

    @Test
    fun retentionForever_keepsEverythingPastWindow() = runTest {
        val r = repo()
        r.maybeSave(true, false, RETENTION_FOREVER, "old", null, null, 0L)
        r.maybeSave(true, false, RETENTION_FOREVER, "new", null, null, 100 * hour)
        assertEquals(2, r.all().size)
    }

    @Test
    fun prune_dropsExpiredEntriesWithoutAWrite() = runTest {
        val r = repo()
        r.maybeSave(true, false, RETENTION_FOREVER, "stale", null, null, 0L)
        r.maybeSave(true, false, RETENTION_FOREVER, "fresh", null, null, 2 * hour)
        // The user is now viewing history under a 60-minute window, long after both were saved.
        r.prune(60, 2 * hour + 1)
        val all = r.all()
        assertEquals(1, all.size)
        assertEquals("fresh", all.first().text)
    }

    @Test
    fun retention_backstopCapPrunesOldestBeyondCap() = runTest {
        val r = repo()
        val n = HistoryRepository.CAP + 5
        // Retention forever, so only the count backstop applies; timestamps are monotonic.
        for (i in 1..n) r.maybeSave(true, false, RETENTION_FOREVER, "t$i", null, null, i.toLong())
        val all = r.all()
        assertEquals(HistoryRepository.CAP, all.size)
        assertEquals("newest kept", n.toLong(), all.first().createdAt)
        assertTrue("oldest 5 pruned", all.none { it.createdAt <= 5L })
    }
}
