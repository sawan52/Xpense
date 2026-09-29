package com.example.xpense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.entity.NotificationItem
import com.example.xpense.notifications.TransactionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "XpenseSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Collect the message bodies to process.
        val messages: List<Pair<String, Long>> = when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val smsList = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                Log.d(TAG, "SMS_RECEIVED: ${smsList.size} part(s)")
                // A long SMS arrives as several ~150-char parts in one intent. Join them back into
                // the one message the inbox stores: handled part by part, the first part (which
                // carries the amount) was saved on its own, and history sync then saved the full
                // inbox text again because the bodies no longer matched for dedup.
                smsList.groupBy { it.displayOriginatingAddress ?: "unknown" }
                    .mapNotNull { (sender, parts) ->
                        val body = parts.mapNotNull { it.displayMessageBody }.joinToString("")
                        if (body.isEmpty()) return@mapNotNull null
                        Log.d(TAG, "From: $sender | Body: ${body.take(100)}")
                        body to System.currentTimeMillis()
                    }
            }
            "com.example.xpense.SIMULATE_SMS" -> {
                val body = intent.getStringExtra("body") ?: return
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
                Log.d(TAG, "SIMULATE_SMS: ${body.take(100)}")
                listOf(body to timestamp)
            }
            else -> return
        }
        if (messages.isEmpty()) return

        // Keep the process alive until the DB work finishes — a bare coroutine launched from a
        // BroadcastReceiver can be killed before it completes, silently dropping transactions.
        val pendingResult = goAsync()
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val rules = db.categoryRuleDao().getAllRulesList()
                val categories = db.categoryDao().getAllCategoriesList()
                for ((body, timestamp) in messages) {
                    processSms(context, db, rules, categories, body, timestamp)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(
        context: Context,
        db: AppDatabase,
        rules: List<com.example.xpense.data.entity.CategoryRule>,
        categories: List<com.example.xpense.data.entity.Category>,
        body: String,
        timestamp: Long
    ) {
        if (db.expenseDao().doesSmsExist(body)) {
            Log.d(TAG, "Duplicate — skipped")
            return
        }
        val transaction = SmsParser.parseTransaction(body, rules, categories)
        if (transaction != null) {
            val expense = Expense(
                amount = transaction.amount,
                merchant = transaction.merchant,
                date = timestamp,
                categoryId = transaction.categoryId,
                rawSms = body,
                dedupKey = body
            )
            val newId = db.expenseDao().insertExpense(expense)
            Log.d(TAG, "Saved expense: ₹${transaction.amount} @ ${transaction.merchant} (id=$newId)")
            // Nudge the user to create a rule only when nothing could categorize it (→ Others).
            // The in-app inbox row is ALWAYS recorded here (real-time SMS only — history sync never
            // reaches this receiver); the pop-up is self-gated by the toggle inside notify().
            if (transaction.uncategorized && newId > 0) {
                db.notificationDao().insert(
                    NotificationItem(
                        expenseId = newId,
                        merchant = transaction.merchant,
                        amount = transaction.amount,
                        date = timestamp
                    )
                )
                TransactionNotifier.notify(context, transaction.merchant, transaction.amount)
            }
        } else {
            Log.d(TAG, "No transaction parsed from SMS")
        }
    }
}
