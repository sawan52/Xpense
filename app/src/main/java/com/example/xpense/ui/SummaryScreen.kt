package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.components.SparklineChart
import com.example.xpense.ui.components.SpendingLineChart
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SummaryScreen(viewModel: ExpenseViewModel, onAddExpense: () -> Unit) {
    val allExpenses    by viewModel.allExpenses.collectAsState()
    val lastSixMonths  by viewModel.lastSixMonthsTotals.collectAsState()
    val userName       by viewModel.userName.collectAsState()

    // Ignored rows (self-transfers etc.) are excluded from balance/month totals.
    val countedExpenses = allExpenses.filter { !it.expense.ignored }
    val totalAmount = countedExpenses.sumOf { it.expense.amount }

    val greeting = remember {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when { h < 12 -> "Good Morning," ; h < 17 -> "Good Afternoon," ; else -> "Good Evening," }
    }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val cal = remember { Calendar.getInstance() }
    val curMonth  = remember { monthFmt.format(cal.time) }
    val prevMonth = remember {
        Calendar.getInstance().also { it.add(Calendar.MONTH, -1) }.let { monthFmt.format(it.time) }
    }
    val curTotal  = countedExpenses.filter { monthFmt.format(Date(it.expense.date)) == curMonth }.sumOf { it.expense.amount }
    val prevTotal = countedExpenses.filter { monthFmt.format(Date(it.expense.date)) == prevMonth }.sumOf { it.expense.amount }
    val pctChange = if (prevTotal > 0) ((curTotal - prevTotal) / prevTotal * 100).toInt() else 0
    val recentFour = countedExpenses.take(4)

    // Local UI-only toggle for masking the balance amount via the eye icon.
    var balanceHidden by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(greeting, color = TextSecondary, fontSize = 13.sp)
                Text("${userName.substringBefore(' ')} 👋", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("You're doing great! Keep it up.", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Total balance card ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B0764), PurplePrimary, PurpleLight)))
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Balance", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    Icon(
                        if (balanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (balanceHidden) "Show balance" else "Hide balance",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { balanceHidden = !balanceHidden }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (balanceHidden) "₹ ••••••" else "₹${CurrencyUtils.format(totalAmount, 2)}",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                val sign = if (pctChange >= 0) "↑" else "↓"
                val changeColor = if (pctChange <= 0) GreenPositive else RedNegative
                Text(
                    "$sign ${Math.abs(pctChange)}% from last month",
                    color = changeColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                // Sparkline
                SparklineChart(
                    data = lastSixMonths,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                            .clickable { viewModel.navigateTo(Screen.INSIGHTS) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("View Insights", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Spending activity ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Spending Activity", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(DarkCard, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("This Month ↓", color = TextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .padding(20.dp)
        ) {
            SpendingLineChart(
                data = lastSixMonths,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Recent transactions ───────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Transactions", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "View All",
                color = PurpleLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.INSIGHTS) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (recentFour.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions yet", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                recentFour.forEach { item ->
                    val color = CategoryUtils.getCategoryColor(item.category)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(color.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    CategoryUtils.getCategoryIcon(item.category), null,
                                    tint = color, modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.expense.merchant, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${item.category.name} • ${formatHomeDate(item.expense.date)}",
                                    color = TextMuted, fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "-₹${CurrencyUtils.format(item.expense.amount, 2)}",
                                    color = RedNegative,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (item.expense.rawSms != "Manual Entry") {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .background(DarkSurface, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("UPI", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

fun formatHomeDate(ts: Long): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(ts))
