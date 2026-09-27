package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CurrencyUtils

@Composable
fun ProfileScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val active   by viewModel.activeExpenses.collectAsState()
    val pending  by viewModel.pendingNotificationCount.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val email    by viewModel.signedInEmail.collectAsState()
    val dark     by viewModel.isDarkTheme.collectAsState()
    val budget   by viewModel.monthlyBudget.collectAsState()
    var editBudget by remember { mutableStateOf(false) }

    // Stats reflect actual spending, so archived rows (self-transfers etc.) are left out.
    val total = active.sumOf { it.expense.amount }
    // Read the real version from the installed package so it can never drift from build.gradle.kts.
    val context = LocalContext.current
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    LaunchedEffect(Unit) { viewModel.refreshBackupState() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { TitleHeader("Profile") }

        item {
            GlassCard(Modifier.fillMaxWidth(), radius = 28.dp, contentPadding = PaddingValues(20.dp), decoration = Modifier.topRightGlow(c.ac, 0.3f, 110.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(c.ac, c.ac2))),
                        contentAlignment = Alignment.Center
                    ) { Text(userName.firstOrNull()?.uppercase() ?: "U", style = XType.h2.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold), color = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(userName, style = XType.section.copy(fontSize = 17.sp), color = c.tx)
                        Text(email ?: "Personal account", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
                    }
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(active.size.toString(), "Transactions", Modifier.weight(1f))
                    StatTile(CurrencyUtils.compact(total), "Total spent", Modifier.weight(1f))
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline("Appearance")
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(16.dp), onClick = { viewModel.setDarkTheme(!dark) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(if (dark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode, c.ac, size = 42.dp, radius = 14.dp, iconSize = 21.dp, background = c.acSoft)
                        Column(Modifier.weight(1f)) {
                            Text(if (dark) "Dark mode" else "Light mode", style = XType.bodyStrong, color = c.tx)
                            Text(if (dark) "Easy on the eyes at night" else "Bright and crisp by day", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                        }
                        ThemeSwitch(dark) { viewModel.setDarkTheme(!dark) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Overline("Settings")
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(0.dp)) {
                    SettingRow(Icons.AutoMirrored.Rounded.HelpOutline, Color(0xFF60A5FA), "Help & guide", "Learn how every feature works", { viewModel.navigateTo(Screen.HELP) })
                    SettingRow(Icons.Rounded.Sms, Color(0xFF22D3EE), "Sync SMS history", "Import last 6 months of bank SMS", { viewModel.startHistoricalSync() }, showDivider = true)
                    SettingRow(
                        Icons.Rounded.Savings, Color(0xFF34D399), "Monthly budget",
                        if (budget > 0) "${CurrencyUtils.rupees(budget)} a month" else "Not set — track what's left",
                        { editBudget = true }, showDivider = true
                    )
                    SettingRow(Icons.Rounded.Inventory2, Color(0xFF4ADE80), "Archived transactions", "View & restore archived", { viewModel.navigateTo(Screen.IGNORED) }, showDivider = true)
                    SettingRow(Icons.Rounded.CloudSync, Color(0xFFA78BFA), "Backup & restore", "Back up to Google Drive", { viewModel.navigateTo(Screen.BACKUP) }, showDivider = true)
                    SettingRow(Icons.Rounded.Notifications, Color(0xFFFACC15), "Notifications", "Alerts for uncategorized", { viewModel.navigateTo(Screen.NOTIFICATIONS) }, showDivider = true, badge = pending)
                }
            }
        }

        item {
            Text("Xpense · v$appVersion", style = XType.caption, color = c.tx3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (editBudget) {
        BudgetDialog(budget, onDismiss = { editBudget = false }) { viewModel.setMonthlyBudget(it); editBudget = false }
    }
}
