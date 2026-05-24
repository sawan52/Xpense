package com.example.xpense.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xpense.data.model.Category

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val date: Long,
    val category: Category,
    val rawSms: String
)