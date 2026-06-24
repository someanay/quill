package com.tihor.quill.overlay

import kotlin.math.hypot

/**
 * Pure (Android-free) geometry for the bubble's drag / snap / tap behaviour, extracted so it can be
 * unit-tested without a device (M1 test task). All values are in pixels.
 */
object BubbleGeometry {

    /** Clamp [value] into the inclusive range [[min], [max]]; tolerant if min > max. */
    fun clamp(value: Int, min: Int, max: Int): Int {
        if (max < min) return min
        return value.coerceIn(min, max)
    }

    /**
     * A press is a tap (not a drag) when the finger moved no further than [touchSlopPx] from where
     * it went down.
     */
    fun isTap(downX: Float, downY: Float, upX: Float, upY: Float, touchSlopPx: Float): Boolean =
        hypot((upX - downX).toDouble(), (upY - downY).toDouble()) <= touchSlopPx

    /**
     * Snap the bubble to the nearer vertical edge. [currentX] is the bubble's left in window
     * coordinates; returns the target left x (0 for the left edge, `screenWidth - bubbleWidth` for
     * the right edge), chosen by which half the bubble's *centre* sits in.
     */
    fun snapTargetX(currentX: Int, bubbleWidth: Int, screenWidth: Int): Int {
        val rightEdgeX = (screenWidth - bubbleWidth).coerceAtLeast(0)
        val center = currentX + bubbleWidth / 2
        return if (center <= screenWidth / 2) 0 else rightEdgeX
    }

    /** Keep the whole bubble on screen vertically. */
    fun clampY(y: Int, bubbleHeight: Int, screenHeight: Int): Int =
        clamp(y, 0, (screenHeight - bubbleHeight).coerceAtLeast(0))
}
