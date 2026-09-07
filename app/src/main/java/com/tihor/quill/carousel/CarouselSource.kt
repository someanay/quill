package com.tihor.quill.carousel

import android.content.res.AssetManager
import java.io.IOException

/**
 * An ordered list of demo clips bundled in the APK assets.
 */
fun interface CarouselSource {
    /** Clips for [surface], sorted by filename, capped at [CarouselScan.MAX_CLIPS]. Empty if none. */
    fun clips(surface: CarouselSurface): List<CarouselClip>
}

/**
 * Pure asset-scan logic — ordering, the max-clips cap, and the `.mp4` filter — extracted from any
 * Android type so it is unit-testable without a device or Robolectric.
 */
object CarouselScan {
    /** At most this many clips per surface render (design decision: keep the carousel short). */
    const val MAX_CLIPS = 6

    /**
     * Turn a raw directory listing ([fileNames], as returned by [AssetManager.list] — possibly `null`
     * for a missing/unreadable folder) into ordered [CarouselClip]s: keep only `.mp4`, sort by name
     * (so `01.mp4` < `02.mp4` …), cap at [MAX_CLIPS], and assign 1-based ordinals for caption lookup.
     */
    fun order(surface: CarouselSurface, fileNames: Array<String>?): List<CarouselClip> {
        if (fileNames == null) return emptyList()
        return fileNames
            .filter { it.endsWith(".mp4", ignoreCase = true) }
            .sorted()
            .take(MAX_CLIPS)
            .mapIndexed { i, name -> CarouselClip(assetPath = "${surface.assetDir}/$name", ordinal = i + 1) }
    }
}

/**
 * [CarouselSource] backed by the APK's `assets/`. The [lister] seam (default: [AssetManager.list],
 * with a missing folder mapped to `null`) lets tests drive the scan with a fake directory listing
 * instead of committing real video files.
 */
class AssetCarouselSource(
    private val lister: (String) -> Array<String>?,
) : CarouselSource {

    constructor(assets: AssetManager) : this({ dir ->
        try {
            assets.list(dir)
        } catch (e: IOException) {
            null // missing/unreadable folder → fail soft to empty
        }
    })

    override fun clips(surface: CarouselSurface): List<CarouselClip> =
        CarouselScan.order(surface, lister(surface.assetDir))
}
