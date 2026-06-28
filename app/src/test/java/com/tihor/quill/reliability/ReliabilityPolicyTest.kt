package com.tihor.quill.reliability

import com.tihor.quill.reliability.ReliabilityPolicy.BootAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M11 "logic only": OEM-survival policy decisions. Survival itself is verified on a real device. */
class ReliabilityPolicyTest {

    // --- Boot ---

    @Test
    fun boot_gatedOff_doesNothing() {
        assertEquals(BootAction.NONE, ReliabilityPolicy.bootAction(bootRestore = false, bubbleActive = true, overlayGranted = true))
    }

    @Test
    fun boot_bubbleNeverActivated_doesNothing() {
        assertEquals(BootAction.NONE, ReliabilityPolicy.bootAction(bootRestore = true, bubbleActive = false, overlayGranted = true))
    }

    @Test
    fun boot_active_overlayGranted_attemptsRestore() {
        assertEquals(BootAction.RESTORE, ReliabilityPolicy.bootAction(bootRestore = true, bubbleActive = true, overlayGranted = true))
    }

    @Test
    fun boot_active_overlayMissing_postsNotification_noIllegalStart() {
        assertEquals(BootAction.NOTIFY, ReliabilityPolicy.bootAction(bootRestore = true, bubbleActive = true, overlayGranted = false))
    }

    @Test
    fun executeBoot_restoreRefused_isCaught_fallsBackToNotify_noCrash() {
        var restored = false
        var notified = false
        ReliabilityPolicy.executeBoot(
            BootAction.RESTORE,
            restore = { restored = true; throw IllegalStateException("ForegroundServiceStartNotAllowed (simulated)") },
            notify = { notified = true },
        )
        assertTrue("restore was attempted", restored)
        assertTrue("refused start fell back to notify", notified)
    }

    @Test
    fun executeBoot_restoreSucceeds_doesNotNotify() {
        var notified = false
        var restored = false
        ReliabilityPolicy.executeBoot(BootAction.RESTORE, restore = { restored = true }, notify = { notified = true })
        assertTrue(restored)
        assertFalse(notified)
    }

    @Test
    fun executeBoot_notify_onlyNotifies() {
        var restored = false
        var notified = false
        ReliabilityPolicy.executeBoot(BootAction.NOTIFY, restore = { restored = true }, notify = { notified = true })
        assertFalse(restored)
        assertTrue(notified)
    }

    @Test
    fun executeBoot_none_doesNothing() {
        var touched = false
        ReliabilityPolicy.executeBoot(BootAction.NONE, restore = { touched = true }, notify = { touched = true })
        assertFalse(touched)
    }

    // --- Foreground recovery ---

    @Test
    fun foreground_recoversIncidentalKill() {
        assertTrue(
            ReliabilityPolicy.shouldRecoverOnForeground(
                bootRestore = true, bubbleActive = true, overlayGranted = true, serviceRunning = false,
            ),
        )
    }

    @Test
    fun foreground_doesNotRestartWhenAlreadyRunning() {
        assertFalse(
            ReliabilityPolicy.shouldRecoverOnForeground(
                bootRestore = true, bubbleActive = true, overlayGranted = true, serviceRunning = true,
            ),
        )
    }

    @Test
    fun foreground_doesNotRestartWhenUserTurnedItOff() {
        assertFalse(
            ReliabilityPolicy.shouldRecoverOnForeground(
                bootRestore = true, bubbleActive = false, overlayGranted = true, serviceRunning = false,
            ),
        )
    }

    // --- Battery re-prompt ---

    @Test
    fun battery_repromptsOnlyWhenUngrantedAndWasKilled() {
        assertTrue(ReliabilityPolicy.shouldRepromptBattery(exemptionGranted = false, wasKilled = true))
        assertFalse("granted → no prompt", ReliabilityPolicy.shouldRepromptBattery(exemptionGranted = true, wasKilled = true))
        assertFalse("never killed → no prompt", ReliabilityPolicy.shouldRepromptBattery(exemptionGranted = false, wasKilled = false))
    }

    // --- Revocation ---

    @Test
    fun revocation_promptsOnlyWhenPreviouslyEnabledAndNowOff() {
        assertTrue(ReliabilityPolicy.shouldPromptRevocation(userEnabledBefore = true, serviceConnected = false))
        assertFalse("still connected → no prompt", ReliabilityPolicy.shouldPromptRevocation(userEnabledBefore = true, serviceConnected = true))
        assertFalse("never enabled → no prompt", ReliabilityPolicy.shouldPromptRevocation(userEnabledBefore = false, serviceConnected = false))
    }
}
