package com.tihor.quill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Fixed website palette, with a dark companion based on its editor panel.
private val LightScheme = lightColorScheme(
    primary = InkIndigo, onPrimary = Color.White,
    primaryContainer = InkIndigoSoft, onPrimaryContainer = InkIndigoText,
    secondary = Ink2, onSecondary = Color.White,
    secondaryContainer = PaperSunken, onSecondaryContainer = Ink,
    surfaceTint = InkIndigo, inverseSurface = InkSurfaceDark, inverseOnSurface = TextDark,
    surfaceContainerLowest = PaperSurface, surfaceContainerLow = Paper,
    surfaceContainer = PaperSunken, surfaceContainerHigh = PaperSunken, surfaceContainerHighest = Line,
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
    secondary = Text2Dark, onSecondary = InkBgDark,
    secondaryContainer = InkSunkenDark, onSecondaryContainer = TextDark,
    surfaceTint = Lavender, inverseSurface = Paper, inverseOnSurface = Ink,
    surfaceContainerLowest = InkBgDark, surfaceContainerLow = InkSurfaceDark,
    surfaceContainer = InkSunkenDark, surfaceContainerHigh = InkSunkenDark, surfaceContainerHighest = LineDark,
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
    val scheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalQuillColors provides if (dark) DarkExt else LightExt,
        // Default loose text/icons to onBackground so unspecified-colour Text never falls back to
        // black on the dark theme (Surfaces still override this to onSurface for their content).
        LocalContentColor provides scheme.onBackground,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = QuillType,
            shapes = QuillShapes,
            content = content,
        )
    }
}
