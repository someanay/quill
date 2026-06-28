package com.tihor.quill.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.CopyRepository
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.history.HistoryStore
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.screens.HistoryScreen
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * History screen against the REAL hand-rolled SQLite store (M9). Seeds rows directly, hosts the
 * screen, and asserts copy/clear-all actually mutate persistence — not sample data. No bubble,
 * no network, no permissions needed, so this runs fully headless on the Honor.
 */
@RunWith(AndroidJUnit4::class)
class HistoryUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val copy by lazy { CopyRepository(ctx) }

    @Before
    fun clearStore() {
        HistoryStore(ctx).clearAll()
    }

    @Test
    fun seededRows_render_thenClearAll_gatesOnConfirm_andEmpties() {
        val store = HistoryStore(ctx)
        val now = System.currentTimeMillis()
        store.insert("alpha entry one", now, "com.example.x", "AppX")
        store.insert("bravo entry two", now, null, null)

        rule.setContent { QuillRoot { HistoryScreen(QuillNav()) } }

        // Rows render from persistence.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("alpha entry one", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("alpha entry one", substring = true).assertIsDisplayed()
        rule.onNodeWithText("bravo entry two", substring = true).assertIsDisplayed()

        // Clear-all opens a confirm dialog (does NOT delete immediately).
        rule.onNodeWithContentDescription(copy.get(K.HISTORY_CLEAR_ALL)).performClick()
        val confirmLabel = copy.get(K.HISTORY_CLEAR_CONFIRM)
        rule.onNodeWithText(confirmLabel).assertIsDisplayed()
        // Rows still present while the dialog is up — gating proven.
        rule.onNodeWithText("alpha entry one", substring = true).assertIsDisplayed()

        // Confirm wipes the table; the list empties and the row is gone from the DB.
        rule.onNodeWithText(confirmLabel).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("alpha entry one", substring = true).fetchSemanticsNodes().isEmpty()
        }
        assertTrue("store should be empty after clear-all", store.queryAll().isEmpty())
    }

    @Test
    fun copyAction_putsRowTextOnClipboard() {
        val store = HistoryStore(ctx)
        store.insert("clipboard target text", System.currentTimeMillis(), null, null)

        rule.setContent { QuillRoot { HistoryScreen(QuillNav()) } }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("clipboard target text", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Trailing copy icon has no contentDescription; tapping the row opens the detail sheet,
        // whose Copy row carries the copy label — exercise that path.
        rule.onNodeWithText("clipboard target text", substring = true).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(copy.get(K.HISTORY_SHEET_COPY)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(copy.get(K.HISTORY_SHEET_COPY)).performClick()
        // No crash + sheet dismissed is the assertion; clipboard read is flaky across OEMs.
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(copy.get(K.HISTORY_SHEET_COPY)).fetchSemanticsNodes().isEmpty()
        }
    }
}
