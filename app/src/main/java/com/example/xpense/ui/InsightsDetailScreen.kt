package com.example.xpense.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.charts.Donut
import com.example.xpense.ui.components.charts.DonutSlice
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/** Breakdown: a big tappable donut for the selected month and every category ranked by spend. */
@Composable
fun InsightsDetailScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val totalAmount   by viewModel.totalForSelectedMonth.collectAsState()
    val summary       by viewModel.categorySummaryForSelectedMonth.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val monthRows     by viewModel.monthExpenses.collectAsState()

    var selected by remember { mutableStateOf<Long?>(null) }
    val ranked = remember(summary) { summary.entries.sortedByDescending { it.value } }
    val counts = remember(monthRows) { monthRows.groupingBy { it.category.id }.eachCount() }
    val slices = remember(ranked) { ranked.map { DonutSlice(it.key.id, it.value, CategoryUtils.getCategoryColor(it.key)) } }
    val sel = ranked.find { it.key.id == selected }
    val glow by animateColorAsState(sel?.let { CategoryUtils.getCategoryColor(it.key) } ?: c.ac, label = "glow")
    val days = daysCounted(selectedMonth, remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            BackHeader("Breakdown", { viewModel.navigateBack() }, subtitle = "${selectedMonth?.let { shortMonth(it) } ?: ""} · ${ranked.size} categories")
        }
        item {
            GlassCard(
                Modifier.padding(top = 8.dp).fillMaxWidth(),
                decoration = Modifier.cornerGlow(glow, 0.35f, 150.dp) { w, _, r -> Offset(w / 2, r * 1.1f) },
                radius = 30.dp,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Donut(slices, 240.dp, 36.dp, Modifier.align(Alignment.CenterHorizontally), selectedKey = selected, onSelect = { selected = it }) {
                    AnimatedContent(sel, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith fadeOut() }, label = "center") { s ->
                        if (s == null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("TOTAL SPENT", style = XType.overlineS.copy(fontSize = XType.nav.fontSize), color = c.tx2)
                                Text(CurrencyUtils.rupees(totalAmount), style = XType.h3.copy(fontFamily = XType.mono.fontFamily), color = c.tx)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    XIcon(Icons.Rounded.TouchApp, 14.dp, c.tx3)
                                    Text("Tap a slice", style = XType.micro, color = c.tx3)
                                }
                            }
                        } else {
                            val col = CategoryUtils.getCategoryColor(s.key)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                IconTile(CategoryUtils.getCategoryIcon(s.key), col, size = 34.dp, radius = 11.dp, iconSize = 18.dp)
                                Text(s.key.name, style = XType.smallStrong, color = c.tx, modifier = Modifier.padding(top = 2.dp))
                                Text(CurrencyUtils.rupees(s.value), style = XType.monoL, color = c.tx)
                                Row(
                                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.13f))
                                        .clickable { viewModel.openCategory(s.key.id) }.padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${pct(s.value, totalAmount)}% · View", style = XType.captionStrong.copy(fontSize = XType.micro.fontSize), color = col)
                                    XIcon(Icons.Rounded.ChevronRight, 14.dp, col)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(ranked.size.toString(), "Categories", Modifier.weight(1f), centered = true, valueStyle = XType.monoM)
                    StatTile(monthRows.size.toString(), "Transactions", Modifier.weight(1f), centered = true, valueStyle = XType.monoM)
                    StatTile(CurrencyUtils.compact(totalAmount / days), "Avg / day", Modifier.weight(1f), centered = true, valueStyle = XType.monoM)
                }
            }
        }
        item { SectionHeader("All categories", Modifier.padding(top = 10.dp), trailing = "Highest first") }
        if (ranked.isEmpty()) {
            item { EmptyState(Icons.Rounded.DonutLarge, "Nothing spent this month", tinted = false, dashed = true) }
        }
        itemsIndexed(ranked, key = { _, e -> e.key.id }) { i, (cat, amt) ->
            val col = CategoryUtils.getCategoryColor(cat)
            val shape = RoundedCornerShape(20.dp)
            val border by animateColorAsState(if (cat.id == selected) col else c.line, label = "b")
            Row(
                Modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, border, shape)
                    .clickable { viewModel.openCategory(cat.id) }.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${i + 1}", style = XType.monoS.copy(fontSize = XType.micro.fontSize), color = c.tx3, modifier = Modifier.width(16.dp))
                IconTile(CategoryUtils.getCategoryIcon(cat), col)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(cat.name, style = XType.bodyStrong, color = c.tx, modifier = Modifier.weight(1f))
                        Text(CurrencyUtils.rupees(amt), style = XType.mono.copy(fontSize = XType.small.fontSize), color = c.tx)
                    }
                    Row(Modifier.padding(top = 3.dp)) {
                        val n = counts[cat.id] ?: 0
                        Text("$n transaction${if (n == 1) "" else "s"}", style = XType.micro, color = c.tx2, modifier = Modifier.weight(1f))
                        Text("${pct(amt, totalAmount)}%", style = XType.monoS.copy(fontSize = XType.micro.fontSize), color = c.tx2)
                    }
                    ProgressBar((amt / ranked.first().value).toFloat(), Modifier.padding(top = 7.dp), brush = SolidColor(col), glow = col)
                }
                XIcon(Icons.Rounded.ChevronRight, 20.dp, c.tx3)
            }
        }
    }
}
