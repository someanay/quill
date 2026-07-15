package com.tihor.quill.carousel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Player-holder state logic (task tests): page settle → target index changes + plays; scroll in
 * progress → paused (target retained); dispose → released + inert; reduced-motion → settle stays
 * paused until an explicit play-tap.
 */
class CarouselPlayerStateTest {

    @Test
    fun pageSettle_setsTargetAndPlays() {
        val s = CarouselPlayerState(autoplay = true)
        s.onPageSettled(2)
        assertEquals(2, s.targetIndex)
        assertTrue(s.playing)
    }

    @Test
    fun settlingNewPage_retargets() {
        val s = CarouselPlayerState(autoplay = true)
        s.onPageSettled(0)
        s.onPageSettled(3)
        assertEquals(3, s.targetIndex)
        assertTrue(s.playing)
    }

    @Test
    fun scrollInProgress_pauses_butKeepsTarget() {
        val s = CarouselPlayerState(autoplay = true)
        s.onPageSettled(1)
        s.onScrollInProgress()
        assertFalse(s.playing)
        assertEquals(1, s.targetIndex)
    }

    @Test
    fun dispose_releases_andGoesInert() {
        val s = CarouselPlayerState(autoplay = true)
        s.onPageSettled(1)
        s.onDispose()
        assertTrue(s.released)
        assertFalse(s.playing)
        assertNull(s.targetIndex)
        // Further events are ignored once released.
        s.onPageSettled(4)
        s.onPlayTapped()
        assertNull(s.targetIndex)
        assertFalse(s.playing)
    }

    @Test
    fun reducedMotion_settleDoesNotAutoplay_untilTapped() {
        val s = CarouselPlayerState(autoplay = false)
        s.onPageSettled(0)
        assertEquals(0, s.targetIndex)
        assertFalse("reduced motion must not autoplay", s.playing)
        s.onPlayTapped()
        assertTrue("tap-to-play starts the current target", s.playing)
    }

    @Test
    fun playTapped_beforeAnySettle_isNoOp() {
        val s = CarouselPlayerState(autoplay = false)
        s.onPlayTapped()
        assertNull(s.targetIndex)
        assertFalse(s.playing)
    }
}
