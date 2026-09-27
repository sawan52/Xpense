package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.NotificationItem
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CurrencyUtils

/**
 * In-app inbox of "uncategorized transaction" alerts (reached from Profile). Durable — survives
 * the user clearing the system notification panel. Records new real-time SMS only (never history
 * sync). Tapping a row opens the pre-filled Add-Rule dialog; an item auto-clears once its
 * transaction leaves "Others". Also hosts the pop-up on/off toggle (silences the system heads-up
 * only — the inbox keeps recording regardless).
 */
@Composable
fun NotificationsScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val notifications by viewModel.pendingNotifications.collectAsState()
    val popupsEnabled by viewModel.notificationsEnabled.collectAsState()
    var showDisableConfirm by remember { mutableStateOf(false) }
    val yellow = Color(0xFFFACC15)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BackHeader("Notifications", { viewModel.navigateBack() }) {
                if (notifications.isNotEmpty()) LinkText("Clear all", { viewModel.clearAllNotifications() })
            }
        }
        item {
            GlassCard(Modifier.padding(top = 6.dp).fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(if (popupsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, yellow, size = 42.dp, radius = 14.dp, iconSize = 21.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Pop-up alerts", style = XType.bodyStrong, color = c.tx)
                        Text("Heads-up when a transaction can't be auto-categorized", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                    }
                    XSwitch(popupsEnabled) { enabled ->
                        if (enabled) viewModel.setNotificationsEnabled(true) else showDisableConfirm = true
                    }
                }
            }
        }
        item {
            Overline("Needs a category", Modifier.padding(top = 8.dp)) {
                Text(notifications.size.toString(), style = XType.monoS, color = c.tx2)
            }
        }
        items(notifications, key = { it.id }) { n ->
            NotificationRow(n, onRule = { viewModel.requestRulePrefill(n.merchant) }, onDismiss = { viewModel.dismissNotification(n.id) })
        }
        if (notifications.isEmpty()) {
            item { EmptyState(Icons.Rounded.DoneAll, "All caught up", "Every transaction has a category.") }
        }
    }

    if (showDisableConfirm) {
        ConfirmDialog(
            title = "Turn off pop-up alerts?",
            message = "You won't get a heads-up when a transaction can't be auto-categorized. " +
                "They'll still be saved here in this list so you can categorize them later.",
            confirmLabel = "Turn off",
            icon = Icons.Rounded.NotificationsOff,
            destructive = false,
            onConfirm = { viewModel.setNotificationsEnabled(false); showDisableConfirm = false },
            onDismiss = { showDisableConfirm = false }
        )
    }
}

@Composable
private fun NotificationRow(item: NotificationItem, onRule: () -> Unit, onDismiss: () -> Unit) {
    val c = XpenseTheme.colors
    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.acSoft)
                    .drawBehind {
                        drawRoundRect(
                            c.ac, cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                        )
                    }
                    .clickable(onClick = onRule),
                contentAlignment = Alignment.Center
            ) { XIcon(Icons.Rounded.Add, 22.dp, c.ac) }
            Column(Modifier.weight(1f).clickable(onClick = onRule)) {
                Text(item.merchant, style = XType.bodyStrong, color = c.tx)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(CurrencyUtils.exact(item.amount), style = XType.monoS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = c.neg)
                    Text("·", style = XType.caption, color = c.tx2)
                    Text("Create rule", style = XType.caption, color = c.ac)
                }
                Text(formatDateTime(item.date), style = XType.micro, color = c.tx3, modifier = Modifier.padding(top = 3.dp))
            }
            Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(4.dp)) {
                XIcon(Icons.Rounded.Close, 20.dp, c.tx3)
            }
        }
    }
}
