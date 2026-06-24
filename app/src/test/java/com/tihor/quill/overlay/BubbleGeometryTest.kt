package com.tihor.quill.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the isolated drag/snap geometry (M1 test task; testing_plan.md §2 Overlay). */
class BubbleGeometryTest {

    private val screenWidth = 1080
    private val bubbleWidth = 150

    @Test
    fun snap_centerInLeftHalf_goesToLeftEdge() {
        // Bubble left at 100 → centre 175 < 540 → snap to 0.
        assertEquals(0, BubbleGeometry.snapTargetX(100, bubbleWidth, screenWidth))
    }

    @Test
    fun snap_centerInRightHalf_goesToRightEdge() {
        // Bubble left at 800 → centre 875 > 540 → snap to right edge.
        assertEquals(screenWidth - bubbleWidth, BubbleGeometry.snapTargetX(800, bubbleWidth, screenWidth))
    }

    @Test
    fun snap_centerExactlyAtMidline_prefersLeft() {
        // centre == screenWidth/2 → left (<=).
        val x = screenWidth / 2 - bubbleWidth / 2
        assertEquals(0, BubbleGeometry.snapTargetX(x, bubbleWidth, screenWidth))
    }

    @Test
    fun snap_bubbleWiderThanScreen_clampsRightEdgeToZero() {
        assertEquals(0, BubbleGeometry.snapTargetX(0, 2000, screenWidth))
    }

    @Test
    fun clamp_withinAndOutOfRange() {
        assertEquals(5, BubbleGeometry.clamp(5, 0, 10))
        assertEquals(0, BubbleGeometry.clamp(-3, 0, 10))
        assertEquals(10, BubbleGeometry.clamp(99, 0, 10))
    }

    @Test
    fun clamp_invertedRange_returnsMin() {
        assertEquals(0, BubbleGeometry.clamp(5, 0, -1))
    }

    @Test
    fun clampY_keepsBubbleOnScreen() {
        assertEquals(0, BubbleGeometry.clampY(-50, 150, 1920))
        assertEquals(1920 - 150, BubbleGeometry.clampY(5000, 150, 1920))
        assertEquals(300, BubbleGeometry.clampY(300, 150, 1920))
    }

    @Test
    fun isTap_smallMovementIsTap_largeMovementIsDrag() {
        assertTrue(BubbleGeometry.isTap(100f, 100f, 104f, 103f, touchSlopPx = 16f))
        assertFalse(BubbleGeometry.isTap(100f, 100f, 200f, 100f, touchSlopPx = 16f))
    }
}
