package com.example.xpense

import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.ui.consolidateRules
import com.example.xpense.ui.mergeKeywordStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConsolidationTest {

    private fun rule(id: Long, keyword: String, categoryId: Long, label: String? = null) =
        CategoryRule(id = id, keyword = keyword, categoryId = categoryId, label = label)

    // --- mergeKeywordStrings ---

    @Test
    fun mergeAppendsNewAlternative() {
        assertEquals("swiggy | zomato", mergeKeywordStrings("swiggy", "zomato"))
    }

    @Test
    fun mergeDeDupesCaseInsensitively() {
        assertEquals("Swiggy", mergeKeywordStrings("Swiggy", "swiggy"))
    }

    @Test
    fun mergePreservesCommaAndGroups() {
        assertEquals(
            "nach, groww | indian clearing",
            mergeKeywordStrings("nach, groww", "indian clearing")
        )
    }

    @Test
    fun mergeDropsBlankIncomingGroups() {
        assertEquals("swiggy", mergeKeywordStrings("swiggy", "  |  "))
    }

    // --- consolidateRules ---

    @Test
    fun foldsDuplicateGroupIntoSmallestIdSurvivor() {
        val rules = listOf(
            rule(2, "zomato", categoryId = 1, label = "Eating out"),
            rule(5, "swiggy", categoryId = 1, label = "Eating out")
        )
        val result = consolidateRules(rules)
        assertEquals(listOf(5L), result.deleteIds)
        assertEquals(1, result.updates.size)
        assertEquals(2L, result.updates.first().id)
        assertEquals("zomato | swiggy", result.updates.first().keyword)
    }

    @Test
    fun blankAndNullLabelsInSameCategoryAreMerged() {
        val rules = listOf(
            rule(1, "swiggy", categoryId = 1, label = null),
            rule(2, "zomato", categoryId = 1, label = "  ")
        )
        val result = consolidateRules(rules)
        assertEquals(listOf(2L), result.deleteIds)
        assertEquals("swiggy | zomato", result.updates.first().keyword)
    }

    @Test
    fun differentCategoryOrLabelLeftUntouched() {
        val rules = listOf(
            rule(1, "swiggy", categoryId = 1, label = "Food"),
            rule(2, "swiggy", categoryId = 2, label = "Food"),     // different category
            rule(3, "swiggy", categoryId = 1, label = "Dining")    // different label
        )
        val result = consolidateRules(rules)
        assertTrue(result.deleteIds.isEmpty())
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun singletonGroupYieldsNothing() {
        val result = consolidateRules(listOf(rule(1, "swiggy", categoryId = 1)))
        assertTrue(result.deleteIds.isEmpty())
        assertTrue(result.updates.isEmpty())
    }
}
