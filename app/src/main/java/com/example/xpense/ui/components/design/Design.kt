package com.example.xpense.ui.components.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Surfaces
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The app background: ink/paper fill with two soft accent glows in the top corners. Drawn as
 * radial gradients rather than Modifier.blur, which only works from API 31 (minSdk is 29).
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = XpenseTheme.colors
    Box(
        modifier
            .background(c.bg)
            .drawBehind {
                val a = c.glow
                val r1 = 300.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        0f to c.ac.copy(alpha = a * 0.75f), 0.5f to c.ac.copy(alpha = a * 0.3f), 1f to Color.Transparent,
                        center = Offset(90.dp.toPx(), 40.dp.toPx()), radius = r1
                    ),
                    radius = r1, center = Offset(90.dp.toPx(), 40.dp.toPx())
                )
                val r2 = 260.dp.toPx()
                val c2 = Offset(size.width + 10.dp.toPx(), 250.dp.toPx())
                drawCircle(
                    Brush.radialGradient(
                        0f to c.ac2.copy(alpha = a * 0.4f), 0.5f to c.ac2.copy(alpha = a * 0.15f), 1f to Color.Transparent,
                        center = c2, radius = r2
                    ),
                    radius = r2, center = c2
                )
            },
        content = content
    )
}

/** Soft blurred colour blob used inside cards (a card's corner glow). */
fun Modifier.cornerGlow(color: Color, alpha: Float, radius: Dp, center: (w: Float, h: Float, r: Float) -> Offset): Modifier =
    drawBehind {
        val r = radius.toPx()
        val o = center(size.width, size.height, r)
        drawCircle(
            Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to Color.Transparent, center = o, radius = r),
            radius = r, center = o
        )
    }

fun Modifier.topRightGlow(color: Color, alpha: Float = 0.35f, radius: Dp = 110.dp) =
    cornerGlow(color, alpha, radius) { w, _, r -> Offset(w - r * 0.25f, r * 0.25f) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    fill: Color = XpenseTheme.colors.card,
    borderColor: Color = XpenseTheme.colors.line,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    // Drawn inside the clipped card, above its fill (e.g. cornerGlow / topRightGlow).
    decoration: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    var m = modifier.clip(shape).background(fill).then(decoration).border(1.dp, borderColor, shape)
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    }
    Column(m.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
}

// ─────────────────────────────────────────────────────────────────────────────
// Icons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun XIcon(icon: ImageVector, size: Dp = 20.dp, tint: Color = LocalContentColor.current, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Tinted rounded square holding an icon — the design's category/setting glyph tile. */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    radius: Dp = 13.dp,
    iconSize: Dp = 20.dp,
    background: Color = color.copy(alpha = 0.13f)
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(background),
        contentAlignment = Alignment.Center
    ) { XIcon(icon, iconSize, color) }
}

/** The 42dp square header button (search, notifications, back, edit…). */
@Composable
fun HeaderButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = XpenseTheme.colors.tx2,
    active: Boolean = false,
    dot: Boolean = false,
    count: Int = 0,
    contentDescription: String? = null
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.size(42.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(if (active) c.acSoft else c.card)
                .border(1.dp, if (active) c.ac else c.line, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription, tint = if (active) c.ac else tint, modifier = Modifier.size(21.dp)) }
        if (dot) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp)
                    .size(7.dp).clip(CircleShape).background(c.neg)
            )
        }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp)
                    .border(3.dp, c.bg, CircleShape)
                    .padding(3.dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape).background(c.ac).padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) { Text(count.toString(), style = XType.nav, color = Color.White) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Headers
// ─────────────────────────────────────────────────────────────────────────────

/** Top-level tab title with an actions slot on the right. */
@Composable
fun TitleHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = XType.h1, color = XpenseTheme.colors.tx, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Sub-page header: back button, title and optional subtitle, actions on the right. */
@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    titleStyle: TextStyle = XType.h2,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val c = XpenseTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HeaderButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, tint = c.tx, contentDescription = "Back")
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = XType.caption, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingIsLink: Boolean = false,
    onTrailing: (() -> Unit)? = null
) {
    val c = XpenseTheme.colors
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = XType.section, color = c.tx, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = if (trailingIsLink) XType.captionStrong else XType.caption,
                color = if (trailingIsLink) c.ac else c.tx2,
                modifier = if (onTrailing != null) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onTrailing) else Modifier
            )
        }
    }
}

