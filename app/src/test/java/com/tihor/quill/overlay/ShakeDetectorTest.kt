package com.tihor.quill.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Tuning guard for [ShakeDetector] (v1.1 shake-to-summon). The whole point of keeping the detector
 * Android-free is that its false-positive resistance can be pinned down here instead of by shaking a
 * phone and hoping — so this exercises the cases that actually matter: a real shake fires, everyday
 * motion doesn't, and one shake fires once.
 *
 * Samples are synthesised at [SAMPLE_MS], matching `SENSOR_DELAY_UI` (~60 ms) as registered by
 * `OverlayService`, so the numbers here are the numbers the device sees.
 */
class ShakeDetectorTest {

    /** Feed [durationMs] of samples from [sample] and return every timestamp that fired. */
    private fun run(
        detector: ShakeDetector = ShakeDetector(),
        durationMs: Long,
        sample: (tMs: Long) -> Triple<Float, Float, Float>,
    ): List<Long> {
        val fires = mutableListOf<Long>()
        var t = 0L
        while (t <= durationMs) {
            val (x, y, z) = sample(t)
            if (detector.onSample(x, y, z, t)) fires += t
            t += SAMPLE_MS
        }
        return fires
    }

    /** Phone held flat (gravity on Z) and shaken side-to-side along X at [hz]. */
    private fun shaking(amplitude: Float, hz: Float): (Long) -> Triple<Float, Float, Float> = { t ->
        val x = amplitude * sin(2.0 * PI * hz * (t / 1000.0)).toFloat()
        Triple(x, 0f, GRAVITY)
    }

    @Test
    fun deliberateShake_fires() {
        val fires = run(durationMs = 1_000L, sample = shaking(amplitude = 30f, hz = 3f))
        assertTrue("A firm 3 Hz side-to-side shake should summon the bubble", fires.isNotEmpty())
    }

    @Test
    fun oneShake_firesOnce_becauseOfTheCooldown() {
        // Shake continuously for just under the cooldown past the first hit: a second fire in this
        // window would mean one enthusiastic shake summoning the bubble repeatedly.
        val fires = run(durationMs = 2_300L, sample = shaking(amplitude = 30f, hz = 3f))
        assertEquals("Continuous shaking inside the cooldown must fire exactly once: $fires", 1, fires.size)
    }

    @Test
    fun shakingPastTheCooldown_canFireAgain() {
        val fires = run(durationMs = 5_000L, sample = shaking(amplitude = 30f, hz = 3f))
        assertTrue("Shaking well past the cooldown should be able to fire again: $fires", fires.size >= 2)
    }

    @Test
    fun walkingWithThePhoneInHand_doesNotFire() {
        // Brisk walk: ~2 Hz cadence, a few m/s² of bounce on Z plus arm swing on X/Y. Periodic, but
        // nowhere near the jolt threshold — this is the motion the threshold exists to reject.
        val fires = run(durationMs = 8_000L) { t ->
            val s = t / 1000.0
            val bounce = 6f * sin(2.0 * PI * 2.0 * s).toFloat()
            val swingX = 4f * sin(2.0 * PI * 1.0 * s).toFloat()
            val swingY = 3f * sin(2.0 * PI * 1.0 * s + 1.0).toFloat()
            Triple(swingX, swingY, GRAVITY + bounce)
        }
        assertTrue("Walking must not summon the bubble (fired at $fires)", fires.isEmpty())
    }

    @Test
    fun joggingWithThePhoneInAPocket_doesNotFire() {
        // Harder case: ~2.6 Hz, bigger amplitude, and multi-axis (a phone loose in a pocket flails).
        // It stays under threshold, and the axis-jumping also denies it a coherent sequence.
        val fires = run(durationMs = 8_000L) { t ->
            val s = t / 1000.0
            Triple(
                9f * sin(2.0 * PI * 2.6 * s).toFloat(),
                8f * sin(2.0 * PI * 2.6 * s + 2.1).toFloat(),
                GRAVITY + 10f * sin(2.0 * PI * 2.6 * s + 0.7).toFloat(),
            )
        }
        assertTrue("Jogging with the phone pocketed must not summon the bubble (fired at $fires)", fires.isEmpty())
    }

    @Test
    fun singleHardJolt_doesNotFire() {
        // Setting the phone down hard, or a bump in a bag: one big spike, no reversal. Exactly what
        // the "require direction reversals" rule exists to reject.
        val fires = run(durationMs = 2_000L) { t ->
            val spike = if (t in 500L..560L) 45f else 0f
            Triple(spike, 0f, GRAVITY)
        }
        assertTrue("A single hard jolt is not a shake (fired at $fires)", fires.isEmpty())
    }

    @Test
    fun slowlyTiltingThePhone_doesNotFire() {
        // Gravity rotating out of Z and into X over ~4 s — picking the phone up off a table. The
        // low-pass tracks it, so almost no linear acceleration is produced.
        val fires = run(durationMs = 4_000L) { t ->
            val angle = (t / 4_000.0) * (PI / 2)
            Triple((GRAVITY * sin(angle)).toFloat(), 0f, (GRAVITY * kotlin.math.cos(angle)).toFloat())
        }
        assertTrue("Tilting the phone must not summon the bubble (fired at $fires)", fires.isEmpty())
    }

    @Test
    fun sittingStill_doesNotFire() {
        val fires = run(durationMs = 5_000L) { Triple(0f, 0f, GRAVITY) }
        assertTrue("A stationary phone must never fire", fires.isEmpty())
    }

    @Test
    fun reset_dropsAnInProgressSequence() {
        val detector = ShakeDetector()
        // Feed half a shake, reset (as a hide/show transition does), then feed the other half. The
        // fragments must not add up to a summon across the boundary.
        val shake = shaking(amplitude = 30f, hz = 3f)
        var t = 0L
        while (t <= 250L) {
            val (x, y, z) = shake(t)
            assertFalse(detector.onSample(x, y, z, t))
            t += SAMPLE_MS
        }
        detector.reset()
        val fires = mutableListOf<Long>()
        while (t <= 500L) {
            val (x, y, z) = shake(t)
            if (detector.onSample(x, y, z, t)) fires += t
            t += SAMPLE_MS
        }
        assertTrue("A reset must discard the partial sequence (fired at $fires)", fires.isEmpty())
    }

    private companion object {
        const val GRAVITY = 9.81f

        /** ~`SENSOR_DELAY_UI`, the rate OverlayService actually registers at. */
        const val SAMPLE_MS = 50L
    }
}
