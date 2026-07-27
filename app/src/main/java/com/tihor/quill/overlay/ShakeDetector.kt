package com.tihor.quill.overlay

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure (Android-free) shake detection for "shake to summon the hidden bubble" (v1.1). Fed raw
 * accelerometer samples by [com.tihor.quill.overlay.OverlayService]; emits `true` on the sample that
 * completes a deliberate shake. Android-free so the tuning is unit-testable without a device — the
 * same reason [BubbleGeometry] exists.
 *
 * ### How a "deliberate shake" is told apart from pocket/walking noise
 * 1. **Gravity is removed, not assumed.** A slow low-pass ([GRAVITY_LOWPASS]) tracks the gravity
 *    vector whatever the phone's orientation; what's left is *linear* acceleration. So a phone lying
 *    flat and a phone held upright behave identically, and slowly tilting the device produces
 *    (correctly) almost no linear acceleration.
 * 2. **A single hard bump is not a shake.** One jolt over [JOLT_THRESHOLD_MPS2] proves nothing — a
 *    phone dropped on a desk clears it easily. We require [REQUIRED_REVERSALS] *direction reversals*:
 *    the dominant axis must swing +, −, + (three jolts) — i.e. the user actually oscillated it.
 * 3. **The reversals must be on the same axis and close together.** A shake is strongly single-axis;
 *    a bag being jostled flails across all three. Changing dominant axis restarts the sequence, as
 *    does letting [SEQUENCE_WINDOW_MS] elapse. Walking/running is periodic but its linear peaks sit
 *    far below [JOLT_THRESHOLD_MPS2] (~1.6 g of *net* motion), so it never opens a sequence at all.
 * 4. **One shake fires once.** [COOLDOWN_MS] after a hit, further samples are ignored, so a two-second
 *    enthusiastic shake summons the bubble once rather than four times.
 *
 * Not thread-safe: feed it from one thread (the sensor callback's main looper).
 */
class ShakeDetector {

    /** Running low-pass estimate of the gravity vector, in m/s². */
    private val gravity = FloatArray(3)
    private var seeded = false

    // --- current jolt sequence ---
    private var lastJoltMs = 0L
    private var lastAxis = NO_AXIS
    private var lastSign = 0
    private var reversals = 0
    private var sequenceStartMs = 0L
    private var cooldownUntilMs = 0L

    /**
     * Feed one accelerometer sample (m/s², device axes) taken at [timestampMs] (any monotonic
     * millisecond clock). Returns true exactly on the sample that completes a shake.
     */
    fun onSample(x: Float, y: Float, z: Float, timestampMs: Long): Boolean {
        // Seed the gravity estimate from the first sample so we don't spend the first ~half second
        // reporting a phantom jolt while the filter converges from zero.
        if (!seeded) {
            gravity[0] = x
            gravity[1] = y
            gravity[2] = z
            seeded = true
            return false
        }
        gravity[0] = GRAVITY_LOWPASS * gravity[0] + (1f - GRAVITY_LOWPASS) * x
        gravity[1] = GRAVITY_LOWPASS * gravity[1] + (1f - GRAVITY_LOWPASS) * y
        gravity[2] = GRAVITY_LOWPASS * gravity[2] + (1f - GRAVITY_LOWPASS) * z

        val lx = x - gravity[0]
        val ly = y - gravity[1]
        val lz = z - gravity[2]

        // Keep the filter fed during the cooldown (so orientation stays tracked), but score nothing.
        if (timestampMs < cooldownUntilMs) return false

        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)
        if (magnitude < JOLT_THRESHOLD_MPS2) return false
        if (timestampMs - lastJoltMs < MIN_JOLT_GAP_MS) return false

        // The axis carrying most of this jolt, and which way it went.
        var axis = AXIS_X
        var component = lx
        if (abs(ly) > abs(component)) { axis = AXIS_Y; component = ly }
        if (abs(lz) > abs(component)) { axis = AXIS_Z; component = lz }
        val sign = if (component >= 0f) 1 else -1

        when {
            // No sequence yet, the axis changed, or the window lapsed → this jolt starts a new one.
            lastSign == 0 || axis != lastAxis || timestampMs - sequenceStartMs > SEQUENCE_WINDOW_MS -> {
                reversals = 0
                sequenceStartMs = timestampMs
            }
            // Same axis, opposite direction → the user swung it back: a genuine reversal.
            sign != lastSign -> reversals++
            // Same axis, same direction → still the same impulse; not new evidence, but not a reset
            // either (resetting here would make a fast sensor rate destroy its own sequence).
            else -> Unit
        }
        lastAxis = axis
        lastSign = sign
        lastJoltMs = timestampMs

        if (reversals >= REQUIRED_REVERSALS) {
            clearSequence()
            cooldownUntilMs = timestampMs + COOLDOWN_MS
            return true
        }
        return false
    }

    /** Forget everything, including the gravity estimate and any cooldown. Call on (un)register. */
    fun reset() {
        clearSequence()
        seeded = false
        cooldownUntilMs = 0L
    }

    private fun clearSequence() {
        lastJoltMs = 0L
        lastAxis = NO_AXIS
        lastSign = 0
        reversals = 0
        sequenceStartMs = 0L
    }

    companion object {
        /**
         * Low-pass weight for the gravity estimate. At `SENSOR_DELAY_UI` (~60 ms) this is a ~370 ms
         * time constant: slow enough to pass a 3–5 Hz shake straight through as linear acceleration,
         * fast enough to re-track gravity within about a second when the phone is turned over.
         */
        const val GRAVITY_LOWPASS = 0.85f

        /**
         * Linear (gravity-removed) acceleration a sample must exceed to count as a jolt, in m/s².
         * ~1.6 g of *net* motion: a firm flick of the wrist clears it comfortably (20–40 m/s²), while
         * walking with the phone in hand peaks around 3–7 and running around 10–15.
         */
        const val JOLT_THRESHOLD_MPS2 = 16f

        /** Ignore jolts closer together than this (ms) so one impulse can't score twice on a fast sensor. */
        const val MIN_JOLT_GAP_MS = 40L

        /** All required reversals must land within this window (ms) of the sequence's first jolt. */
        const val SEQUENCE_WINDOW_MS = 900L

        /** Direction reversals required to fire — 2 reversals = a +/−/+ swing = three jolts. */
        const val REQUIRED_REVERSALS = 2

        /** After firing, ignore samples for this long (ms) so one shake summons the bubble once. */
        const val COOLDOWN_MS = 2_000L

        private const val NO_AXIS = -1
        private const val AXIS_X = 0
        private const val AXIS_Y = 1
        private const val AXIS_Z = 2
    }
}
