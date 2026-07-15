package com.tihor.quill.carousel

/**
 * Pure state for the carousel's **single** ExoPlayer holder (design decision 4) — extracted from the
 * composable so the play/pause/target/release rules are unit-testable without Android or ExoPlayer.
 *
 * The composable feeds it pager events and reads [targetIndex]/[playing] back to drive the one real
 * player: it prepares the [targetIndex] clip and plays iff [playing].
 *
 * @param autoplay whether a freshly-settled page should start playing. Set to `false` when the system
 *   animator duration scale is 0 (animations off, decision 7): the page becomes the target but stays
 *   paused on its poster/first frame until [onPlayTapped].
 */
class CarouselPlayerState(private val autoplay: Boolean = true) {

    /** The page the single player should be prepared on, or `null` before any settle / after release. */
    var targetIndex: Int? = null
        private set

    /** Whether the player should currently be playing (false while scrolling, released, or reduced-motion). */
    var playing: Boolean = false
        private set

    /** Once released, the holder is inert — every further event is ignored. */
    var released: Boolean = false
        private set

    /** The pager settled on [index]: that page becomes the target and plays (unless [autoplay] is off). */
    fun onPageSettled(index: Int) {
        if (released) return
        targetIndex = index
        playing = autoplay
    }

    /** A drag/scroll is in progress: pause the player but keep the current [targetIndex]. */
    fun onScrollInProgress() {
        if (released) return
        playing = false
    }

    /** Reduced-motion play-on-tap: start the current target playing. No-op if nothing is targeted yet. */
    fun onPlayTapped() {
        if (released || targetIndex == null) return
        playing = true
    }

    /** Composable disposed / lifecycle stop: release the player and go inert. */
    fun onDispose() {
        released = true
        playing = false
        targetIndex = null
    }
}
