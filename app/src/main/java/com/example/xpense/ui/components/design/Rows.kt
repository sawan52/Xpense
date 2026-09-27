package com.example.xpense.ui.components.design

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.ExpenseWithCategory
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** A transaction row for an [ExpenseWithCategory], with optional multi-select check. */
@Composable
fun ExpenseRow(
    item: ExpenseWithCategory,
    modifier: Modifier = Modifier,
    subtitle: String = item.category.name,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = XpenseTheme.colors
    TransactionRow(
        icon = CategoryUtils.getCategoryIcon(item.category),
        color = CategoryUtils.getCategoryColor(item.category),
        title = item.expense.merchant,
        subtitle = subtitle,
        amount = (if (muted) "" else "-") + CurrencyUtils.rupees(item.expense.amount, 0),
        amountColor = if (muted) c.tx2 else c.neg,
        selected = selected,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        trailing = if (selectionMode) {
            {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .background(if (selected) c.ac else Color.Transparent)
                        .border(1.5.dp, if (selected) c.ac else c.tx3, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (selected) XIcon(Icons.Rounded.Check, 15.dp, Color.White) }
            }
        } else trailing
    )
}

// Swipe-to-reveal actions: swipe left to reveal "Archive" (active lists), swipe right to reveal
// "Restore" (Archived). The row only slides to expose a button — the action runs ONLY when that
// button is tapped, so an accidental swipe while scrolling never archives/restores on its own. The
// drag is horizontal-only, so vertical list scrolling is unaffected.
@Composable
fun SwipeToRevealRow(
    enabled: Boolean,
    revealFromEnd: Boolean,
    actionIcon: ImageVector,
    actionLabel: String,
    actionColor: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = XpenseTheme.colors
    val revealDp = 92.dp
    val revealPx = with(LocalDensity.current) { revealDp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val minOffset = if (revealFromEnd) -revealPx else 0f
    val maxOffset = if (revealFromEnd) 0f else revealPx

    LaunchedEffect(enabled) { if (!enabled) offsetX.animateTo(0f) }

    Box(modifier.fillMaxWidth()) {
        if (enabled && offsetX.value != 0f) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(18.dp)).background(actionColor.copy(alpha = 0.16f)),
                contentAlignment = if (revealFromEnd) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Column(
                    Modifier.width(revealDp).fillMaxHeight().clickable {
                        onAction()
                        scope.launch { offsetX.animateTo(0f) }
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    XIcon(actionIcon, 22.dp, actionColor)
                    Spacer(Modifier.height(4.dp))
                    Text(actionLabel, style = XType.captionStrong.copy(fontSize = XType.micro.fontSize), color = actionColor)
                }
            }
        }
        // The card fill is translucent in dark mode, so paint the page background under it to keep
        // the revealed button from showing through while dragging.
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(18.dp))
                .background(c.bg)
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(minOffset, maxOffset)) }
                    },
                    onDragStopped = {
                        val opened = abs(offsetX.value) >= revealPx / 2f
                        offsetX.animateTo(if (!opened) 0f else if (revealFromEnd) minOffset else maxOffset)
                    }
                )
        ) { content() }
    }
}

@Composable
fun SwipeToArchiveRow(enabled: Boolean, onArchive: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SwipeToRevealRow(enabled, true, Icons.Rounded.Archive, "Archive", XpenseTheme.colors.ac, onArchive, modifier, content)
}

@Composable
fun SwipeToRestoreRow(onRestore: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SwipeToRevealRow(true, false, Icons.Rounded.Unarchive, "Restore", XpenseTheme.colors.pos, onRestore, modifier, content)
}

// ── Date helpers ─────────────────────────────────────────────────────────────

private val dayKeyFmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

/** "Today", "Yesterday", or "26 Sept" (with the year when it isn't this year). */
fun dayLabel(ts: Long, now: Long = System.currentTimeMillis()): String {
    val key = dayKeyFmt.format(Date(ts))
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    if (key == dayKeyFmt.format(cal.time)) return "Today"
    cal.add(Calendar.DAY_OF_YEAR, -1)
    if (key == dayKeyFmt.format(cal.time)) return "Yesterday"
    val sameYear = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.YEAR) ==
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
    return SimpleDateFormat(if (sameYear) "dd MMM" else "dd MMM yyyy", Locale.getDefault()).format(Date(ts))
}

/** Rows grouped by calendar day, preserving the input order (callers pass them sorted). */
fun groupByDay(rows: List<ExpenseWithCategory>): List<Pair<String, List<ExpenseWithCategory>>> {
    val out = LinkedHashMap<String, MutableList<ExpenseWithCategory>>()
    rows.forEach { out.getOrPut(dayKeyFmt.format(Date(it.expense.date))) { mutableListOf() }.add(it) }
    return out.values.map { dayLabel(it.first().expense.date) to it }
}

fun formatCardDate(ts: Long): String = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))

fun formatDateTime(ts: Long): String = SimpleDateFormat("dd MMM yyyy · hh:mm a", Locale.getDefault()).format(Date(ts))
