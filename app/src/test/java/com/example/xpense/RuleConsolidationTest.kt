package com.example.xpense

import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.ui.consolidateRules
import com.example.xpense.ui.joinAlternatives
import com.example.xpense.ui.mergeKeywordStrings
import com.example.xpense.ui.splitAlternatives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConsolidationTest {

    private fun rule(id: Long, keyword: String, categoryId: Long, label: String? = null) =
        CategoryRule(id = id, keyword = keyword, categoryId = categoryId, label = label)

    // --- splitAlternatives / joinAlternatives (the Auto-Rules keyword list) ---

    @Test
    fun splitReturnsTrimmedAlternativesInStoredOrder() {
        assertEquals(
            listOf("HULIGEPPA", "MRS KAMALA", "SUMIT"),
            splitAlternatives("HULIGEPPA | MRS KAMALA|SUMIT ")
        )
    }

    @Test
    fun splitPreservesCasingAndUpiHandles() {
        // Unlike the matcher's own split, the UI view must not lowercase or strip "@handle",
        // otherwise editing a keyword would silently rewrite what is stored.
        assertEquals(
            listOf("DMART.27186418@hdfcbank", "PTM*FLIPKAR"),
            splitAlternatives("DMART.27186418@hdfcbank | PTM*FLIPKAR")
        )
    }

    @Test
    fun splitKeepsAndGroupIntact() {
        assertEquals(listOf("nach, groww invest"), splitAlternatives("nach, groww invest"))
    }

    @Test
    fun splitDropsBlankAlternatives() {
        assertEquals(listOf("swiggy"), splitAlternatives(" | swiggy |  | "))
        assertEquals(emptyList<String>(), splitAlternatives("  |  "))
    }

    @Test
    fun splitJoinRoundTripsRealKeyword() {
        val stored = "nach, groww invest | nach, indian clearing | policybaza"
        assertEquals(stored, joinAlternatives(splitAlternatives(stored)))
    }

    @Test
    fun joinNormalisesSeparatorsAndDropsBlanks() {
        assertEquals("a | b", joinAlternatives(listOf(" a ", "", "b", "   ")))
    }

    // --- the edits the rule detail screen performs on that list ---

    @Test
    fun editingOneKeywordLeavesTheOthersUntouched() {
        val parts = splitAlternatives("HULIGEPPA | MRS KAMALA | SUMIT").toMutableList()
        parts[1] = "MRS KAMALA R"
        assertEquals("HULIGEPPA | MRS KAMALA R | SUMIT", joinAlternatives(parts))
    }

    @Test
    fun removingOneKeywordLeavesTheOthersUntouched() {
        val parts = splitAlternatives("HULIGEPPA | MRS KAMALA | SUMIT").toMutableList()
        parts.removeAt(0)
        assertEquals("MRS KAMALA | SUMIT", joinAlternatives(parts))
    }

    @Test
    fun removingTheOnlyKeywordYieldsAnEmptyString() {
        // The screen turns this case into "delete the whole rule" rather than storing a rule that
        // can never match.
        val parts = splitAlternatives("swiggy").toMutableList()
        parts.removeAt(0)
        assertEquals("", joinAlternatives(parts))
    }

    @Test
    fun addingAKeywordAppendsItAsANewAlternative() {
        assertEquals("swiggy | zomato", mergeKeywordStrings("swiggy", "zomato"))
    }

    @Test
    fun addingADuplicateKeywordIsIgnored() {
        val existing = "HULIGEPPA | MRS KAMALA"
        assertEquals(existing, mergeKeywordStrings(existing, "huligeppa"))
    }

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
