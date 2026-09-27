package com.example.xpense.ui.utils

import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Indian-style amount formatting (lakh/crore grouping): the last three digits are grouped, then
 * every two digits, e.g. 5860627.75 → "58,60,627.75". The explicit DecimalFormat patterns keep
 * this deterministic regardless of the device locale. Returns the grouped number only (no ₹ or
 * sign) so callers keep their own prefix.
 */
object CurrencyUtils {
    // "#,##,##0" → primary group of 3 (rightmost), then groups of 2. HALF_UP matches old %f output.
    private val df0 = DecimalFormat("#,##,##0").apply { roundingMode = RoundingMode.HALF_UP }
    private val df2 = DecimalFormat("#,##,##0.00").apply { roundingMode = RoundingMode.HALF_UP }

    /** decimals = 0 (rounded) or 2 (with paise). */
    fun format(amount: Double, decimals: Int = 2): String =
        (if (decimals == 0) df0 else df2).format(amount)

    /** "₹1,56,777" — the design shows whole rupees almost everywhere. */
    fun rupees(amount: Double, decimals: Int = 0): String = "₹" + format(amount, decimals)

    /** Compact ₹ amount for tight spots: 1500 → "₹1.5k", 250000 → "₹2.5L", 2.3e7 → "₹2.3Cr". */
    fun compact(v: Double, symbol: Boolean = true): String {
        val s = when {
            v >= 1_00_00_000 -> "${trimDecimal(v / 1_00_00_000, 2)}Cr"
            v >= 1_00_000    -> "${trimDecimal(v / 1_00_000, 2)}L"
            v >= 1_000       -> "${trimDecimal(v / 1_000, 1)}k"
            else             -> "${v.roundToInt()}"
        }
        return if (symbol) "₹$s" else s
    }

    private fun trimDecimal(x: Double, places: Int): String {
        val f = if (places == 2) 100 else 10
        val rounded = (x * f).roundToInt().toDouble() / f
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
