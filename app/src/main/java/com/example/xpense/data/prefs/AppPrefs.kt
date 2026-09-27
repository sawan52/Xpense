package com.example.xpense.data.prefs

import android.content.Context
import com.example.xpense.notifications.TransactionNotifier

/**
 * Small UI preferences kept alongside the notification toggle in `xpense_prefs`. Not backed up to
 * Drive: they describe this device's presentation, not the user's data.
 */
class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(TransactionNotifier.PREFS_NAME, Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK, value).apply()

    /** Monthly spending budget in rupees; 0 means "not set". */
    var monthlyBudget: Double
        get() = prefs.getFloat(KEY_BUDGET, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_BUDGET, value.toFloat()).apply()

    /** Most-recent-first search history, capped at [MAX_RECENT]. */
    var recentSearches: List<String>
        get() = prefs.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_RECENT, value.take(MAX_RECENT).joinToString("\n")).apply()

    companion object {
        private const val KEY_DARK = "theme_dark"
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_RECENT = "recent_searches"
        const val MAX_RECENT = 6
    }
}
