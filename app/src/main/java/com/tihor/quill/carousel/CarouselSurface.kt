package com.tihor.quill.carousel

/**
 * Where a [DemoCarousel][com.tihor.quill.ui.components.DemoCarousel] appears. Each surface maps to its
 * own asset folder (scanned at runtime, never hardcoded filenames) and its own copy-key prefix for the
 * per-clip captions. Adding a surface here + its asset folder + caption keys is all a new placement needs.
 */
enum class CarouselSurface(
    /** Asset folder scanned for clips, relative to `assets/`. Files named `01.mp4`, `02.mp4`, … */
    val assetDir: String,
    /** Copy-key prefix for this surface's captions: `<prefix>.<ordinal>.caption`. */
    val copyPrefix: String,
) {
    HOME("carousel/home", "carousel.home"),
    ONBOARDING_ACCESSIBILITY("carousel/accessibility", "carousel.accessibility"),
    ONBOARDING_KEY("carousel/key", "carousel.key"),
    ;

    /** Copy key of the [ordinal]-th clip's caption (1-based), e.g. `carousel.home.1.caption`. */
    fun captionKey(ordinal: Int): String = "$copyPrefix.$ordinal.caption"
}

/**
 * One playable demo clip resolved from a surface's asset folder.
 *
 * @param assetPath asset-relative path (e.g. `carousel/home/01.mp4`) — feed ExoPlayer as `asset:///$assetPath`.
 * @param ordinal 1-based position after sort, used to look up the caption key (max [CarouselScan.MAX_CLIPS]).
 */
data class CarouselClip(
    val assetPath: String,
    val ordinal: Int,
)
