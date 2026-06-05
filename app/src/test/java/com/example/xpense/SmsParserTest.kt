package com.example.xpense

import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private val testCategories = listOf(
        Category(id = 1, name = "Food", iconName = "Restaurant"),
        Category(id = 2, name = "Shopping", iconName = "ShoppingCart"),
        Category(id = 3, name = "Transport", iconName = "DirectionsCar"),
        Category(id = 4, name = "Bills", iconName = "ReceiptLong"),
        Category(id = 5, name = "Others", iconName = "Category")
    )

    @Test
    fun testDebitSmsParsing() {
        val sms = "Rs.500.00 debited from A/c **1234 on 01-Jan-24 to VPA Swiggy"
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(transaction)
        assertEquals(500.0, transaction?.amount)
        assertEquals(1L, transaction?.categoryId) // Food
    }

    @Test
    fun testCreditSmsParsing() {
        val sms = "Your A/c X1234 has been credited by Rs 2000.0 on 05-Mar-24."
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNull(transaction)
    }

    @Test
    fun testShoppingCategory() {
        val sms = "Rs 1500.0 spent at Amazon on 10-Feb-24."
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(transaction)
        assertEquals(2L, transaction?.categoryId) // Shopping
    }

    @Test
    fun testTransportCategory() {
        val sms = "INR 250.0 paid to Uber"
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(transaction)
        assertEquals(3L, transaction?.categoryId) // Transport
    }

    @Test
    fun testUserRuleOverridesDefault() {
        val rules = listOf(CategoryRule(id = 1, keyword = "netflix", categoryId = 5L))
        val sms = "INR 649.0 debited for Netflix subscription"
        val transaction = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(transaction)
        assertEquals(5L, transaction?.categoryId) // Others (mapped by rule)
    }

    @Test
    fun testHdfcCardTxnAlertParsed() {
        // HDFC card UPI alert: no explicit debit verb, signals spend with "Txn"
        val sms = """
            Txn Rs.1607.00
            On HDFC Bank Card 1234
            At poyfsnoesm.rzp@mairtel
            by UPI 123466789012
            On 01-06
            Not You?
            Call 1800180018/SMS BLOCK CC 1234 to 7312345678
        """.trimIndent()
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(transaction)
        assertEquals(1607.0, transaction?.amount)
        assertEquals("poyfsnoesm.rzp@mairtel", transaction?.merchant)
    }

    @Test
    fun testCreditTxnAlertIgnored() {
        // A "Txn" alert that is actually a credit must still be rejected
        val sms = "Txn Rs.500.00 credited to your HDFC Bank Card 1234"
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNull(transaction)
    }

    @Test
    fun testCustomCategoryWithUserRules() {
        // User creates a custom parent category "Groceries" and maps keywords to it.
        val categoriesWithCustom = testCategories + Category(id = 6, name = "Groceries", iconName = "ShoppingCart")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "blinkit", categoryId = 6),
            CategoryRule(id = 2, keyword = "zepto", categoryId = 6),
            CategoryRule(id = 3, keyword = "bigbasket", categoryId = 6)
        )

        val sms = "Rs.320.00 debited at Blinkit on 05-Jun via UPI"
        val txn = SmsParser.parseTransaction(sms, rules, categoriesWithCustom)

        assertNotNull(txn)
        assertEquals(6L, txn?.categoryId) // routed to the custom Groceries category
    }

    @Test
    fun testCategorizeForRetroApply() {
        // categorizeFor is what the "Re-apply rules" action uses on already-saved SMS.
        val categoriesWithCustom = testCategories + Category(id = 6, name = "Groceries", iconName = "ShoppingCart")
        val noRules = emptyList<CategoryRule>()
        val sms = "Rs.320.00 debited at Zepto on 05-Jun"

        // Before the rule exists it falls through to Others (id 5)...
        assertEquals(5L, SmsParser.categorizeFor(sms, noRules, categoriesWithCustom))

        // ...after the user adds a rule, the same SMS resolves to Groceries (id 6).
        val rules = listOf(CategoryRule(id = 1, keyword = "zepto", categoryId = 6))
        assertEquals(6L, SmsParser.categorizeFor(sms, rules, categoriesWithCustom))
    }

    @Test
    fun testCreditCardBillPaymentSkipped() {
        // Paying the CC bill must NOT be recorded — the individual spends were already captured.
        val samples = listOf(
            "Rs.15,000.00 has been debited from your A/c XX1234 towards payment of your HDFC Credit Card bill.",
            "Rs.15000 paid to HDFC Credit Card from a/c XX1234 via NetBanking.",
            "Thank you for paying your Credit Card bill of Rs.8,500.00.",
            "Your HDFC Bank Credit Card payment of Rs 15000 is successful.",
            "Rs.5000 debited for CC bill payment via BBPS."
        )
        samples.forEach { sms ->
            assertNull("Should skip CC bill payment: $sms",
                SmsParser.parseTransaction(sms, emptyList(), testCategories))
        }
    }

    @Test
    fun testCardSpendStillParsedNotTreatedAsBillPayment() {
        // Spending ON the card (card is the instrument, not the destination) must still record.
        val sms = """
            Txn Rs.1607.00
            On HDFC Bank Card 1234
            At poyfsnoesm.rzp@mairtel
            by UPI 123466789012
            On 01-06
            Not You?
            Call 1800180018/SMS BLOCK CC 1234 to 7312345678
        """.trimIndent()
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(transaction)
        assertEquals(1607.0, transaction?.amount)
    }

    @Test
    fun testOtpIgnored() {
        val sms = "Your OTP is 123456. Do not share this with anyone."
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNull(transaction)
    }
}