/** Uppercase letter-spaced group label ("SETTINGS", "SORT BY"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = XType.overline, color = XpenseTheme.colors.tx2, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Uppercase date on the left, mono day total on the right. */
@Composable
fun DayGroupHeader(date: String, total: String) {
    val c = XpenseTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(date.uppercase(), style = XType.captionStrong.copy(letterSpacing = XType.overlineS.letterSpacing), color = c.tx2, modifier = Modifier.weight(1f))
        Text(total, style = XType.monoS, color = c.tx2)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chips, pills, toggles
// ─────────────────────────────────────────────────────────────────────────────

/** Rounded pill; selected pills invert to text-on-background like the design's month chips. */
@Composable
fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) c.tx else c.card)
            .border(1.dp, c.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, style = XType.smallStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = if (selected) c.bg else c.tx2)
        if (trailing != null) Text(trailing, style = XType.monoS.copy(fontSize = XType.micro.fontSize), color = (if (selected) c.bg else c.tx2).copy(alpha = 0.7f))
    }
}

/** Category chip with a coloured icon; selected chips take a tinted fill and coloured border. */
@Composable
fun CategoryChip(
    name: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(if (large) 14.dp else 12.dp)
    Row(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else c.card2)
            .border(1.dp, if (selected) color else c.line, shape)
            .clickable(onClick = onClick)
            .padding(start = if (large) 10.dp else 8.dp, end = if (large) 14.dp else 12.dp, top = if (large) 9.dp else 7.dp, bottom = if (large) 9.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 6.dp else 5.dp)
    ) {
        XIcon(icon, if (large) 18.dp else 16.dp, color)
        Text(name, style = if (large) XType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) else XType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = c.tx)
    }
}

/** Removable active-filter chip. */
@Composable
fun FilterChipX(label: String, onRemove: () -> Unit) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(c.acSoft).border(1.dp, c.ac, shape).clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = XType.captionStrong, color = c.ac)
        XIcon(Icons.Rounded.Close, 15.dp, c.ac)
    }
}

/** Options in a recessed track; the selected one lifts onto a card. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    val c = XpenseTheme.colors
    val outer = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(outer).background(c.card2).border(1.dp, c.line, outer).padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(12.dp)
            Box(
                Modifier
                    .weight(1f)
                    .then(if (on) Modifier.shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f)) else Modifier)
                    .clip(shape)
                    .background(if (on) c.bg2 else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = if (compact) 9.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = if (compact) XType.captionStrong else XType.smallStrong, color = if (on) c.tx else c.tx2, maxLines = 1)
            }
        }
    }
}

/** The design's 50×30 toggle. */
@Composable
fun XSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = XpenseTheme.colors
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch")
    val track by animateColorAsState(if (checked) c.ac else c.card2, label = "track")
    Box(
        Modifier.size(50.dp, 30.dp).clip(RoundedCornerShape(15.dp)).background(track)
            .border(1.dp, if (checked) Color.Transparent else c.line, RoundedCornerShape(15.dp))
            .clickable { onCheckedChange(!checked) }.padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = x).size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White)
        )
    }
}

