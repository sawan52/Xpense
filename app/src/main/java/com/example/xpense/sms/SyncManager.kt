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
    private val db = AppDatabase.getDatabase(context)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.categoryRuleDao()
    private val categoryDao = db.categoryDao()

    fun syncHistoricalSms(): Flow<SyncProgress> = flow {
        emit(SyncProgress.Started)
        
        val rules = ruleDao.getAllRulesList()
        val categories = categoryDao.getAllCategoriesList()
        
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

            while (it.moveToNext()) {
                val body = it.getString(bodyIndex)
                val date = it.getLong(dateIndex)
                
                val transaction = SmsParser.parseTransaction(body, rules, categories)
                if (transaction != null) {
                    // A row the user edited before the fix lost its SMS identity (rawSms became the
                    // 'Manual Update' sentinel, dedupKey nulled), so plain dedup misses it and a
                    // duplicate gets inserted. If this SMS matches such a row (same timestamp +
                    // amount), re-adopt it instead: restore its key while keeping the user's edits,
                    // and drop any duplicate an earlier resync already created.
                    val edited = expenseDao.findEditedMatch(transaction.amount, date)
                    if (edited != null) {
                        expenseDao.deleteByDedupKey(body) // delete before adopt: avoids unique-index clash
                        expenseDao.adoptSmsIdentity(edited.id, body)
                    } else if (!expenseDao.doesSmsExist(body)) {
                        val expense = Expense(
                            amount = transaction.amount,
                            merchant = transaction.merchant,
                            date = date,
                            categoryId = transaction.categoryId,
                            rawSms = body,
                            dedupKey = body
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
    }.flowOn(Dispatchers.IO)

    sealed class SyncProgress {
        object Started : SyncProgress()
        data class Progress(val percentage: Float) : SyncProgress()
        object Completed : SyncProgress()
    }
}
