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

    private val screenHeight = 1920
    private val dismissThreshold = 200 // ~dp(72) at 2.75x density, in px for the test

    @Test
    fun dismissZone_releaseNearBottom_dismisses() {
        // Visible bottom at 1800 → within 200 of 1920 → dismiss.
        assertTrue(BubbleGeometry.isInDismissZone(1800, screenHeight, dismissThreshold))
    }

    @Test
    fun dismissZone_releaseUpScreen_doesNotDismiss() {
        // Visible bottom at 600 → far from the bottom → normal snap.
        assertFalse(BubbleGeometry.isInDismissZone(600, screenHeight, dismissThreshold))
    }

    @Test
    fun dismissZone_exactlyAtZoneEdge_dismisses() {
        // Boundary: bottom == screenHeight - threshold → inclusive → dismiss.
        assertTrue(BubbleGeometry.isInDismissZone(screenHeight - dismissThreshold, screenHeight, dismissThreshold))
        // One pixel above the zone → not dismissed.
        assertFalse(BubbleGeometry.isInDismissZone(screenHeight - dismissThreshold - 1, screenHeight, dismissThreshold))
    }

    @Test
    fun dismissZone_atVeryBottom_dismisses() {
        assertTrue(BubbleGeometry.isInDismissZone(screenHeight, screenHeight, dismissThreshold))
    }

    // --- v1.1: bin-target proximity (drives the bin's fade/grow while dragging) ---

    /** Approach band: the bin starts appearing well above the zone it announces. */
    private val approach = 600

    @Test
    fun proximity_farFromTheBottom_isZero() {
        // Above the approach band → the bin stays hidden entirely.
        assertEquals(0f, BubbleGeometry.dismissProximity(200, screenHeight, dismissThreshold, approach), 0f)
        assertEquals(
            "Exactly at the band's top edge is still nothing",
            0f,
            BubbleGeometry.dismissProximity(screenHeight - approach, screenHeight, dismissThreshold, approach),
            0f,
        )
    }

    @Test
    fun proximity_insideTheDismissZone_isFullyOne() {
        // At and below the dismiss zone the bin is fully drawn — proximity == 1 mirrors isInDismissZone.
        assertEquals(
            1f,
            BubbleGeometry.dismissProximity(screenHeight - dismissThreshold, screenHeight, dismissThreshold, approach),
            0f,
        )
        assertEquals(1f, BubbleGeometry.dismissProximity(screenHeight, screenHeight, dismissThreshold, approach), 0f)
        // Past the bottom edge (a fast drag can overshoot) must clamp, not exceed 1.
        assertEquals(
            1f,
            BubbleGeometry.dismissProximity(screenHeight + 500, screenHeight, dismissThreshold, approach),
            0f,
        )
    }

    @Test
    fun proximity_rampsLinearlyAcrossTheApproachBand() {
        // Band spans (screenHeight - approach) .. (screenHeight - threshold) = 1320..1720, i.e. 400 px.
        val bandTop = screenHeight - approach
        assertEquals(0.5f, BubbleGeometry.dismissProximity(bandTop + 200, screenHeight, dismissThreshold, approach), 1e-4f)
        assertEquals(0.25f, BubbleGeometry.dismissProximity(bandTop + 100, screenHeight, dismissThreshold, approach), 1e-4f)
    }

    @Test
    fun proximity_isMonotonicAsTheBubbleDescends() {
        var previous = -1f
        for (bottom in 0..screenHeight step 40) {
            val p = BubbleGeometry.dismissProximity(bottom, screenHeight, dismissThreshold, approach)
            assertTrue("Proximity must never decrease as the bubble moves down (at y=$bottom)", p >= previous)
            previous = p
        }
    }

    @Test
    fun proximity_degradesToAStep_whenTheApproachBandIsDegenerate() {
        // Guards the divide-by-zero path: approach <= threshold falls back to a hard 0/1 at the zone.
        assertEquals(0f, BubbleGeometry.dismissProximity(1000, screenHeight, dismissThreshold, dismissThreshold), 0f)
        assertEquals(1f, BubbleGeometry.dismissProximity(1800, screenHeight, dismissThreshold, dismissThreshold), 0f)
        assertEquals(1f, BubbleGeometry.dismissProximity(1900, screenHeight, dismissThreshold, 10), 0f)
    }
}
