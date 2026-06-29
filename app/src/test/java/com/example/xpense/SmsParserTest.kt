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
    fun testAxisCardMerchantOnOwnLine() {
        // Axis card alert: the merchant sits on its own line above "Avl Limit", with no at/to
        // preposition. The bare "to 9999999999" in the fraud-report line must NOT be grabbed as
        // the merchant; the real merchant (GOOGLEPLAY) must be extracted instead.
        val sms = """
            Spent INR 299
            Axis Bank Card no. XX1234
            28-06-26 08:20:36 IST
            GOOGLEPLAY
            Avl Limit: INR 123456
            Not you? SMS BLOCK 1234 to 9999999999
        """.trimIndent()
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(299.0, txn?.amount)
        assertEquals("GOOGLEPLAY", txn?.merchant)
    }

    @Test
    fun testAxisCardAggregatorMerchantExtracted() {
        // Aggregator merchants arrive prefixed (PTM* = Paytm, RAZ* = Razorpay) on their own line.
        // The '*' must be kept and the block phone number must not be grabbed instead.
        val sms = """
            Spent INR 260
            Axis Bank Card no. XX3768
            09-05-26 11:21:46 IST
            PTM*FLIPKAR
            Avl Limit: INR 95000
            Not you? SMS BLOCK 3768 to 919951860002
        """.trimIndent()
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals("PTM*FLIPKAR", txn?.merchant)
    }

    @Test
    fun testPhoneNumberNotUsedAsMerchant() {
        // A debit whose only "to <x>" is the fraud-report phone number falls back to Unknown,
        // never the phone number itself.
        val sms = """
            Spent INR 150
            HDFC Bank Card XX9999
            Not you? SMS BLOCK to 9876543210
        """.trimIndent()
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals("Unknown", txn?.merchant)
    }

    @Test
    fun testAutoDebitReminderIgnored() {
        // A future auto-debit / AutoPay reminder must NOT be tracked — the actual debit arrives as
        // its own SMS later. (Real format from an Axis Credit Card Google Play AutoPay reminder.)
        val sms = "INR 299.00 for Google Play will be auto debited via Axis Bank Credit Card no. " +
            "XX3768 by 28-06-26. Please maintain sufficient limit on the card to process the auto " +
            "debit. To deactivate AutoPay facility for ID XShjgwt3ZT, click https://ccm.ax"
        assertNull(SmsParser.parseTransaction(sms, emptyList(), testCategories))
    }

    @Test
    fun testActualCardDebitStillParsedNotReminder() {
        // The real debit for the same mandate (present tense "Spent") must still be recorded.
        val sms = """
            Spent INR 299
            Axis Bank Card no. XX3768
            28-06-26 08:20:36 IST
            GOOGLEPLAY
            Avl Limit: INR 154392.4
            Not you? SMS BLOCK 3768 to 919951860002
        """.trimIndent()
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(299.0, txn?.amount)
        assertEquals("GOOGLEPLAY", txn?.merchant)
    }

    @Test
    fun testIciciDebitPayeeReadFromBeforeCredited() {
        // ICICI phrases the spend as "... debited for Rs N ...; <payee> credited." — the payee is
        // named AFTER "credited" with no at/to/for preposition. The for-capture would otherwise grab
        // the amount string ("Rs 164.00 on 27-Jun-26") as the merchant; that is rejected and the real
        // payee is read from the word before "credited".
        val sms = "ICICI Bank Acct XX013 debited for Rs 164.00 on 27-Jun-26; Amazon Pay Groc " +
            "credited. UPI:751483975479. Call 18002662 for dispute. SMS BLOCK 013 to 9215676766."
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(164.0, txn?.amount)          // first Rs amount, not the dispute/block numbers
        assertEquals("Amazon Pay Groc", txn?.merchant)
        assertEquals(2L, txn?.categoryId)         // Shopping (amazon keyword)
    }

    @Test
    fun testAmountStringNotUsedAsMerchant() {
        // Even without a "credited" payee, the "debited for Rs <amount>" capture must never store the
        // amount as the merchant — it falls back to Unknown instead.
        val sms = "A/c XX013 debited for Rs 164.00 on 27-Jun-26. Ref 751483975479."
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(164.0, txn?.amount)
        assertEquals("Unknown", txn?.merchant)
    }

    @Test
    fun testRuleKeywordDoesNotMatchUpiHandleSuffix() {
        // Reported bug: a card txn to UPI id "policybaza@mairtel" was caught by an "airtel -> Bills"
        // rule because "airtel" is a substring of the handle "mairtel". The part after "@" is a
        // PSP/bank handle, never a merchant, so it must not be matched. The "policybaza" rule (which
        // matches the name BEFORE "@") must win instead.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(
            CategoryRule(id = 1, keyword = "airtel", categoryId = 4L, label = "Airtel"),       // Bills
            CategoryRule(id = 2, keyword = "policybaza", categoryId = 7L, label = "Term Insurance") // Investment
        )
        val sms = """
            Txn Rs.1607.00
            On HDFC Bank Card 1234
            At policybaza@mairtel
            by UPI 123466789012
            On 01-06
        """.trimIndent()
        val txn = SmsParser.parseTransaction(sms, rules, cats)

        assertNotNull(txn)
        assertEquals(1607.0, txn?.amount)
        assertEquals(7L, txn?.categoryId)          // Investment, NOT Bills
        assertEquals("Term Insurance", txn?.merchant)
    }

    @Test
    fun testRuleKeywordDoesNotMatchHandleAloneEvenWithoutCompetingRule() {
        // Even with ONLY the airtel rule present, "policybaza@mairtel" must not become Bills/Airtel.
        val rules = listOf(CategoryRule(id = 1, keyword = "airtel", categoryId = 4L, label = "Airtel"))
        val sms = "Txn Rs.500.00 On HDFC Bank Card 1234 At policybaza@mairtel by UPI 123"
        val txn = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(txn)
        assertEquals(5L, txn?.categoryId)          // Others (rule did not match the handle)
        assertEquals(false, txn?.merchant == "Airtel")
    }

    @Test
    fun testRuleMatchesNameBeforeHandle() {
        // The portion BEFORE "@" is still matchable: a rule keyed on it must still apply.
        val rules = listOf(CategoryRule(id = 1, keyword = "q126589", categoryId = 2L, label = "MyShop"))
        val sms = "Rs.300.00 debited to q126589@ybl on 05-Jun"
        val txn = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(txn)
        assertEquals(2L, txn?.categoryId)
        assertEquals("MyShop", txn?.merchant)
    }

    @Test
    fun testRuleKeywordCreatedWithHandleIgnoresItsOwnSuffix() {
        // A rule typed WITH an "@" (e.g. "policybaza@axis") must match on just "policybaza", so it
        // applies regardless of which handle the actual transaction used.
        val cats = testCategories + Category(id = 7, name = "Investment", iconName = "TrendingUp")
        val rules = listOf(CategoryRule(id = 1, keyword = "policybaza@axis", categoryId = 7L, label = "Term Insurance"))
        listOf(
            "Rs.1607.00 debited to policybaza@mairtel via UPI",
            "Rs.1607.00 debited to policybaza on 01-Jun"
        ).forEach { sms ->
            val txn = SmsParser.parseTransaction(sms, rules, cats)
            assertNotNull(txn)
            assertEquals(7L, txn?.categoryId)
            assertEquals("Term Insurance", txn?.merchant)
        }
    }

    @Test
    fun testPureHandleKeywordMatchesNothing() {
        // A keyword that is ONLY a handle ("@ybl") strips to empty and must not match everything.
        val rules = listOf(CategoryRule(id = 1, keyword = "@ybl", categoryId = 4L, label = "Bills"))
        val sms = "Rs.300.00 debited to someone@ybl on 05-Jun"
        val txn = SmsParser.parseTransaction(sms, rules, testCategories)

        assertNotNull(txn)
        assertEquals(5L, txn?.categoryId)          // Others — the empty keyword matched nothing
    }

    @Test
    fun testHdfcCardMerchantWithLeadingDotNoiseExtracted() {
        // HDFC card "Spent" alert prefixes the merchant with ".." noise after "At ", which the
        // primary at/to/for capture can't read (it requires an alphanumeric first char). The real
        // merchant must still be extracted, not lost to "Unknown", and the all-digit SMS-BLOCK
        // number must never be grabbed instead.
        val sms = "Spent Rs.90000 From HDFC Bank Card x4437 At ..SUDHA SILK AND S_ On " +
            "2026-01-24:20:30:09 Bal Rs.73283.27 Not You? Call 18002586161/SMS BLOCK DC 4437 to 7308080808"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals(90000.0, txn?.amount)            // first Rs amount, not the trailing Bal
        assertEquals("SUDHA SILK AND S", txn?.merchant) // leading ".." skipped, trailing "_" dropped
    }

    @Test
    fun testCleanAtMerchantUnaffectedByNoiseFallback() {
        // Sanity: a clean "at <merchant>" with no punctuation noise is still handled by the primary
        // pattern exactly as before (the new noise-skipping fallback must not interfere).
        val sms = "Rs.50.00 debited at Starbucks Coffee"
        val txn = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNotNull(txn)
        assertEquals("Starbucks Coffee", txn?.merchant)
    }

    @Test
    fun testOtpIgnored() {
        val sms = "Your OTP is 123456. Do not share this with anyone."
        val transaction = SmsParser.parseTransaction(sms, emptyList(), testCategories)

        assertNull(transaction)
    }
}