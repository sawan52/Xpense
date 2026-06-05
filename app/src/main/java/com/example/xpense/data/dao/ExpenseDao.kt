package com.example.xpense.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // IGNORE so a racing duplicate SMS insert (same dedupKey) is dropped by the DB instead of
    // throwing. Manual rows have dedupKey = null and never conflict.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    // Edit only the user-editable columns by id, leaving rawSms + dedupKey untouched. Building a
    // fresh Expense here (the old approach) wiped both keys and made resync re-insert the row.
    @Query("UPDATE expenses SET amount = :amount, merchant = :merchant, categoryId = :categoryId, date = :date WHERE id = :id")
    suspend fun updateExpenseFields(id: Long, amount: Double, merchant: String, categoryId: Long, date: Long)

    // Repair support: find a previously-edited SMS row that lost its dedup identity (rawSms was
    // overwritten with the 'Manual Update' sentinel and dedupKey nulled). Matched by the SMS's
    // exact timestamp + amount so resync can re-adopt it instead of inserting a duplicate.
    @Query("SELECT * FROM expenses WHERE dedupKey IS NULL AND rawSms = 'Manual Update' AND date = :date AND amount = :amount LIMIT 1")
    suspend fun findEditedMatch(amount: Double, date: Long): Expense?

    @Query("DELETE FROM expenses WHERE dedupKey = :dedupKey")
    suspend fun deleteByDedupKey(dedupKey: String)

    // Restore an edited row's SMS identity, preserving the user's edited merchant/categoryId.
    @Query("UPDATE expenses SET rawSms = :body, dedupKey = :body WHERE id = :id")
    suspend fun adoptSmsIdentity(id: Long, body: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
}