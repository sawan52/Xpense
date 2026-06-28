package com.example.xpense

import com.example.xpense.ui.reapplyDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rule re-application semantics. Priority: rule > manual edit > default — EXCEPT a manual edit made
 * after a rule already existed (userPinned) wins and is never overridden.
 */
class ReapplyDecisionTest {

    private val FOOD = 1L; private val SHOPPING = 2L; private val TRANSPORT = 3L
    private val BILLS = 4L; private val GROCERIES = 11L
    private val INVESTMENT = 8L; private val OTHERS = 7L; private val GIFTS = 99L

    // ── Not pinned: a matching rule wins (sets category + label) ─────────────────────────────────

    @Test fun unpinned_rule_setsCategoryAndLabel() {
        val d = reapplyDecision(OTHERS, "Groww Invest Tech Private", userPinned = false,
            fromRule = true, ruleCategoryId = INVESTMENT, ruleLabel = "Groww",
            reextractedMerchant = "Groww Invest Tech Private")
        assertEquals(INVESTMENT, d.categoryId)
        assertEquals("Groww", d.merchant)
    }

    @Test fun unpinned_ruleLabelShowsWhenCategoryAlreadyMatches() {
        val d = reapplyDecision(TRANSPORT, "HULIGEPPA via UPI", userPinned = false,
            fromRule = true, ruleCategoryId = TRANSPORT, ruleLabel = "Auto",
            reextractedMerchant = "HULIGEPPA via UPI")
        assertEquals(TRANSPORT, d.categoryId)
        assertEquals("Auto", d.merchant)
    }

    @Test fun unpinned_ruleOverridesAnEarlierManualCategory() {
        // Manually moved to Gifts WHILE NO RULE existed (so not pinned); a rule created later wins.
        val d = reapplyDecision(GIFTS, "Amazon", userPinned = false,
            fromRule = true, ruleCategoryId = SHOPPING, ruleLabel = "Amazon",
            reextractedMerchant = "Amazon")
        assertEquals(SHOPPING, d.categoryId)
        assertEquals("Amazon", d.merchant)
    }

    @Test fun unpinned_ruleWithoutLabelKeepsMerchant() {
        val d = reapplyDecision(OTHERS, "Some Vendor", userPinned = false,
            fromRule = true, ruleCategoryId = FOOD, ruleLabel = null,
            reextractedMerchant = "Some Vendor")
        assertEquals(FOOD, d.categoryId)
        assertEquals("Some Vendor", d.merchant)
    }

    // ── Pinned: the user edited a row that already had a rule → fully protected ───────────────────

    @Test fun pinned_ruleNeverOverridesCategory() {
        // Zepto paid via an "@airtel" handle matches airtel→Bills, but the user pinned it to
        // Groceries — the false/unwanted rule match must not move it.
        val d = reapplyDecision(GROCERIES, "Zepto", userPinned = true,
            fromRule = true, ruleCategoryId = BILLS, ruleLabel = "Airtel",
            reextractedMerchant = "Zepto")
        assertEquals(GROCERIES, d.categoryId)
        assertEquals("Zepto", d.merchant)
    }

    @Test fun pinned_ruleNeverOverridesNameEvenWhenCategoryAgrees() {
        // User renamed the merchant on a ruled transaction (kept the category); their name stands.
        val d = reapplyDecision(INVESTMENT, "My Groww SIP", userPinned = true,
            fromRule = true, ruleCategoryId = INVESTMENT, ruleLabel = "Groww",
            reextractedMerchant = "Groww Invest Tech Private")
        assertEquals(INVESTMENT, d.categoryId)
        assertEquals("My Groww SIP", d.merchant)
    }

    // ── No rule match: existing (manual or default) value preserved ──────────────────────────────

    @Test fun noRule_keepsManualCategory() {
        val d = reapplyDecision(SHOPPING, "Some Vendor", userPinned = false,
            fromRule = false, ruleCategoryId = FOOD, ruleLabel = null,
            reextractedMerchant = "Some Vendor")
        assertEquals(SHOPPING, d.categoryId)
        assertEquals("Some Vendor", d.merchant)
    }

    // ── Useless-merchant repair (unpinned, no rule label, re-extraction improves it) ─────────────

    @Test fun unpinned_repairsNumericMerchant() {
        val d = reapplyDecision(OTHERS, "919951860002", userPinned = false,
            fromRule = false, ruleCategoryId = 0L, ruleLabel = null,
            reextractedMerchant = "GOOGLEPLAY")
        assertEquals("GOOGLEPLAY", d.merchant)
    }

    @Test fun unpinned_repairKeepsUnknownWhenReextractionAlsoUseless() {
        val d = reapplyDecision(OTHERS, "Unknown", userPinned = false,
            fromRule = false, ruleCategoryId = 0L, ruleLabel = null,
            reextractedMerchant = "Unknown")
        assertEquals("Unknown", d.merchant)
    }

    @Test fun pinned_uselessMerchantNotRepaired() {
        val d = reapplyDecision(OTHERS, "919951860002", userPinned = true,
            fromRule = false, ruleCategoryId = 0L, ruleLabel = null,
            reextractedMerchant = "GOOGLEPLAY")
        assertEquals("919951860002", d.merchant)
    }

    @Test fun unpinned_goodMerchantNeverOverwrittenByReextraction() {
        val d = reapplyDecision(FOOD, "Starbucks", userPinned = false,
            fromRule = false, ruleCategoryId = 0L, ruleLabel = null,
            reextractedMerchant = "Starbucks Coffee")
        assertEquals("Starbucks", d.merchant)
    }
}
