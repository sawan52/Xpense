package com.example.xpense.sms

import android.content.Context
import android.provider.Telephony
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.*

class SyncManager(private val context: Context) {
    private val expenseDao = AppDatabase.getDatabase(context).expenseDao()

    fun syncHistoricalSms(): Flow<SyncProgress> = flow {
        try {
            emit(SyncProgress.Started)
            
            val sixMonthsAgo = Calendar.getInstance().apply {
                add(Calendar.MONTH, -6)
            }.timeInMillis

            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.Inbox.BODY, Telephony.Sms.Inbox.DATE),
                "${Telephony.Sms.Inbox.DATE} > ?",
                arrayOf(sixMonthsAgo.toString()),
                "${Telephony.Sms.Inbox.DATE} DESC"
            )

            cursor?.use {
                val bodyIndex = it.getColumnIndex(Telephony.Sms.Inbox.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.Inbox.DATE)
                val totalCount = it.count
                var processedCount = 0

                if (totalCount == 0) {
                    emit(SyncProgress.Completed)
                    return@flow
                }

                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    
                    val transaction = SmsParser.parseTransaction(body)
                    if (transaction != null) {
                        if (!expenseDao.doesExpenseExist(body, date)) {
                            val expense = Expense(
                                amount = transaction.amount,
                                merchant = transaction.merchant,
                                date = date,
                                category = transaction.category,
                                rawSms = body
                            )
                            expenseDao.insertExpense(expense)
                        }
                    }
                    
                    processedCount++
                    if (processedCount % 10 == 0 || processedCount == totalCount) {
                        emit(SyncProgress.Progress(processedCount.toFloat() / totalCount.toFloat()))
                    }
                }
            }
            
            emit(SyncProgress.Completed)
        } catch (e: Exception) {
            emit(SyncProgress.Error(e.message ?: "Unknown error occurred during sync"))
        }
    }.flowOn(Dispatchers.IO)

    sealed class SyncProgress {
        object Started : SyncProgress()
        data class Progress(val percentage: Float) : SyncProgress()
        object Completed : SyncProgress()
        data class Error(val message: String) : SyncProgress()
    }
}
