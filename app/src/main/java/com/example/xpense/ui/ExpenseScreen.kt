package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.components.FilterSheet
import com.example.xpense.ui.components.charts.Donut
import com.example.xpense.ui.components.charts.DonutSlice
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/** Insights tab: month picker, donut summary, smart insights and the filterable transaction list. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val monthRows       by viewModel.monthExpenses.collectAsState()
    val visible         by viewModel.filteredExpenses.collectAsState()
    val prevRows        by viewModel.prevMonthExpenses.collectAsState()
    val totalAmount     by viewModel.totalForSelectedMonth.collectAsState()
    val summary         by viewModel.categorySummaryForSelectedMonth.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedMonth   by viewModel.selectedMonth.collectAsState()
    val selectedIds     by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val categories      by viewModel.allCategories.collectAsState()
    val ignoredTotal    by viewModel.ignoredTotalForSelectedMonth.collectAsState()
    val ignored         by viewModel.ignoredExpenses.collectAsState()
    val filter          by viewModel.txFilter.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    LaunchedEffect(availableMonths) {
        if (selectedMonth == null && availableMonths.isNotEmpty()) viewModel.selectMonth(availableMonths.first())
    }
    BackHandler(enabled = isSelectionMode) { viewModel.exitSelectionMode() }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val archivedInMonth = remember(ignored, selectedMonth) {
        ignored.count { selectedMonth == null || monthFmt.format(Date(it.expense.date)) == selectedMonth }
    }
    val slices = remember(summary) {
        summary.entries.sortedByDescending { it.value }
            .map { DonutSlice(it.key.id, it.value, CategoryUtils.getCategoryColor(it.key)) }
    }
    val top = summary.maxByOrNull { it.value }
    val insights = remember(monthRows, prevRows) { buildSmartInsights(monthRows, prevRows) }
    // Day groups only make sense when the list is in date order. Sorted by amount, grouping would
    // gather same-day rows together and break the order, so it becomes one flat list instead.
    val byAmount = filter.sort == TxSort.HIGHEST || filter.sort == TxSort.LOWEST
    val groups = remember(visible, byAmount) { if (byAmount) listOf("" to visible) else groupByDay(visible) }
    val dailyAvg = totalAmount / daysCounted(selectedMonth, monthFmt)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = screenPadding(horizontal = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Box(Gutter) {
                if (isSelectionMode) {
                    TitleHeader("${selectedIds.size} selected") {
                        HeaderButton(Icons.Rounded.Archive, { viewModel.setIgnoredForSelected(true) }, tint = c.ac, contentDescription = "Archive selected")
                        HeaderButton(Icons.Rounded.Delete, { showDeleteConfirm = true }, tint = c.neg, contentDescription = "Delete selected")
                        HeaderButton(Icons.Rounded.Close, { viewModel.exitSelectionMode() }, contentDescription = "Cancel selection")
                    }
                } else {
                    TitleHeader("Insights") {
                        HeaderButton(Icons.Rounded.Search, { viewModel.openSearch(SearchScope.TRANSACTIONS) }, contentDescription = "Search")
                        HeaderButton(Icons.Rounded.Tune, { showFilter = true }, active = filter.isActive, count = filter.activeCount, contentDescription = "Filter & sort")
                    }
                }
            }
        }

        // ── Month pills ───────────────────────────────────────────────────
        item {
            LazyRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableMonths) { m -> Pill(shortMonth(m), m == selectedMonth, { viewModel.selectMonth(m) }) }
            }
        }

        // ── Active filter chips ───────────────────────────────────────────
        if (filter.isActive) {
            item {
                FlowRow(
                    Gutter.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filter.sort != TxSort.NEWEST) FilterChipX("Sort: ${filter.sort.label}") { viewModel.setTxFilter(filter.copy(sort = TxSort.NEWEST)) }
                    filter.categoryIds.forEach { id ->
                        val name = categories.find { it.id == id }?.name ?: "Category"
                        FilterChipX(name) { viewModel.setTxFilter(filter.copy(categoryIds = filter.categoryIds - id)) }
                    }
                    if (filter.amount != AmountRange.ANY) FilterChipX(filter.amount.label) { viewModel.setTxFilter(filter.copy(amount = AmountRange.ANY)) }
                    if (filter.source != TxSource.ALL) FilterChipX(filter.source.label) { viewModel.setTxFilter(filter.copy(source = TxSource.ALL)) }
                    LinkText("Clear all", { viewModel.clearTxFilter() }, color = c.tx2)
                }
            }
        }

        // ── Donut summary card ────────────────────────────────────────────
        item {
            GlassCard(Gutter.padding(top = 12.dp).fillMaxWidth(), radius = 28.dp, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Donut(
                        slices, 140.dp, 18.dp,
                        Modifier.clip(RoundedCornerShape(70.dp)).clickable { viewModel.navigateTo(Screen.INSIGHTS_DETAIL) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SPENT", style = XType.overlineS.copy(fontSize = XType.nav.fontSize), color = c.tx2)
                            Text(CurrencyUtils.rupees(totalAmount), style = XType.monoM, color = c.tx, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Column {
                            Text("TOP CATEGORY", style = XType.overlineS, color = c.tx2)
                            if (top != null) {
                                val tc = CategoryUtils.getCategoryColor(top.key)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    XIcon(CategoryUtils.getCategoryIcon(top.key), 18.dp, tc)
                                    Text(top.key.name, style = XType.bodyStrong, color = tc, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${pct(top.value, totalAmount)}% of spend", style = XType.caption, color = c.tx2)
                            } else Text("—", style = XType.bodyStrong, color = c.tx3, modifier = Modifier.padding(top = 4.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Column {
                            Text("DAILY AVG", style = XType.overlineS, color = c.tx2)
                            Text(CurrencyUtils.rupees(dailyAvg), style = XType.mono, color = c.tx, modifier = Modifier.padding(top = 4.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.navigateTo(Screen.IGNORED) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("EXCLUDED", style = XType.overlineS, color = c.tx2)
                                XIcon(Icons.Rounded.Info, 13.dp, c.tx3)
                            }
                            Text(CurrencyUtils.rupees(ignoredTotal), style = XType.mono, color = c.tx2, modifier = Modifier.padding(top = 4.dp))
                            Text("$archivedInMonth archived", style = XType.micro, color = c.tx3)
                        }
                    }
                }
            }
        }

        // ── Smart insights ────────────────────────────────────────────────
        if (insights.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Smart insights", Gutter)
                    // A plain scrolling Row (at most 4 cards) rather than a LazyRow, so IntrinsicSize.Min
                    // can give every card the height of the tallest one.
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Min).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        insights.forEachIndexed { i, ins ->
                            InsightCard(ins, categories, highlighted = i == 0, modifier = Modifier.fillMaxHeight()) {
                                ins.categoryId?.let { viewModel.openCategory(it) }
                            }
                        }
                    }
                }
            }
        }

        // ── Transactions ──────────────────────────────────────────────────
        item {
            SectionHeader("Transactions", Gutter.padding(top = 12.dp), trailing = "${visible.size} of ${monthRows.size}".takeIf { filter.isActive } ?: "${visible.size} total")
        }
        groups.forEach { (label, rows) ->
            if (!byAmount && rows.isNotEmpty()) {
                item(key = "h_${rows.first().expense.id}") {
                    Box(Gutter.animateItem().padding(top = 8.dp, bottom = 2.dp)) { DayGroupHeader(label, CurrencyUtils.exact(rows.sumOf { it.expense.amount })) }
                }
            }
            items(rows, key = { it.expense.id }) { row ->
                SwipeToArchiveRow(
                    enabled = !isSelectionMode,
                    onArchive = { viewModel.setIgnored(row.expense.id, true) },
                    modifier = Gutter.animateItem()
                ) {
                    ExpenseRow(
                        row,
                        // Without day headers, the date moves into the row itself.
                        subtitle = if (byAmount) "${row.category.name} · ${formatCardDate(row.expense.date)}" else row.category.name,
                        selectionMode = isSelectionMode,
                        selected = row.expense.id in selectedIds,
                        onClick = { if (isSelectionMode) viewModel.toggleSelection(row.expense.id) else viewModel.editExpense(row.expense.id) },
                        onLongClick = { viewModel.enterSelectionMode(row.expense.id) }
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                if (filter.isActive) {
                    EmptyState(Icons.Rounded.FilterAltOff, "No transactions match", tinted = false, dashed = true,
                        actionText = "Clear filters", onAction = { viewModel.clearTxFilter() }, modifier = Gutter)
                } else {
                    EmptyState(Icons.Rounded.ReceiptLong, "No transactions this month", "New bank SMS will show up here.", tinted = false, dashed = true, modifier = Gutter)
                }
            }
        }
    }

    if (showFilter) {
        FilterSheet(
            initial = filter,
            categories = categories,
            resultCount = { applyTxFilter(monthRows, it).size },
            onDismiss = { showFilter = false },
            onApply = { viewModel.setTxFilter(it); showFilter = false }
        )
    }

    if (showDeleteConfirm) {
        val count = selectedIds.size
        ConfirmDialog(
            title = "Delete $count transaction${if (count == 1) "" else "s"}?",
            message = "This can't be undone. To hide a transaction from your totals instead, archive it.",
            onConfirm = { viewModel.deleteSelected(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/** One smart-insight card in the horizontal carousel. */
