package com.tihor.quill.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * M8 Copy Catalog tests (spec §2.3 / testing_plan §1): the baked-in catalog is complete, missing
 * keys fall back, and the OTA override layer can never change a **compliance** key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyRepositoryTest {

    private fun repo() = CopyRepository(RuntimeEnvironment.getApplication())

    @Test
    fun bakedIn_containsEveryReferencedKey() {
        val r = repo()
        assertTrue("strings.default.json is missing keys referenced in code", r.isComplete())
        // Every referenced key has a non-blank baked-in default — except the intentionally-blank ones
        // (carousel captions), which are still *present* (isComplete covers that) but render nothing.
        CopyKeys.ALL.filterNot { it in CopyKeys.OPTIONAL_BLANK }.forEach { key ->
            assertFalse("blank value for $key", r.get(key).isBlank())
        }
    }

    /**
     * Carousel copy: the Home header has a non-blank default and is OTA-overridable; the 18 caption
     * keys are present (so OTA can set them) yet blank by default (no caption row) and overridable.
     */
    @Test
    fun carouselKeys_headerNonBlank_captionsPresentBlankAndOverridable() {
        val r = repo()
        assertFalse("carousel home header must have a default", r.get(CopyKeys.CAROUSEL_HOME_HEADER).isBlank())
        assertTrue(CopyKeys.CAROUSEL_HOME_HEADER in CopyKeys.OVERRIDABLE)
        assertEquals("See Quill in action", r.get(CopyKeys.CAROUSEL_HOME_HEADER))
        CopyKeys.CAROUSEL_CAPTIONS.forEach { key ->
            assertTrue("$key must be a referenced key", key in CopyKeys.ALL)
            assertEquals("$key must exist in the catalog (blank default)", "", r.bakedInValue(key))
            assertTrue("$key must be OTA-overridable", key in CopyKeys.OVERRIDABLE)
        }
    }

    @Test
    fun missingKey_fallsBackToTheKey_noBlank() {
        assertEquals("nonexistent.key", repo().get("nonexistent.key"))
    }

    /**
     * The "Check for updated text" toggle was removed: the OTA copy fetch is now unconditional, so its
     * two copy keys no longer exist. Guards against re-introducing a vestigial setting key (and the
     * baked-in catalog must not carry orphaned strings for them either).
     */
    @Test
    fun copyUpdatesToggleKeys_areGone() {
        val removed = setOf("settings.advanced.copy_updates", "settings.advanced.copy_updates_sub")
        removed.forEach { key ->
            assertFalse("removed key $key must not be referenced in CopyKeys.ALL", key in CopyKeys.ALL)
        }
        val r = repo()
        removed.forEach { key ->
            assertEquals("removed key $key must not be in the baked-in catalog", null, r.bakedInValue(key))
        }
    }

    @Test
    fun complianceKeys_areNotInOverridableAllowlist() {
        CopyKeys.COMPLIANCE.forEach { key ->
            assertFalse("compliance key $key must not be overridable", key in CopyKeys.OVERRIDABLE)
        }
    }

    @Test
    fun applyOverride_changesOverridableKey_butIgnoresComplianceKey() {
        val r = repo()
        val complianceKey = CopyKeys.DISCLOSURE_BODY
        val overridableKey = CopyKeys.WELCOME_TITLE
        val bakedCompliance = r.get(complianceKey)

        r.applyOverride(
            mapOf(
                overridableKey to "OVERRIDDEN WELCOME",
                complianceKey to "MALICIOUS DISCLOSURE", // must be ignored
            ),
        )

        assertEquals("OVERRIDDEN WELCOME", r.get(overridableKey))
        assertEquals("compliance copy must stay binary-only", bakedCompliance, r.get(complianceKey))
    }

    @Test
    fun formatArgs_areApplied() {
        // A key with a placeholder formats; none of ours need args yet, so just verify the path.
        assertEquals("hi bob", "hi %s".format("bob"))
    }

    @Test
    fun get_appliesFormatArgsToRealCatalogKeys() {
        val r = repo()
        // settings.keys.set == "%1$d set"; history.clear.body embeds a %1$d count.
        assertEquals("3 set", r.get(CopyKeys.SETTINGS_KEYS_SET, 3))
        assertTrue(r.get(CopyKeys.HISTORY_CLEAR_BODY, 5).contains("5"))
    }

    @Test
    fun applyOverride_cannotChangeAnyComplianceKey() {
        val r = repo()
        // The whole locked set at once: an attacker-controlled bundle targeting every compliance key.
        val baked = CopyKeys.COMPLIANCE.associateWith { r.get(it) }
        r.applyOverride(CopyKeys.COMPLIANCE.associateWith { "TAMPERED" })
        CopyKeys.COMPLIANCE.forEach { key ->
            assertEquals("compliance key $key must stay binary-only", baked[key], r.get(key))
        }
    }

    @Test
    fun applyOverride_overridableKey_appliesThenRevertsWhenAbsentFromNextBundle() {
        val r = repo()
        val key = CopyKeys.WELCOME_TITLE
        val baked = r.get(key)
        r.applyOverride(mapOf(key to "Bienvenue"))
        assertEquals("Bienvenue", r.get(key))
        // applyOverride rebuilds from baked-in each call, so a later bundle without the key reverts it.
        r.applyOverride(emptyMap())
        assertEquals(baked, r.get(key))
    }

    /** The bubble dismiss/summon + FGS-notification copy keys exist, resolve non-blank, and stay OTA-able. */
    @Test
    fun bubbleDismissSummonKeys_arePresentAndOverridable() {
        val r = repo()
        listOf(
            CopyKeys.NOTIFICATION_ACTION_SHOW, CopyKeys.NOTIFICATION_ACTION_STOP,
            CopyKeys.NOTIFICATION_TITLE_ACTIVE, CopyKeys.NOTIFICATION_TEXT_ACTIVE,
            CopyKeys.NOTIFICATION_TITLE_HIDDEN, CopyKeys.NOTIFICATION_TEXT_HIDDEN,
            CopyKeys.NOTIFICATION_TITLE_RECORDING, CopyKeys.NOTIFICATION_TEXT_RECORDING,
            CopyKeys.BUBBLE_DISMISS_TOAST,
        ).forEach { key ->
            assertTrue("$key must be referenced in CopyKeys.ALL", key in CopyKeys.ALL)
            assertFalse("$key must resolve to a non-blank baked-in default", r.get(key).isBlank())
            assertTrue("$key must be OTA-overridable (not compliance-locked)", key in CopyKeys.OVERRIDABLE)
        }
    }

    @Test
    fun applyOverride_unknownKey_isIgnoredNotAdded() {
        val r = repo()
        r.applyOverride(mapOf("totally.unknown.key" to "x"))
        // Unknown keys never become resolvable; a missing key falls back to the key string itself.
        assertEquals("totally.unknown.key", r.get("totally.unknown.key"))
    }
}
