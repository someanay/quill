package com.tihor.quill.accessibility

import com.tihor.quill.R
import com.tihor.quill.copy.CopyKeys
import com.tihor.quill.copy.CopyRepository
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityDisclosureTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val sourceRoot get() = File("src/main/java/com/tihor/quill")

    @Test
    fun disclosureResources_areAbsentFromCatalog_andCannotBeOverridden() {
        val repo = CopyRepository(context)
        val ids = listOf(R.string.a11y_disclosure_title, R.string.a11y_disclosure_body,
            R.string.a11y_disclosure_accept, R.string.a11y_disclosure_decline,
            R.string.accessibility_service_description)
        val before = ids.map(context::getString)
        val oldKeys = listOf("title", "body", "accept", "decline")
            .map { "compliance.accessibility.disclosure_$it" }
        val resourceKeys = ids.map { context.resources.getResourceEntryName(it) }
        (oldKeys + resourceKeys).forEach {
            assertFalse(it in CopyKeys.ALL)
            assertNull(repo.bakedInValue(it))
        }
        assertEquals(before, ids.map(context::getString))
        assertFalse(repo.resolved.value.keys.any { it in oldKeys || it in resourceKeys })
    }

    @Test
    fun entryIntent_alwaysTargetsDisclosure_notSystemSettings() {
        repeat(2) {
            val intent = AccessibilityUtil.disclosureIntent(context)
            assertEquals(AccessibilityDisclosureActivity::class.java.name, intent.component?.className)
            assertNull(intent.action)
        }
    }

    @Test
    fun onlyDisclosureHost_canConstructAccessibilitySettingsIntent_andUsesResources() {
        assertTrue(sourceRoot.isDirectory)
        val callers = sourceRoot.walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("ACTION_ACCESSIBILITY_SETTINGS") }.toList()
        assertEquals(listOf("AccessibilityDisclosureActivity.kt"), callers.map { it.name })
        val screen = callers.single().readText().replace("\r\n", "\n")
        assertTrue(screen.contains("onDecline = { finish() }"))
        assertFalse(screen.contains("com.tihor.quill.copy"))
        assertFalse(screen.contains("copy("))
        // Post-consent video preparation has a timeout; consent gating is exercised by
        // AccessibilityGuideFlowTest rather than banning timers from the whole host.
    }

    @Test
    fun disclosureSummaryMatchesManifestDescription_andActionsAreExplicit() {
        assertEquals(context.getString(R.string.accessibility_service_description),
            context.getString(R.string.a11y_disclosure_title))
        assertEquals("Agree", context.getString(R.string.a11y_disclosure_accept))
        assertEquals("Not now", context.getString(R.string.a11y_disclosure_decline))
        assertTrue(context.getString(R.string.a11y_disclosure_body)
            .contains("Do not turn on the Accessibility Shortcut"))
    }
}
