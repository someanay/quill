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
 * Scope doc §8.2 / §10: every new writing-style string is a real Copy Catalog key — present in
 * [CopyKeys.ALL] and `strings.default.json`, non-blank, and **overridable** (this is product copy, not
 * compliance copy).
 *
 * The mirror-image assertion matters just as much: none of these may land in [CopyKeys.COMPLIANCE].
 * The locked set is exactly the accessibility disclosure, permission rationales, privacy promises, and
 * the BYOK warning; quietly growing it here would dilute the thing that makes the lock meaningful.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WritingStyleCopyTest {

    private val styleKeys = listOf(
        CopyKeys.SETTINGS_ROW_WRITING_STYLE,
        CopyKeys.SETTINGS_STYLE_TITLE,
        CopyKeys.SETTINGS_STYLE_INTRO,
        CopyKeys.SETTINGS_STYLE_DEFAULT_SECTION,
        CopyKeys.SETTINGS_STYLE_PER_APP_SECTION,
        CopyKeys.SETTINGS_STYLE_FORMAL,
        CopyKeys.SETTINGS_STYLE_FORMAL_SUB,
        CopyKeys.SETTINGS_STYLE_NEUTRAL,
        CopyKeys.SETTINGS_STYLE_NEUTRAL_SUB,
        CopyKeys.SETTINGS_STYLE_CASUAL,
        CopyKeys.SETTINGS_STYLE_CASUAL_SUB,
        CopyKeys.SETTINGS_STYLE_SEARCH_HINT,
        CopyKeys.SETTINGS_STYLE_ASSIGN_TITLE,
        CopyKeys.SETTINGS_STYLE_USE_DEFAULT,
        CopyKeys.SETTINGS_STYLE_LOADING,
        CopyKeys.SETTINGS_STYLE_NO_RESULTS,
        CopyKeys.SETTINGS_STYLE_NEEDS_CLEANUP,
        CopyKeys.SETTINGS_STYLE_NEEDS_GROQ,
        CopyKeys.SETTINGS_STYLE_WORDS_PROMISE,
        CopyKeys.SETTINGS_STYLE_HELP,
    )

    @Test
    fun everyStyleKey_isReferenced_nonBlank_andOverridable() {
        val r = CopyRepository(RuntimeEnvironment.getApplication())
        styleKeys.forEach { key ->
            assertTrue("$key must be in CopyKeys.ALL", key in CopyKeys.ALL)
            assertFalse("$key must have a non-blank baked-in default", r.get(key).isBlank())
            assertTrue("$key must be OTA-overridable", key in CopyKeys.OVERRIDABLE)
            assertFalse("$key must NOT be compliance-locked", key in CopyKeys.COMPLIANCE)
            assertFalse("$key is not an intentionally-blank key", key in CopyKeys.OPTIONAL_BLANK)
        }
    }

    @Test
    fun styleKeys_areNotAddedToTheComplianceSet() {
        // The locked set must stay exactly what CLAUDE.md pins it to. If this fails, something was
        // added to COMPLIANCE — check it belongs there before touching this number.
        assertEquals("the compliance set must not grow for a product feature", 11, CopyKeys.COMPLIANCE.size)
        assertTrue(CopyKeys.COMPLIANCE.all { it.startsWith("compliance.") })
    }

    @Test
    fun availabilityNotices_nameWhatIsMissing() {
        // §7: the notice must be actionable — "turn cleanup on" / "you need a Groq key" — not a vague
        // "unavailable". These are the strings that stop the feature reading as broken.
        val r = CopyRepository(RuntimeEnvironment.getApplication())
        assertTrue(r.get(CopyKeys.SETTINGS_STYLE_NEEDS_CLEANUP).contains("Clean up with AI"))
        assertTrue(r.get(CopyKeys.SETTINGS_STYLE_NEEDS_GROQ).contains("Groq"))
    }

    @Test
    fun theWordsPromise_statesTheCoreConstraint() {
        // §4 is the product promise ("what you say is what gets typed"), so the screen says it.
        val promise = CopyRepository(RuntimeEnvironment.getApplication())
            .get(CopyKeys.SETTINGS_STYLE_WORDS_PROMISE).lowercase()
        assertTrue(promise.contains("never changes your words"))
    }

    @Test
    fun styleKeys_areUnique_andNamespaced() {
        assertEquals("duplicate style copy key", styleKeys.size, styleKeys.toSet().size)
        styleKeys.filter { it != CopyKeys.SETTINGS_ROW_WRITING_STYLE }.forEach {
            assertTrue("$it should live under settings.style.", it.startsWith("settings.style."))
        }
    }
}
