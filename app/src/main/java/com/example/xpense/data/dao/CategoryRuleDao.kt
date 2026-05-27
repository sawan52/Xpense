package com.example.xpense.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.xpense.data.entity.CategoryRule
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules ORDER BY keyword ASC")
    fun getAllRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules")
    suspend fun getAllRulesList(): List<CategoryRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)

    @Query("SELECT COUNT(*) FROM category_rules")
    suspend fun getRuleCount(): Int
}
