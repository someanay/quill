package com.tihor.quill.overlay

/**
 * Visual states of the floating bubble (PRD §5 decision 5). M1 only *renders* these as stubs and
 * lets a tap cycle through them for visual verification; the real transitions are wired by the
 * recorder (M4), STT (M5), and the end-to-end path (M6).
 */
enum class BubbleState {
    /** Small, semi-transparent. Waiting. */
    IDLE,

    /** Capturing audio (live level indicator is added at M4). */
    RECORDING,

    /** Audio captured; awaiting the transcript. */
    TRANSCRIBING,

    /** Insertion fell back to the clipboard; user taps to paste (spec §5 ladder). */
    PASTE,

    /** Something failed; offers retry (wired later). */
    ERROR,
    ;

    /** Next state in the demo cycle (M1 tap behaviour only). */
    fun next(): BubbleState = entries[(ordinal + 1) % entries.size]
}
