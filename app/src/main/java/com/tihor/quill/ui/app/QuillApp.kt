package com.tihor.quill.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.components.QuillTopBar
import com.tihor.quill.ui.components.QuillTopBarTitle
import com.tihor.quill.ui.screens.AboutScreen
import com.tihor.quill.ui.screens.AdvancedScreen
import com.tihor.quill.ui.screens.AiCleanupScreen
import com.tihor.quill.ui.screens.ApiKeysScreen
import com.tihor.quill.ui.screens.AudioRecordingScreen
import com.tihor.quill.ui.screens.BubbleAppearanceScreen
import com.tihor.quill.ui.screens.CustomWordsScreen
import com.tihor.quill.ui.screens.HomeScreen
import com.tihor.quill.ui.screens.PrivacyScreen
import com.tihor.quill.ui.screens.SettingsScreen
import com.tihor.quill.ui.screens.SpeechModelScreen
import com.tihor.quill.ui.theme.quill

/** Single-activity root: hosts the back stack and dispatches to the current [Screen]. */
@Composable
fun QuillApp() {
    val nav = remember { QuillNav() }
    BackHandler(enabled = nav.canGoBack) { nav.back() }

    when (nav.current) {
        Screen.Home -> HomeScreen(nav)
        Screen.History -> Placeholder(nav, "History")
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

/** Temporary destination until its section lands. */
@Composable
private fun Placeholder(nav: QuillNav, title: String) {
    Scaffold(
        topBar = {
            if (nav.current.topLevel) {
                QuillTopBar(title = { QuillTopBarTitle(title) })
            } else {
                QuillTopBar(title = { QuillTopBarTitle(title) }, onBack = nav::back)
            }
        },
        bottomBar = { if (nav.current.topLevel) QuillBottomNav(nav.current, nav::switchTab) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Coming in this pass",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.quill.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
