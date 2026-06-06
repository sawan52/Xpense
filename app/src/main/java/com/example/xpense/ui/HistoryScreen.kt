package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.components.AddExpenseBottomSheet
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: ExpenseViewModel) {
    val allExpenses     by viewModel.allExpenses.collectAsState()
    val selectedIds     by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val categories      by viewModel.allCategories.collectAsState()
    val hideIgnored     by viewModel.hideIgnored.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) { viewModel.exitSelectionMode() }

    val hasIgnored = remember(allExpenses) { allExpenses.any { it.expense.ignored } }
    val dayFmt  = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val today   = remember { dayFmt.format(Date()) }
    // "Hide ignored" drops ignored rows from the list; per-day totals always exclude them.
    val visibleExpenses = remember(allExpenses, hideIgnored) {
        if (hideIgnored) allExpenses.filter { !it.expense.ignored } else allExpenses
    }
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
                if (isSelectionMode) "${selectedIds.size} Selected" else "History",
                color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            if (isSelectionMode) {
                Row {
                    if (selectedIds.size == 1) {
                        IconButton(onClick = {
                            expenseToEdit = allExpenses.find { it.expense.id == selectedIds.first() }?.expense
                            showEditSheet = true
                        }) { Icon(Icons.Default.Edit, null, tint = PurpleLight) }
                    }
                    // Bulk ignore: if every selected row is already ignored, this un-ignores them.
                    val allSelectedIgnored = selectedIds.isNotEmpty() &&
                        selectedIds.all { id -> allExpenses.find { it.expense.id == id }?.expense?.ignored == true }
                    IconButton(onClick = { viewModel.setIgnoredForSelected(!allSelectedIgnored) }) {
                        Icon(
                            if (allSelectedIgnored) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (allSelectedIgnored) "Un-ignore selected" else "Ignore selected",
                            tint = PurpleLight
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = RedNegative)
                    }
                    IconButton(onClick = { viewModel.exitSelectionMode() }) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary)
                    }
                }
            } else if (hasIgnored) {
                IconButton(onClick = { viewModel.toggleHideIgnored() }) {
                    Icon(
                        if (hideIgnored) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hideIgnored) "Show ignored" else "Hide ignored",
                        tint = if (hideIgnored) PurpleLight else TextSecondary
                    )
                }
            }
        }

        if (allExpenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("No transactions yet", color = TextMuted, fontSize = 16.sp)
                    Text("Add one or sync your SMS", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (date, items) ->
                    item(key = "header_$date") {
                        val label = when (date) {
                            today -> "Today"
                            else  -> date
                        }
                        val dayTotal = items.filter { !it.expense.ignored }.sumOf { it.expense.amount }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "₹${CurrencyUtils.format(dayTotal, 2)}",
                                color = TextMuted, fontSize = 12.sp
                            )
                        }
                    }
                    items(items, key = { it.expense.id }) { item ->
                        DarkTransactionCard(
                            item = item,
                            isSelected = selectedIds.contains(item.expense.id),
                            isSelectionMode = isSelectionMode,
                            onToggle = { viewModel.toggleSelection(item.expense.id) },
                            onLongClick = { viewModel.enterSelectionMode(item.expense.id) },
                            onToggleIgnored = { viewModel.setIgnored(item.expense.id, !item.expense.ignored) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
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
