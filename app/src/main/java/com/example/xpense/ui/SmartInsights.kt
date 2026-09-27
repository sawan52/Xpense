package com.example.xpense.ui

import com.example.xpense.ui.utils.CurrencyUtils
import kotlin.math.abs
import kotlin.math.roundToInt

enum class InsightKind { CATEGORY_MOVE, TOTAL_DOWN, TOTAL_UP, TOP_SHARE, RECURRING, BIGGEST }

/** One smart-insight card. [categoryId] lets the UI tint it and link to that category. */
data class Insight(val kind: InsightKind, val title: String, val subtitle: String, val categoryId: Long? = null)

/**
 * Builds up to four insight cards from a month's rows and the previous month's (both already
 * free of archived rows). Pure so it can be unit-tested; ordering is most-actionable first.
 */
fun buildSmartInsights(month: List<ExpenseWithCategory>, prev: List<ExpenseWithCategory>): List<Insight> {
    if (month.isEmpty()) return emptyList()
    val out = mutableListOf<Insight>()
    val total = month.sumOf { it.expense.amount }
    val prevTotal = prev.sumOf { it.expense.amount }
    fun inr(v: Double) = CurrencyUtils.rupees(v)

    // 1. The category whose spend moved the most vs last month, and who drove it.
    if (prev.isNotEmpty()) {
        val cur = month.groupBy { it.category.id }
        val old = prev.groupBy { it.category.id }.mapValues { (_, l) -> l.sumOf { it.expense.amount } }
        val mover = cur.map { (id, rows) -> Triple(id, rows, rows.sumOf { it.expense.amount } - (old[id] ?: 0.0)) }
            .filter { (id, _, _) -> (old[id] ?: 0.0) > 0 }
            .maxByOrNull { abs(it.third) }
        if (mover != null && abs(mover.third) >= 1) {
            val (id, rows, diff) = mover
            val before = old[id] ?: 0.0
            val pct = (abs(diff) / before * 100).roundToInt()
            val name = rows.first().category.name
            val drivers = rows.groupBy { it.expense.merchant }
                .mapValues { (_, l) -> l.sumOf { it.expense.amount } }
                .entries.sortedByDescending { it.value }.take(2).map { it.key }
            val who = if (diff > 0 && drivers.isNotEmpty()) " — mostly ${drivers.joinToString(" & ")}" else ""
            out += Insight(
                InsightKind.CATEGORY_MOVE,
                "$name spend ${if (diff > 0) "up" else "down"} $pct%$who",
                "${inr(rows.sumOf { it.expense.amount })} this month vs ${inr(before)} last month",
                id
            )
        }
    }

    // 2. Month total vs last month.
    if (prevTotal > 0) {
        val pct = ((total - prevTotal) / prevTotal * 100).roundToInt()
        val less = total < prevTotal
        out += Insight(
            if (less) InsightKind.TOTAL_DOWN else InsightKind.TOTAL_UP,
            if (pct == 0) "Spending is flat vs last month" else "You spent ${abs(pct)}% ${if (less) "less" else "more"} than last month",
            if (less) "That's ${inr(prevTotal - total)} saved so far" else "${inr(total - prevTotal)} over last month's ${inr(prevTotal)}"
        )
    }

    // 3. Payments that repeat month over month (same merchant, same amount).
    val prevKeys = prev.map { it.expense.merchant.lowercase() to it.expense.amount }.toSet()
    val recurring = month.filter { (it.expense.merchant.lowercase() to it.expense.amount) in prevKeys }
        .distinctBy { it.expense.merchant.lowercase() }
    if (recurring.isNotEmpty()) {
        val n = recurring.size
        out += Insight(
            InsightKind.RECURRING,
            "$n recurring payment${if (n == 1) "" else "s"} this month",
            "${inr(recurring.sumOf { it.expense.amount })} · ${recurring.take(3).joinToString(", ") { it.expense.merchant }}"
        )
    }

    // 4. Share of the top category.
    val top = month.groupBy { it.category }.mapValues { (_, l) -> l }.maxByOrNull { (_, l) -> l.sumOf { it.expense.amount } }
    if (top != null && total > 0) {
        val amt = top.value.sumOf { it.expense.amount }
        out += Insight(
            InsightKind.TOP_SHARE,
            "${top.key.name} is ${(amt / total * 100).roundToInt()}% of your spend",
            "${inr(amt)} across ${top.value.size} transaction${if (top.value.size == 1) "" else "s"}",
            top.key.id
        )
    }

    // 5. Biggest single spend.
    month.maxByOrNull { it.expense.amount }?.let {
        out += Insight(InsightKind.BIGGEST, "Biggest spend: ${it.expense.merchant}", "${inr(it.expense.amount)} in ${it.category.name}", it.category.id)
    }
    return out.take(4)
}
