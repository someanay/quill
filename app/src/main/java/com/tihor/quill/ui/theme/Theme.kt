package com.tihor.quill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Ink & Signal scheme — design_system.md §2.3 / §14. NO dynamic colour (§2.3): Quill's identity is
// the fixed ink-indigo + gold; wallpaper-derived colour would erase it.
private val LightScheme = lightColorScheme(
    primary = InkIndigo, onPrimary = Color.White,
    primaryContainer = InkIndigoSoft, onPrimaryContainer = InkIndigoText,
    background = Paper, onBackground = Ink,
    surface = PaperSurface, onSurface = Ink,
    surfaceVariant = PaperSunken, onSurfaceVariant = Muted,
    outline = LineStrong, outlineVariant = Line,
    error = Err, onError = Color.White, errorContainer = ErrSoft,
    tertiary = Gold, tertiaryContainer = GoldSoft, onTertiaryContainer = GoldDeep,
)

private val DarkScheme = darkColorScheme(
    primary = InkIndigoDark, onPrimary = Color.White,
    primaryContainer = InkIndigoSoftDk, onPrimaryContainer = Lavender,
    background = InkBgDark, onBackground = TextDark,
    surface = InkSurfaceDark, onSurface = TextDark,
    surfaceVariant = InkSunkenDark, onSurfaceVariant = MutedDark,
    outline = LineStrongDark, outlineVariant = LineDark,
    error = ErrDark, onError = Color.White, errorContainer = ErrSoftDark,
    tertiary = GoldDark, tertiaryContainer = GoldSoftDark, onTertiaryContainer = GoldDeepDark,
)

private val LightExt = QuillColors(
    brandInk = InkIndigoText, brandSoft = InkIndigoSoft,
    gold = Gold, goldDeep = GoldDeep, goldSoft = GoldSoft,
    ok = Ok, okSoft = OkSoft,
    line = Line, lineStrong = LineStrong,
    muted = Muted, faint = Faint, text2 = Ink2,
)

private val DarkExt = QuillColors(
    brandInk = Lavender, brandSoft = InkIndigoSoftDk,
    gold = GoldDark, goldDeep = GoldDeepDark, goldSoft = GoldSoftDark,
    ok = OkDark, okSoft = OkSoftDark,
    line = LineDark, lineStrong = LineStrongDark,
    muted = MutedDark, faint = FaintDark, text2 = Text2Dark,
)

@Composable
fun QuillTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalQuillColors provides if (dark) DarkExt else LightExt) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = QuillType,
            shapes = QuillShapes,
            content = content,
        )
    }
}
