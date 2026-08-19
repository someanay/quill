package com.tihor.quill.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The editorial serif — the "Fraunces voice" shared with the marketing site and the Windows app.
 * Android maps it to the system serif (Noto Serif), the same stand-in strategy the Windows app uses
 * with Palatino Linotype (neither app bundles Fraunces). Declared before [QuillType] because the scale
 * references it — a top-level val used before its own declaration would initialise to null.
 */
val WordmarkFont: FontFamily = FontFamily.Serif

/**
 * Type scale from design_system.md §3.2. UI sans = system default. The editorial serif ([WordmarkFont])
 * carries the Fraunces voice on the DISPLAY / HEADLINE / TITLE-LARGE headings — page & step titles, the
 * app-bar screen title, and the wordmark — matching the web and the Windows redesign (commit ab8b90a,
 * "section titles the editorial serif to match the web's Fraunces voice"). titleMedium and below (body,
 * labels) plus the ALL-CAPS eyebrows stay sans, so the serif reads as a deliberate accent, not a default.
 */
val QuillType = Typography(
    displaySmall = TextStyle(fontFamily = WordmarkFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = WordmarkFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = WordmarkFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
)

/** Section/overline eyebrow: 12sp/700 ALL-CAPS, tracked (design_system.md §3.2). Apply uppercase at call site. */
val EyebrowStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
val SectionHeaderStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
