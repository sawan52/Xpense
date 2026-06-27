package com.example.xpense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.NotificationItem
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * In-app inbox of "uncategorized transaction" alerts (reached from Profile). Durable — survives
 * the user clearing the system notification panel. Records new real-time SMS only (never history
 * sync). Tapping a row opens the pre-filled Add-Rule dialog; an item auto-clears once its
 * transaction leaves "Others". Also hosts the pop-up on/off toggle (silences the system heads-up
 * only — the inbox keeps recording regardless).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: ExpenseViewModel) {
    val notifications by viewModel.pendingNotifications.collectAsState()
    val popupsEnabled by viewModel.notificationsEnabled.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }

    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, "More options", tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = DarkSurface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Send test notification", color = TextPrimary) },
                            onClick = {
                                menuExpanded = false
                                viewModel.sendTestNotification()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear all", color = if (notifications.isEmpty()) TextMuted else RedNegative) },
                            enabled = notifications.isNotEmpty(),
                            onClick = {
                                menuExpanded = false
                                viewModel.clearAllNotifications()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Pop-up alerts toggle (silences the system heads-up only — the inbox keeps recording).
            PopupToggleRow(
                checked = popupsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) viewModel.setNotificationsEnabled(true)
                    else showDisableConfirm = true
                }
            )
            HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

            if (notifications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NotificationsActive, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Text("You're all caught up", color = TextMuted, fontSize = 16.sp)
                        Text(
                            "Transactions we couldn't auto-categorize show up here",
                            color = TextMuted, fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        NotificationRow(
                            item = item,
                            dateText = fmt.format(Date(item.date)),
                            onClick = { viewModel.requestRulePrefill(item.merchant) },
                            onDismiss = { viewModel.dismissNotification(item.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showDisableConfirm) {
        ConfirmDialog(
            title = "Turn off pop-up alerts?",
            message = "You won't get a heads-up when a transaction can't be auto-categorized. " +
                "They'll still be saved here in this list so you can categorize them later.",
            confirmLabel = "Turn off",
            onConfirm = {
                viewModel.setNotificationsEnabled(false)
                showDisableConfirm = false
            },
            onDismiss = { showDisableConfirm = false }
        )
    }
}

@Composable
private fun PopupToggleRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(CategoryEntertainmentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (checked) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                null, tint = CategoryEntertainmentColor, modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Pop-up alerts", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Heads-up when a transaction can't be auto-categorized",
                color = TextMuted, fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = PurplePrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = DarkSurface,
                uncheckedBorderColor = DarkBorder
            )
        )
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    dateText: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(PurplePrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, null, tint = PurpleLight, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.merchant, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "₹${CurrencyUtils.format(item.amount, 2)} • Tap to create a rule",
                color = TextMuted, fontSize = 12.sp
            )
            Text(dateText, color = TextMuted, fontSize = 11.sp)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, "Dismiss", tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
