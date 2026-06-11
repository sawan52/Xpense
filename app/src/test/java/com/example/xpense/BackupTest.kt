package com.example.xpense

import com.example.xpense.data.backup.BackupData
import com.example.xpense.data.backup.CategoryDto
import com.example.xpense.data.backup.ExpenseDto
import com.example.xpense.data.backup.RuleDto
import com.example.xpense.data.backup.planMerge
import com.example.xpense.data.entity.Category
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun sampleBackup() = BackupData(
        schemaVersion = 6,
        appVersionCode = 3,
        exportedAt = 1_718_000_000_000L,
        categories = listOf(
            CategoryDto(1, "Food", "Restaurant"),
            CategoryDto(2, "Shopping", "ShoppingCart")
        ),
        rules = listOf(
            RuleDto(1, "swiggy", 1, null),
            RuleDto(2, "groww invest", 2, "MF SIP")
        ),
        expenses = listOf(
            ExpenseDto(1, 500.0, "Swiggy", 1_718_000_000_000L, 1, "raw sms body", "raw sms body", false),
            ExpenseDto(2, 2000.0, "Self", 1_718_000_100_000L, 2, "Manual Entry", null, true)
        )
    )

    @Test
    fun jsonRoundTripPreservesEverything() {
        val original = sampleBackup()
        val restored = json.decodeFromString<BackupData>(json.encodeToString(original))
        assertEquals(original, restored)
    }

    @Test
    fun roundTripPreservesIdsAndCategoryLinks() {
        val restored = json.decodeFromString<BackupData>(json.encodeToString(sampleBackup()))
        // The expense's categoryId must still point at the right category after a round trip.
        val foodExpense = restored.expenses.first { it.merchant == "Swiggy" }
        assertEquals(1L, foodExpense.categoryId)
        assertEquals("Food", restored.categories.first { it.id == foodExpense.categoryId }.name)
        // dedupKey + ignored survive.
        assertEquals("raw sms body", foodExpense.dedupKey)
        assertTrue(restored.expenses.first { it.merchant == "Self" }.ignored)
        assertNull(restored.expenses.first { it.merchant == "Self" }.dedupKey)
    }

    @Test
    fun toEntityMapsBackToRoomEntity() {
        val e = ExpenseDto(7, 99.5, "Shop", 123L, 2, "sms", "sms", false).toEntity()
        assertEquals(7L, e.id)
        assertEquals(2L, e.categoryId)
        assertEquals(99.5, e.amount, 0.0001)
    }

    @Test
    fun planMergeReusesIdForNameMatchAndQueuesUnmatched() {
        // Existing DB has Food (live id 10); backup has Food (id 1) + Travel (id 2).
        val existing = listOf(
            Category(id = 10, name = "Food", iconName = "Restaurant"),
            Category(id = 11, name = "Others", iconName = "Category")
        )
        val backup = sampleBackup().copy(
            categories = listOf(
                CategoryDto(1, "Food", "Restaurant"),       // matches existing id 10 (case/space-insensitive)
                CategoryDto(2, "Travel", "DirectionsCar")   // no match -> insert fresh
            )
        )

        val plan = planMerge(existing, backup)

        assertEquals(mapOf(1L to 10L), plan.matchedRemap)      // Food remapped to live id
        assertEquals(1, plan.categoriesToInsert.size)
        assertEquals("Travel", plan.categoriesToInsert.first().name)
    }

    @Test
    fun planMergeMatchesCaseInsensitively() {
        val existing = listOf(Category(id = 5, name = "shopping", iconName = "ShoppingCart"))
        val backup = sampleBackup().copy(categories = listOf(CategoryDto(2, "Shopping", "ShoppingCart")))

        val plan = planMerge(existing, backup)

        assertEquals(mapOf(2L to 5L), plan.matchedRemap)
        assertTrue(plan.categoriesToInsert.isEmpty())
    }
}
