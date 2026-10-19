package com.tihor.quill.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tihor.quill.ui.screens.AboutScreen
import com.tihor.quill.ui.screens.AdvancedScreen
import com.tihor.quill.ui.screens.AiCleanupScreen
import com.tihor.quill.ui.screens.ApiKeysScreen
import com.tihor.quill.ui.screens.BubbleAppearanceScreen
import com.tihor.quill.ui.screens.ContactsVocabScreen
import com.tihor.quill.ui.screens.CustomWordsScreen
import com.tihor.quill.ui.screens.HistoryScreen
import com.tihor.quill.ui.screens.HomeScreen
import com.tihor.quill.ui.screens.CrashReportScreen
import com.tihor.quill.ui.screens.OemSurvivalScreen
import com.tihor.quill.ui.screens.PermissionRevokedScreen
import com.tihor.quill.ui.screens.PrivacyScreen
import com.tihor.quill.ui.screens.SettingsScreen
import com.tihor.quill.ui.screens.SpeechModelScreen
import com.tihor.quill.ui.screens.WritingStyleScreen

/** Single-activity root: hosts the back stack and dispatches to the current [Screen]. */
@Composable
fun QuillApp(initialScreen: Screen = Screen.Home) {
    val nav = remember { QuillNav().apply {
        if (initialScreen != Screen.Home) { switchTab(Screen.Settings); navigate(initialScreen) }
    } }
    BackHandler(enabled = nav.canGoBack) { nav.back() }

    val savedState = rememberSaveableStateHolder()
    AnimatedContent(targetState = nav.current, modifier = Modifier.fillMaxSize(),
        transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 16 }) togetherWith fadeOut(tween(110)) },
        label = "screenTransition", // copy-exempt
    ) { destination ->
      savedState.SaveableStateProvider(destination.toString()) {
       when (destination) {
        Screen.Home -> HomeScreen(nav)
        Screen.History -> HistoryScreen(nav)
        Screen.Settings -> SettingsScreen(nav)
        Screen.SpeechModel -> SpeechModelScreen(nav)
        Screen.ApiKeys -> ApiKeysScreen(nav)
        Screen.AiCleanup -> AiCleanupScreen(nav)
        Screen.WritingStyle -> WritingStyleScreen(nav)
        Screen.CustomWords -> CustomWordsScreen(nav)
        Screen.ContactsVocab -> ContactsVocabScreen(nav)
        Screen.AudioRecording -> BubbleAppearanceScreen(nav)
        Screen.Privacy -> PrivacyScreen(nav)
        Screen.BubbleAppearance -> BubbleAppearanceScreen(nav)
        Screen.Advanced -> AdvancedScreen(nav)
        Screen.About -> AboutScreen(nav)
        Screen.Recovery -> PermissionRevokedScreen(nav)
        Screen.CrashReport -> CrashReportScreen(nav)
        Screen.OemSurvival -> OemSurvivalScreen(nav)
       }
      }
    }
}
