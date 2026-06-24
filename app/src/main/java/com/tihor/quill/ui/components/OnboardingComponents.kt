package com.tihor.quill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.theme.BrandGradient
import com.tihor.quill.ui.theme.GoldGradient
import com.tihor.quill.ui.theme.quill

enum class IllustrationVariant { BRAND, GOLD, OK }

/** Permission/empty/success illustration (design_system.md §8.16). */
@Composable
fun PermissionIllustration(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    variant: IllustrationVariant = IllustrationVariant.BRAND,
) {
    val q = MaterialTheme.quill
    val coreGradient = when (variant) {
        IllustrationVariant.BRAND -> BrandGradient
        IllustrationVariant.GOLD -> GoldGradient
        IllustrationVariant.OK -> listOf(q.ok, q.ok)
    }
    val glow = when (variant) {
        IllustrationVariant.GOLD -> q.goldSoft
        IllustrationVariant.OK -> q.okSoft
        else -> q.brandSoft
    }
    val ringColor = q.lineStrong

    Box(
        modifier = modifier
            .size(118.dp)
            .background(Brush.radialGradient(listOf(glow, Color.Transparent)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(108.dp)) {
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                ),
            )
        }
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(coreGradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/** Onboarding progress dots (design_system.md §8.15): active is a brand pill. */
@Composable
fun ProgressDots(total: Int, active: Int, modifier: Modifier = Modifier) {
    val q = MaterialTheme.quill
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            if (i == active) {
                Box(Modifier.size(width = 22.dp, height = 7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            } else {
                Box(Modifier.size(7.dp).clip(CircleShape).background(q.lineStrong))
            }
        }
    }
}
