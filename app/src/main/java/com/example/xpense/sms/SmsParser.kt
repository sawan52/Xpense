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

    // Mutual-fund / demat CONFIRMATION & statement SMS — "units allotted", NAV, folio, demat.
    // These are informational acknowledgements from the AMC/broker; the actual money debit arrives
    // as a separate bank/NACH SMS and is already recorded. Counting the allotment confirmation too
    // would double-count the investment. The trigger words never appear in a normal spend SMS, so
    // this does NOT block genuine debits (incl. "NACH DR INW - GROWW INVEST" bank debits).
    private val INVESTMENT_CONFIRMATION_PATTERN = Pattern.compile(
        "(?i)(" +
            "units?\\s+(are\\s+|were\\s+)?(allotted|allotment)" +   // "36.443 units are allotted"
            "|allotment\\s+of\\b" +
            "|folio\\s*(no|number|#)" +                            // "Folio No 1234567890"
            "|@\\s*nav\\b|\\bnav\\s+(of\\s+)?(rs|inr)" +            // "@ NAV Rs. 105.639"
            "|demat\\s+(mode|account)" +
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

        // Mutual-fund/demat allotment confirmations echo a debit already captured from the bank
        // SMS; "purchase request ... processed" would otherwise trip the debit check below.
        if (INVESTMENT_CONFIRMATION_PATTERN.matcher(lowerBody).find()) {
            Log.d(TAG, "SKIP (investment confirmation): ${smsBody.take(80)}")
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

        val extractedMerchant = extractMerchant(smsBody)
        val result = categorize(extractedMerchant, smsBody, rules, categories)
        // A rule's label (when set) is a meaningful name for the txn; it wins over the
        // auto-extracted merchant, which is often "Unknown" for NACH/forex SMS.
        val merchant = result.label?.takeIf { it.isNotBlank() } ?: extractedMerchant

        Log.d(TAG, "PARSED: amount=₹$amount merchant=$merchant categoryId=${result.categoryId}")
        return TransactionDetails(amount, merchant, result.categoryId)
    }

    /**
     * Re-evaluate an already-saved SMS against the current rules. Used to retro-apply rules to
     * transactions imported before a rule existed. Returns both the resolved category and the
     * matched rule's label (so the caller can refresh the display name too).
     */
    fun categorizationFor(
        smsBody: String,
        rules: List<CategoryRule>,
        categories: List<Category>
    ): Categorization = categorize(extractMerchant(smsBody), smsBody, rules, categories)

    /** Convenience accessor kept for callers that only need the category id. */
    fun categorizeFor(
        smsBody: String,
        rules: List<CategoryRule>,
        categories: List<Category>
    ): Long = categorizationFor(smsBody, rules, categories).categoryId

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
    ): Categorization {
        val text = (merchant + " " + smsBody).lowercase()

        // A rule's keyword field may hold several comma-separated keywords; the rule matches only
        // when EVERY keyword is present in the SMS (AND semantics). Evaluate the most specific
        // rules first (most keywords) so e.g. "nach, groww invest" wins over a generic "nach".
        rules.sortedByDescending { keywordsOf(it).size }.forEach { rule ->
            val keywords = keywordsOf(rule)
            if (keywords.isNotEmpty() && keywords.all { text.contains(it) }) {
                return Categorization(rule.categoryId, rule.label)
            }
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

        // Keyword fallback resolves only a category — no custom label.
        val categoryId = categories.find { it.name.equals(categoryName, ignoreCase = true) }?.id
            ?: categories.find { it.name.equals("Others", ignoreCase = true) }?.id
            ?: 0L
        return Categorization(categoryId, null)
    }

    private fun String.containsAny(vararg terms: String) = terms.any { this.contains(it) }

    /** Splits a rule's (possibly comma-separated) keyword field into trimmed, lowercased terms. */
    private fun keywordsOf(rule: CategoryRule): List<String> =
        rule.keyword.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Result of categorization: the resolved category plus an optional rule-supplied display name. */
    data class Categorization(
        val categoryId: Long,
        val label: String?
    )

    data class TransactionDetails(
        val amount: Double,
        val merchant: String,
        val categoryId: Long
    )
}
