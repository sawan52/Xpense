package com.example.xpense.ui.utils

import java.math.RoundingMode
import java.text.DecimalFormat

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
}
