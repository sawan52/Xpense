package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CurrencyUtils

@Composable
fun ProfileScreen(viewModel: ExpenseViewModel) {
    val allExpenses by viewModel.allExpenses.collectAsState()
    val pendingNotificationCount by viewModel.pendingNotificationCount.collectAsState()
    val userName by viewModel.userName.collectAsState()
    // Stats reflect actual spending, so ignored rows (self-transfers etc.) are left out.
    val countedExpenses = allExpenses.filter { !it.expense.ignored }
    val totalAmount = countedExpenses.sumOf { it.expense.amount }

    // Read the real version from the installed package so the displayed value can never drift from
    // versionName in build.gradle.kts the way a hardcoded string did.
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
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
                    Text(userName.firstOrNull()?.uppercase() ?: "U", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(userName, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconColor = PurplePrimary,
                title = "Help & Guide",
                subtitle = "Learn how every feature works",
                onClick = { viewModel.navigateTo(Screen.HELP) }
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
                icon = Icons.Default.Archive,
                iconColor = CategoryHealthColor,
                title = "Archived Transactions",
                subtitle = "View & restore archived transactions",
                onClick = { viewModel.navigateTo(Screen.IGNORED) }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuItem(
                icon = Icons.Default.CloudUpload,
                iconColor = CategoryTravelColor,
                title = "Backup & Restore",
                subtitle = "Back up your data to Google Drive",
                onClick = { viewModel.navigateTo(Screen.BACKUP) }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
            ProfileMenuItem(
                icon = Icons.Default.Notifications,
                iconColor = CategoryEntertainmentColor,
                title = "Notifications",
                subtitle = "Alerts for uncategorized transactions",
                badgeCount = pendingNotificationCount,
                onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) }
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
                subtitle = appVersion,
                onClick = {},
                showArrow = false
            )
        }

        Spacer(Modifier.height(24.dp))
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
    showArrow: Boolean = true,
    badgeCount: Int = 0
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
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .background(PurplePrimary, CircleShape)
                    .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badgeCount > 99) "99+" else "$badgeCount",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (showArrow) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                tint = TextMuted, modifier = Modifier.size(14.dp)
            )
        }
    }
}
