package com.tihor.quill.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extension colours M3 has no slot for (design_system.md §14): the gold family, brand-as-text,
 * the `-soft` tints, and the hairline/line tokens. Access via `MaterialTheme.quill`.
 */
@Immutable
data class QuillColors(
    val brandInk: Color, // brand as text/icon
    val brandSoft: Color,
    val gold: Color,
    val goldDeep: Color,
    val goldSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val line: Color,
    val lineStrong: Color,
    val muted: Color,
    val faint: Color,
    val text2: Color,
)

val LocalQuillColors = staticCompositionLocalOf<QuillColors> { error("QuillColors not provided") }

val MaterialTheme.quill: QuillColors
    @Composable get() = LocalQuillColors.current
