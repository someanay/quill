package com.tihor.quill.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The synchronous mirror of the unified bubble-visibility state. Defaults to shown; flips both ways;
 * persists so a new instance (a service restart / boot) reads the same value back ("hidden restores
 * as hidden"). Its lockstep with the DataStore Flow is covered in [SettingsRepositoryTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BubbleVisibilityStoreTest {

    private fun store() = BubbleVisibilityStore(RuntimeEnvironment.getApplication())

    @Test
    fun defaultsToVisible() {
        assertTrue("a fresh install shows the bubble", store().visible)
    }

    @Test
    fun hide_thenShow_flipsBothWays() {
        val s = store()
        s.visible = false
        assertFalse(s.visible)
        s.visible = true // summon / toggle on
        assertTrue(s.visible)
    }

    @Test
    fun hiddenState_survivesARestart() {
        store().visible = false
        // A new instance models the service being recreated (START_STICKY / boot / foreground recovery).
        assertFalse("a hidden bubble stays hidden across a restart", store().visible)
    }
}
