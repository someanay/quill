package com.tihor.quill.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tihor.quill.ui.theme.BrandGradient
import com.tihor.quill.ui.theme.GoldGradient
import com.tihor.quill.ui.theme.quill

// ============================================================================
// Buttons — the pill `.btn` family (design board §buttons; radius 999, height 48 ≥ touch floor).
// ============================================================================

/** Primary CTA — solid brand fill (`.btn.primary`). */
@Composable
fun QuillPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier.heightIn(min = 48.dp),
    ) { ButtonContent(text, leadingIcon) }
}

/** Tonal CTA — soft brand tint (`.btn.tonal`). */
@Composable
fun QuillTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val q = MaterialTheme.quill
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = q.brandSoft, contentColor = q.brandInk),
        modifier = modifier.heightIn(min = 48.dp),
    ) { ButtonContent(text, leadingIcon) }
}

/** Ghost CTA — outlined, brand-ink text (`.btn.ghost`). */
@Composable
fun QuillGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val q = MaterialTheme.quill
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(1.dp, q.lineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = q.brandInk),
        modifier = modifier.heightIn(min = 48.dp),
    ) { ButtonContent(text, leadingIcon) }
}

/** Destructive CTA — solid error fill (`.btn.danger`). */
@Composable
fun QuillDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) { ButtonContent(text, null) }
}

@Composable
private fun ButtonContent(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
}

// ============================================================================
// Lead icon — the `.lead-ic` rounded-square glyph chip (34dp, 10dp radius).
// ============================================================================

enum class LeadIconVariant { BRAND, GOLD, PLAIN, OK, ERROR }

@Composable
fun LeadIcon(
    icon: ImageVector,
    variant: LeadIconVariant = LeadIconVariant.BRAND,
    modifier: Modifier = Modifier,
) {
    val q = MaterialTheme.quill
    val (bg, fg) = when (variant) {
        LeadIconVariant.BRAND -> q.brandSoft to q.brandInk
        LeadIconVariant.GOLD -> q.goldSoft to q.goldDeep
        LeadIconVariant.PLAIN -> MaterialTheme.colorScheme.surfaceVariant to q.text2
        LeadIconVariant.OK -> q.okSoft to q.ok
        LeadIconVariant.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
    }
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
    }
}

// ============================================================================
// Card + list container — `.card` and `.list` (surface, hairline border, 16dp corners).
// ============================================================================

/**
 * A surface card with the hairline border + soft elevation of `.card`. A real [Surface] so it sets
 * `contentColor` = onSurface — text inside adapts to the theme instead of defaulting to black.
 */
@Composable
fun QuillCard(
    modifier: Modifier = Modifier,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val q = MaterialTheme.quill
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (border != null) 1.5.dp else 1.dp, border ?: q.line),
    ) { Column(content = content) }
}

/** A bordered, rounded container for a stack of [QuillRow]s — the `.list` unit. Rows self-divide. */
@Composable
fun QuillListContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val q = MaterialTheme.quill
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, q.line),
    ) { Column(content = content) }
}

/** Hairline between rows inside a [QuillListContainer] (`.row` border-top). */
@Composable
fun RowDivider() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.quill.line)
}

// ============================================================================
// Row — the repeatable `.row` (lead icon · title/subtitle · trailing slot).
// ============================================================================

@Composable
fun QuillRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadIcon: ImageVector? = null,
    leadVariant: LeadIconVariant = LeadIconVariant.BRAND,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (leadIcon != null) LeadIcon(leadIcon, leadVariant)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.quill.muted,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/** Muted value text + chevron — the most common `.row .val` trailing combo. */
@Composable
fun RowValueChevron(value: String? = null) {
    if (value != null) {
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.quill.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
    }
    RowChevron()
}

@Composable
fun RowChevron() {
    Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.quill.faint,
        modifier = Modifier.size(20.dp),
    )
}

// ============================================================================
// Chip — the pill selector `.chip` / `.chip.on` (provider, size, theme pickers).
// ============================================================================

@Composable
fun QuillChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val q = MaterialTheme.quill
    val bg = if (selected) q.brandSoft else MaterialTheme.colorScheme.surface
    val fg = when {
        !enabled -> q.faint
        selected -> q.brandInk
        else -> q.text2
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.dp, q.lineStrong, CircleShape))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            Icon(
                androidx.compose.material.icons.Icons.Rounded.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, softWrap = false)
    }
}

/** The small bold label above a field/chip row (`.flabel-i`). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.quill.text2,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

/** Muted helper text under a field (`.help`). [bad] flips it to the error colour. */
@Composable
fun HelpText(text: String, modifier: Modifier = Modifier, bad: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.quill.muted,
        modifier = modifier.padding(start = 2.dp, top = 6.dp),
    )
}

// ============================================================================
// Bubble — the floating-dictation hero (`.bubble`), with its contrast-safe light ring.
// ============================================================================

enum class BubbleState { IDLE, PAUSED, RECORDING, BUSY, ERROR }

@Composable
fun QuillBubble(
    state: BubbleState,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val q = MaterialTheme.quill
    val gradient: List<Color> = when (state) {
        BubbleState.IDLE -> BrandGradient
        BubbleState.RECORDING -> GoldGradient
        BubbleState.BUSY -> listOf(Color(0xFF5B5BD6), Color(0xFF7E78E8))
        BubbleState.ERROR -> listOf(Color(0xFFD6453D), Color(0xFFE8635B))
        BubbleState.PAUSED -> listOf(q.lineStrong, q.lineStrong)
    }
    val iconTint = if (state == BubbleState.PAUSED) q.faint else Color.White
    // Light ring (the non-negotiable contrast affordance): a white disc with the gradient inset.
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size - 5.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(size * 0.46f))
        }
    }
}
