package com.tihor.quill.carousel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asset-scan logic (task tests): ordering by filename, the max-6 cap, missing folder → empty, and
 * non-mp4 files ignored. Pure JVM — no Android, no committed video fixtures (a fake lister stands in
 * for [android.content.res.AssetManager.list]).
 */
class CarouselScanTest {

    private val surface = CarouselSurface.HOME

    @Test
    fun ordersByFilename_notListingOrder() {
        val clips = CarouselScan.order(surface, arrayOf("03.mp4", "01.mp4", "02.mp4"))
        assertEquals(listOf("carousel/home/01.mp4", "carousel/home/02.mp4", "carousel/home/03.mp4"), clips.map { it.assetPath })
        assertEquals(listOf(1, 2, 3), clips.map { it.ordinal })
    }

    @Test
    fun capsAtSixClips() {
        val names = (1..9).map { "%02d.mp4".format(it) }.shuffled().toTypedArray()
        val clips = CarouselScan.order(surface, names)
        assertEquals(CarouselScan.MAX_CLIPS, clips.size)
        // The cap keeps the first six *by sort order*, not the first six of the raw listing.
        assertEquals(
            listOf("01.mp4", "02.mp4", "03.mp4", "04.mp4", "05.mp4", "06.mp4").map { "carousel/home/$it" },
            clips.map { it.assetPath },
        )
    }

    @Test
    fun missingFolder_nullListing_isEmpty() {
        assertTrue(CarouselScan.order(surface, null).isEmpty())
    }

    @Test
    fun emptyFolder_isEmpty() {
        assertTrue(CarouselScan.order(surface, emptyArray()).isEmpty())
    }

    @Test
    fun ignoresNonMp4Files() {
        val clips = CarouselScan.order(surface, arrayOf("01.mp4", ".gitkeep", "readme.txt", "02.MP4", "poster.png"))
        // .mp4 (case-insensitive) only; the .gitkeep placeholder and stray files are dropped.
        assertEquals(listOf("carousel/home/01.mp4", "carousel/home/02.MP4"), clips.map { it.assetPath })
    }

    @Test
    fun surfaceCaptionKey_isPrefixedAndOrdinal() {
        assertEquals("carousel.home.1.caption", CarouselSurface.HOME.captionKey(1))
        assertEquals("carousel.accessibility.4.caption", CarouselSurface.ONBOARDING_ACCESSIBILITY.captionKey(4))
        assertEquals("carousel.key.6.caption", CarouselSurface.ONBOARDING_KEY.captionKey(6))
    }

    @Test
    fun assetCarouselSource_usesListerSeam_perSurface() {
        // Fake AssetManager seam: only the accessibility folder has clips; the others are missing/empty.
        val source = AssetCarouselSource { dir ->
            when (dir) {
                CarouselSurface.ONBOARDING_ACCESSIBILITY.assetDir -> arrayOf("02.mp4", "01.mp4")
                CarouselSurface.HOME.assetDir -> emptyArray()
                else -> null
            }
        }
        assertEquals(
            listOf("carousel/accessibility/01.mp4", "carousel/accessibility/02.mp4"),
            source.clips(CarouselSurface.ONBOARDING_ACCESSIBILITY).map { it.assetPath },
        )
        assertTrue(source.clips(CarouselSurface.HOME).isEmpty())
        assertTrue(source.clips(CarouselSurface.ONBOARDING_KEY).isEmpty())
    }
}
