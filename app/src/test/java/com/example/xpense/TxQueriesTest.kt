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
import com.example.xpense.ui.formatAmountInput
import com.example.xpense.ui.indianGrouping
import com.example.xpense.ui.sanitizeAmountInput
import com.example.xpense.ui.utils.CurrencyUtils
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

    @Test fun formatAmountInput_twoDecimalsNoScientific() {
        assertEquals("41277", formatAmountInput(41277.0))
        assertEquals("345.5", formatAmountInput(345.5))
        assertEquals("157043.71", formatAmountInput(157043.71))
        assertEquals("10000000", formatAmountInput(1.0E7))
        assertEquals("12.35", formatAmountInput(12.345678))
    }

    @Test fun sanitizeAmountInput_limitsDecimals() {
        assertEquals("12.34", sanitizeAmountInput("12.34"))
        assertNull(sanitizeAmountInput("12.345"))
        assertNull(sanitizeAmountInput("1.2.3"))
        assertEquals("1500", sanitizeAmountInput("1,500"))
        assertNull(sanitizeAmountInput("1234567890"))
    }

    @Test fun exact_showsPaiseOnlyWhenPresent() {
        assertEquals("₹41,277", CurrencyUtils.exact(41277.0))
        assertEquals("₹345.50", CurrencyUtils.exact(345.5))
        // Under ₹1 lakh: the JVM formatter lacks the Indian 2-digit grouping Android applies above that.
        assertEquals("₹57,043.71", CurrencyUtils.exact(57043.71))
        assertEquals("₹0.05", CurrencyUtils.exact(0.05))
    }

    @Test fun indianGrouping_groupsThreeThenTwo() {
        assertEquals("0", indianGrouping("0").text)
        assertEquals("999", indianGrouping("999").text)
        assertEquals("1,000", indianGrouping("1000").text)
        assertEquals("12,345", indianGrouping("12345").text)
        assertEquals("1,23,456", indianGrouping("123456").text)
        assertEquals("12,34,567.89", indianGrouping("1234567.89").text)
        assertEquals("12,34,56,789.99", indianGrouping("123456789.99").text)
        assertEquals("1,234.", indianGrouping("1234.").text)
        assertEquals("56.39", indianGrouping("56.39").text)
    }

    @Test fun indianGrouping_mapsCursorPastCommas() {
        val g = indianGrouping("123456") // "1,23,456"
        assertEquals(0, g.rawToGrouped[0])
        assertEquals(2, g.rawToGrouped[1]) // before "2", after the first comma
        assertEquals(5, g.rawToGrouped[3]) // before "4"
        assertEquals(8, g.rawToGrouped[6]) // end
    }
}
