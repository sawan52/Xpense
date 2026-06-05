package com.example.xpense.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.xpense.data.entity.Expense
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses")
    suspend fun getAllExpensesList(): List<Expense>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE rawSms = :rawSms AND date = :date)")
    suspend fun doesExpenseExist(rawSms: String, date: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE rawSms = :rawSms)")
    suspend fun doesSmsExist(rawSms: String): Boolean

    @Insert
    suspend fun insertExpense(expense: Expense)

    @Update
    suspend fun updateExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id IN (:ids)")
    suspend fun deleteExpenses(ids: List<Long>)

    @Query("UPDATE expenses SET categoryId = :newId WHERE categoryId = :oldId")
    suspend fun reassignCategory(oldId: Long, newId: Long)

    @Query("UPDATE expenses SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateExpenseCategory(id: Long, categoryId: Long)

    @Query("UPDATE expenses SET categoryId = :categoryId, merchant = :merchant WHERE id = :id")
    suspend fun updateExpenseCategoryAndMerchant(id: Long, categoryId: Long, merchant: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}