package com.example.xpense

import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.AmountRange
import com.example.xpense.ui.ExpenseWithCategory
import com.example.xpense.ui.TxFilter
import com.example.xpense.ui.TxSort
import com.example.xpense.ui.TxSource
import com.example.xpense.ui.applyTxFilter
import com.example.xpense.ui.matchRange
import com.example.xpense.ui.searchAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TxQueriesTest {
    private val food = Category(1, "Food", "Restaurant")
    private val bills = Category(2, "Bills", "ReceiptLong")

    private fun row(id: Long, merchant: String, amount: Double, date: Long, cat: Category, manual: Boolean = false, note: String? = null) =
        ExpenseWithCategory(
            Expense(id = id, amount = amount, merchant = merchant, date = date, categoryId = cat.id,
                rawSms = if (manual) "Manual Entry" else "Rs $amount debited", note = note),
            cat
        )

    private val rows = listOf(
        row(1, "Swiggy", 345.0, 300, food),
        row(2, "BESCOM", 1200.0, 200, bills),
        row(3, "Zomato", 6400.0, 100, food, manual = true, note = "team dinner"),
    )

    @Test fun defaultFilter_keepsAll_newestFirst() {
        assertEquals(listOf(1L, 2L, 3L), applyTxFilter(rows.reversed(), TxFilter()).map { it.expense.id })
        assertEquals(0, TxFilter().activeCount)
    }

    @Test fun sortOptions() {
        assertEquals(listOf(3L, 2L, 1L), applyTxFilter(rows, TxFilter(sort = TxSort.OLDEST)).map { it.expense.id })
        assertEquals(listOf(3L, 2L, 1L), applyTxFilter(rows, TxFilter(sort = TxSort.HIGHEST)).map { it.expense.id })
        assertEquals(listOf(1L, 2L, 3L), applyTxFilter(rows, TxFilter(sort = TxSort.LOWEST)).map { it.expense.id })
    }

    @Test fun categoryAmountAndSourceFilters() {
        assertEquals(listOf(1L, 3L), applyTxFilter(rows, TxFilter(categoryIds = setOf(1))).map { it.expense.id })
        assertEquals(listOf(1L), applyTxFilter(rows, TxFilter(amount = AmountRange.UNDER_500)).map { it.expense.id })
        assertEquals(listOf(2L), applyTxFilter(rows, TxFilter(amount = AmountRange.MID)).map { it.expense.id })
        assertEquals(listOf(3L), applyTxFilter(rows, TxFilter(amount = AmountRange.OVER_5K)).map { it.expense.id })
        assertEquals(listOf(3L), applyTxFilter(rows, TxFilter(source = TxSource.MANUAL)).map { it.expense.id })
        assertEquals(listOf(1L, 2L), applyTxFilter(rows, TxFilter(source = TxSource.SMS)).map { it.expense.id })
    }

    @Test fun activeCount_countsEachChip() {
        val f = TxFilter(sort = TxSort.HIGHEST, categoryIds = setOf(1, 2), amount = AmountRange.MID, source = TxSource.SMS)
        assertEquals(5, f.activeCount)
    }

    @Test fun search_matchesMerchantCategoryNoteAndAmount() {
        val rules = listOf(CategoryRule(1, "swiggy | instamart", 1, "Swiggy"), CategoryRule(2, "bescom", 2))
        val cats = listOf(food, bills)
        assertEquals(listOf(1L), searchAll("swig", rows, cats, rules).transactions.map { it.expense.id })
        assertEquals(1, searchAll("swig", rows, cats, rules).rules.size)
        assertEquals(listOf(1L, 3L), searchAll("FOOD", rows, cats, rules).transactions.map { it.expense.id })
        assertEquals(listOf(food), searchAll("foo", rows, cats, rules).categories)
        assertEquals(listOf(3L), searchAll("dinner", rows, cats, rules).transactions.map { it.expense.id })
        assertEquals(listOf(2L), searchAll("1,2", rows, cats, rules).transactions.map { it.expense.id })
        assertEquals(listOf(1L), searchAll("instamart", rows, cats, rules).rules.map { it.id })
        assertTrue(searchAll("   ", rows, cats, rules).total == 0)
    }

    @Test fun matchRange_isCaseInsensitive() {
        assertEquals(0..2, matchRange("Swiggy", "swi"))
        assertNull(matchRange("Swiggy", "zom"))
        assertNull(matchRange("Swiggy", " "))
    }
}
