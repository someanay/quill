package com.tihor.quill.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The dismiss/summon hidden-state flag (Wispr-Flow-style hide/restore). Summoning the bubble
 * (`show`) clears the flag; hiding (drag-to-dismiss) sets it; the flag persists so a new store
 * instance (a service restart) reads the same value back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BubbleVisibilityStoreTest {

    private fun store() = BubbleVisibilityStore(RuntimeEnvironment.getApplication())

    @Test
    fun defaultsToVisible() {
        assertFalse("a fresh install shows the bubble", store().hidden)
    }

    @Test
    fun hide_thenShow_clearsTheFlag() {
        val s = store()
        s.hidden = true
        assertTrue(s.hidden)
        s.hidden = false // summon
        assertFalse("re-show must clear the hidden flag", s.hidden)
    }

    @Test
    fun hiddenState_survivesARestart() {
        store().hidden = true
        // A new instance models the service being recreated (START_STICKY / foreground recovery).
        assertTrue("a deliberately-hidden bubble stays hidden across a restart", store().hidden)
    }
}
