package com.tihor.quill.ui.theme

import androidx.compose.ui.graphics.Color

// Ink & Signal palette (design_system.md §2 / §14). Hex is exact — copied verbatim.

// Brand (ink-indigo)
val InkIndigo = Color(0xFF4A3FD0)
val InkIndigoText = Color(0xFF3A2EA3) // brand-ink (on light)
val InkIndigoSoft = Color(0xFFECEAFB)
val InkIndigoDark = Color(0xFF5A4FE0) // brand fill (dark)
val Lavender = Color(0xFFA79BFF) // brand-ink (dark)
val InkIndigoSoftDk = Color(0xFF26233F)

// Gold (signal — live/listening only)
val Gold = Color(0xFFE0A03A)
val GoldDeep = Color(0xFF8A5A12)
val GoldSoft = Color(0xFFFBEFD6)
val GoldDark = Color(0xFFE8AE4E)
val GoldDeepDark = Color(0xFFF0C277)
val GoldSoftDark = Color(0xFF332919)

// Ink / surface (light)
val Ink = Color(0xFF1C1B33)
val Ink2 = Color(0xFF4A4A5C)
val Muted = Color(0xFF6E6E81)
val Faint = Color(0xFF9A9AAC)
val Paper = Color(0xFFEFF1F6)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSunken = Color(0xFFF6F7FB)
val Line = Color(0xFFE3E5EE)
val LineStrong = Color(0xFFD3D6E2)

// Ink / surface (dark)
val InkBgDark = Color(0xFF0F1117)
val InkSurfaceDark = Color(0xFF191B23)
val InkSunkenDark = Color(0xFF20232D)
val LineDark = Color(0xFF2A2D38)
val LineStrongDark = Color(0xFF363A48)
val TextDark = Color(0xFFECEDF3)
val Text2Dark = Color(0xFFC3C5D2)
val MutedDark = Color(0xFF9A9DAC)
val FaintDark = Color(0xFF71748A)

// Semantic
val Ok = Color(0xFF2E9E6B)
val OkSoft = Color(0xFFE1F3EA)
val Err = Color(0xFFD6453D)
val ErrSoft = Color(0xFFFBE7E5)
val OkDark = Color(0xFF43B585)
val OkSoftDark = Color(0xFF16291F)
val ErrDark = Color(0xFFE8635B)
val ErrSoftDark = Color(0xFF2E1817)

// Gradient stops (design_system.md §2.4) — for the bubble, meter, wordmark.
val BrandGradient = listOf(Color(0xFF4A3FD0), Color(0xFF6A5BEA))
val GoldGradient = listOf(Color(0xFFE0A03A), Color(0xFFEFC069))
val BusyGradient = listOf(Color(0xFF5B5BD6), Color(0xFF7E78E8))
val ErrorGradient = listOf(Color(0xFFD6453D), Color(0xFFE8635B))
val MeterGoldGradient = listOf(Color(0xFFE0A03A), Color(0xFFC9871F))
val MeterBrandGradient = listOf(Color(0xFF4A3FD0), Color(0xFF3A2EA3))
