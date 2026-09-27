package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CurrencyUtils

/**
 * Archived ("ignored") transactions — self-transfers and the like, hidden from every total.
 * Restore with the per-row button or by swiping right.
 */
@Composable
fun IgnoredTransactionsScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val ignored by viewModel.ignoredExpenses.collectAsState()
    val groups = remember(ignored) { groupByDay(ignored) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { BackHeader("Archived", { viewModel.navigateBack() }, subtitle = "${ignored.size} transaction${if (ignored.size == 1) "" else "s"}") }
        item { InfoBanner(Icons.Rounded.Info, "Archived items are hidden from your totals.", Modifier.padding(top = 8.dp)) }
        groups.forEach { (label, rows) ->
            item(key = "h_${rows.first().expense.id}") {
                Box(Modifier.animateItem().padding(top = 12.dp, bottom = 2.dp)) { DayGroupHeader(label, CurrencyUtils.exact(rows.sumOf { it.expense.amount })) }
            }
            items(rows, key = { it.expense.id }) { row ->
                SwipeToRestoreRow({ viewModel.setIgnored(row.expense.id, false) }, Modifier.animateItem()) {
                    ExpenseRow(row, muted = true, onClick = { viewModel.editExpense(row.expense.id) }) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.acSoft)
                                .clickable { viewModel.setIgnored(row.expense.id, false) },
                            contentAlignment = Alignment.Center
                        ) { XIcon(Icons.Rounded.Unarchive, 18.dp, c.ac) }
                    }
                }
            }
        }
        if (ignored.isEmpty()) {
            item { EmptyState(Icons.Rounded.Inventory2, "Nothing archived", "Swipe a transaction left on Insights to archive it.", tinted = false) }
        }
    }
}
