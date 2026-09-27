package com.example.xpense.ui

import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Expense

// Pure list logic behind the Filter & sort sheet and the global search (no DB/IO, unit-tested).

enum class TxSort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), HIGHEST("Highest"), LOWEST("Lowest") }

enum class AmountRange(val label: String) {
    ANY("Any"), UNDER_500("< ₹500"), MID("₹500 to 5k"), OVER_5K("> ₹5k");

    fun matches(amount: Double): Boolean = when (this) {
        ANY -> true
        UNDER_500 -> amount < 500
        MID -> amount in 500.0..5000.0
        OVER_5K -> amount > 5000
    }
}

enum class TxSource(val label: String) { ALL("All"), SMS("SMS"), MANUAL("Manual") }

data class TxFilter(
    val sort: TxSort = TxSort.NEWEST,
    val categoryIds: Set<Long> = emptySet(),
    val amount: AmountRange = AmountRange.ANY,
    val source: TxSource = TxSource.ALL
) {
    /** Number of removable chips the filter shows (one per category + one per non-default choice). */
    val activeCount: Int
        get() = categoryIds.size +
            (if (sort != TxSort.NEWEST) 1 else 0) +
            (if (amount != AmountRange.ANY) 1 else 0) +
            (if (source != TxSource.ALL) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** Manual rows carry a sentinel instead of an SMS body ("Manual Entry" / "Manual Update"). */
fun isManualExpense(e: Expense): Boolean = e.rawSms.startsWith("Manual")

fun applyTxFilter(list: List<ExpenseWithCategory>, f: TxFilter): List<ExpenseWithCategory> {
    val kept = list.filter { row ->
        (f.categoryIds.isEmpty() || row.expense.categoryId in f.categoryIds) &&
            f.amount.matches(row.expense.amount) &&
            when (f.source) {
                TxSource.ALL -> true
                TxSource.SMS -> !isManualExpense(row.expense)
                TxSource.MANUAL -> isManualExpense(row.expense)
            }
    }
    return when (f.sort) {
        TxSort.NEWEST -> kept.sortedByDescending { it.expense.date }
        TxSort.OLDEST -> kept.sortedBy { it.expense.date }
        TxSort.HIGHEST -> kept.sortedByDescending { it.expense.amount }
        TxSort.LOWEST -> kept.sortedBy { it.expense.amount }
    }
}

enum class SearchScope(val label: String) { ALL("All"), TRANSACTIONS("Transactions"), CATEGORIES("Categories"), RULES("Rules") }

data class SearchResults(
    val transactions: List<ExpenseWithCategory> = emptyList(),
    val categories: List<Category> = emptyList(),
    val rules: List<CategoryRule> = emptyList()
) {
    val total: Int get() = transactions.size + categories.size + rules.size
    fun count(scope: SearchScope): Int = when (scope) {
        SearchScope.ALL -> total
        SearchScope.TRANSACTIONS -> transactions.size
        SearchScope.CATEGORIES -> categories.size
        SearchScope.RULES -> rules.size
    }
}

/**
 * Case-insensitive substring search across transactions (merchant, category, note, amount),
 * categories (name) and rules (label, keyword). A blank query matches nothing. Transactions keep
 * their input order, which callers pass newest-first.
 */
fun searchAll(
    query: String,
    expenses: List<ExpenseWithCategory>,
    categories: List<Category>,
    rules: List<CategoryRule>
): SearchResults {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return SearchResults()
    val digits = q.replace(",", "").removePrefix("₹")
    val isNumber = digits.isNotEmpty() && digits.all { it.isDigit() || it == '.' }
    val tx = expenses.filter { row ->
        row.expense.merchant.lowercase().contains(q) ||
            row.category.name.lowercase().contains(q) ||
            row.expense.note?.lowercase()?.contains(q) == true ||
            (isNumber && amountText(row.expense.amount).startsWith(digits))
    }
    val cats = categories.filter { it.name.lowercase().contains(q) }
    val rl = rules.filter { it.label?.lowercase()?.contains(q) == true || it.keyword.lowercase().contains(q) }
    return SearchResults(tx, cats, rl)
}

private fun amountText(a: Double): String =
    if (a % 1.0 == 0.0) a.toLong().toString() else a.toString()

/** Where [query] first occurs in [text] (case-insensitive), for highlighting; null when absent. */
fun matchRange(text: String, query: String): IntRange? {
    val q = query.trim()
    if (q.isEmpty()) return null
    val i = text.indexOf(q, ignoreCase = true)
    return if (i < 0) null else i until i + q.length
}

/**
 * A stored amount as editable text: at most 2 decimals, no trailing zeros, never scientific
 * notation (Double.toString gives "1.0E7" for ₹1 crore). 41277.0 → "41277", 345.5 → "345.5".
 */
fun formatAmountInput(amount: Double): String =
    java.math.BigDecimal.valueOf(amount).setScale(2, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()

/**
 * Cleans typed amount text: digits and one '.', at most 2 decimals and 9 whole digits. Returns
 * null when the edit should be rejected (the field then keeps its previous value).
 */
fun sanitizeAmountInput(raw: String): String? {
    val clean = raw.filter { it.isDigit() || it == '.' }
    if (clean.count { it == '.' } > 1) return null
    val whole = clean.substringBefore('.')
    val frac = if ('.' in clean) clean.substringAfter('.') else ""
    if (whole.length > 9 || frac.length > 2) return null
    return clean
}

/** Grouped display text plus, for every cursor offset in the raw text, its offset in [text]. */
class GroupedAmount(val text: String, val rawToGrouped: IntArray)

/**
 * Indian digit grouping for amount *input*: the last three whole digits, then every two
 * (1234567.89 → "12,34,567.89"). Works on partial input too ("1234." → "1,234."), and keeps the
 * decimal part untouched. Returns an offset map so a text field can keep the cursor in place.
 */
fun indianGrouping(raw: String): GroupedAmount {
    val dot = raw.indexOf('.')
    val wholeLen = if (dot < 0) raw.length else dot
    val out = StringBuilder()
    val map = IntArray(raw.length + 1)
    for (i in raw.indices) {
        // Digits left of this one in the whole part decide whether a comma goes before it.
        val remaining = wholeLen - i
        if (i in 1 until wholeLen && (remaining == 3 || (remaining > 3 && (remaining - 3) % 2 == 0))) out.append(',')
        map[i] = out.length
        out.append(raw[i])
    }
    map[raw.length] = out.length
    return GroupedAmount(out.toString(), map)
}
