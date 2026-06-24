package com.tihor.quill.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Quill's own glyphs. The brand mark is a clean, minimalist single-stroke feather. */
object QuillIcons {
    /**
     * A minimalist feather (stroke-only, round joins) — the Quill logo mark. Drawn black; callers
     * tint it via [androidx.compose.material3.Icon] (white on the brand gradient, brand-ink on tints).
     */
    val Feather: ImageVector by lazy {
        ImageVector.Builder(
            name = "QuillFeather",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Blade
                moveTo(20.24f, 12.24f)
                arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, -8.49f, -8.49f)
                lineTo(5f, 10.5f)
                verticalLineTo(19f)
                horizontalLineToRelative(8.5f)
                close()
                // Quill (the shaft)
                moveTo(16f, 8f)
                lineTo(2f, 22f)
                // Barb
                moveTo(17.5f, 15f)
                lineTo(9f, 15f)
            }
        }.build()
    }
}
