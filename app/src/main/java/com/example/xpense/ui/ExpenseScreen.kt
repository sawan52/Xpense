package com.example.xpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material. icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.model.Category
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel = viewModel()) {
    val expenses by viewModel.allExpenses.collectAsState()
    val totalAmount by viewModel.totalExpense.collectAsState()
    val summary by viewModel.categorySummary.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Xpense Tracker") },
                actions = {
                    IconButton(onClick = { viewModel.clearAll() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
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
            SummaryCard(totalAmount, summary)
            
            Text(
                "Recent Transactions",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(expenses) { expense ->
                    ExpenseItem(expense)
                }
            }
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

@Composable
fun ExpenseItem(expense: Expense) {
    ListItem(
        headlineContent = { Text(expense.merchant) },
        supportingContent = { 
            Text("${expense.category.name} • ${formatDate(expense.date)}")
        },
        trailingContent = {
            Text(
                "₹${String.format("%.2f", expense.amount)}",
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    )
    HorizontalDivider()
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
