package com.example.xpense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                val body = message.displayMessageBody
                val transaction = SmsParser.parseTransaction(body)
                
                if (transaction != null) {
                    saveExpense(context, transaction, body)
                }
            }
        }
    }

    private fun saveExpense(context: Context, transaction: SmsParser.TransactionDetails, rawSms: String) {
        val expense = Expense(
            amount = transaction.amount,
            merchant = transaction.merchant,
            date = System.currentTimeMillis(),
            category = transaction.category,
            rawSms = rawSms
        )
        
        scope.launch {
            AppDatabase.getDatabase(context).expenseDao().insertExpense(expense)
        }
    }
}