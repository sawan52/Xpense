package com.example.xpense.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long, // Use ID instead of enum
    // Optional display name stamped onto matching transactions (e.g. "MF SIP" for a
    // "GROWW INVEST" NACH debit). When set, it overrides the auto-extracted merchant —
    // useful for SMS (NACH/forex) that carry no usable merchant text. Null = keep auto name.
    val label: String? = null
)
