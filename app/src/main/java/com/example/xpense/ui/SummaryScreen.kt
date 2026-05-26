package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AccountCircle
import com.example.xpense.data.model.Category
import com.example.xpense.ui.components.ExpenseDialog
import com.example.xpense.ui.components.SpendingChart

import com.example.xpense.ui.utils.CategoryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel = viewModel()) {
    val lastSixMonths by viewModel.lastSixMonthsTotals.collectAsState()
    val allExpenses by viewModel.allExpenses.collectAsState()
    val allExpensesTotal = allExpenses.sumOf { it.amount }
    
    val syncProgress by viewModel.syncProgress.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val showSyncConfirm by viewModel.showSyncConfirm.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color(0xFFF5F7FF),
        topBar = {
            TopAppBar(
                title = { Text("Overview", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.startHistoricalSync() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync SMS", tint = Color(0xFF475569))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 0.dp).size(64.dp).shadow(12.dp, CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Expense", modifier = Modifier.size(32.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = Color(0xFFEEF2FF)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Hello, User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Welcome back", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Premium Total Card
            PremiumSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .shadow(20.dp, RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                brush = Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Total Spending", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(
                        "₹${String.format("%.2f", allExpensesTotal)}",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last 6 months", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Chart Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Spending Trends", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    SpendingChart(data = lastSixMonths, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Recent Transactions Preview (Simple version for Home)
            Text("Recent Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            
            allExpenses.take(3).forEach { expense ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Icon(
                                    imageVector = CategoryUtils.getCategoryIcon(expense.category),
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(expense.merchant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(expense.category.name, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Text("-₹${expense.amount}", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
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

        if (syncProgress != null) {
            SyncProgressDialog(progress = syncProgress!!)
        }

        if (showSyncConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.hideSyncConfirm() },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmSyncAndStart() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Yes, Sync Now", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideSyncConfirm() }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                title = { Text("Confirm Sync", fontWeight = FontWeight.ExtraBold) },
                text = { Text("This will scan your SMS inbox for the last 6 months to find bank transactions. Do you want to proceed?") },
                shape = RoundedCornerShape(28.dp),
                containerColor = Color.White
            )
        }

        if (syncMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearSyncMessage() },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearSyncMessage() }) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                },
                title = { Text("Sync Update") },
                text = { Text(syncMessage!!) },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White
            )
        }
    }
}

@Composable
fun SyncProgressDialog(progress: Float) {
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    color = Color(0xFF4F46E5),
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Scanning Messages...",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${(progress * 100).toInt()}% complete",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun PremiumSurface(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    brush: Brush,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.background(brush, shape)) {
        content()
    }
}
