package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * All ignored transactions in one place, reached from Profile. Rows are grouped by day like
 * History; the only action is the per-row eye toggle, which un-ignores the transaction and
 * sends it back to the main lists (its date is untouched, so it reappears under its original
 * month in Insights automatically).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredTransactionsScreen(viewModel: ExpenseViewModel) {
    val ignoredExpenses by viewModel.ignoredExpenses.collectAsState()

    BackHandler { viewModel.navigateTo(Screen.PROFILE) }

    val dayFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val today  = remember { dayFmt.format(Date()) }
    val grouped = remember(ignoredExpenses) {
        ignoredExpenses.groupBy { dayFmt.format(Date(it.expense.date)) }
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Ignored Transactions", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.PROFILE) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        if (ignoredExpenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.VisibilityOff, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Text("No ignored transactions", color = TextMuted, fontSize = 16.sp)
                    Text("Tap the eye icon on a transaction to ignore it", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (date, items) ->
                    item(key = "header_$date") {
                        val label = when (date) {
                            today -> "Today"
                            else  -> date
                        }
                        val dayTotal = items.sumOf { it.expense.amount }
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
                            isSelected = false,
                            isSelectionMode = false,
                            onToggle = {},
                            onLongClick = {},
                            onToggleIgnored = { viewModel.setIgnored(item.expense.id, false) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
