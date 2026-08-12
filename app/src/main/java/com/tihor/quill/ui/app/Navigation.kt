package com.tihor.quill.ui.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.Color
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.ui.theme.quill

/**
 * In-app destinations. A hand-rolled back stack (no navigation-compose) keeps the single-activity
 * bottom-nav shell free of an extra dependency on this future-dated Compose stack; it is swappable.
 */
sealed interface Screen {
    /** Top-level tabs show the bottom nav and reset the back stack when selected. */
    val topLevel: Boolean get() = false

    // Tabs
    data object Home : Screen { override val topLevel = true }
    data object History : Screen { override val topLevel = true }
    data object Settings : Screen { override val topLevel = true }

    // Settings detail
    data object SpeechModel : Screen
    data object ApiKeys : Screen
    data object AiCleanup : Screen
    data object WritingStyle : Screen
    data object CustomWords : Screen
    data object ContactsVocab : Screen
    data object AudioRecording : Screen
    data object Privacy : Screen
    data object BubbleAppearance : Screen
    data object Advanced : Screen
    data object About : Screen

    // System states
    data object Recovery : Screen
    data object CrashReport : Screen
    data object OemSurvival : Screen
}

/** Minimal observable back stack: tabs reset to a single root; details push/pop. */
class QuillNav {
    private val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun navigate(screen: Screen) { stack.add(screen) }
    fun switchTab(tab: Screen) { stack.clear(); stack.add(tab) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

/** Bottom nav bar (`.navbar`): pill-behind-icon active state, brand-ink selected, faint idle. */
@Composable
fun QuillBottomNav(current: Screen, onSelect: (Screen) -> Unit) {
    val q = MaterialTheme.quill
    val items = listOf(
        Triple(Screen.Home, Icons.Rounded.Home, copy(K.NAV_HOME)),
        Triple(Screen.History, Icons.Rounded.Schedule, copy(K.NAV_HISTORY)),
        Triple(Screen.Settings, Icons.Rounded.Settings, copy(K.NAV_SETTINGS)),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = q.brandInk,
                    selectedTextColor = q.brandInk,
                    indicatorColor = q.brandSoft,
                    unselectedIconColor = q.faint,
                    unselectedTextColor = q.faint,
                ),
            )
        }
    }
}
