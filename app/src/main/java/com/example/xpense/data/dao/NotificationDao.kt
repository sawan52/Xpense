package com.example.xpense.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.xpense.data.entity.NotificationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY date DESC")
    fun getAll(): Flow<List<NotificationItem>>

    @Insert
    suspend fun insert(item: NotificationItem)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE expenseId = :expenseId")
    suspend fun deleteByExpenseId(expenseId: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
