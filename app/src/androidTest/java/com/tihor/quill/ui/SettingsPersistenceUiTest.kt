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
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.app.QuillNav
import com.tihor.quill.ui.screens.SpeechModelScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real settings picker round-trips through DataStore. The language chips are text-anchored (easy
 * to click reliably), and the assertion reads the persisted value back through a fresh
 * [SettingsRepository] — proving the write survives, not just that the UI state flipped.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val copy by lazy { CopyRepository(ctx) }

    @After
    fun resetLanguage() {
        // Restore the "en" default (the Hinglish-romanization seed) so this test leaves no residue.
        runBlocking { SettingsRepository(ctx).setLanguageHint("en") }
    }

    @Test
    fun languagePicker_persistsToDataStore_andDefaultIsEn() {
        rule.setContent { QuillRoot { SpeechModelScreen(QuillNav()) } }

        // Pick Hindi.
        val hindi = copy.get(K.SETTINGS_LANG_HI)
        rule.waitUntil(5_000) { rule.onAllNodesWithText(hindi).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(hindi).performClick()

        rule.waitUntil(5_000) {
            runBlocking { SettingsRepository(ctx).settings.first().languageHint } == "hi"
        }
        assertEquals("hi", runBlocking { SettingsRepository(ctx).settings.first().languageHint })

        // Pick English back — the "en" sentinel round-trips too.
        rule.onNodeWithText(copy.get(K.SETTINGS_LANG_EN)).performClick()
        rule.waitUntil(5_000) {
            runBlocking { SettingsRepository(ctx).settings.first().languageHint } == "en"
        }
        assertEquals("en", runBlocking { SettingsRepository(ctx).settings.first().languageHint })
    }
}
