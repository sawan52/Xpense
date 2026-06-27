package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Detail screen showing the Category Breakdown + Smart Insights for the currently selected
 * month, reached via the "View Breakdown" button on the Insights screen. Reuses the same
 * ViewModel month state and the shared buildInsights/SmartInsightRow helpers from ExpenseScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsDetailScreen(viewModel: ExpenseViewModel) {
    val allExpenses      by viewModel.allExpenses.collectAsState()
    val totalAmount      by viewModel.totalForSelectedMonth.collectAsState()
    val summary          by viewModel.categorySummaryForSelectedMonth.collectAsState()
    val availableMonths  by viewModel.availableMonths.collectAsState()
    val selectedMonth    by viewModel.selectedMonth.collectAsState()
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()

    // Tapping a category row opens a popup listing just that category's transactions.
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    // Same derived insights as ExpenseScreen (prev-month change + top category).
    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedIdx = availableMonths.indexOf(selectedMonth)
    val prevMonthStr = if (selectedIdx >= 0 && selectedIdx < availableMonths.size - 1)
        availableMonths[selectedIdx + 1] else null
    val prevTotal = allExpenses
        .filter { !it.expense.ignored && monthFmt.format(Date(it.expense.date)) == prevMonthStr }
        .sumOf { it.expense.amount }
    val pctChange = if (prevTotal > 0) ((totalAmount - prevTotal) / prevTotal * 100).toInt() else 0
    val topEntry = summary.maxByOrNull { it.value }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spending Breakdown", color = TextPrimary, fontWeight = FontWeight.Bold)
                        selectedMonth?.let {
                            Text(it, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Category breakdown ────────────────────────────────────────
            item {
                Text("Category Breakdown", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            item {
                if (summary.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkCard)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No spending this month", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                // Tap a category to see the transactions that make it up.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedCategory = cat }
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
                                    "₹${CurrencyUtils.format(amount, 0)}",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
                }
            }
            item {
                val insights = buildInsights(pctChange, topEntry?.key, topEntry?.value, totalAmount, prevTotal)
                if (insights.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkCard)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Not enough data for insights yet", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(DarkCard)
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        insights.forEachIndexed { i, insight ->
                            SmartInsightRow(insight = insight)
                            if (i < insights.size - 1) HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    selectedCategory?.let { cat ->
        CategoryTransactionsDialog(
            category = cat,
            transactions = filteredExpenses.filter { it.category.id == cat.id },
            onDismiss = { selectedCategory = null }
        )
    }
}

/**
 * Floating popup listing every transaction in one category for the selected month. It leaves an
 * equal margin on all four sides (it never covers the whole screen) and is closed with the ✕ in
 * the top-right. Rows are read-only — this is a quick "what's in this category" view.
 */
@Composable
private fun CategoryTransactionsDialog(
    category: Category,
    transactions: List<ExpenseWithCategory>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkBg)
                    .border(1.dp, DarkBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                // Header: category + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val color = CategoryUtils.getCategoryColor(category)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(color.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(CategoryUtils.getCategoryIcon(category), null, tint = color, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(category.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        val total = transactions.sumOf { it.expense.amount }
                        Text(
                            "${transactions.size} transaction${if (transactions.size == 1) "" else "s"} • ₹${CurrencyUtils.format(total, 0)}",
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (transactions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transactions, key = { it.expense.id }) { item ->
                            DarkTransactionCard(
                                item = item,
                                isSelected = false,
                                isSelectionMode = false,
                                onToggle = {},
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