@Composable
fun InsightCard(insight: Insight, categories: List<Category>, highlighted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    val cat = insight.categoryId?.let { id -> categories.find { it.id == id } }
    val (icon, tint) = insightIcon(insight, cat)
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.width(230.dp).clip(shape)
            .background(if (highlighted) Brush.linearGradient(listOf(c.acSoft, Color.Transparent)) else Brush.linearGradient(listOf(c.card, c.card)))
            .border(1.dp, c.line, shape)
            .clickable(enabled = cat != null, onClick = onClick)
            .padding(16.dp)
    ) {
        XIcon(icon, 22.dp, tint)
        Text(insight.title, style = XType.bodyStrong, color = c.tx, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(insight.subtitle, style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun insightIcon(insight: Insight, cat: Category?): Pair<ImageVector, Color> {
    val c = XpenseTheme.colors
    return when (insight.kind) {
        InsightKind.CATEGORY_MOVE -> Icons.Rounded.AutoAwesome to c.ac
        InsightKind.TOTAL_DOWN -> Icons.AutoMirrored.Rounded.TrendingDown to c.pos
        InsightKind.TOTAL_UP -> Icons.AutoMirrored.Rounded.TrendingUp to c.neg
        InsightKind.RECURRING -> Icons.Rounded.EventRepeat to c.ac2
        InsightKind.TOP_SHARE, InsightKind.BIGGEST ->
            if (cat != null) CategoryUtils.getCategoryIcon(cat) to CategoryUtils.getCategoryColor(cat)
            else Icons.Rounded.Insights to c.ac
    }
}

/** "September 2026" → "Sept 2026" style short label for pills and subtitles. */
fun shortMonth(month: String): String {
    val full = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return runCatching { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(full.parse(month)!!) }.getOrDefault(month)
}

fun pct(part: Double, total: Double): Int = if (total > 0) Math.round(part / total * 100).toInt() else 0

/** Days to average over: elapsed days for the current month, the whole month otherwise. */
fun daysCounted(month: String?, fmt: SimpleDateFormat): Int {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance()
    month?.let { m -> runCatching { fmt.parse(m) }.getOrNull()?.let { cal.time = it } }
    val same = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    return if (same || month == null) now.get(Calendar.DAY_OF_MONTH) else cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}
