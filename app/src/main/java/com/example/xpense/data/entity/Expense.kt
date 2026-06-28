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
    val ignored: Boolean = false,
    // Optional free-text note the user attaches when editing/adding a transaction. Null = no note.
    val note: String? = null,
    // "User-pinned": set when the user edits this transaction AND a rule already matched it at that
    // time. Such a row is the user's deliberate override of an already-applied rule, so rule
    // re-application leaves it untouched (rule > manual edit, EXCEPT a manual edit made after a rule
    // existed wins). Editing a still-rule-less row does NOT pin it, so a rule created later can still
    // apply. Also dismisses the "uncategorized" notification. See reapplyDecision / updateExpense.
    val categoryLocked: Boolean = false
)