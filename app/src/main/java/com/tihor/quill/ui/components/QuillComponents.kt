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
        text = text,
        style = SectionHeaderStyle,
        color = MaterialTheme.quill.muted,
        modifier = modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
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

/** The website wordmark, including its blue full stop. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Text(
        text = androidx.compose.ui.text.buildAnnotatedString {
            append("quill") // copy-exempt: brand wordmark
            pushStyle(androidx.compose.ui.text.SpanStyle(color = if (androidx.compose.foundation.isSystemInDarkTheme()) com.tihor.quill.ui.theme.Lavender else com.tihor.quill.ui.theme.InkIndigo))
            append(".")
            pop()
        },
        modifier = modifier,
        fontFamily = com.tihor.quill.ui.theme.WordmarkFont,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(-2f, androidx.compose.ui.unit.TextUnitType.Sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}