package com.example.xpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Category
import com.example.xpense.sms.SmsParser
import com.example.xpense.sms.SyncManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class Screen {
    HOME, INSIGHTS, HISTORY, PROFILE, CATEGORY_RULES
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.categoryRuleDao()
    private val categoryDao = db.categoryDao()
    private val syncManager = SyncManager(application)

    val allCategories = categoryDao.getAllCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allRules = ruleDao.getAllRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        seedInitialData()
    }

    private fun seedInitialData() {
        viewModelScope.launch {
            if (categoryDao.getCategoryCount() == 0) {
                val defaultCategories = listOf(
                    Category(name = "Food", iconName = "Restaurant"),
                    Category(name = "Shopping", iconName = "ShoppingCart"),
                    Category(name = "Transport", iconName = "DirectionsCar"),
                    Category(name = "Bills", iconName = "ReceiptLong"),
                    Category(name = "Health", iconName = "MedicalServices"),
                    Category(name = "Entertainment", iconName = "ConfirmationNumber"),
                    Category(name = "Others", iconName = "Category")
                )
                defaultCategories.forEach { categoryDao.insertCategory(it) }
                
                // Seed default rules after categories are in
                val savedCategories = categoryDao.getAllCategoriesList()
                val foodId = savedCategories.find { it.name == "Food" }?.id ?: 0L
                val shoppingId = savedCategories.find { it.name == "Shopping" }?.id ?: 0L
                val transportId = savedCategories.find { it.name == "Transport" }?.id ?: 0L
                val billsId = savedCategories.find { it.name == "Bills" }?.id ?: 0L

                val defaultRules = listOf(
                    CategoryRule(keyword = "swiggy", categoryId = foodId),
                    CategoryRule(keyword = "zomato", categoryId = foodId),
                    CategoryRule(keyword = "amazon", categoryId = shoppingId),
                    CategoryRule(keyword = "flipkart", categoryId = shoppingId),
                    CategoryRule(keyword = "uber", categoryId = transportId),
                    CategoryRule(keyword = "ola", categoryId = transportId),
                    CategoryRule(keyword = "jio", categoryId = billsId),
                    CategoryRule(keyword = "airtel", categoryId = billsId)
                )
                defaultRules.forEach { ruleDao.insertRule(it) }
            }
        }
    }

    fun addCategory(name: String, iconName: String = "Category") {
        viewModelScope.launch {
            categoryDao.insertCategory(Category(name = name, iconName = iconName))
        }
    }

    fun updateCategory(id: Long, name: String, iconName: String) {
        viewModelScope.launch {
            categoryDao.updateCategory(Category(id = id, name = name, iconName = iconName))
        }
    }

    /**
     * Deletes a category and moves its expenses and rules to "Others" so nothing is left
     * orphaned. The "Others" fallback category itself cannot be deleted.
     */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            val others = categoryDao.getCategoryByName("Others")
            if (others == null || category.id == others.id) return@launch
            expenseDao.reassignCategory(category.id, others.id)
            ruleDao.reassignRulesToCategory(category.id, others.id)
            categoryDao.deleteCategory(category)
        }
    }

    fun addRule(keyword: String, categoryId: Long, label: String? = null) {
        viewModelScope.launch {
            ruleDao.insertRule(
                CategoryRule(
                    keyword = keyword,
                    categoryId = categoryId,
                    label = label?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            // Apply the new rule to transactions that were already imported so the effect
            // is visible immediately (otherwise only future SMS would be affected).
            reapplyRules()
        }
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch {
            ruleDao.deleteRule(id)
        }
    }

    private val _reapplyResult = MutableStateFlow<Int?>(null)
    val reapplyResult: StateFlow<Int?> = _reapplyResult.asStateFlow()

    fun clearReapplyResult() { _reapplyResult.value = null }

    /**
     * Re-runs the rule/keyword categorization over every SMS-derived transaction using the
     * current rules and categories. Manually added/edited expenses are left untouched so the
     * user's explicit choices are respected. Returns how many were moved to a new category.
     */
    private suspend fun reapplyRules(): Int {
        val rules = ruleDao.getAllRulesList()
        val categories = categoryDao.getAllCategoriesList()
        if (categories.isEmpty()) return 0
        var changed = 0
        expenseDao.getAllExpensesList().forEach { exp ->
            if (exp.rawSms == "Manual Entry" || exp.rawSms == "Manual Update") return@forEach
            val result = SmsParser.categorizationFor(exp.rawSms, rules, categories)
            val newCategoryId = if (result.categoryId != 0L) result.categoryId else exp.categoryId
            // A rule label refreshes the display name too; otherwise keep the existing merchant.
            val newMerchant = result.label?.takeIf { it.isNotBlank() } ?: exp.merchant
            if (newCategoryId != exp.categoryId || newMerchant != exp.merchant) {
                expenseDao.updateExpenseCategoryAndMerchant(exp.id, newCategoryId, newMerchant)
                changed++
            }
        }
        return changed
    }

    /** User-triggered re-apply (e.g. after editing several rules); surfaces a result count. */
    fun reapplyRulesToExistingTransactions() {
        viewModelScope.launch {
            _reapplyResult.value = reapplyRules()
        }
    }

    private val _currentScreen = MutableStateFlow(Screen.HOME)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _syncProgress = MutableStateFlow<Float?>(null)
    val syncProgress: StateFlow<Float?> = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _showSyncConfirm = MutableStateFlow(false)
    val showSyncConfirm: StateFlow<Boolean> = _showSyncConfirm.asStateFlow()

    // Enhanced expense list with Category objects
    val allExpenses: StateFlow<List<ExpenseWithCategory>> = combine(
        expenseDao.getAllExpenses(),
        allCategories
    ) { expenses, categories ->
        expenses.map { expense ->
            val category = categories.find { it.id == expense.categoryId }
                ?: Category(name = "Unknown", iconName = "Category")
            ExpenseWithCategory(expense, category)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    val monthlyTotals: StateFlow<Map<String, Double>> = allExpenses.map { expenses ->
        expenses.groupBy { monthFormat.format(Date(it.expense.date)) }
            .mapValues { (_, list) -> list.sumOf { it.expense.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val availableMonths: StateFlow<List<String>> = monthlyTotals.map { 
        it.keys.toList().sortedByDescending { month -> monthFormat.parse(month) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lastSixMonthsTotals: StateFlow<List<Pair<String, Double>>> = monthlyTotals.map { totals ->
        val cal = Calendar.getInstance()
        (0 until 6).map { i ->
            val month = monthFormat.format(cal.time)
            cal.add(Calendar.MONTH, -1)
            month to (totals[month] ?: 0.0)
        }.reversed()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedMonth = MutableStateFlow<String?>(null)
    val selectedMonth: StateFlow<String?> = _selectedMonth.asStateFlow()

    val filteredExpenses: StateFlow<List<ExpenseWithCategory>> = combine(
        allExpenses,
        _selectedMonth
    ) { expenses, month ->
        if (month == null) expenses else expenses.filter { monthFormat.format(Date(it.expense.date)) == month }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalForSelectedMonth: StateFlow<Double> = filteredExpenses.map { 
        it.sumOf { exp -> exp.expense.amount } 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categorySummaryForSelectedMonth: StateFlow<Map<Category, Double>> = filteredExpenses.map { expenses ->
        expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.expense.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun startHistoricalSync() {
        _showSyncConfirm.value = true
    }

    fun confirmSyncAndStart() {
        _showSyncConfirm.value = false
        viewModelScope.launch {
            syncManager.syncHistoricalSms().collect { progress ->
                when (progress) {
                    is SyncManager.SyncProgress.Started -> _syncProgress.value = 0f
                    is SyncManager.SyncProgress.Progress -> _syncProgress.value = progress.percentage
                    is SyncManager.SyncProgress.Completed -> {
                        _syncProgress.value = null
                        _syncMessage.value = "Historical sync completed!"
                    }
                }
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun hideSyncConfirm() {
        _showSyncConfirm.value = false
    }

    fun addExpense(amount: Double, merchant: String, categoryId: Long, date: Long) {
        viewModelScope.launch {
            expenseDao.insertExpense(Expense(amount = amount, merchant = merchant, categoryId = categoryId, date = date, rawSms = "Manual Entry"))
        }
    }

    fun updateExpense(id: Long, amount: Double, merchant: String, categoryId: Long, date: Long) {
        viewModelScope.launch {
            expenseDao.updateExpense(Expense(id = id, amount = amount, merchant = merchant, categoryId = categoryId, date = date, rawSms = "Manual Update"))
        }
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
        if (current.isEmpty()) _isSelectionMode.value = false
    }

    fun enterSelectionMode(id: Long) {
        _selectedIds.value = setOf(id)
        _isSelectionMode.value = true
    }

    fun exitSelectionMode() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelected() {
        viewModelScope.launch {
            expenseDao.deleteExpenses(_selectedIds.value.toList())
            exitSelectionMode()
        }
    }
}

data class ExpenseWithCategory(
    val expense: Expense,
    val category: Category
)
