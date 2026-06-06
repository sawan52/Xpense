package com.example.xpense.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    // Unique dedup key for SMS-derived rows. SQLite allows multiple NULLs in a unique index,
    // so manual entries (dedupKey = null) are unconstrained while no two SMS rows can share a body.
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val date: Long,
    val categoryId: Long, // Use ID instead of enum
    val rawSms: String,
    // SMS body for SMS-derived transactions (enforces no-duplicate at the DB level); null for
    // manual entries so they are never deduped.
    val dedupKey: String? = null,
    // User flag for "not really an expense" (e.g. a self-transfer). Ignored rows still appear in
    // lists (rendered faded) but are excluded from every total/aggregate.
    val ignored: Boolean = false
)