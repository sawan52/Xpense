package com.example.xpense

import com.example.xpense.ui.forcibleRuleOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Force auto rule" visibility + outcome. A row is forcible (and pinned) exactly when a real rule
 * matches it but the stored category/merchant diverge from what the rule would set. Editing only the
 * amount/note leaves category + merchant equal to the rule outcome → no divergence → not forcible.
 */
class ForcibleRuleTest {

    private val FOOD = 1L; private val SHOPPING = 2L; private val INVESTMENT = 8L; private val OTHERS = 7L

    @Test fun noRule_notForcible() {
        assertNull(
            forcibleRuleOutcome(SHOPPING, "Some Vendor", fromRule = false, ruleCategoryId = FOOD, ruleLabel = "X")
        )
    }

    @Test fun ruleCategoryZero_notForcible() {
        assertNull(
            forcibleRuleOutcome(SHOPPING, "Amazon", fromRule = true, ruleCategoryId = 0L, ruleLabel = "Amazon")
        )
    }

    @Test fun alreadyMatchesRule_notForcible() {
        // Category == rule and merchant == label: nothing to force (covers amount/note-only edits).
        assertNull(
            forcibleRuleOutcome(SHOPPING, "Amazon", fromRule = true, ruleCategoryId = SHOPPING, ruleLabel = "Amazon")
        )
    }

    @Test fun categoryDiverged_forcibleToRule() {
        val r = forcibleRuleOutcome(OTHERS, "Amazon", fromRule = true, ruleCategoryId = SHOPPING, ruleLabel = "Amazon")
        assertEquals(SHOPPING, r!!.categoryId)
        assertEquals("Amazon", r.merchant)
    }

    @Test fun merchantRenamed_categoryAgrees_forcibleResetsMerchantToLabel() {
        val r = forcibleRuleOutcome(INVESTMENT, "My Groww SIP", fromRule = true, ruleCategoryId = INVESTMENT, ruleLabel = "Groww")
        assertEquals(INVESTMENT, r!!.categoryId)
        assertEquals("Groww", r.merchant)
    }

    @Test fun labelLessRule_categoryDiverged_keepsCurrentMerchant() {
        val r = forcibleRuleOutcome(OTHERS, "Some Vendor", fromRule = true, ruleCategoryId = FOOD, ruleLabel = null)
        assertEquals(FOOD, r!!.categoryId)
        assertEquals("Some Vendor", r.merchant)
    }

    @Test fun labelLessRule_categoryAgrees_notForcible() {
        assertNull(
            forcibleRuleOutcome(FOOD, "Some Vendor", fromRule = true, ruleCategoryId = FOOD, ruleLabel = null)
        )
    }
}
