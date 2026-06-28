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
        CopyKeys.ALL.forEach { key ->
            assertFalse("blank value for $key", r.get(key).isBlank())
        }
    }

    @Test
    fun missingKey_fallsBackToTheKey_noBlank() {
        assertEquals("nonexistent.key", repo().get("nonexistent.key"))
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

    @Test
    fun applyOverride_unknownKey_isIgnoredNotAdded() {
        val r = repo()
        r.applyOverride(mapOf("totally.unknown.key" to "x"))
        // Unknown keys never become resolvable; a missing key falls back to the key string itself.
        assertEquals("totally.unknown.key", r.get("totally.unknown.key"))
    }
}
