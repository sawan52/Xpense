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
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                Log.d(TAG, "SMS_RECEIVED: ${messages.size} message(s)")
                for (message in messages) {
                    val sender = message.displayOriginatingAddress ?: "unknown"
                    val body = message.displayMessageBody ?: continue
                    Log.d(TAG, "From: $sender | Body: ${body.take(100)}")
                    processSms(context, body)
                }
            }
            "com.example.xpense.SIMULATE_SMS" -> {
                val body = intent.getStringExtra("body") ?: return
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
                Log.d(TAG, "SIMULATE_SMS: ${body.take(100)}")
                processSms(context, body, timestamp)
            }
        }
    }

    private fun processSms(context: Context, body: String, timestamp: Long = System.currentTimeMillis()) {
        scope.launch {
            val db = AppDatabase.getDatabase(context)
            if (db.expenseDao().doesSmsExist(body)) {
                Log.d(TAG, "Duplicate — skipped")
                return@launch
            }
            val rules = db.categoryRuleDao().getAllRulesList()
            val categories = db.categoryDao().getAllCategoriesList()
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
}
