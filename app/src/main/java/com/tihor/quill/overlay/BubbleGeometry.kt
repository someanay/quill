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

    /**
     * True when the bubble has been dragged into the bottom "dismiss zone": its visible bottom edge
     * ([bubbleBottomY], in window coordinates) sits within [thresholdPx] of the screen's bottom edge
     * (the charging-port side). Releasing a drag here hides the bubble instead of edge-snapping it
     * (Wispr-Flow-style). Pass the *visible* bottom (excluding the bubble's transparent halo/shadow
     * padding) so the threshold matches what the user sees.
     */
    fun isInDismissZone(bubbleBottomY: Int, screenHeight: Int, thresholdPx: Int): Boolean =
        bubbleBottomY >= screenHeight - thresholdPx

    /**
     * How close the dragged bubble is to dismissing, as 0..1 — what fades and grows the bin target
     * while the user drags (v1.1). 0 until the bubble's visible bottom edge ([bubbleBottomY]) enters
     * the approach band [approachPx] off the screen's bottom, then ramps linearly to 1 at the dismiss
     * zone itself ([thresholdPx]), staying 1 all the way down. So the bin appears *before* the drop
     * would take effect, and is fully drawn exactly when releasing would hide the bubble — the
     * proximity `> 0` is the show/hide signal for the bin window, and `== 1` mirrors
     * [isInDismissZone].
     *
     * [approachPx] is expected to be larger than [thresholdPx]; if it isn't, this degrades to a hard
     * 0/1 step at the dismiss zone rather than dividing by zero.
     */
    fun dismissProximity(
        bubbleBottomY: Int,
        screenHeight: Int,
        thresholdPx: Int,
        approachPx: Int,
    ): Float {
        if (approachPx <= thresholdPx) {
            return if (isInDismissZone(bubbleBottomY, screenHeight, thresholdPx)) 1f else 0f
        }
        val enterY = screenHeight - approachPx
        val fullY = screenHeight - thresholdPx
        return ((bubbleBottomY - enterY).toFloat() / (fullY - enterY)).coerceIn(0f, 1f)
    }
}
