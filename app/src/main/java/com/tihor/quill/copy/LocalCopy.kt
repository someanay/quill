package com.tihor.quill.copy

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.tihor.quill.settings.QuillSettings
import com.tihor.quill.settings.SettingsRepository
import com.tihor.quill.ui.theme.QuillTheme

/** Process-wide [CopyRepository] (parses the baked-in catalog once). */
object Copy {
    @Volatile
    private var instance: CopyRepository? = null

    fun repository(context: Context): CopyRepository =
        instance ?: synchronized(this) {
            instance ?: CopyRepository(context.applicationContext).also {
                // Layer 2 (spec §2.3): re-verify + apply the cached OTA bundle once, before first read,
                // so every entry point (activities, overlay, notifications) sees the same resolved copy.
                it.applyCachedOverride(context.applicationContext)
                instance = it
            }
        }
}

val LocalCopy = staticCompositionLocalOf<CopyRepository> { error("CopyRepository not provided") }

/** Read UI copy by key in a composable (spec §2.3). No string literals in composables. */
@Composable
@ReadOnlyComposable
fun copy(key: String): String = LocalCopy.current.get(key)

@Composable
@ReadOnlyComposable
fun copy(key: String, vararg args: Any?): String = LocalCopy.current.get(key, *args)

/** App root every Activity wraps its content in: provides the Copy Catalog + the Ink&Signal theme. */
@Composable
fun QuillRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val settings by settingsRepository.settings.collectAsState(initial = QuillSettings())
    // The "Theme" choice in Visual preferences drives the whole UI; "Match system" defers to the OS.
    val dark = when (settings.appTheme) {
        QuillSettings.APP_THEME_LIGHT -> false
        QuillSettings.APP_THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalCopy provides Copy.repository(context)) {
        QuillTheme(dark = dark) { content() }
    }
}
