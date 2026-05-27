package com.example.xpense.sms

import android.util.Log
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Category
import java.util.regex.Pattern

object SmsParser {
    private const val TAG = "XpenseSmsParser"

    // Only reject definitive OTP/security messages — NOT "code" standalone (breaks UPI ref codes)
    private val SPAM_PATTERN = Pattern.compile(
        "(?i)\\b(OTP|one.time.password|verification code|login code|login otp|secret pin|entered wrong pin)\\b"
    )

    // Debit action keywords — added "debit" (noun form) and "withdrawal"
    private val DEBIT_PATTERN = Pattern.compile(
        "(?i)\\b(debited|debit|spent|paid|sent|transferred|deducted|withdrawn|withdrawal|purchase|charged)\\b"
    )

    // Credit-only keywords — only used to reject when NO debit keyword is also present
    private val CREDIT_PATTERN = Pattern.compile(
        "(?i)\\b(credited|received|deposited|refunded|cashback|reversal|reversed)\\b"
    )

    // Matches "Rs.500", "Rs 500", "INR500", "INR 500", "Amt 500", "Rs.1,500.00"
    private val AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:Rs\\.?|INR|Amt)\\s?([\\d,]+\\.?\\d{0,2})"
    )

    fun parseTransaction(
        smsBody: String,
        rules: List<CategoryRule> = emptyList(),
        categories: List<Category> = emptyList()
    ): TransactionDetails? {
        val lowerBody = smsBody.lowercase()

        if (SPAM_PATTERN.matcher(lowerBody).find()) {
            Log.d(TAG, "SKIP (spam/otp): ${smsBody.take(80)}")
            return null
        }

        val hasDebit = DEBIT_PATTERN.matcher(lowerBody).find()
        if (!hasDebit) {
            Log.d(TAG, "SKIP (no debit keyword): ${smsBody.take(80)}")
            return null
        }

        // If the SMS has only credit action words and no debit word — it's a credit/refund SMS.
        // If debit word IS present (checked above), we allow it even if "credited" also appears
        // (balance notifications like "Avl Bal: Rs.4500 (Cr)" are common in debit SMS).
        // But if the message primarily says "credited" without any debit keyword we already
        // returned null above — so nothing extra needed here.

        val amountMatcher = AMOUNT_PATTERN.matcher(smsBody)
        if (!amountMatcher.find()) {
            Log.d(TAG, "SKIP (no amount): ${smsBody.take(80)}")
            return null
        }

        val amountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
        val amount = amountStr.toDoubleOrNull() ?: return null
        if (amount <= 0) {
            Log.d(TAG, "SKIP (zero amount): ${smsBody.take(80)}")
            return null
        }

        val merchant = extractMerchant(smsBody)
        val categoryId = categorize(merchant, smsBody, rules, categories)

        Log.d(TAG, "PARSED: amount=₹$amount merchant=$merchant categoryId=$categoryId")
        return TransactionDetails(amount, merchant, categoryId)
    }

    private fun extractMerchant(smsBody: String): String {
        val patterns = listOf(
            // "at Swiggy", "to Zomato", "at VPA abc@upi"
            Pattern.compile("(?i)(?:at|to|in\\*|for)\\s(?:VPA\\s)?([A-Za-z0-9][A-Za-z0-9\\s.\\-@]{2,24})"),
            // "spent on Swiggy"
            Pattern.compile("(?i)spent\\s+on\\s+([A-Za-z0-9][A-Za-z0-9\\s.]{2,24})")
        )
        for (p in patterns) {
            val m = p.matcher(smsBody)
            if (m.find()) {
                val candidate = m.group(1)?.trim()?.take(25) ?: continue
                // Reject if it looks like a bank/account phrase
                if (candidate.lowercase().contains("your") ||
                    candidate.lowercase().contains("account") ||
                    candidate.lowercase().contains("bank")
                ) continue
                return candidate
            }
        }
        return "Unknown"
    }

    private fun categorize(
        merchant: String,
        smsBody: String,
        rules: List<CategoryRule>,
        categories: List<Category>
    ): Long {
        val text = (merchant + " " + smsBody).lowercase()

        rules.forEach { rule ->
            if (text.contains(rule.keyword.lowercase())) return rule.categoryId
        }

        val categoryName = when {
            text.containsAny("swiggy", "zomato", "restaurant", "food", "cafe", "pizza", "burger") -> "Food"
            text.containsAny("amazon", "flipkart", "myntra", "shopping", "mall", "store") -> "Shopping"
            text.containsAny("uber", "ola", "rapido", "fuel", "petrol", "diesel", "metro", "bus") -> "Transport"
            text.containsAny("bill", "recharge", "electricity", "water", "gas", "broadband", "wifi") -> "Bills"
            text.containsAny("hospital", "pharmacy", "medicine", "doctor", "clinic", "health") -> "Health"
            text.containsAny("netflix", "spotify", "prime", "hotstar", "movie", "game") -> "Entertainment"
            else -> "Others"
        }

        return categories.find { it.name.equals(categoryName, ignoreCase = true) }?.id
            ?: categories.find { it.name.equals("Others", ignoreCase = true) }?.id
            ?: 0L
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }

    data class TransactionDetails(
        val amount: Double,
        val merchant: String,
        val categoryId: Long
    )
}
