package com.tihor.quill.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scope doc §10: the three-tier resolution order, reset-to-default semantics, and curated-map sanity.
 * Pure — no Android, no storage — which is the whole point of keeping [WritingStyleResolver] free of
 * both (same contract as `BubbleGeometry` / `CleanupGuard`).
 */
class WritingStyleResolverTest {

    private val curated = mapOf(
        "com.work.app" to WritingStyle.FORMAL,
        "com.social.app" to WritingStyle.CASUAL,
    )

    private fun resolve(
        pkg: String?,
        overrides: Map<String, WritingStyle> = emptyMap(),
        global: WritingStyle = WritingStyle.NEUTRAL,
    ) = WritingStyleResolver.resolve(pkg, overrides, global, curated)

    // --- Tier order ---

    @Test
    fun override_beatsCuratedAndGlobal() {
        // The user's explicit choice wins over Quill's guess for the same app.
        assertEquals(
            WritingStyle.CASUAL,
            resolve("com.work.app", overrides = mapOf("com.work.app" to WritingStyle.CASUAL)),
        )
    }

    @Test
    fun curated_beatsGlobal_whenNoOverride() {
        assertEquals(WritingStyle.FORMAL, resolve("com.work.app", global = WritingStyle.CASUAL))
        assertEquals(WritingStyle.CASUAL, resolve("com.social.app", global = WritingStyle.FORMAL))
    }

    @Test
    fun unknownPackage_fallsBackToGlobalDefault() {
        assertEquals(WritingStyle.NEUTRAL, resolve("com.never.heard.of"))
        assertEquals(WritingStyle.FORMAL, resolve("com.never.heard.of", global = WritingStyle.FORMAL))
    }

    @Test
    fun overrideForAnotherApp_doesNotLeak() {
        val overrides = mapOf("com.social.app" to WritingStyle.FORMAL)
        assertEquals("only the overridden package changes", WritingStyle.FORMAL, resolve("com.work.app", overrides))
        assertEquals(WritingStyle.FORMAL, resolve("com.social.app", overrides))
    }

    // --- Always resolves: a missing package can never fail or block a dictation ---

    @Test
    fun nullOrBlankPackage_resolvesToGlobalDefault() {
        assertEquals(WritingStyle.NEUTRAL, resolve(null))
        assertEquals(WritingStyle.NEUTRAL, resolve(""))
        assertEquals(WritingStyle.NEUTRAL, resolve("   "))
        assertEquals(WritingStyle.CASUAL, resolve(null, global = WritingStyle.CASUAL))
    }

    // --- Reset (§10): deleting an override drops to the CURATED default, not the last resolved value ---

    @Test
    fun reset_fallsBackToCurated_notToThePreviouslyResolvedValue() {
        val pkg = "com.work.app" // curated FORMAL
        val overridden = mapOf(pkg to WritingStyle.CASUAL)
        assertEquals(WritingStyle.CASUAL, resolve(pkg, overridden))

        // "Reset to default" is exactly deleting the key — and it must land on the curated FORMAL,
        // not stay on CASUAL and not drop all the way to the global default.
        val afterReset = overridden - pkg
        assertEquals(WritingStyle.FORMAL, resolve(pkg, afterReset))
    }

    @Test
    fun reset_onAnUncuratedApp_fallsBackToGlobalDefault() {
        val pkg = "com.never.heard.of"
        val overridden = mapOf(pkg to WritingStyle.FORMAL)
        assertEquals(WritingStyle.FORMAL, resolve(pkg, overridden))
        assertEquals(WritingStyle.CASUAL, resolve(pkg, overridden - pkg, global = WritingStyle.CASUAL))
    }

    @Test
    fun curatedDefaults_trackTheGlobalDefault_onlyWhereUncurated() {
        // Changing the global default must not move a curated app — that's why the Neutral entries are
        // listed explicitly rather than left to fall through.
        assertEquals(WritingStyle.FORMAL, resolve("com.work.app", global = WritingStyle.CASUAL))
        assertEquals(WritingStyle.CASUAL, resolve("com.other.app", global = WritingStyle.CASUAL))
    }

