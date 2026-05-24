package com.example.xpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen {
    HOME, TRACKER
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    val allExpenses: StateFlow<List<Expense>> = expenseDao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

    val monthlyTotals: StateFlow<Map<String, Double>> = allExpenses.map { expenses ->
        expenses.groupBy { monthFormat.format(Date(it.date)) }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val availableMonths: StateFlow<List<String>> = monthlyTotals.map { 
        it.keys.toList().sortedByDescending { monthStr ->
            monthFormat.parse(monthStr)?.time ?: 0L
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSixMonthsTotals: StateFlow<List<Pair<String, Double>>> = combine(availableMonths, monthlyTotals) { months, totals ->
        months.take(6).reversed().map { month ->
            month to (totals[month] ?: 0.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMonth = MutableStateFlow<String?>(null)
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    val filteredExpenses: StateFlow<List<Expense>> = combine(allExpenses, _selectedMonth) { expenses, month ->
        if (month == null) expenses
        else expenses.filter { monthFormat.format(Date(it.date)) == month }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalForSelectedMonth: StateFlow<Double> = filteredExpenses.map { expenses ->
        expenses.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categorySummaryForSelectedMonth: StateFlow<Map<Category, Double>> = filteredExpenses.map { expenses ->
        expenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun navigateTo(screen: Screen, month: String? = null) {
        _currentScreen.value = screen
        if (month != null) {
            _selectedMonth.value = month
        } else if (_selectedMonth.value == null && screen == Screen.TRACKER) {
            // Default to most recent month if available
            _selectedMonth.value = availableMonths.value.firstOrNull()
        }
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun addExpense(amount: Double, merchant: String, category: Category, date: Long) {
        viewModelScope.launch {
            val expense = Expense(
                amount = amount,
                merchant = merchant,
                category = category,
                date = date,
                rawSms = "Manually Added"
            )
            expenseDao.insertExpense(expense)
        }
    }

    fun updateExpense(id: Long, amount: Double, merchant: String, category: Category, date: Long) {
        viewModelScope.launch {
            val expense = Expense(
                id = id,
                amount = amount,
                merchant = merchant,
                category = category,
                date = date,
                rawSms = "Edited"
            )
            expenseDao.updateExpense(expense)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            expenseDao.deleteAllExpenses()
        }
    }

    fun toggleSelection(id: Long) {
        _selectedIds.update { if (it.contains(id)) it - id else it + id }
        if (_selectedIds.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun enterSelectionMode(id: Long) {
        _isSelectionMode.value = true
        toggleSelection(id)
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            expenseDao.deleteExpenses(_selectedIds.value.toList())
            exitSelectionMode()
        }
    }
}