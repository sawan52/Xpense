package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.model.Category
import com.example.xpense.ui.components.ExpenseDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel = viewModel()) {
    val expenses by viewModel.filteredExpenses.collectAsState()
    val totalAmount by viewModel.totalForSelectedMonth.collectAsState()
    val summary by viewModel.categorySummaryForSelectedMonth.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var expenseToEdit by remember { mutableStateOf<Expense?>(null) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (!isSelectionMode) "Monthly Tracker" else "${selectedIds.size} Selected") },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                        }
                    } else {
                        IconButton(onClick = { viewModel.navigateTo(Screen.HOME) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Summary")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedIds.size == 1) {
                            IconButton(onClick = {
                                expenseToEdit = expenses.find { it.id == selectedIds.first() }
                                showEditDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Selected")
                            }
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab Row for Months
            if (availableMonths.isNotEmpty()) {
                val selectedIndex = availableMonths.indexOf(selectedMonth).coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = {}
                ) {
                    availableMonths.forEachIndexed { index, month ->
                        Tab(
                            selected = selectedMonth == month,
                            onClick = { viewModel.selectMonth(month) },
                            text = { Text(month) }
                        )
                    }
                }
            }

            SummaryCard(totalAmount, summary)
            
            Text(
                "Transactions for $selectedMonth",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseItem(
                        expense = expense,
                        isSelected = selectedIds.contains(expense.id),
                        isSelectionMode = isSelectionMode,
                        onToggle = { viewModel.toggleSelection(expense.id) },
                        onLongClick = { viewModel.enterSelectionMode(expense.id) }
                    )
                }
            }
        }

        if (showEditDialog) {
            ExpenseDialog(
                expense = expenseToEdit,
                onDismiss = { 
                    showEditDialog = false
                    viewModel.exitSelectionMode()
                },
                onConfirm = { amount, merchant, category, date ->
                    expenseToEdit?.let { 
                        viewModel.updateExpense(it.id, amount, merchant, category, date)
                    }
                    showEditDialog = false
                    viewModel.exitSelectionMode()
                }
            )
        }
    }
}

@Composable
fun SummaryCard(total: Double, summary: Map<Category, Double>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Spending", fontSize = 14.sp)
            Text("₹${String.format("%.2f", total)}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            summary.forEach { (category, amount) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(category.name.lowercase().capitalize(), fontSize = 12.sp)
                    Text("₹${String.format("%.2f", amount)}", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseItem(
    expense: Expense,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = { if (isSelectionMode) onToggle() },
            onLongClick = { onLongClick() }
        ),
        headlineContent = { Text(expense.merchant) },
        supportingContent = { 
            Text("${expense.category.name} • ${formatDate(expense.date)}")
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "₹${String.format("%.2f", expense.amount)}",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle() }
                    )
                }
            }
        }
    )
    HorizontalDivider()
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
