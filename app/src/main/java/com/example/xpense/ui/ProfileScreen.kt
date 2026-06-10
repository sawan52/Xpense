package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CurrencyUtils

@Composable
fun ProfileScreen(viewModel: ExpenseViewModel) {
    val allExpenses by viewModel.allExpenses.collectAsState()
    // Stats reflect actual spending, so ignored rows (self-transfers etc.) are left out.
    val countedExpenses = allExpenses.filter { !it.expense.ignored }
    val totalAmount = countedExpenses.sumOf { it.expense.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Text(
            "Profile",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // ── Avatar card ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(listOf(PurplePrimary, PurpleLight)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Sawan", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Personal Account", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatChip("${countedExpenses.size}", "Transactions")
                        StatChip("₹${CurrencyUtils.format(totalAmount, 0)}", "Total Spent")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Settings section ──────────────────────────────────────────────
        Text(
            "Settings",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
        ) {
            ProfileMenuItem(
                icon = Icons.Default.Category,
                iconColor = CategoryShoppingColor,
                title = "Category Rules",
                subtitle = "Manage auto-categorization",
                onClick = { viewModel.navigateTo(Screen.CATEGORY_RULES) }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuItem(
                icon = Icons.Default.Sync,
                iconColor = CategoryTravelColor,
                title = "Sync SMS History",
                subtitle = "Import last 6 months of bank SMS",
                onClick = { viewModel.startHistoricalSync() }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuItem(
                icon = Icons.Default.VisibilityOff,
                iconColor = CategoryHealthColor,
                title = "Ignored Transactions",
                subtitle = "View & restore ignored transactions",
                onClick = { viewModel.navigateTo(Screen.IGNORED) }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                iconColor = CategoryEntertainmentColor,
                title = "Notifications",
                subtitle = "Manage app notifications",
                onClick = {}
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "About",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
        ) {
            ProfileMenuItem(
                icon = Icons.Default.Info,
                iconColor = CategoryBillsColor,
                title = "App Version",
                subtitle = "1.2",
                onClick = {},
                showArrow = false
            )
        }
    }
}

@Composable
private fun StatChip(value: String, label: String) {
    Column {
        Text(value, color = PurpleLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showArrow: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        if (showArrow) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                tint = TextMuted, modifier = Modifier.size(14.dp)
            )
        }
    }
}
