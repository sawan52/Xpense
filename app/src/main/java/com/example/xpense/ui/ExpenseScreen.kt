package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.components.AddExpenseBottomSheet
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils
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

    var showEditSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

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
        .filter { monthFmt.format(Date(it.expense.date)) == prevMonthStr }
        .sumOf { it.expense.amount }
    val pctChange = if (prevTotal > 0) ((totalAmount - prevTotal) / prevTotal * 100).toInt() else 0

    val topEntry = summary.maxByOrNull { it.value }
    val topPct   = if (totalAmount > 0) ((topEntry?.value ?: 0.0) / totalAmount * 100).toInt() else 0

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
                    if (selectedIds.size == 1) {
                        IconButton(onClick = {
                            expenseToEdit = filteredExpenses.find { it.expense.id == selectedIds.first() }?.expense
                            showEditSheet = true
                        }) { Icon(Icons.Default.Edit, null, tint = PurpleLight) }
                    }
                    IconButton(onClick = { viewModel.deleteSelected() }) {
                        Icon(Icons.Default.Delete, null, tint = RedNegative)
                    }
                    IconButton(onClick = { viewModel.exitSelectionMode() }) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary)
                    }
                } else {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.FilterList, null, tint = TextSecondary)
                    }
                }
            }
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
                        // Donut
                        Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
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
                                    "₹${String.format("%,.0f", totalAmount)}",
                                    color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── Category breakdown ────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Category Breakdown", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("View All", color = PurpleLight, fontSize = 13.sp,
                        modifier = Modifier.clickable { viewModel.navigateTo(Screen.CATEGORY_RULES) })
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val sorted = summary.entries.sortedByDescending { it.value }
                    sorted.forEach { (cat, amount) ->
                        val pct = if (totalAmount > 0) (amount / totalAmount * 100).toInt() else 0
                        val color = CategoryUtils.getCategoryColor(cat)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(CategoryUtils.getCategoryIcon(cat), null, tint = color, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("$pct%", color = TextSecondary, fontSize = 13.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                        .background(DarkSurface)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(pct / 100f)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                            Text(
                                "₹${String.format("%,.0f", amount)}",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Smart insights ────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smart Insights", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("View All", color = PurpleLight, fontSize = 13.sp)
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val insights = buildInsights(pctChange, topEntry?.key, topEntry?.value, totalAmount, prevTotal)
                    insights.forEachIndexed { i, insight ->
                        SmartInsightRow(insight = insight)
                        if (i < insights.size - 1) HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            // ── Transaction list ──────────────────────────────────────────
            item {
                Text("Transactions", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(filteredExpenses, key = { it.expense.id }) { item ->
                DarkTransactionCard(
                    item = item,
                    isSelected = selectedIds.contains(item.expense.id),
                    isSelectionMode = isSelectionMode,
                    onToggle = { viewModel.toggleSelection(item.expense.id) },
                    onLongClick = { viewModel.enterSelectionMode(item.expense.id) }
                )
            }
            if (filteredExpenses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No transactions this month", color = TextMuted, fontSize = 14.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showEditSheet && expenseToEdit != null) {
        AddExpenseBottomSheet(
            expense = expenseToEdit,
            categories = categories,
            onDismiss = {
                showEditSheet = false
                viewModel.exitSelectionMode()
            },
            onConfirm = { amount, merchant, categoryId, date ->
                expenseToEdit?.let { viewModel.updateExpense(it.id, amount, merchant, categoryId, date) }
                showEditSheet = false
                viewModel.exitSelectionMode()
            }
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
                "₹${String.format("%,.0f", totalAmount)}",
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
            subtitle = "You spent ₹${String.format("%,.0f", topAmt ?: 0.0)} this month"
        ))
    }
    val saved = prevTotal - totalAmount
    if (saved > 0) {
        add(InsightItem(
            icon = Icons.Default.Savings,
            iconColor = GreenPositive,
            title = "You saved ₹${String.format("%,.0f", saved)} compared to last month",
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
    onLongClick: () -> Unit
) {
    val color = CategoryUtils.getCategoryColor(item.category)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PurplePrimary.copy(alpha = 0.12f) else DarkCard)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() },
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
                Text(item.expense.merchant, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.category.name} • ${formatHomeDate(item.expense.date)}",
                    color = TextMuted, fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "-₹${String.format("%.2f", item.expense.amount)}",
                    color = RedNegative, fontSize = 14.sp, fontWeight = FontWeight.Bold
                )
                if (item.expense.rawSms != "Manual Entry" && item.expense.rawSms != "Manual Update") {
                    Box(
                        modifier = Modifier
                            .background(DarkSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("UPI", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
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

fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
