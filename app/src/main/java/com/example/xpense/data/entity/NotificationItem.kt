package com.example.xpense.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A durable record of an "uncategorized transaction" alert, shown in the in-app Notifications
 * inbox (Profile → Notifications). Written ONLY by the real-time SmsReceiver (never by history
 * sync), so the inbox reflects new messages only. An item is considered resolved — and hidden —
 * once its linked expense leaves "Others" (a rule or a manual edit categorized it); see
 * `visibleNotifications` in ExpenseViewModel.
 */
@Entity(tableName = "notifications")
data class NotificationItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val expenseId: Long,
    val merchant: String,
    val amount: Double,
    val date: Long
)
