package com.tihor.quill.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.theme.SectionHeaderStyle
import com.tihor.quill.ui.theme.quill

/** ALL-CAPS muted group header (design_system.md §8.8). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SectionHeaderStyle,
        color = MaterialTheme.quill.muted,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

enum class PillKind { LIVE, OK, INFO, ERROR }

/** Status pill (design_system.md §8.10): Live (gold + dot), OK (green), etc. */
@Composable
fun StatusPill(text: String, kind: PillKind, modifier: Modifier = Modifier) {
    val q = MaterialTheme.quill
    val (fill, content) = when (kind) {
        PillKind.LIVE -> q.goldSoft to q.goldDeep
        PillKind.OK -> q.okSoft to q.ok
        PillKind.INFO -> q.brandSoft to q.brandInk
        PillKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (kind == PillKind.LIVE) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(q.gold))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1, softWrap = false)
    }
}

/** Tinted callout (design_system.md §8.9). [kind] sets the soft fill + content colour. */
@Composable
fun Banner(
    text: String,
    icon: ImageVector,
    kind: PillKind,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val q = MaterialTheme.quill
    val (fill, content) = when (kind) {
        PillKind.LIVE -> q.goldSoft to q.goldDeep
        PillKind.OK -> q.okSoft to q.ok
        PillKind.INFO -> q.brandSoft to q.brandInk
        PillKind.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        androidx.compose.foundation.layout.Column {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = content)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}

/**
 * "Quill" wordmark (design_system.md §8.1 / §14): a brand-gradient rounded-square mark with a nib
 * glyph + the serif logotype. The only place the serif is used. (Nib drawn simply for v1; swap in the
 * board SVG later.)
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(com.tihor.quill.ui.theme.BrandGradient),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                QuillIcons.Feather,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Quill",
            fontFamily = com.tihor.quill.ui.theme.WordmarkFont,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
