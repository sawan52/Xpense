package com.example.xpense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.ui.components.ExpenseDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel = viewModel()) {
    val monthlyTotals by viewModel.monthlyTotals.collectAsState()
    val sortedMonths by viewModel.availableMonths.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allExpensesTotal = allExpenses.sumOf { it.amount }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Expense Summary") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TotalCard(allExpensesTotal)

            Text(
                "Monthly Breakdown",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sortedMonths) { month ->
                    val total = monthlyTotals[month] ?: 0.0
                    MonthSummaryItem(month, total) {
                        viewModel.navigateTo(Screen.TRACKER, month)
                    }
                }
            }
        }

        if (showAddDialog) {
            ExpenseDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { amount, merchant, category, date ->
                    viewModel.addExpense(amount, merchant, category, date)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun TotalCard(total: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Overall Spending", color = Color.White, fontSize = 16.sp)
            Text(
                "₹${String.format("%.2f", total)}",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MonthSummaryItem(month: String, total: Double, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(month, fontWeight = FontWeight.SemiBold) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "₹${String.format("%.2f", total)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp).padding(start = 4.dp))
            }
        }
    )
    HorizontalDivider()
}
