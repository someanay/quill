package com.tihor.quill.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "bring the bubble back on its own after a while" timer (v1.1). Flicking the bubble into the bin
 * is a *temporary* get-out-of-the-way, not a dismissal — so that gesture schedules a return, and any
 * early re-show (notification summon, Home toggle, shake) cancels it.
 *
 * Deliberately **not** armed by every hide: `OverlayService` schedules this only for a drag-dismiss.
 * Switching the Home master toggle off is a stated preference, and a preference that flips itself
 * back on later isn't one — see `OverlayService.hiddenByDrag`.
 *
 * ### Why a coroutine and not AlarmManager / WorkManager
 * Under Option A, hiding the bubble does **not** stop the service: the foreground service and its
 * notification stay alive the whole time the bubble is hidden (only the notification's explicit
 * "Dismiss bubble" full-stop tears it down). A living FGS can simply hold a suspended coroutine, so
 * this needs no scheduler and — decisively — **no `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`**, which
 * are Play-restricted permissions we deliberately don't ship (M12).
 *
 * The accepted cost: [delay] is not Doze-proof. With the screen off in deep idle the return fires on
 * the next wake rather than exactly on time. That's fine — the bubble is invisible during Doze anyway
 * — and if the process is killed and later recovered (M11) the timer is simply gone, leaving the
 * bubble hidden with the notification as the way back. Neither is worth a restricted permission.
 *
 * Owned by [OverlayService]; give it the service's scope so `onDestroy`'s `scope.cancel()` also
 * disarms the timer.
 */
class AutoReturn(
    private val scope: CoroutineScope,
    private val delayMs: Long = DEFAULT_DELAY_MS,
    private val onReturn: suspend () -> Unit,
) {

    private var job: Job? = null

    /** True while a return is armed and hasn't fired yet. */
    val isPending: Boolean get() = job?.isActive == true

    /**
     * Arm the return, replacing any timer already running — so re-hiding restarts the full countdown
     * rather than stacking a second one.
     */
    fun schedule(delayMillis: Long = delayMs) {
        cancel()
        if (delayMillis <= 0) return
        job = scope.launch {
            delay(delayMillis)
            job = null
            onReturn()
        }
    }

    /** Disarm. Safe to call when nothing is armed (every show path calls it unconditionally). */
    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        /** How long a hidden bubble stays hidden before it comes back on its own. */
        const val DEFAULT_DELAY_MS = 10 * 60 * 1000L // 10 minutes
    }
}