/** 64×34 dark/light toggle with the mode icon riding in the thumb. */
@Composable
fun ThemeSwitch(dark: Boolean, onToggle: () -> Unit) {
    val c = XpenseTheme.colors
    val x by animateDpAsState(if (dark) 30.dp else 0.dp, animationSpec = spring(dampingRatio = 0.6f), label = "theme")
    Box(
        Modifier.size(64.dp, 34.dp).clip(RoundedCornerShape(17.dp)).background(if (dark) c.ac else c.card2)
            .border(1.dp, c.line, RoundedCornerShape(17.dp))
            .clickable(onClick = onToggle).padding(3.dp)
    ) {
        Box(
            Modifier.offset(x = x).size(28.dp).shadow(5.dp, CircleShape).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            XIcon(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, 17.dp, if (dark) c.ac else Color(0xFFF59E0B))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

enum class BtnStyle { Gradient, Accent, Secondary, Outline, Danger, Inverse }

@Composable
fun XButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Gradient,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
    radius: Dp = 16.dp,
    textStyle: TextStyle = XType.bodyStrong
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(radius)
    val (bg: Brush, fg: Color) = when {
        !enabled -> SolidColor(c.card2) to c.tx3
        style == BtnStyle.Gradient -> Brush.linearGradient(listOf(c.ac, c.ac2)) to Color.White
        style == BtnStyle.Accent -> SolidColor(c.ac) to Color.White
        style == BtnStyle.Secondary -> SolidColor(c.card2) to c.tx
        style == BtnStyle.Outline -> SolidColor(Color.Transparent) to c.ac
        style == BtnStyle.Danger -> SolidColor(c.neg) to Color.White
        else -> SolidColor(c.tx) to c.bg
    }
    val glow = enabled && (style == BtnStyle.Gradient || style == BtnStyle.Accent)
    Row(
        modifier
            .height(height)
            .then(if (glow) Modifier.shadow(14.dp, shape, ambientColor = c.ac, spotColor = c.ac) else Modifier)
            .clip(shape)
            .background(bg)
            .then(if (style == BtnStyle.Outline && enabled) Modifier.border(1.5.dp, c.ac, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) XIcon(icon, 20.dp, fg)
        Text(text, style = textStyle, color = fg, maxLines = 1)
    }
}

/** Small text button like "Clear all" / "View all". */
@Composable
fun LinkText(text: String, onClick: () -> Unit, color: Color = XpenseTheme.colors.ac, style: TextStyle = XType.captionStrong) {
    Text(
        text, style = style, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows & states
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = XpenseTheme.colors.neg,
    selected: Boolean = false,
    iconSize: Dp = 40.dp,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(18.dp)
    var m = modifier.fillMaxWidth().clip(shape)
        .background(if (selected) c.acSoft else c.card)
        .border(1.dp, if (selected) c.ac else c.line, shape)
    if (onClick != null || onLongClick != null) m = m.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    Row(m.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, color, size = iconSize, iconSize = if (iconSize > 40.dp) 21.dp else 20.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = XType.bodyStrong, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = XType.caption, color = c.tx2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text(amount, style = XType.mono, color = amountColor)
        trailing?.invoke()
    }
}

/** A tappable row inside a settings-style list. */
@Composable
fun SettingRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = false,
    badge: Int = 0,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = XpenseTheme.colors
    Column {
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(icon, color, size = 38.dp, radius = 12.dp, iconSize = 19.dp)
            Column(Modifier.weight(1f)) {
                Text(title, style = XType.bodyStrong, color = c.tx)
                if (subtitle != null) Text(subtitle, style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
            }
            if (badge > 0) {
                Box(
                    Modifier.defaultMinSize(22.dp, 22.dp).clip(CircleShape).background(c.ac).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) { Text(badge.toString(), style = XType.captionStrong.copy(fontSize = XType.micro.fontSize), color = Color.White) }
            }
            if (trailing != null) trailing() else XIcon(Icons.Rounded.ChevronRight, 19.dp, c.tx3)
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tinted: Boolean = true,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    dashed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth()
            .then(if (dashed) Modifier.drawBehind {
                drawRoundRect(
                    color = c.line.copy(alpha = (c.line.alpha * 2.2f).coerceAtMost(1f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            } else Modifier)
            .padding(horizontal = 20.dp, vertical = if (dashed) 30.dp else 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(shape).background(if (tinted) c.acSoft else c.card2),
            contentAlignment = Alignment.Center
        ) { XIcon(icon, 30.dp, if (tinted) c.ac else c.tx3) }
        Text(title, style = XType.section, color = c.tx, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (subtitle != null) Text(subtitle, style = XType.small, color = c.tx2, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) LinkText(actionText, onAction, style = XType.smallStrong)
    }
}

/** Muted inline info strip ("Archived items are hidden from your totals."). */
@Composable
fun InfoBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier, accent: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(if (accent) c.acSoft else c.card2)
            .then(if (accent) Modifier.border(1.dp, c.ac, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        XIcon(icon, 18.dp, c.ac)
        Text(text, style = if (accent) XType.small else XType.caption, color = if (accent) c.tx else c.tx2, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) LinkText(action, onAction, style = XType.smallStrong)
    }
}

/** Thin rounded progress bar with an optional coloured glow. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, brush: Brush, track: Color = XpenseTheme.colors.card2, height: Dp = 4.dp, glow: Color? = null) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                .then(if (glow != null) Modifier.shadow(6.dp, RoundedCornerShape(height), ambientColor = glow, spotColor = glow) else Modifier)
                .clip(RoundedCornerShape(height)).background(brush)
        )
    }
}

/** Stat tile: mono value over a small label. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, fill: Color = XpenseTheme.colors.card2, bordered: Boolean = false, centered: Boolean = false, valueStyle: TextStyle = XType.monoL) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(if (bordered) 18.dp else 16.dp)
    Column(
        modifier.clip(shape).background(fill).then(if (bordered) Modifier.border(1.dp, c.line, shape) else Modifier)
            .padding(horizontal = if (centered) 10.dp else 14.dp, vertical = if (bordered) 14.dp else 12.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(value, style = valueStyle, color = c.tx, maxLines = 1)
        Text(label, style = XType.micro, color = c.tx2, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inputs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun XTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingTint: Color = XpenseTheme.colors.tx2,
    mono: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    fill: Color = XpenseTheme.colors.card2,
    bordered: Boolean = true,
    height: Dp = 52.dp
) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val style = (if (mono) XType.mono.copy(fontSize = XType.small.fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal) else XType.body).copy(color = c.tx)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(c.ac),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (singleLine) Modifier.height(height) else Modifier.heightIn(min = height))
                    .clip(shape).background(fill)
                    .then(if (bordered) Modifier.border(1.dp, c.line, shape) else Modifier)
                    .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (leadingIcon != null) XIcon(leadingIcon, 19.dp, leadingTint)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.tx3), maxLines = 1)
                    inner()
                }
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialogs & sheets
// ─────────────────────────────────────────────────────────────────────────────

/** Centered modal card (30dp radius) used for every confirm/editor dialog. */
@Composable
fun XDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = XpenseTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.94f)) {
            Column(
                Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
                    .shadow(30.dp, RoundedCornerShape(30.dp))
                    .clip(RoundedCornerShape(30.dp))
                    .background(c.bg2)
                    .border(1.dp, c.line, RoundedCornerShape(30.dp))
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                content = content
            )
        }
    }
}

/** Big 56dp icon badge at the top of a dialog. */
@Composable
fun DialogBadge(icon: ImageVector, color: Color? = null, size: Dp = 56.dp) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(size / 3.1f)
    Box(
        Modifier.size(size)
            .then(
                if (color == null) Modifier.shadow(14.dp, shape, ambientColor = c.ac, spotColor = c.ac).clip(shape).background(Brush.linearGradient(listOf(c.ac, c.ac2)))
                else Modifier.clip(shape).background(color.copy(alpha = 0.14f)).border(1.dp, color, shape)
            ),
        contentAlignment = Alignment.Center
    ) { XIcon(icon, size / 2, color ?: Color.White) }
}

@Composable
fun DialogTitle(title: String, body: String? = null) {
    val c = XpenseTheme.colors
    Column {
        Text(title, style = XType.h3, color = c.tx)
        if (body != null) Text(body, style = XType.body, color = c.tx2, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Cancel + confirm row at the bottom of a dialog. */
@Composable
fun DialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmStyle: BtnStyle = BtnStyle.Gradient,
    cancelText: String = "Cancel",
    confirmWeight: Float = 1.4f
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        XButton(cancelText, onCancel, Modifier.weight(1f), style = BtnStyle.Secondary)
        XButton(confirmText, onConfirm, Modifier.weight(confirmWeight), style = confirmStyle, enabled = confirmEnabled)
    }
}

/** Themed modal bottom sheet with the design's handle and title row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = XpenseTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.bg2,
        contentColor = c.tx,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(c.tx3))
        }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = XType.h3, color = c.tx)
                    if (subtitle != null) Text(subtitle, style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.card2).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { XIcon(Icons.Rounded.Close, 19.dp, c.tx) }
            }
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Toast
// ─────────────────────────────────────────────────────────────────────────────

class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial by mutableIntStateOf(0)
    internal val key get() = serial
    fun show(text: String) { message = text; serial++ }
    internal fun hide() { message = null }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

/** Top-anchored confirmation toast (inverted colours, green check). Auto-hides after 2.2s. */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = XpenseTheme.colors
    val msg = state.message
    var last by remember { mutableStateOf("") }
    if (msg != null) last = msg
    LaunchedEffect(state.key) {
        if (state.message != null) { delay(2200); state.hide() }
    }
    AnimatedVisibility(
        visible = msg != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth()
                .shadow(20.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp)).background(c.tx)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            XIcon(Icons.Rounded.CheckCircle, 20.dp, c.pos)
            Text(last, style = XType.smallStrong, color = c.bg)
        }
    }
}

/** Standard list padding: 20dp gutters and room at the bottom for the floating nav bar. */
@Composable
fun screenPadding(horizontal: Dp = 20.dp): PaddingValues {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = horizontal, end = horizontal, top = 8.dp, bottom = 124.dp + nav)
}

/** Horizontal gutter for LazyColumn items when the list itself has none (so carousels can bleed). */
val Gutter = Modifier.padding(horizontal = 20.dp)
