package com.example.xpense

import com.example.xpense.data.entity.Expense
import com.example.xpense.data.entity.NotificationItem
import com.example.xpense.ui.visibleNotifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsVisibilityTest {

    private val othersId = 5L

    private fun expense(id: Long, categoryId: Long, locked: Boolean = false) =
        Expense(
            id = id,
            amount = 100.0,
            merchant = "M$id",
            date = 0L,
            categoryId = categoryId,
            rawSms = "body$id",
            dedupKey = "body$id",
            categoryLocked = locked
        )

    private fun notif(id: Long, expenseId: Long) =
        NotificationItem(id = id, expenseId = expenseId, merchant = "M$expenseId", amount = 100.0, date = 0L)

    @Test
    fun shownWhileExpenseStaysInOthersAndUnlocked() {
        val items = listOf(notif(1, 10))
        val expenses = listOf(expense(10, othersId))
        assertEquals(listOf(notif(1, 10)), visibleNotifications(items, expenses, othersId))
    }

    @Test
    fun hiddenOnceCategorizedOutOfOthers() {
        // A rule moved the expense to Food (id 1) → its notification disappears.
        val items = listOf(notif(1, 10))
        val expenses = listOf(expense(10, 1L))
        assertTrue(visibleNotifications(items, expenses, othersId).isEmpty())
    }

    @Test
    fun hiddenWhenManuallyLockedEvenIfStillOthers() {
        // User manually set the category (categoryLocked) — treat as resolved even if still Others.
        val items = listOf(notif(1, 10))
        val expenses = listOf(expense(10, othersId, locked = true))
        assertTrue(visibleNotifications(items, expenses, othersId).isEmpty())
    }

    @Test
    fun hiddenWhenLinkedExpenseDeleted() {
        val items = listOf(notif(1, 99))
        val expenses = listOf(expense(10, othersId))   // no expense with id 99
        assertTrue(visibleNotifications(items, expenses, othersId).isEmpty())
    }

    @Test
    fun emptyWhenOthersCategoryMissing() {
        val items = listOf(notif(1, 10))
        val expenses = listOf(expense(10, othersId))
        assertTrue(visibleNotifications(items, expenses, null).isEmpty())
    }
}
