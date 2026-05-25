package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.model.Category
import com.example.xpense.ui.components.CategoryDonutChart
import com.example.xpense.ui.components.ExpenseDialog
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

import com.example.xpense.ui.utils.CategoryUtils

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
        containerColor = Color(0xFFF5F7FF),
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (!isSelectionMode) "Insights" else "${selectedIds.size} Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    ) 
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit Selection")
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
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Horizontal Month Chips
            ScrollableTabRow(
                selectedTabIndex = availableMonths.indexOf(selectedMonth).coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                availableMonths.forEach { month ->
                    val isSelected = selectedMonth == month
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .shadow(if (isSelected) 8.dp else 0.dp, RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) Color(0xFF4F46E5) else Color.White,
                                RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(onClick = { viewModel.selectMonth(month) })
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = month,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monthly Donut Breakdown
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryDonutChart(summary = summary, modifier = Modifier.weight(1f))
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Total Spent", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            "₹${String.format("%.2f", totalAmount)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transaction List with premium cards
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
            
            Spacer(modifier = Modifier.height(80.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseItem(
    expense: Expense,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isSelected) 12.dp else 2.dp, RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() },
                onLongClick = { onLongClick() }
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) Color(0xFFEEF2FF) else Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F5F9)
            ) {
                Icon(
                    imageVector = CategoryUtils.getCategoryIcon(expense.category),
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.merchant, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text(
                    "${expense.category.name} • ${formatDate(expense.date)}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "₹${String.format("%.2f", expense.amount)}",
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E293B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                if (isSelectionMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4F46E5))
                    )
                }
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
