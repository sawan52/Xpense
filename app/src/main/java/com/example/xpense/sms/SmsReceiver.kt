package com.example.xpense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "XpenseSmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Collect the message bodies to process (real SMS may arrive multi-part).
        val messages: List<Pair<String, Long>> = when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val smsList = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
                Log.d(TAG, "SMS_RECEIVED: ${smsList.size} message(s)")
                smsList.mapNotNull { msg ->
                    val sender = msg.displayOriginatingAddress ?: "unknown"
                    val body = msg.displayMessageBody ?: return@mapNotNull null
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
                    processSms(db, rules, categories, body, timestamp)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(
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
                rawSms = body
            )
            db.expenseDao().insertExpense(expense)
            Log.d(TAG, "Saved expense: ₹${transaction.amount} @ ${transaction.merchant}")
        } else {
            Log.d(TAG, "No transaction parsed from SMS")
        }
    }
}
