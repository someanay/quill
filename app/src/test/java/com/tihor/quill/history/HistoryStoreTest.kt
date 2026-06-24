package com.tihor.quill.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** M9 "write first": the hand-rolled SQLite store inserts/queries/deletes and prunes oldest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryStoreTest {

    private fun store() = HistoryStore(RuntimeEnvironment.getApplication()).also { it.clearAll() }

    @Test
    fun insert_query_delete_clear() {
        val s = store()
        val id1 = s.insert("hello", 1_000, "com.a", "App A")
        s.insert("world", 2_000, "com.b", "App B")

        val all = s.queryAll()
        assertEquals(2, all.size)
        assertEquals("world", all.first().text) // newest first
        assertEquals("App A", all.last().appLabel)

        assertEquals(1, s.delete(id1))
        assertEquals(1, s.queryAll().size)

        s.clearAll()
        assertEquals(0L, s.count())
    }

    @Test
    fun pruneTo_dropsOldestBeyondCap() {
        val s = store()
        for (i in 1..5) s.insert("t$i", i.toLong(), null, null)

        val deleted = s.pruneTo(3)
        assertEquals(2, deleted)

        val all = s.queryAll()
        assertEquals(3, all.size)
        assertEquals(5L, all.first().createdAt) // newest kept
        assertEquals(3L, all.last().createdAt)  // oldest kept
        assertTrue("oldest two pruned", all.none { it.createdAt <= 2L })
    }
}
