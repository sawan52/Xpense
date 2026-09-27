package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.charts.BarDatum
import com.example.xpense.ui.components.charts.MonthBars
import com.example.xpense.ui.components.charts.Sparkline
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/** Home tab: greeting, this month's hero card, 6-month activity and recent transactions. */
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val active        by viewModel.activeExpenses.collectAsState()
    val lastSixMonths by viewModel.lastSixMonthsTotals.collectAsState()
    val userName      by viewModel.userName.collectAsState()
    val budget        by viewModel.monthlyBudget.collectAsState()
    val unread        by viewModel.pendingNotificationCount.collectAsState()

    var hidden by rememberSaveable { mutableStateOf(false) }
    var editBudget by remember { mutableStateOf(false) }

    val greeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    }
    val curTotal = lastSixMonths.lastOrNull()?.second ?: 0.0
    val prevTotal = lastSixMonths.getOrNull(lastSixMonths.size - 2)?.second ?: 0.0
    val monthName = remember { SimpleDateFormat("MMM", Locale.getDefault()).format(Date()) }
    // Cumulative spend per day this month, for the hero sparkline.
    val spark = remember(active) {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH); val year = cal.get(Calendar.YEAR)
        val perDay = DoubleArray(today)
        active.forEach {
            cal.timeInMillis = it.expense.date
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) perDay[cal.get(Calendar.DAY_OF_MONTH) - 1] += it.expense.amount
        }
        var acc = 0.0
        listOf(0.0) + perDay.map { acc += it; acc }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(c.ac, c.ac2))),
                    contentAlignment = Alignment.Center
                ) { Text(userName.take(1).uppercase(), style = XType.bodyStrong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(greeting, style = XType.caption, color = c.tx2)
                    Text(userName.substringBefore(' '), style = XType.h3.copy(fontSize = XType.h3.fontSize * 0.9f), color = c.tx)
                }
                HeaderButton(Icons.Rounded.Search, { viewModel.openSearch(SearchScope.ALL) }, contentDescription = "Search")
                Spacer(Modifier.width(8.dp))
                HeaderButton(Icons.Rounded.Notifications, { viewModel.navigateTo(Screen.NOTIFICATIONS) }, dot = unread > 0, contentDescription = "Notifications")
            }
        }

        // ── Hero card ─────────────────────────────────────────────────────
        item {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier.fillMaxWidth()
                    .shadow(24.dp, shape, ambientColor = c.ac, spotColor = c.ac.copy(alpha = 0.5f))
                    .clip(shape)
                    .background(Brush.linearGradient(c.hero, start = Offset(0f, 0f), end = Offset(900f, 1200f)))
                    .border(1.dp, c.heroLine, shape)
                    .cornerGlow(c.ac, 0.7f, 150.dp) { w, _, _ -> Offset(w + 40f, -40f) }
                    .cornerGlow(c.ac2, 0.3f, 140.dp) { _, h, _ -> Offset(20f, h + 60f) }
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL SPENT · ${monthName.uppercase()}", style = XType.overline.copy(letterSpacing = XType.overline.letterSpacing * 1.2f), color = Color.White.copy(alpha = 0.65f), modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { hidden = !hidden },
                        contentAlignment = Alignment.Center
                    ) { XIcon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, 18.dp, Color.White) }
                }
                Text(
                    if (hidden) "₹ •••••••" else CurrencyUtils.rupees(curTotal, 2),
                    style = XType.display, color = Color.White, modifier = Modifier.padding(top = 8.dp), maxLines = 1
                )
                if (prevTotal > 0 && !hidden) {
                    val change = (curTotal - prevTotal) / prevTotal * 100
                    val down = change <= 0
                    Row(
                        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(20.dp))
                            .background((if (down) Color(0xFF34E0A1) else Color(0xFFFF6B86)).copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tint = if (down) Color(0xFF5EF0B8) else Color(0xFFFF9BAE)
                        XIcon(if (down) Icons.AutoMirrored.Rounded.TrendingDown else Icons.AutoMirrored.Rounded.TrendingUp, 15.dp, tint)
                        Text("${"%.1f".format(kotlin.math.abs(change))}% vs last month", style = XType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = tint)
                    }
                }
                Sparkline(spark, Modifier.fillMaxWidth().height(60.dp).padding(top = 10.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Budget left
                    Column(
                        Modifier.weight(1f).height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
                            .clickable { editBudget = true }.padding(12.dp)
                    ) {
                        if (budget > 0) {
                            val left = budget - curTotal
                            Text(if (left >= 0) "Budget left" else "Over budget", style = XType.micro, color = Color.White.copy(alpha = 0.6f))
                            Text(
                                if (hidden) "₹ •••" else CurrencyUtils.rupees(kotlin.math.abs(left)),
                                style = XType.monoM, color = if (left >= 0) Color.White else Color(0xFFFF9BAE), modifier = Modifier.padding(top = 4.dp)
                            )
                            ProgressBar(
                                (left / budget).toFloat(), Modifier.padding(top = 8.dp),
                                brush = Brush.horizontalGradient(listOf(c.ac2, Color.White)), track = Color.White.copy(alpha = 0.15f)
                            )
                        } else {
                            Text("Monthly budget", style = XType.micro, color = Color.White.copy(alpha = 0.6f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text("Set a budget", style = XType.smallStrong, color = Color.White, modifier = Modifier.weight(1f))
                                XIcon(Icons.Rounded.Add, 18.dp, Color.White)
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
                            .clickable { viewModel.navigateTo(Screen.INSIGHTS) }.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deep dive", style = XType.micro, color = Color.White.copy(alpha = 0.6f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            Text("Insights", style = XType.bodyStrong, color = Color.White, modifier = Modifier.weight(1f))
                            XIcon(Icons.Rounded.ArrowOutward, 20.dp, Color.White)
                        }
                    }
                }
            }
        }

        // ── Spending activity ─────────────────────────────────────────────
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Spending activity", style = XType.section, color = c.tx, modifier = Modifier.weight(1f))
                    Text("Last 6 months", style = XType.caption, color = c.tx2)
                }
                val fmtIn = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
                val fmtOut = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
                MonthBars(
                    lastSixMonths.map { (m, v) -> BarDatum(runCatching { fmtOut.format(fmtIn.parse(m)!!) }.getOrDefault(m.take(3)), v) },
                    Modifier.fillMaxWidth().height(130.dp).padding(top = 16.dp),
                    valueLabel = { if (it > 0) CurrencyUtils.compact(it, symbol = false) else "0" }
                )
            }
        }

        // ── Recent ────────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Recent", trailing = "View all", trailingIsLink = true, onTrailing = { viewModel.navigateTo(Screen.INSIGHTS) })
                active.take(4).forEach { row ->
                    ExpenseRow(
                        row,
                        subtitle = "${row.category.name} · ${formatCardDate(row.expense.date)}",
                        onClick = { viewModel.editExpense(row.expense.id) }
                    )
                }
                if (active.isEmpty()) {
                    EmptyState(Icons.Rounded.Sms, "No transactions yet", "Sync your SMS history from Profile, or tap + to add one.", dashed = true)
                }
            }
        }
    }

    if (editBudget) {
        BudgetDialog(budget, onDismiss = { editBudget = false }) { viewModel.setMonthlyBudget(it); editBudget = false }
    }
}

/** Set or clear the monthly budget. */
@Composable
fun BudgetDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val c = XpenseTheme.colors
    var text by remember { mutableStateOf(if (current > 0) current.toLong().toString() else "") }
    XDialog(onDismiss) {
        DialogBadge(Icons.Rounded.Savings)
        DialogTitle("Monthly budget", "Home shows how much is left this month. Leave it empty to turn the budget off.")
        XTextField(
            text, { v -> text = v.filter { it.isDigit() }.take(9) }, "e.g. 60000",
            leadingIcon = Icons.Rounded.CurrencyRupee, leadingTint = c.ac, mono = true, keyboardType = KeyboardType.Number
        )
        DialogButtons("Save", { onSave(text.toDoubleOrNull() ?: 0.0) }, onDismiss)
    }
}
