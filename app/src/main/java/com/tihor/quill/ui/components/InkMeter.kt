package com.tihor.quill.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.theme.LineStrong
import com.tihor.quill.ui.theme.MeterBrandGradient
import com.tihor.quill.ui.theme.MeterGoldGradient
import kotlin.math.abs

/**
 * SIGNATURE 2 — the ink-stroke level meter (design_system.md §10): voice drawn as gold nib-strokes.
 * Vertical rounded bars grow up & down from a shared centre baseline; heights track [amplitude]
 * (0..1), smoothed (~100 ms) so it reads as ink, not a jittery visualiser. Gold while [recording],
 * brand otherwise. Honours reduced motion by damping to a steady low state.
 */
@Composable
fun InkMeter(
    amplitude: Float,
    modifier: Modifier = Modifier,
    bars: Int = 13,
    large: Boolean = true,
    recording: Boolean = true,
) {
    val reduced = isReducedMotion()
    val target = if (reduced) 0.18f else amplitude.coerceIn(0f, 1f)
    val smoothed by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reduced) 0 else 110),
        label = "inkMeterAmplitude", // copy-exempt
    )

    val barWidth = (if (large) 5f else 4f).dp
    val gap = (if (large) 4f else 3f).dp
    val gradient = if (recording) MeterGoldGradient else MeterBrandGradient
    val brush = Brush.verticalGradient(gradient)

    Canvas(modifier = modifier) {
        val bw = barWidth.toPx()
        val g = gap.toPx()
        val totalW = bars * bw + (bars - 1) * g
        val startX = (size.width - totalW) / 2f
        val centerY = size.height / 2f
        val maxHalf = size.height / 2f
        val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

        for (i in 0 until bars) {
            // A stable bell-ish weight so the centre strokes are tallest — organic, not uniform.
            val t = if (bars <= 1) 0.5f else i.toFloat() / (bars - 1)
            val weight = 0.45f + 0.55f * (1f - abs(t - 0.5f) * 2f)
            val half = (maxHalf * 0.16f) + (maxHalf * 0.84f) * smoothed * weight
            val x = startX + i * (bw + g)
            val active = smoothed * weight > 0.06f
            if (active) {
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, centerY - half),
                    size = Size(bw, half * 2),
                    cornerRadius = radius,
                )
            } else {
                drawRoundRect(
                    color = LineStrong,
                    topLeft = Offset(x, centerY - bw / 2),
                    size = Size(bw, bw),
                    cornerRadius = radius,
                )
            }
        }
    }
}

@Composable
private fun isReducedMotion(): Boolean {
    val context = LocalContext.current
    return android.provider.Settings.Global.getFloat(
        context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f
}
