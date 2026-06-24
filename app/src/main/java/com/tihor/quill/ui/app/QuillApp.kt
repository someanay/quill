package com.tihor.quill.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tihor.quill.ui.screens.AboutScreen
import com.tihor.quill.ui.screens.AdvancedScreen
import com.tihor.quill.ui.screens.AiCleanupScreen
import com.tihor.quill.ui.screens.ApiKeysScreen
import com.tihor.quill.ui.screens.AudioRecordingScreen
import com.tihor.quill.ui.screens.BubbleAppearanceScreen
import com.tihor.quill.ui.screens.CustomWordsScreen
import com.tihor.quill.ui.screens.HistoryScreen
import com.tihor.quill.ui.screens.HomeScreen
import com.tihor.quill.ui.screens.PrivacyScreen
import com.tihor.quill.ui.screens.SettingsScreen
import com.tihor.quill.ui.screens.SpeechModelScreen

/** Single-activity root: hosts the back stack and dispatches to the current [Screen]. */
@Composable
fun QuillApp() {
    val nav = remember { QuillNav() }
    BackHandler(enabled = nav.canGoBack) { nav.back() }

    when (nav.current) {
        Screen.Home -> HomeScreen(nav)
        Screen.History -> HistoryScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
        Screen.SpeechModel -> SpeechModelScreen(nav)
        Screen.ApiKeys -> ApiKeysScreen(nav)
        Screen.AiCleanup -> AiCleanupScreen(nav)
        Screen.CustomWords -> CustomWordsScreen(nav)
        Screen.AudioRecording -> AudioRecordingScreen(nav)
        Screen.Privacy -> PrivacyScreen(nav)
        Screen.BubbleAppearance -> BubbleAppearanceScreen(nav)
        Screen.Advanced -> AdvancedScreen(nav)
        Screen.About -> AboutScreen(nav)
    }
}
