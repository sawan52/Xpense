package com.example.xpense.data.backup

import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Expense
import kotlinx.serialization.Serializable

/**
 * The on-disk / on-Drive backup format. Deliberately decoupled from the Room entities so the file
 * format can outlive a schema change: [schemaVersion] records the DB version the file was written
 * with, letting a future importer migrate older backups instead of failing.
 *
 * IDs are preserved in the file. On a REPLACE restore the original ids are written straight back
 * (Room keeps a provided non-zero autoGenerate id), so every expense.categoryId still points at the
 * right category. MERGE remaps ids by category name instead (see BackupManager.planMerge).
 */
@Serializable
data class BackupData(
    val schemaVersion: Int,
    val appVersionCode: Int,
    val exportedAt: Long,
    val categories: List<CategoryDto>,
    val rules: List<RuleDto>,
    val expenses: List<ExpenseDto>
)

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val iconName: String
) {
    fun toEntity() = Category(id = id, name = name, iconName = iconName)
    companion object {
        fun from(c: Category) = CategoryDto(c.id, c.name, c.iconName)
    }
}

@Serializable
data class RuleDto(
    val id: Long,
    val keyword: String,
    val categoryId: Long,
    val label: String? = null
) {
    fun toEntity() = CategoryRule(id = id, keyword = keyword, categoryId = categoryId, label = label)
    companion object {
        fun from(r: CategoryRule) = RuleDto(r.id, r.keyword, r.categoryId, r.label)
    }
}

@Serializable
data class ExpenseDto(
    val id: Long,
    val amount: Double,
    val merchant: String,
    val date: Long,
    val categoryId: Long,
    val rawSms: String,
    val dedupKey: String? = null,
    val ignored: Boolean = false
) {
    fun toEntity() = Expense(
        id = id,
        amount = amount,
        merchant = merchant,
        date = date,
        categoryId = categoryId,
        rawSms = rawSms,
        dedupKey = dedupKey,
        ignored = ignored
    )
    companion object {
        fun from(e: Expense) = ExpenseDto(
            id = e.id,
            amount = e.amount,
            merchant = e.merchant,
            date = e.date,
            categoryId = e.categoryId,
            rawSms = e.rawSms,
            dedupKey = e.dedupKey,
            ignored = e.ignored
        )
    }
}
