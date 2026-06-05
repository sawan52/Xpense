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

    // Explicit debit/spend verbs — strong signal that money left the account
    private val DEBIT_PATTERN = Pattern.compile(
        "(?i)\\b(debited|debit|spent|paid|sent|transferred|deducted|withdrawn|withdrawal|purchase|charged)\\b"
    )

    // Generic transaction-alert words. Many bank/card alerts (e.g. HDFC card UPI alerts)
    // never use an explicit debit verb — they just say "Txn Rs.X On Card... At <merchant>".
    // Treated as a spend UNLESS a credit keyword is present (see CREDIT_PATTERN below).
    private val TXN_PATTERN = Pattern.compile(
        "(?i)\\b(txn|transaction)\\b"
    )

    // Credit-only keywords — used to reject credits/refunds that lack an explicit debit verb
    private val CREDIT_PATTERN = Pattern.compile(
        "(?i)\\b(credited|received|deposited|refunded|cashback|reversal|reversed)\\b"
    )

    // Credit-card BILL PAYMENT — money moving FROM a bank account TO a credit card to clear the
    // outstanding balance. These must be skipped: every spend on the card was already recorded
    // from its own transaction SMS, so counting the bill payment too would double-count spending.
    // Keyed on the card being the PAYMENT DESTINATION ("credit card bill", "payment towards
    // credit card") — NOT the bare word "card", which legitimate card-spend alerts also contain
    // (e.g. "Txn Rs.X On HDFC Bank Card At <merchant>").
    private val CARD_PAYMENT_PATTERN = Pattern.compile(
        "(?i)(" +
            "(credit\\s*card|\\bcc\\b)\\s*(bill|payment)" +                          // "credit card bill", "cc payment"
            "|card\\s*bill" +                                                        // "card bill"
            "|(payment|paid|pay)\\s+(towards|to|of|for)\\b.*(credit\\s*card|\\bcc\\b)" + // "payment of ... credit card"
            "|towards\\b.*(credit\\s*card|\\bcc\\b)\\s*(bill|payment|outstanding|dues?)" + // "towards your credit card bill"
        ")"
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

        // Credit-card bill payments are already covered by the individual card-spend SMS; recording
        // the bill payment too would double-count. Skip before the debit check, since these SMS do
        // carry debit verbs ("debited"/"paid").
        if (CARD_PAYMENT_PATTERN.matcher(lowerBody).find()) {
            Log.d(TAG, "SKIP (credit card bill payment): ${smsBody.take(80)}")
            return null
        }

        val hasDebit = DEBIT_PATTERN.matcher(lowerBody).find()
        val hasTxn = TXN_PATTERN.matcher(lowerBody).find()
        if (!hasDebit && !hasTxn) {
            Log.d(TAG, "SKIP (no debit/txn keyword): ${smsBody.take(80)}")
            return null
        }

        // Reject credits/refunds. A credit keyword combined with NO explicit debit verb
        // means the money came IN (e.g. "Txn Rs.X credited", refunds, cashback). When an
        // explicit debit verb IS present we still allow it, because a trailing balance
        // notice like "Avl Bal: Rs.4500 (Cr)" is common inside genuine debit SMS.
        val hasCredit = CREDIT_PATTERN.matcher(lowerBody).find()
        if (hasCredit && !hasDebit) {
            Log.d(TAG, "SKIP (credit/refund): ${smsBody.take(80)}")
            return null
        }

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

    /**
     * Re-evaluate which category an already-saved SMS belongs to, using the current rules.
     * Used to retro-apply rules to transactions that were imported before the rule existed.
     * Returns the resolved categoryId (0L only if no categories exist at all).
     */
    fun categorizeFor(
        smsBody: String,
        rules: List<CategoryRule>,
        categories: List<Category>
    ): Long = categorize(extractMerchant(smsBody), smsBody, rules, categories)

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
                // Stop at a line break so a merchant on its own line doesn't swallow the next line.
                val candidate = m.group(1)?.substringBefore("\n")?.trim()?.take(25) ?: continue
                if (candidate.isEmpty()) continue
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
