package com.tihor.quill.reliability

/**
 * Pure, Android-free decisions for M11 OEM-survival behaviour, factored out so the policy is
 * unit-testable on the JVM (the survival itself — reboot, doze, force-stop — is only verifiable on a
 * real device). Default posture: persistent but bounded — assertive where the user benefits, never a
 * respawn war against a deliberate stop.
 */
object ReliabilityPolicy {

    /** What [com.tihor.quill.reliability.BootReceiver] should do when the device finishes booting. */
    enum class BootAction {
        /** Do nothing — recovery is off, or the user didn't have the bubble running. */
        NONE,

        /**
         * Attempt to restore the bubble. The caller starts the (specialUse) overlay service and MUST
         * catch a refusal — a background FGS start is illegal on modern Android and falls back to
         * [NOTIFY]. We never start the microphone FGS from boot.
         */
        RESTORE,

        /** Post a low-priority "Tap to re-activate Quill" notification instead of starting anything. */
        NOTIFY,
    }

    /**
     * Decide the boot behaviour. We never blindly start a background service:
     * - recovery off, or bubble wasn't active → [BootAction.NONE]
     * - active + overlay granted → [BootAction.RESTORE] (attempt; refusal → notify)
     * - active + overlay missing → [BootAction.NOTIFY] (can't show a bubble; nudge the user to reopen)
     */
    fun bootAction(bootRestore: Boolean, bubbleActive: Boolean, overlayGranted: Boolean): BootAction =
        when {
            !bootRestore || !bubbleActive -> BootAction.NONE
            overlayGranted -> BootAction.RESTORE
            else -> BootAction.NOTIFY
        }

    /**
     * Execute a [BootAction] with injected side-effects, so the "refused start → notify, never crash"
     * behaviour is unit-testable without Android. A RESTORE whose [restore] throws (e.g.
     * ForegroundServiceStartNotAllowedException) falls back to [notify].
     */
    inline fun executeBoot(action: BootAction, restore: () -> Unit, notify: () -> Unit) {
        when (action) {
            BootAction.NONE -> Unit
            BootAction.NOTIFY -> notify()
            BootAction.RESTORE -> try {
                restore()
            } catch (e: Exception) {
                notify()
            }
        }
    }

    /**
     * Should we restart the bubble now that the app is in the foreground? Legal here (the app is
     * visible), so this recovers an *incidental* kill (memory/doze/OEM trim): the user wants it on,
     * overlay is granted, and the service isn't already running. A deliberate force-stop is respected
     * by the OS (it won't restart and the app won't be foregrounded until the user relaunches).
     */
    fun shouldRecoverOnForeground(
        bootRestore: Boolean,
        bubbleActive: Boolean,
        overlayGranted: Boolean,
        serviceRunning: Boolean,
    ): Boolean = bootRestore && bubbleActive && overlayGranted && !serviceRunning

    /**
     * Re-prompt for the battery-optimisation exemption only when it's needed AND earned: still
     * ungranted AND the bubble has been killed at least once. Not on a timer, not every launch.
     */
    fun shouldRepromptBattery(exemptionGranted: Boolean, wasKilled: Boolean): Boolean =
        !exemptionGranted && wasKilled

    /**
     * Whether to surface the accessibility-revocation recovery prompt: the user enabled the service
     * before (so they want it) but it is no longer connected (the OS/OEM revoked it).
     */
    fun shouldPromptRevocation(userEnabledBefore: Boolean, serviceConnected: Boolean): Boolean =
        userEnabledBefore && !serviceConnected
}
