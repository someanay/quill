package com.tihor.quill.overlay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AutoReturn] behaviour on virtual time — the ~10-minute "bring the hidden bubble back" timer,
 * tested without waiting ten real minutes. Each case mirrors one thing `OverlayService` relies on:
 * showing early disarms it, re-hiding restarts it rather than stacking, and a full-stop kills it.
 *
 * *Which* hides arm it is the service's call, not this class's: only a drag-dismiss schedules, while a
 * Home-toggle "off" cancels (`OverlayService.hiddenByDrag`). Both routes bottom out in the
 * [AutoReturn.schedule] / [AutoReturn.cancel] behaviour pinned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReturnTest {

    private fun scope() = TestScope()

    @Test
    fun fires_onceTheDelayElapses() {
        val scope = scope()
        var returns = 0
        val autoReturn = AutoReturn(scope, DELAY) { returns++ }

        autoReturn.schedule()
        scope.advanceTimeBy(DELAY - 1)
        scope.runCurrent()
        assertEquals("Must not return early", 0, returns)
        assertTrue(autoReturn.isPending)

        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals("Must return once the delay elapses", 1, returns)
        assertFalse("No longer pending after firing", autoReturn.isPending)
    }

    @Test
    fun cancel_disarmsIt_asAnEarlyShowDoes() {
        // The notification summon, the Home toggle, and a shake all land on applyVisibility(true),
        // which cancels. The bubble is already back — a later automatic re-show would be a bug.
        val scope = scope()
        var returns = 0
        val autoReturn = AutoReturn(scope, DELAY) { returns++ }

        autoReturn.schedule()
        scope.advanceTimeBy(DELAY / 2)
        autoReturn.cancel()
        assertFalse(autoReturn.isPending)

        scope.advanceTimeBy(DELAY * 2)
        scope.runCurrent()
        assertEquals("A cancelled return must never fire", 0, returns)
    }

    @Test
    fun rescheduling_restartsTheClock_andDoesNotStackTimers() {
        // Hide → show → hide again. The second hide must give a full fresh delay, and the first
        // timer must be gone (two fires would show the bubble twice).
        val scope = scope()
        var returns = 0
        val autoReturn = AutoReturn(scope, DELAY) { returns++ }

        autoReturn.schedule()
        scope.advanceTimeBy(DELAY / 2)
        autoReturn.schedule() // re-hidden

        scope.advanceTimeBy(DELAY / 2 + 1) // past when the FIRST timer would have fired
        scope.runCurrent()
        assertEquals("The replaced timer must not fire", 0, returns)

        scope.advanceTimeBy(DELAY / 2)
        scope.runCurrent()
        assertEquals("The restarted timer fires exactly once", 1, returns)
    }

    @Test
    fun cancellingTheScope_killsIt_asAFullStopDoes() {
        // ACTION_STOP ("Dismiss bubble") tears the service down; onDestroy cancels the scope this
        // timer runs on. A bubble the user explicitly dismissed must never come back on a timer.
        val scope = scope()
        var returns = 0
        val autoReturn = AutoReturn(scope, DELAY) { returns++ }

        autoReturn.schedule()
        scope.advanceTimeBy(DELAY / 2)
        scope.cancel()

        assertEquals("A full-stop must cancel the pending return", 0, returns)
        assertFalse(autoReturn.isPending)
    }

    @Test
    fun defaultDelay_isTenMinutes() {
        assertEquals(10 * 60 * 1000L, AutoReturn.DEFAULT_DELAY_MS)
    }

    private companion object {
        const val DELAY = 10 * 60 * 1000L
    }
}
