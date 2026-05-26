package com.example.xpense.sms

import com.example.xpense.data.model.Category
import java.util.regex.Pattern

object SmsParser {
    private val AMOUNT_PATTERN = Pattern.compile("(?i)(?:Rs\\.?|INR|MRP)\\s?([\\d,]+\\.?\\d{0,2})")
    private val DEBIT_PATTERN = Pattern.compile("(?i)\\b(debited|spent|paid|sent|transferred|deducted)\\b")
    
    fun parseTransaction(smsBody: String): TransactionDetails? {
        val isDebit = DEBIT_PATTERN.matcher(smsBody).find()
        if (!isDebit) return null

        val amountMatcher = AMOUNT_PATTERN.matcher(smsBody)
        if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1)?.replace(",", "") ?: return null
            val amount = amountStr.toDoubleOrNull() ?: return null
            
            val merchant = extractMerchant(smsBody)
            val category = categorize(merchant, smsBody)
            
            return TransactionDetails(amount, merchant, category)
        }
        return null
    }

    private fun extractMerchant(smsBody: String): String {
        val merchantPattern = Pattern.compile("(?i)(?:at|to|in\\*|spent\\son)\\s(?:VPA\\s)?([A-Za-z0-9\\s\\.\\-\\*]{3,25})")
        val matcher = merchantPattern.matcher(smsBody)
        return if (matcher.find()) {
            matcher.group(1)?.trim() ?: "Unknown Merchant"
        } else {
            "Unknown Merchant"
        }
    }

    private fun categorize(merchant: String, smsBody: String): Category {
        val text = (merchant + " " + smsBody).lowercase()
        return when {
            text.contains("swiggy") || text.contains("zomato") || text.contains("restaurant") || text.contains("food") || text.contains("eat") -> Category.FOOD
            text.contains("amazon") || text.contains("flipkart") || text.contains("myntra") || text.contains("shopping") || text.contains("mall") -> Category.SHOPPING
            text.contains("uber") || text.contains("ola") || text.contains("metro") || text.contains("fuel") || text.contains("petrol") || text.contains("travel") -> Category.TRANSPORT
            text.contains("electricity") || text.contains("recharge") || text.contains("bill") || text.contains("jio") || text.contains("airtel") -> Category.BILLS
            text.contains("hospital") || text.contains("pharmacy") || text.contains("medical") || text.contains("health") -> Category.HEALTH
            text.contains("netflix") || text.contains("hotstar") || text.contains("cinema") || text.contains("movie") || text.contains("entertainment") -> Category.ENTERTAINMENT
            else -> Category.OTHERS
        }
    }

    data class TransactionDetails(
        val amount: Double,
        val merchant: String,
        val category: Category
    )
}