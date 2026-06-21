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
    fun testMerchantTrimmedAtViaUpi() {
        // UPI debits append a ref after "via UPI"; the saved name keeps up to and including
        // "via UPI" and drops the trailing ref number.
        val sms = "Rs.100.00 debited to John Doe via UPI Ref 502912345678"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals("John Doe via UPI", txn?.merchant)
    }

    @Test
    fun testMerchantWithoutViaUpiUnchanged() {
        // No "via UPI" marker => the extracted name is preserved as-is (capped at 25 chars).
        val sms = "Rs.50.00 debited at Starbucks Coffee"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals("Starbucks Coffee", txn?.merchant)
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
    fun testRuleLabelOverridesMerchant() {
        // A NACH SIP debit has no usable merchant text; a rule label names it ("MF SIP").
        val categoriesWithInvest = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(CategoryRule(id = 1, keyword = "groww invest", categoryId = 7L, label = "MF SIP"))
        val sms = "BOI - Rs 4000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - GROWW INVEST TEC ON 03-06-2026. Avl Bal 32902.85"

        val txn = SmsParser.parseTransaction(sms, rules, categoriesWithInvest)

        assertNotNull(txn)
        assertEquals(4000.0, txn?.amount)
        assertEquals(7L, txn?.categoryId)          // Investment
        assertEquals("MF SIP", txn?.merchant)      // rule label wins over auto-extraction
    }

    @Test
    fun testRuleWithoutLabelKeepsExtractedMerchant() {
        // A rule with no label only re-categorizes; the auto-extracted merchant is preserved.
        val rules = listOf(CategoryRule(id = 1, keyword = "amazon", categoryId = 2L, label = null))
        val sms = "Rs 1500.0 spent at Amazon on 10-Feb-24."

        val txn = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(txn)
        assertEquals(2L, txn?.categoryId)
        // No label => merchant comes from SMS extraction, not from the rule.
        assertEquals(true, txn?.merchant?.contains("Amazon", ignoreCase = true))
    }

    @Test
    fun testCategorizationForReturnsLabel() {
        // The retro-apply path surfaces the matched rule's label so it can refresh the name.
        val categoriesWithInvest = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(CategoryRule(id = 1, keyword = "groww invest", categoryId = 7L, label = "MF SIP"))
        val sms = "BOI - Rs 4000.00 Debited (NACH) - NACH DR INW - GROWW INVEST TEC"

        val result = SmsParser.categorizationFor(sms, rules, categoriesWithInvest)

        assertEquals(7L, result.categoryId)
        assertEquals("MF SIP", result.label)
    }

    @Test
    fun testFromRuleFlagDistinguishesRuleMatchFromFallback() {
        // reapplyRules relies on fromRule: only a genuine rule match may re-categorize a row;
        // the keyword fallback (fromRule = false) must never overwrite an existing category.
        val rules = listOf(CategoryRule(id = 1, keyword = "amazon", categoryId = 2L, label = null))

        // A matching rule => fromRule true.
        val matched = SmsParser.categorizationFor("Rs 1500.0 spent at Amazon", rules, testCategories)
        assertEquals(true, matched.fromRule)

        // No rule matches "LIC premium" => keyword fallback resolves Others with fromRule false.
        val fallback = SmsParser.categorizationFor("Rs 2000.0 debited for LIC premium", rules, testCategories)
        assertEquals(false, fallback.fromRule)
    }

    @Test
    fun testMutualFundAllotmentConfirmationSkipped() {
        // AMC allotment confirmation — informational, the real debit is a separate bank SMS.
        // "purchase request" would otherwise trip the debit check.
        val sms = "Dear Investor, Your purchase request dated 02/06/2026 in scheme MOMF Midcap " +
            "Fund - Direct Plan Growth under the Folio No 1234567890 for Rs. 3849.81 is processed " +
            "and 36.443 units are allotted in demat mode @ NAV Rs. 105.639. Please contact you DP " +
            "for any queries on your demat account. Motilal Oswal MF."
        assertNull(SmsParser.parseTransaction(sms, emptyList(), testCategories))
    }

    @Test
    fun testNachInvestmentDebitStillParsed() {
        // The bank NACH debit (the actual money outflow) must STILL be recorded — it has none
        // of the allotment/NAV/folio/demat confirmation words.
        val sms = "BOI - Rs 4000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - GROWW INVEST TEC ON 03-06-2026. Avl Bal 32902.85"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(4000.0, txn?.amount)
    }

    @Test
    fun testMultiKeywordRuleRequiresAllKeywords() {
        // "nach, groww invest" (AND) matches only the Groww SMS, not the Indian Clearing one.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "nach, groww invest", categoryId = 7L, label = "MF SIP"),
            CategoryRule(id = 2, keyword = "nach, indian clearing", categoryId = 7L, label = "MF SIP")
        )
        val groww = "BOI - Rs 4000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - GROWW INVEST TEC ON 03-06-2026. Avl Bal 32902.85"
        val clearing = "BOI - Rs 6000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - Indian Clearing ON 03-06-2026. Avl Bal 36902.85"

        val growwTxn = SmsParser.parseTransaction(groww, rules, cats)
        assertNotNull(growwTxn)
        assertEquals(7L, growwTxn?.categoryId)
        assertEquals("MF SIP", growwTxn?.merchant)

        val clearingTxn = SmsParser.parseTransaction(clearing, rules, cats)
        assertNotNull(clearingTxn)
        assertEquals(7L, clearingTxn?.categoryId)   // matched by rule 2, not rule 1
        assertEquals("MF SIP", clearingTxn?.merchant)
    }

    @Test
    fun testMultiKeywordRuleDoesNotMatchUnrelatedNach() {
        // A generic NACH debit (no "groww invest") must NOT be caught by the multi-keyword rule.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(CategoryRule(id = 1, keyword = "nach, groww invest", categoryId = 7L, label = "MF SIP"))
        val sms = "BOI - Rs 1200.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - LIC PREMIUM ON 03-06-2026."

        val txn = SmsParser.parseTransaction(sms, rules, cats)
        assertNotNull(txn)                          // still a valid debit...
        assertEquals(5L, txn?.categoryId)           // ...but falls through to Others, not Investment
        assertEquals(false, txn?.merchant == "MF SIP") // the rule's label was NOT applied
    }

    @Test
    fun testMostSpecificRuleWins() {
        // A generic single-keyword rule and a specific multi-keyword rule both "could" apply;
        // the more specific (more keywords) one must win regardless of rule order.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "nach", categoryId = 4L),                          // generic -> Bills
            CategoryRule(id = 2, keyword = "nach, groww invest", categoryId = 7L, label = "MF SIP") // specific -> Investment
        )
        val groww = "BOI - Rs 4000.00 Debited (NACH) - NACH DR INW - GROWW INVEST TEC"

        val txn = SmsParser.parseTransaction(groww, rules, cats)
        assertNotNull(txn)
        assertEquals(7L, txn?.categoryId)           // Investment wins over Bills
        assertEquals("MF SIP", txn?.merchant)
    }

    @Test
    fun testSingleKeywordRuleStillWorks() {
        // Backward compatibility: a plain single keyword behaves exactly as before.
        val rules = listOf(CategoryRule(id = 1, keyword = "swiggy", categoryId = 1L))
        val sms = "Rs.500.00 debited from A/c **1234 on 01-Jan-24 to VPA Swiggy"

        val txn = SmsParser.parseTransaction(sms, rules, testCategories)
        assertNotNull(txn)
        assertEquals(1L, txn?.categoryId)
    }

    @Test
    fun testOrRuleMatchesBothAlternatives() {
        // ONE rule with '|' alternatives covers both NACH variants → same category + label.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "nach, groww invest | nach, indian clearing", categoryId = 7L, label = "MF SIP")
        )
        val groww = "BOI - Rs 4000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - GROWW INVEST TEC ON 03-06-2026. Avl Bal 32902.85"
        val clearing = "BOI - Rs 6000.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - Indian Clearing ON 03-06-2026. Avl Bal 36902.85"

        listOf(groww, clearing).forEach { sms ->
            val txn = SmsParser.parseTransaction(sms, rules, cats)
            assertNotNull(txn)
            assertEquals(7L, txn?.categoryId)
            assertEquals("MF SIP", txn?.merchant)
        }
    }

    @Test
    fun testOrRuleDoesNotMatchUnrelatedMessage() {
        // Neither alternative fully matches a generic NACH debit → falls through to Others.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "nach, groww invest | nach, indian clearing", categoryId = 7L, label = "MF SIP")
        )
        val sms = "BOI - Rs 1200.00 has been Debited (NACH) in your account XXXX1234 - NACH DR INW - LIC PREMIUM ON 03-06-2026."

        val txn = SmsParser.parseTransaction(sms, rules, cats)
        assertNotNull(txn)
        assertEquals(5L, txn?.categoryId)              // Others, not Investment
        assertEquals(false, txn?.merchant == "MF SIP") // label not applied
    }

    @Test
    fun testOrRuleSpecificityJudgedPerMatchedGroup() {
        // Generic "nach" rule (Bills) vs OR-rule whose matched group has 2 keywords:
        // the OR-rule wins where one of its groups matches; the generic rule wins otherwise.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "nach", categoryId = 4L),  // generic -> Bills
            CategoryRule(id = 2, keyword = "nach, groww invest | nach, indian clearing", categoryId = 7L, label = "MF SIP")
        )
        val groww = "BOI - Rs 4000.00 Debited (NACH) - NACH DR INW - GROWW INVEST TEC"
        val lic   = "BOI - Rs 1200.00 Debited (NACH) - NACH DR INW - LIC PREMIUM"

        val growwTxn = SmsParser.parseTransaction(groww, rules, cats)
        assertEquals(7L, growwTxn?.categoryId)          // specific group (2 kw) beats generic (1 kw)
        assertEquals("MF SIP", growwTxn?.merchant)

        val licTxn = SmsParser.parseTransaction(lic, rules, cats)
        assertEquals(4L, licTxn?.categoryId)            // only the generic rule matches
    }

    @Test
    fun testOrRuleIgnoresEmptyAlternatives() {
        // A trailing '|' or empty group must not become a match-everything alternative.
        val rules = listOf(CategoryRule(id = 1, keyword = "swiggy |", categoryId = 1L))
        val unrelated = "Rs 1500.0 spent at Amazon on 10-Feb-24."

        val txn = SmsParser.parseTransaction(unrelated, rules, testCategories)
        assertNotNull(txn)
        assertEquals(2L, txn?.categoryId) // falls through to keyword fallback (Shopping), not the rule
    }

    @Test
    fun testUncategorizedFlagSetWhenFallsToOthers() {
        // No rule and no keyword match => lands in Others => should flag for a "create rule" nudge.
        val sms = "Rs.500.00 debited at SomeRandomVendor on 01-Jan-24"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(5L, txn?.categoryId)          // Others
        assertEquals(true, txn?.uncategorized)
    }

    @Test
    fun testUncategorizedFlagClearWhenRuleMatches() {
        // A user rule placed it => not uncategorized, even if the rule maps to Others.
        val rules = listOf(CategoryRule(id = 1, keyword = "somerandomvendor", categoryId = 5L))
        val sms = "Rs.500.00 debited at SomeRandomVendor on 01-Jan-24"
        val txn = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(txn)
        assertEquals(false, txn?.uncategorized)
    }

    @Test
    fun testUncategorizedFlagClearWhenKeywordFallbackCategorizes() {
        // The built-in keyword fallback put it in Food (not Others) => no nudge needed.
        val sms = "Rs.500.00 debited from A/c **1234 to VPA Swiggy"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(1L, txn?.categoryId)          // Food via keyword fallback
        assertEquals(false, txn?.uncategorized)
    }

    @Test
    fun testOtpIgnored() {
        val sms = "Your OTP is 123456. Do not share this with anyone."
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNull(transaction)
    }
}