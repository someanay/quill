package com.tihor.quill.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tihor.quill.R

// Bundled website fonts; no downloadable-font dependency.
val PlexSans = FontFamily(Font(R.font.ibm_plex_sans))
val PlexSerif = FontFamily(Font(R.font.ibm_plex_serif))
val WordmarkFont = PlexSans
private fun sans(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = PlexSans, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)
private fun serif(size: Int, height: Int) =
    TextStyle(fontFamily = PlexSerif, fontSize = size.sp, lineHeight = height.sp,
        fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp)
val QuillType = Typography(
    displayLarge = serif(48, 56), displayMedium = serif(36, 44), displaySmall = serif(30, 38),
    headlineLarge = serif(32, 40), headlineMedium = serif(28, 36), headlineSmall = serif(24, 32),
    titleLarge = serif(24, 32), titleMedium = sans(17, 24, FontWeight.Medium),
    titleSmall = sans(15, 22, FontWeight.Medium),
    bodyLarge = sans(16, 25), bodyMedium = sans(14, 22), bodySmall = sans(12, 18),
    labelLarge = sans(15, 20, FontWeight.Medium), labelMedium = sans(14, 20), labelSmall = sans(12, 18),
)
val EyebrowStyle = sans(12, 18).copy(letterSpacing = 1.sp)
val SectionHeaderStyle = sans(13, 18, FontWeight.Medium).copy(letterSpacing = 0.3.sp)