    // --- Curated map sanity (§10) ---

    @Test
    fun curatedMap_hasNoBlankPackages_andEveryEntryIsAValidStyle() {
        assertTrue("the curated map should be substantive", CuratedAppStyles.MAP.size >= 30)
        CuratedAppStyles.MAP.forEach { (pkg, style) ->
            assertTrue("blank package name in the curated map", pkg.isNotBlank())
            assertTrue("package names must be dotted ids: $pkg", pkg.contains('.'))
            assertFalse("package names must not carry whitespace: '$pkg'", pkg.any { it.isWhitespace() })
            assertTrue("unknown style for $pkg", style in WritingStyle.entries)
        }
    }

    @Test
    fun curatedMap_hasNoDuplicatePackages_evenCaseInsensitively() {
        // A Map literal can't hold exact duplicates, but "com.Slack" vs "com.slack" would be two
        // entries that can never both match — a copy/paste slip worth failing on.
        val lowered = CuratedAppStyles.MAP.keys.map { it.lowercase() }
        assertEquals("duplicate package (case-insensitive) in the curated map", lowered.size, lowered.toSet().size)
    }

    @Test
    fun curatedMap_coversAllThreeStyles() {
        WritingStyle.entries.forEach { style ->
            assertTrue("no curated package maps to $style", CuratedAppStyles.MAP.containsValue(style))
        }
    }

    @Test
    fun curatedMap_pinsTheEasilyMistypedPackages() {
        // Regression pins for the ids most likely to be "corrected" to a wrong-but-plausible spelling.
        assertEquals(WritingStyle.FORMAL, CuratedAppStyles["com.Slack"]) // capital S — genuinely
        assertEquals(WritingStyle.FORMAL, CuratedAppStyles["notion.id"]) // not com.notion.*
        assertEquals(WritingStyle.CASUAL, CuratedAppStyles["com.zhiliaoapp.musically"]) // TikTok
        assertEquals(WritingStyle.CASUAL, CuratedAppStyles["com.instagram.barcelona"]) // Threads
        assertEquals(WritingStyle.CASUAL, CuratedAppStyles["com.twitter.android"]) // X kept the old id
        assertNotNull(CuratedAppStyles["com.google.android.gm"]) // Gmail
    }

    @Test
    fun browsers_areNeutral_notFormal() {
        // §5.1: a browser field could be webmail or a Reddit comment — the package can't tell us.
        assertEquals(WritingStyle.NEUTRAL, CuratedAppStyles["com.android.chrome"])
        assertEquals(WritingStyle.NEUTRAL, CuratedAppStyles["org.mozilla.firefox"])
    }

    @Test
    fun curatedLookup_ofAnUnknownPackage_isNull() {
        assertEquals(null, CuratedAppStyles["com.never.heard.of"])
        assertEquals(null, CuratedAppStyles[null])
    }

    // --- Style id persistence contract ---

    @Test
    fun styleIds_areStableAndRoundTrip() {
        WritingStyle.entries.forEach { assertEquals(it, WritingStyle.fromId(it.id)) }
        // Pinned literals: these strings are on disk in users' DataStore.
        assertEquals(WritingStyle.FORMAL, WritingStyle.fromId("formal"))
        assertEquals(WritingStyle.NEUTRAL, WritingStyle.fromId("neutral"))
        assertEquals(WritingStyle.CASUAL, WritingStyle.fromId("casual"))
    }

    @Test
    fun unknownOrMissingId_degradesToTheDefault_neverThrows() {
        assertEquals(WritingStyle.NEUTRAL, WritingStyle.fromId(null))
        assertEquals(WritingStyle.NEUTRAL, WritingStyle.fromId(""))
        assertEquals(WritingStyle.NEUTRAL, WritingStyle.fromId("shakespearean"))
        assertEquals("the shipped global default is Neutral", WritingStyle.NEUTRAL, WritingStyle.DEFAULT)
    }
}
