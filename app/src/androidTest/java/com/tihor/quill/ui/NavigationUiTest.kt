package com.tihor.quill.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.CopyRepository
import com.tihor.quill.copy.QuillRoot
import com.tihor.quill.history.HistoryStore
import com.tihor.quill.ui.app.QuillApp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bottom-nav tab switching through the real single-activity back stack ([QuillApp]). Anchors on
 * screen-unique copy (a Settings row, a History empty title) rather than the nav labels, which
 * collide with the screen titles. Headless-safe: no permissions, no bubble.
 */
@RunWith(AndroidJUnit4::class)
class NavigationUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val copy by lazy { CopyRepository(ctx) }

    @Before
    fun clearStore() {
        HistoryStore(ctx).clearAll()
    }

    @Test
    fun bottomNav_switchesHomeSettingsHistory() {
        rule.setContent { QuillRoot { QuillApp() } }

        // Home is the default destination — the Settings detail row is NOT present yet.
        val speechRow = copy.get(K.SETTINGS_ROW_SPEECH_MODEL)
        assert(rule.onAllNodesWithText(speechRow).fetchSemanticsNodes().isEmpty())

        // Home -> Settings
        rule.onNodeWithText("Settings").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(speechRow).fetchSemanticsNodes().isNotEmpty()
        }

        // Settings -> History. With the store cleared the History tab shows an empty state; which
        // title (history-off vs history-on) depends on the persisted toggle, so accept either.
        rule.onNodeWithText("History").performClick()
        val emptyOff = copy.get(K.HISTORY_EMPTY_OFF_TITLE)
        val emptyOn = copy.get(K.HISTORY_EMPTY_ON_TITLE)
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(emptyOff).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText(emptyOn).fetchSemanticsNodes().isNotEmpty()
        }

        // History -> Home (the Settings row is gone again)
        rule.onNodeWithText("Home").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(speechRow).fetchSemanticsNodes().isEmpty()
        }
    }
}
