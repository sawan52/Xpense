package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.charts.BarDatum
import com.example.xpense.ui.components.charts.MonthBars
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/** One category in the selected month: hero, 6-month trend, top merchants and its transactions. */
@Composable
fun CategoryDetailScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val categoryId    by viewModel.selectedCategoryId.collectAsState()
    val categories    by viewModel.allCategories.collectAsState()
    val active        by viewModel.activeExpenses.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthTotal    by viewModel.totalForSelectedMonth.collectAsState()

    val category = categories.find { it.id == categoryId }
    if (category == null) {
        LaunchedEffect(Unit) { viewModel.navigateBack() }
        return
    }
    val color = CategoryUtils.getCategoryColor(category)
    var merchantFilter by remember(categoryId) { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val shortFmt = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val anchor = remember(selectedMonth) {
        Calendar.getInstance().apply { selectedMonth?.let { m -> runCatching { monthFmt.parse(m) }.getOrNull()?.let { time = it } } }
    }
    val mine = remember(active, categoryId) { active.filter { it.category.id == categoryId } }
    val byMonth = remember(mine) { mine.groupBy { monthFmt.format(Date(it.expense.date)) } }
    val monthKey = monthFmt.format(anchor.time)
    val prevKey = monthFmt.format((anchor.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.time)
    val rows = byMonth[monthKey].orEmpty()
    val spent = rows.sumOf { it.expense.amount }
    val prevSpent = byMonth[prevKey].orEmpty().sumOf { it.expense.amount }
    val bars = (5 downTo 0).map { back ->
        val cal = (anchor.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
        BarDatum(shortFmt.format(cal.time), byMonth[monthFmt.format(cal.time)].orEmpty().sumOf { it.expense.amount })
    }
    val merchants = rows.groupBy { it.expense.merchant }.mapValues { (_, l) -> l.sumOf { it.expense.amount } }
        .entries.sortedByDescending { it.value }.take(8)
    val listed = rows.filter { merchantFilter == null || it.expense.merchant == merchantFilter }
    val groups = remember(listed) { groupByDay(listed) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(horizontal = 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Gutter) {
                BackHeader(category.name, { viewModel.navigateBack() }, subtitle = "${shortMonth(monthKey)} · Breakdown") {
                    HeaderButton(Icons.Rounded.Edit, { editing = true }, contentDescription = "Edit category")
                }
            }
        }
        // ── Hero ──────────────────────────────────────────────────────────
        item {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Gutter.padding(top = 10.dp).fillMaxWidth().clip(shape)
                    .background(Brush.linearGradient(0f to color.copy(alpha = 0.16f), 0.75f to c.card, 1f to c.card))
                    .topRightGlow(color, 0.35f, 120.dp)
                    .border(1.dp, color.copy(alpha = 0.3f), shape)
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("SPENT IN ${shortFmt.format(anchor.time).uppercase()}", style = XType.overline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.tx2)
                        Text(CurrencyUtils.rupees(spent), style = XType.display.copy(fontSize = XType.display.fontSize * 0.94f), color = c.tx, modifier = Modifier.padding(top = 6.dp))
                    }
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.13f)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { XIcon(CategoryUtils.getCategoryIcon(category), 25.dp, color) }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${pct(spent, monthTotal)}% of total", style = XType.captionStrong, color = c.tx,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(c.card2).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                    if (prevSpent > 0) {
                        val up = spent > prevSpent
                        val tint = if (up) c.neg else c.pos
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.14f)).padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            XIcon(if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, 16.dp, tint)
                            Text("${pct(kotlin.math.abs(spent - prevSpent), prevSpent)}% vs last month", style = XType.captionStrong, color = tint)
                        }
                    }
                }
                MonthBars(
                    bars, Modifier.fillMaxWidth().height(180.dp).padding(top = 18.dp),
                    valueLabel = { if (it > 0) CurrencyUtils.compact(it, symbol = false) else "0" },
                    highlight = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.6f))), glow = color, barRadius = 8.dp, gap = 8.dp,
                    // Past months in a light tint of the category colour: the neutral card2 grey all
                    // but disappears on this tinted card in light mode.
                    muted = color.copy(alpha = if (c.isDark) 0.18f else 0.28f)
                )
            }
        }
        item {
            Row(Gutter.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(rows.size.toString(), "Transactions", Modifier.weight(1f), fill = c.card, bordered = true)
                StatTile(if (rows.isEmpty()) "₹0" else CurrencyUtils.rupees(spent / rows.size), "Avg per transaction", Modifier.weight(1f), fill = c.card, bordered = true)
            }
        }
        if (merchants.isNotEmpty()) {
            item {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader("Top merchants", Gutter)
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(merchants, key = { it.key }) { (name, amt) ->
                            val on = merchantFilter == name
                            val shape = RoundedCornerShape(16.dp)
                            Column(
                                Modifier.clip(shape).background(if (on) color.copy(alpha = 0.16f) else c.card)
                                    .border(1.dp, if (on) color else c.line, shape)
                                    .clickable { merchantFilter = if (on) null else name }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(name, style = XType.smallStrong, color = c.tx, maxLines = 1)
                                Text(CurrencyUtils.rupees(amt), style = XType.monoS.copy(fontSize = XType.micro.fontSize), color = if (on) color else c.tx2)
                            }
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Transactions", Gutter.padding(top = 10.dp), trailing = if (merchantFilter != null) "${listed.size} · $merchantFilter" else "${listed.size} total")
        }
        groups.forEach { (label, day) ->
            item(key = "h_${day.first().expense.id}") {
                Box(Gutter.padding(top = 8.dp, bottom = 2.dp)) { DayGroupHeader(label, CurrencyUtils.exact(day.sumOf { it.expense.amount })) }
            }
            items(day, key = { it.expense.id }) { row ->
                ExpenseRow(
                    row, Gutter,
                    subtitle = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(row.expense.date)) + (row.expense.note?.let { " · $it" } ?: ""),
                    onClick = { viewModel.editExpense(row.expense.id) }
                )
            }
        }
        if (rows.isEmpty()) {
            item { EmptyState(Icons.Rounded.ReceiptLong, "Nothing in ${category.name} this month", tinted = false, dashed = true, modifier = Gutter) }
        }
    }

    if (editing) {
        CategoryEditorDialog(
            initialName = category.name,
            initialIcon = category.iconName,
            editing = true,
            color = color,
            onDismiss = { editing = false },
            onConfirm = { name, icon -> viewModel.updateCategory(category.id, name, icon); editing = false }
        )
    }
}
