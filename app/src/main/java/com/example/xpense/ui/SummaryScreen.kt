package com.example.xpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.example.xpense.ui.components.SpendingChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel = viewModel()) {
    val lastSixMonths by viewModel.lastSixMonthsTotals.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allExpensesTotal = allExpenses.sumOf { it.amount }

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Overview") })
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
                .verticalScroll(rememberScrollState())
        ) {
            TotalCard(allExpensesTotal)

            Text(
                "Spending Trends (Last 6 Months)",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            
            SpendingChart(
                data = lastSixMonths,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(80.dp)) // FAB padding
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
