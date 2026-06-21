package com.example.xpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.example.xpense.data.backup.BackupManager
import com.example.xpense.data.backup.RestoreMode
import com.example.xpense.data.database.AppDatabase
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.NotificationItem
import com.example.xpense.notifications.TransactionNotifier
import com.example.xpense.sms.SmsParser
import com.example.xpense.sms.SyncManager
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class Screen {
    HOME, INSIGHTS, INSIGHTS_DETAIL, HISTORY, PROFILE, CATEGORY_RULES, IGNORED, BACKUP, NOTIFICATIONS
}

/** UI status for the Backup & Restore screen. */
sealed class BackupUiState {
    object Idle : BackupUiState()
    data class Working(val message: String) : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()
    private val ruleDao = db.categoryRuleDao()
    private val categoryDao = db.categoryDao()
    private val notificationDao = db.notificationDao()
    private val syncManager = SyncManager(application)
    private val backupManager = BackupManager(application)
    private val prefs = application.getSharedPreferences(
        TransactionNotifier.PREFS_NAME, Context.MODE_PRIVATE
    )

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

    fun updateRule(id: Long, keyword: String, categoryId: Long, label: String? = null) {
        viewModelScope.launch {
            ruleDao.updateRule(
                CategoryRule(
                    id = id,
                    keyword = keyword,
                    categoryId = categoryId,
                    label = label?.trim()?.takeIf { it.isNotBlank() }
                )
            )
            // Re-apply so the edited rule retro-categorizes existing transactions, matching addRule.
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
            // Respect a user's manual category choice and never auto-revert it.
            if (exp.categoryLocked) return@forEach
            val result = SmsParser.categorizationFor(exp.rawSms, rules, categories)
            // Only an actual user rule may re-categorize a row; the keyword fallback (which would
            // demote unmatched rows to "Others") must not overwrite an existing category.
            if (!result.fromRule) return@forEach
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

    // Ignored rows live only on the dedicated Ignored Transactions screen; every main list and
    // total is built from activeExpenses, so ignoring a row removes it from the UI entirely.
    val activeExpenses: StateFlow<List<ExpenseWithCategory>> = allExpenses.map { expenses ->
        expenses.filter { !it.expense.ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ignoredExpenses: StateFlow<List<ExpenseWithCategory>> = allExpenses.map { expenses ->
        expenses.filter { it.expense.ignored }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    // Ignored rows (self-transfers etc.) are excluded from every total/sum.
    val monthlyTotals: StateFlow<Map<String, Double>> = activeExpenses.map { expenses ->
        expenses.groupBy { monthFormat.format(Date(it.expense.date)) }
            .mapValues { (_, list) -> list.sumOf { it.expense.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // A month whose transactions are all ignored gets no chip — those rows are reachable from
    // the Ignored Transactions screen instead.
    val availableMonths: StateFlow<List<String>> = activeExpenses.map { expenses ->
        expenses.map { monthFormat.format(Date(it.expense.date)) }
            .distinct()
            .sortedByDescending { month -> monthFormat.parse(month) }
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
        activeExpenses,
        _selectedMonth
    ) { expenses, month ->
        if (month == null) expenses else expenses.filter { monthFormat.format(Date(it.expense.date)) == month }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalForSelectedMonth: StateFlow<Double> = filteredExpenses.map {
        it.sumOf { exp -> exp.expense.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Sum of ignored transactions in the selected month — surfaced as the "₹X excluded" hint.
    val ignoredTotalForSelectedMonth: StateFlow<Double> = combine(
        ignoredExpenses,
        _selectedMonth
    ) { expenses, month ->
        expenses.filter { month == null || monthFormat.format(Date(it.expense.date)) == month }
            .sumOf { it.expense.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categorySummaryForSelectedMonth: StateFlow<Map<Category, Double>> = filteredExpenses.map { expenses ->
        expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.expense.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setIgnored(id: Long, ignored: Boolean) {
        viewModelScope.launch { expenseDao.setIgnored(id, ignored) }
    }

    /** Bulk ignore/un-ignore every currently selected transaction, then leave selection mode. */
    fun setIgnoredForSelected(ignored: Boolean) {
        viewModelScope.launch {
            expenseDao.setIgnoredForIds(_selectedIds.value.toList(), ignored)
            exitSelectionMode()
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // ── New-transaction notifications ────────────────────────────────────────
    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(TransactionNotifier.KEY_NOTIFICATIONS_ENABLED, true)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(TransactionNotifier.KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _notificationsEnabled.value = enabled
    }

    // A notification tap carries the merchant so the UI can open a pre-filled Add-Rule dialog.
    private val _pendingRuleKeyword = MutableStateFlow<String?>(null)
    val pendingRuleKeyword: StateFlow<String?> = _pendingRuleKeyword.asStateFlow()

    fun requestRulePrefill(keyword: String) { _pendingRuleKeyword.value = keyword }
    fun clearRulePrefill() { _pendingRuleKeyword.value = null }

    /** Posts a sample notification so the user can verify the alert + tap-to-create-rule flow. */
    fun sendTestNotification() {
        TransactionNotifier.sendTest(getApplication())
    }

    // ── In-app Notifications inbox ───────────────────────────────────────────
    // Durable record of uncategorized-transaction alerts. An item is shown only while its linked
    // expense is still uncategorized (in Others, not locked); once a rule or a manual edit moves it
    // out, it auto-clears. See `visibleNotifications`.
    val pendingNotifications: StateFlow<List<NotificationItem>> = combine(
        notificationDao.getAll(),
        allExpenses,
        allCategories
    ) { items, expenses, categories ->
        val othersId = categories.find { it.name.equals("Others", ignoreCase = true) }?.id
        visibleNotifications(items, expenses.map { it.expense }, othersId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingNotificationCount: StateFlow<Int> = pendingNotifications
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun dismissNotification(id: Long) {
        viewModelScope.launch { notificationDao.deleteById(id) }
    }

    fun clearAllNotifications() {
        viewModelScope.launch { notificationDao.deleteAll() }
    }

    /**
     * Whether an actual user rule currently matches this transaction. Drives the edit-sheet
     * "Add a rule" button (shown only for SMS rows with no rule yet). Manual rows have no SMS to
     * base a rule on, so they always return false.
     */
    fun hasUserRuleFor(expense: Expense): Boolean {
        if (expense.rawSms == "Manual Entry" || expense.rawSms == "Manual Update") return false
        return SmsParser.categorizationFor(expense.rawSms, allRules.value, allCategories.value).fromRule
    }

    fun selectMonth(month: String) {
        _selectedMonth.value = month
    }

    fun startHistoricalSync() {
        _showSyncConfirm.value = true
    }

    @Volatile
    private var isSyncing = false

    fun confirmSyncAndStart() {
        _showSyncConfirm.value = false
        // Guard against overlapping syncs: a second run could otherwise race the first and
        // insert duplicates (the DB unique index is the backstop; this avoids it entirely).
        if (isSyncing) return
        isSyncing = true
        viewModelScope.launch {
            try {
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
            } finally {
                isSyncing = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun hideSyncConfirm() {
        _showSyncConfirm.value = false
    }

    fun addExpense(amount: Double, merchant: String, categoryId: Long, date: Long, note: String? = null) {
        viewModelScope.launch {
            expenseDao.insertExpense(Expense(amount = amount, merchant = merchant, categoryId = categoryId, date = date, rawSms = "Manual Entry", note = note))
        }
    }

    fun updateExpense(id: Long, amount: Double, merchant: String, categoryId: Long, date: Long, note: String? = null) {
        viewModelScope.launch {
            // Update only the editable columns; rawSms + dedupKey must survive so resync still
            // recognises an edited SMS row and skips it instead of inserting a duplicate.
            // Lock the category once the user changes it (and keep it locked thereafter) so rule
            // re-application never reverts a manual choice.
            val existing = expenseDao.getExpenseById(id)
            val locked = existing?.categoryLocked == true || (existing != null && existing.categoryId != categoryId)
            expenseDao.updateExpenseFields(id, amount, merchant, categoryId, date, note, locked)
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

    // ── Google Drive backup & restore ────────────────────────────────────────
    private val _signedInEmail = MutableStateFlow<String?>(null)
    val signedInEmail: StateFlow<String?> = _signedInEmail.asStateFlow()

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Account-picker intent for the screen's ActivityResult launcher. */
    fun driveSignInIntent(): Intent = backupManager.signInIntent()

    /** Re-read sign-in + last-backup state (call on entering the Backup screen). */
    fun refreshBackupState() {
        _signedInEmail.value = backupManager.signedInEmail()
        if (_signedInEmail.value == null) {
            _lastBackupTime.value = null
        } else {
            viewModelScope.launch {
                _lastBackupTime.value = runCatching { backupManager.lastBackupTime() }.getOrNull()
            }
        }
    }

    fun onDriveSignInResult(data: Intent?) {
        val result = backupManager.handleSignInResult(data)
        _signedInEmail.value = result.email
        if (result.email != null) {
            _backupState.value = BackupUiState.Success("Connected as ${result.email}")
            refreshBackupState()
        } else {
            _backupState.value = BackupUiState.Error(result.error ?: "Sign-in failed")
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working("Backing up…")
            try {
                backupManager.backup()
                _lastBackupTime.value = runCatching { backupManager.lastBackupTime() }.getOrNull()
                _backupState.value = BackupUiState.Success("Backup complete")
            } catch (e: Exception) {
                android.util.Log.w("XpenseBackup", "Backup failed", e)
                _backupState.value = BackupUiState.Error(e.message ?: "Backup failed")
            }
        }
    }

    /** Restore from Drive. The reactive expense/category flows refresh the UI automatically. */
    fun restoreBackup(mode: RestoreMode) {
        viewModelScope.launch {
            _backupState.value = BackupUiState.Working("Restoring…")
            try {
                backupManager.restore(mode)
                _backupState.value = BackupUiState.Success(
                    if (mode == RestoreMode.REPLACE) "Restore complete — data replaced"
                    else "Restore complete — data merged"
                )
            } catch (e: Exception) {
                android.util.Log.w("XpenseBackup", "Restore failed", e)
                _backupState.value = BackupUiState.Error(e.message ?: "Restore failed")
            }
        }
    }

    fun signOutDrive() {
        viewModelScope.launch {
            backupManager.signOut()
            _signedInEmail.value = null
            _lastBackupTime.value = null
            _backupState.value = BackupUiState.Idle
        }
    }

    fun clearBackupState() { _backupState.value = BackupUiState.Idle }
}

data class ExpenseWithCategory(
    val expense: Expense,
    val category: Category
)

/**
 * Pure visibility filter for the Notifications inbox (no DB/IO so it is unit-testable): an item is
 * shown only while its linked expense still exists, sits in "Others", and isn't categoryLocked.
 * The moment a rule or a manual edit moves the expense out of Others, the item disappears.
 */
fun visibleNotifications(
    items: List<NotificationItem>,
    expenses: List<Expense>,
    othersId: Long?
): List<NotificationItem> {
    if (othersId == null) return emptyList()
    val byId = expenses.associateBy { it.id }
    return items.filter { n ->
        val e = byId[n.expenseId]
        e != null && e.categoryId == othersId && !e.categoryLocked
    }
}
