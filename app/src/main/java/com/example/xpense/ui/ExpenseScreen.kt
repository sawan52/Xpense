package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.components.AddExpenseBottomSheet
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel) {
    val allExpenses      by viewModel.allExpenses.collectAsState()
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val totalAmount      by viewModel.totalForSelectedMonth.collectAsState()
    val summary          by viewModel.categorySummaryForSelectedMonth.collectAsState()
    val availableMonths  by viewModel.availableMonths.collectAsState()
    val selectedMonth    by viewModel.selectedMonth.collectAsState()
    val selectedIds      by viewModel.selectedIds.collectAsState()
    val isSelectionMode  by viewModel.isSelectionMode.collectAsState()
    val categories       by viewModel.allCategories.collectAsState()
    val ignoredTotal     by viewModel.ignoredTotalForSelectedMonth.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Search narrows only the month's transaction list (charts stay on the month total).
    val visibleExpenses = filteredExpenses
        .filter {
            searchQuery.isBlank() ||
                it.expense.merchant.contains(searchQuery, ignoreCase = true) ||
                it.category.name.contains(searchQuery, ignoreCase = true) ||
                it.expense.amount.toString().contains(searchQuery)
        }

    LaunchedEffect(availableMonths) {
        if (selectedMonth == null && availableMonths.isNotEmpty()) {
            viewModel.selectMonth(availableMonths.first())
        }
    }

    BackHandler(enabled = isSelectionMode) { viewModel.exitSelectionMode() }

    // Derived insights
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedIdx = availableMonths.indexOf(selectedMonth)
    val prevMonthStr = if (selectedIdx >= 0 && selectedIdx < availableMonths.size - 1)
        availableMonths[selectedIdx + 1] else null
    val prevTotal = allExpenses
        .filter { !it.expense.ignored && monthFmt.format(Date(it.expense.date)) == prevMonthStr }
        .sumOf { it.expense.amount }
    val pctChange = if (prevTotal > 0) ((totalAmount - prevTotal) / prevTotal * 100).toInt() else 0

    val topEntry = summary.maxByOrNull { it.value }
    val topPct   = if (totalAmount > 0) ((topEntry?.value ?: 0.0) / totalAmount * 100).toInt() else 0

    // Group the month's transactions by day so the list reads like the History screen: a date
    // header with that day's total on the right, followed by the day's transactions. Rows arrive
    // newest-first (DAO orders by date DESC), so groups and rows are already in descending order.
    val dayFmt  = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val today   = remember { dayFmt.format(Date()) }
    val grouped = remember(visibleExpenses) {
        visibleExpenses.groupBy { dayFmt.format(Date(it.expense.date)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isSelectionMode) "${selectedIds.size} Selected" else "Insights",
                color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Row {
                if (isSelectionMode) {
                    // Bulk archive: rows here are never archived (those live on the Archived screen).
                    IconButton(onClick = { viewModel.setIgnoredForSelected(true) }) {
                        Icon(
                            Icons.Default.Archive,
                            contentDescription = "Archive selected",
                            tint = PurpleLight
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = RedNegative)
                    }
                    IconButton(onClick = { viewModel.exitSelectionMode() }) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary)
                    }
                } else {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(Icons.Default.Search, "Search transactions", tint = if (searchActive) PurpleLight else TextSecondary)
                    }
                }
            }
        }

        // ── Search bar (toggled by the header search icon) ────────────────
        if (searchActive && !isSelectionMode) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search transactions…", color = TextMuted, fontSize = 14.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isBlank()) searchActive = false else searchQuery = ""
                    }) {
                        Icon(Icons.Default.Close, "Clear search", tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurplePrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PurpleLight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            )
        }

        // ── Month chips ───────────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(availableMonths) { month ->
                val isSel = month == selectedMonth
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSel) PurplePrimary else DarkCard)
                        .clickable { viewModel.selectMonth(month) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        month,
                        color = if (isSel) Color.White else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Donut + stats card ────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(DarkCard)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Donut — tap to open the full category breakdown + smart insights.
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .clickable { viewModel.navigateTo(Screen.INSIGHTS_DETAIL) },
                            contentAlignment = Alignment.Center
                        ) {
                            InsightsDonut(summary = summary, totalAmount = totalAmount, pctChange = pctChange)
                        }
                        Spacer(Modifier.width(20.dp))
                        // Right stats
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            topEntry?.let { (cat, _) ->
                                Column {
                                    Text("Top Category", color = TextSecondary, fontSize = 12.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(CategoryUtils.getCategoryColor(cat).copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                CategoryUtils.getCategoryIcon(cat), null,
                                                tint = CategoryUtils.getCategoryColor(cat),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Text(cat.name, color = PurpleLight, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("$topPct% of total spending", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = DarkBorder, thickness = 1.dp)
                            Column {
                                Text("Amount", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    "₹${CurrencyUtils.format(totalAmount, 0)}",
                                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                                )
                                if (ignoredTotal > 0) {
                                    Text(
                                        "₹${CurrencyUtils.format(ignoredTotal, 0)} excluded",
                                        color = TextMuted, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Day-grouped transaction list (date on the left, day's total on the right) ──
            grouped.forEach { (date, dayItems) ->
                item(key = "header_$date") {
                    val label = if (date == today) "Today" else date
                    val dayTotal = dayItems.sumOf { it.expense.amount }
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "₹${CurrencyUtils.format(dayTotal, 2)}",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
                items(dayItems, key = { it.expense.id }) { item ->
                    // Swipe left to archive; disabled during multi-select so the checkbox tap wins.
                    // animateItem() slides the remaining rows up smoothly when this one leaves.
                    SwipeToArchiveRow(
                        enabled = !isSelectionMode,
                        onArchive = { viewModel.setIgnored(item.expense.id, true) },
                        modifier = Modifier.animateItem()
                    ) {
                        DarkTransactionCard(
                            item = item,
                            isSelected = selectedIds.contains(item.expense.id),
                            isSelectionMode = isSelectionMode,
                            onToggle = { viewModel.toggleSelection(item.expense.id) },
                            onLongClick = { viewModel.enterSelectionMode(item.expense.id) },
                            onClick = {
                                expenseToEdit = item.expense
                                showEditSheet = true
                            }
                        )
                    }
                }
            }
            if (visibleExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searchQuery.isBlank()) "No transactions this month"
                            else "No transactions match \"$searchQuery\"",
                            color = TextMuted, fontSize = 14.sp
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showEditSheet && expenseToEdit != null) {
        val editing = expenseToEdit!!
        val showAddRule = editing.rawSms != "Manual Entry" && editing.rawSms != "Manual Update" &&
            !viewModel.hasUserRuleFor(editing)
        val showForceRule = viewModel.forcibleRuleFor(editing) != null
        AddExpenseBottomSheet(
            expense = expenseToEdit,
            categories = categories,
            onDismiss = {
                showEditSheet = false
                viewModel.exitSelectionMode()
            },
            onConfirm = { amount, merchant, categoryId, date, note ->
                expenseToEdit?.let { viewModel.updateExpense(it.id, amount, merchant, categoryId, date, note) }
                showEditSheet = false
                viewModel.exitSelectionMode()
            },
            onAddCategory = { name, icon -> viewModel.addCategory(name, icon) },
            showAddRule = showAddRule,
            onAddRule = {
                showEditSheet = false
                viewModel.exitSelectionMode()
                viewModel.requestRulePrefill(editing.merchant)
            },
            showForceRule = showForceRule,
            onForceRule = {
                showEditSheet = false
                viewModel.exitSelectionMode()
                viewModel.forceRule(editing.id)
            }
        )
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        ConfirmDialog(
            title = "Delete Transaction${if (count == 1) "" else "s"}",
            message = "Delete $count transaction${if (count == 1) "" else "s"}? This can't be undone.",
            onConfirm = {
                viewModel.deleteSelected()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

// ── Donut chart ───────────────────────────────────────────────────────────
@Composable
fun InsightsDonut(summary: Map<Category, Double>, totalAmount: Double, pctChange: Int) {
    val total = summary.values.sum().coerceAtLeast(1.0)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            var start = -90f
            summary.entries.toList().forEach { (cat, amt) ->
                val sweep = (amt / total * 360f).toFloat()
                drawArc(
                    color = CategoryUtils.getCategoryColor(cat),
                    startAngle = start, sweepAngle = sweep,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                start += sweep
            }
            if (summary.isEmpty()) {
                drawArc(
                    color = DarkSurface,
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total Spent", color = TextSecondary, fontSize = 10.sp)
            Text(
                "₹${CurrencyUtils.format(totalAmount, 0)}",
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            val sign = if (pctChange >= 0) "↑" else "↓"
            val col  = if (pctChange <= 0) GreenPositive else RedNegative
            Text("$sign ${Math.abs(pctChange)}%", color = col, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("from last month", color = TextMuted, fontSize = 9.sp)
        }
    }
}

// ── Smart insight data class + builder ────────────────────────────────────
data class InsightItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val iconColor: Color, val title: String, val subtitle: String)

fun buildInsights(
    pctChange: Int, topCat: Category?, topAmt: Double?, totalAmount: Double, prevTotal: Double
): List<InsightItem> = buildList {
    if (prevTotal > 0) {
        val abs = Math.abs(pctChange)
        val less = pctChange < 0
        add(InsightItem(
            icon = if (less) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
            iconColor = if (less) GreenPositive else RedNegative,
            title = "You spent $abs% ${if (less) "less" else "more"} this month",
            subtitle = if (less) "Great job! Keep it up." else "Try to reduce spending."
        ))
    }
    topCat?.let {
        add(InsightItem(
            icon = CategoryUtils.getCategoryIcon(it),
            iconColor = CategoryUtils.getCategoryColor(it),
            title = "${it.name} is your highest expense category",
            subtitle = "You spent ₹${CurrencyUtils.format(topAmt ?: 0.0, 0)} this month"
        ))
    }
    val saved = prevTotal - totalAmount
    if (saved > 0) {
        add(InsightItem(
            icon = Icons.Default.Savings,
            iconColor = GreenPositive,
            title = "You saved ₹${CurrencyUtils.format(saved, 0)} compared to last month",
            subtitle = "Well done!"
        ))
    }
}

@Composable
fun SmartInsightRow(insight: InsightItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(insight.iconColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(insight.icon, null, tint = insight.iconColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(insight.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(insight.subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = TextMuted, modifier = Modifier.size(14.dp))
    }
}

// ── Shared dark transaction card used by both Insights + History ───────────
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DarkTransactionCard(
    item: ExpenseWithCategory,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit = {}
) {
    val color = CategoryUtils.getCategoryColor(item.category)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PurplePrimary.copy(alpha = 0.12f) else DarkCard)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() else onClick() },
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(CategoryUtils.getCategoryIcon(item.category), null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Single-line + ellipsis so a long merchant never makes this card taller than others.
                Text(
                    item.expense.merchant,
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.category.name} • ${formatCardDate(item.expense.date)}",
                    color = TextMuted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Never wrap the amount — it keeps its full width and the merchant ellipsizes instead.
                Text(
                    "-₹${CurrencyUtils.format(item.expense.amount, 2)}",
                    color = RedNegative, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false
                )
            }
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = PurplePrimary)
                )
            }
        }
    }
}

// ── Swipe-to-reveal actions: swipe left to reveal "Archive" (active lists), swipe right to reveal
// "Restore" (Archived). The row only slides to expose a button — the action runs ONLY when that
// button is tapped, so an accidental swipe while scrolling never archives/restores on its own. The
// drag is horizontal-only, so vertical list scrolling is unaffected.

@Composable
fun SwipeToRevealRow(
    enabled: Boolean,
    revealFromEnd: Boolean,          // true: swipe-left reveals on the right; false: swipe-right on the left
    actionIcon: ImageVector,
    actionLabel: String,
    actionColor: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val revealDp = 92.dp
    val revealPx = with(LocalDensity.current) { revealDp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Closed = 0. Swipe-left reveals on the right (negative offset); swipe-right reveals on the left.
    val minOffset = if (revealFromEnd) -revealPx else 0f
    val maxOffset = if (revealFromEnd) 0f else revealPx

    // Snap shut when the row is disabled (e.g. entering multi-select).
    LaunchedEffect(enabled) { if (!enabled) offsetX.animateTo(0f) }

    Box(modifier = modifier.fillMaxWidth()) {
        // Action button revealed behind the card on the swiped edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp))
                .background(actionColor.copy(alpha = 0.18f)),
            contentAlignment = if (revealFromEnd) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .width(revealDp)
                    .fillMaxHeight()
                    .clickable {
                        onAction()
                        scope.launch { offsetX.animateTo(0f) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(actionIcon, contentDescription = actionLabel, tint = actionColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(actionLabel, color = actionColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Foreground card — draggable horizontally to reveal the button; opaque (its own DarkCard
        // background) so it fully hides the button when closed.
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetX.snapTo((offsetX.value + delta).coerceIn(minOffset, maxOffset)) }
                    },
                    onDragStopped = {
                        // Settle open past the half-way point, otherwise snap closed.
                        val opened = abs(offsetX.value) >= revealPx / 2f
                        offsetX.animateTo(if (!opened) 0f else if (revealFromEnd) minOffset else maxOffset)
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
fun SwipeToArchiveRow(
    enabled: Boolean,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeToRevealRow(
        enabled = enabled,
        revealFromEnd = true,
        actionIcon = Icons.Default.Archive,
        actionLabel = "Archive",
        actionColor = PurpleLight,
        onAction = onArchive,
        modifier = modifier,
        content = content
    )
}

@Composable
fun SwipeToRestoreRow(
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SwipeToRevealRow(
        enabled = true,
        revealFromEnd = false,
        actionIcon = Icons.Default.Unarchive,
        actionLabel = "Restore",
        actionColor = GreenPositive,
        onAction = onRestore,
        modifier = modifier,
        content = content
    )
}

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))

// Date without the time — used on the Insights transaction list, where the time is intentionally
// hidden (it's still shown when the transaction is opened in the edit sheet).
fun formatCardDate(ts: Long): String =
    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(ts))
