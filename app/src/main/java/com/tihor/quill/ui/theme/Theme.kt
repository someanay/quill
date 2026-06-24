package com.tihor.quill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Minimal Material 3 baseline for M0. The contrast-safe bubble palette and the design-token
// driven scheme land at the UI milestones (M8–M10); see README "design tokens" note.
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun QuillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
