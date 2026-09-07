package com.tihor.quill.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared Android typography. Serif headings contrast with the system sans-serif controls. */
val WordmarkFont: FontFamily = FontFamily.Serif

/** Shared Android typography. Serif headings contrast with the system sans-serif controls. */
val QuillType = Typography(
    displaySmall = TextStyle(fontFamily = WordmarkFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = WordmarkFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = WordmarkFont, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
)

/** Shared Android typography. Serif headings contrast with the system sans-serif controls. */
val EyebrowStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
val SectionHeaderStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
