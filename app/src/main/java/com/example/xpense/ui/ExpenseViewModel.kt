package com.example.xpense.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import com.example.xpense.data.backup.AutoBackupFrequency
import com.example.xpense.data.backup.AutoBackupScheduler
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
    HOME, INSIGHTS, INSIGHTS_DETAIL, PROFILE, CATEGORY_RULES, IGNORED, BACKUP, NOTIFICATIONS, HELP
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

    // Started eagerly (not WhileSubscribed) because hasUserRuleFor() reads .value synchronously to
    // decide whether to show the edit-sheet "Add a rule" button. The screens that call it (History,
    // Insights) don't collect these flows, so a cold WhileSubscribed flow would hand back the empty
    // initialValue and wrongly report "no rule" for already-ruled transactions. Both are tiny
    // reference tables, so keeping them collected for the ViewModel's lifetime is negligible.
    val allCategories = categoryDao.getAllCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val allRules = ruleDao.getAllRules().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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
            val cleanLabel = label?.trim()?.takeIf { it.isNotBlank() }
            // Fold into an existing rule with the same category + label instead of creating a
            // duplicate row: the new keyword becomes another '|'-alternative on that rule.
            val existing = ruleDao.getAllRulesList().firstOrNull {
                it.categoryId == categoryId && normalizeRuleLabel(it.label) == normalizeRuleLabel(cleanLabel)
            }
            if (existing != null) {
                val merged = mergeKeywordStrings(existing.keyword, keyword)
                if (merged != existing.keyword) ruleDao.updateRule(existing.copy(keyword = merged))
            } else {
                ruleDao.insertRule(CategoryRule(keyword = keyword, categoryId = categoryId, label = cleanLabel))
            }
            // Apply the rule to transactions that were already imported so the effect
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

    private val _mergeResult = MutableStateFlow<Int?>(null)
    val mergeResult: StateFlow<Int?> = _mergeResult.asStateFlow()

    fun clearMergeResult() { _mergeResult.value = null }

    /**
     * Collapses duplicate auto-rules (same category + label, differing only by keyword) into a
     * single rule whose keyword joins every alternative with '|'. Surfaces how many rows were
     * folded away for a snackbar. Matching is unchanged since each keyword stays its own group.
     */
    fun mergeDuplicateRules() {
        viewModelScope.launch {
            val consolidation = consolidateRules(ruleDao.getAllRulesList())
            consolidation.updates.forEach { ruleDao.updateRule(it) }
            consolidation.deleteIds.forEach { ruleDao.deleteRule(it) }
            _mergeResult.value = consolidation.deleteIds.size
        }
    }

    /**
     * Re-runs the rule categorization over every SMS-derived transaction using the current rules
     * and categories. A matching rule always wins (it sets the category and label), so a manual
     * edit only stands while no rule matches the row. Manual ("Manual Entry"/"Manual Update") rows
     * have no SMS to match and are skipped. Returns how many rows changed. See [reapplyDecision].
     */
    private suspend fun reapplyRules(): Int {
        val rules = ruleDao.getAllRulesList()
        val categories = categoryDao.getAllCategoriesList()
        if (categories.isEmpty()) return 0
        var changed = 0
        expenseDao.getAllExpensesList().forEach { exp ->
            if (exp.rawSms == "Manual Entry" || exp.rawSms == "Manual Update") return@forEach
            val result = SmsParser.categorizationFor(exp.rawSms, rules, categories)
            val decision = reapplyDecision(
                currentCategoryId = exp.categoryId,
                currentMerchant = exp.merchant,
                userPinned = exp.categoryLocked,
                fromRule = result.fromRule,
                ruleCategoryId = result.categoryId,
                ruleLabel = result.label,
                reextractedMerchant = SmsParser.extractMerchantFor(exp.rawSms)
            )
            if (decision.categoryId != exp.categoryId || decision.merchant != exp.merchant) {
                expenseDao.updateExpenseCategoryAndMerchant(exp.id, decision.categoryId, decision.merchant)
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

    // Navigation history. The last element is the visible screen; HOME is always the root. Back
    // pops one entry, so the user retraces the exact path they took. Revisiting a screen already
    // in history collapses back to it instead of stacking a duplicate (avoids unbounded growth and
    // loops when hopping between bottom-bar tabs).
    private val _backStack = MutableStateFlow(listOf(Screen.HOME))

    val currentScreen: StateFlow<Screen> = _backStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.HOME)

    /** True when there is somewhere to go back to (i.e. we're not at the HOME root). */
    val canNavigateBack: StateFlow<Boolean> = _backStack
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
        val stack = _backStack.value
        if (stack.last() == screen) return // already showing it
        val existing = stack.indexOf(screen)
        _backStack.value = if (existing >= 0) {
            // Revisiting a screen already in history → unwind back to it (no duplicate, no loop).
            stack.subList(0, existing + 1).toList()
        } else {
            stack + screen
        }
    }

    /**
     * Pop one screen off the history. Returns false when already at the HOME root, in which case
     * the caller (Activity) should let the system handle back — i.e. leave the app.
     */
    fun navigateBack(): Boolean {
        val stack = _backStack.value
        if (stack.size <= 1) return false
        _backStack.value = stack.dropLast(1)
        return true
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

    /**
     * Non-null when this row has a matching user rule but was manually diverged from it — i.e. the
     * edit sheet should offer "Force auto rule". The value is what forcing would apply. Manual rows
     * have no SMS to match, so they always return null. See [forcibleRuleOutcome].
     */
    fun forcibleRuleFor(expense: Expense): ForcibleRule? {
        if (expense.rawSms == "Manual Entry" || expense.rawSms == "Manual Update") return null
        val c = SmsParser.categorizationFor(expense.rawSms, allRules.value, allCategories.value)
        return forcibleRuleOutcome(expense.categoryId, expense.merchant, c.fromRule, c.categoryId, c.label)
    }

    /**
     * Re-applies the matching rule to one manually-overridden row: sets the rule's category and its
     * label as the merchant, and clears the pin so the rule governs the row again. Amount, note,
     * date, rawSms and dedupKey are left untouched. No-op if the row has nothing to force.
     */
    fun forceRule(id: Long) {
        viewModelScope.launch {
            val e = expenseDao.getExpenseById(id) ?: return@launch
            val target = forcibleRuleFor(e) ?: return@launch
            expenseDao.updateExpense(
                e.copy(categoryId = target.categoryId, merchant = target.merchant, categoryLocked = false)
            )
        }
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
            //
            // Pin the row (categoryLocked) — so rule re-application never overrides this edit — only
            // when this edit DIVERGES the category/merchant from a rule that already matches it.
            // Editing a still-rule-less transaction (or only its amount/note, which leave the
            // category/merchant equal to the rule's outcome) leaves it unpinned, so a rule created
            // later can still apply. This keeps "pinned ⟺ Force auto rule is offered" true.
            val existing = expenseDao.getExpenseById(id)
            val pinned = existing != null && run {
                val c = SmsParser.categorizationFor(existing.rawSms, allRules.value, allCategories.value)
                forcibleRuleOutcome(categoryId, merchant, c.fromRule, c.categoryId, c.label) != null
            }
            expenseDao.updateExpenseFields(id, amount, merchant, categoryId, date, note, pinned)
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

    // Display name for the Home greeting and Profile header; "User" when nobody is signed in.
    // Seeded eagerly so it's correct before the first composition (and before init runs).
    private val _userName = MutableStateFlow(currentUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()

    private fun currentUserName(): String =
        backupManager.signedInName()?.takeIf { it.isNotBlank() } ?: "User"

    private fun refreshUserName() {
        _userName.value = currentUserName()
    }

    private val _lastBackupTime = MutableStateFlow<Long?>(null)
    val lastBackupTime: StateFlow<Long?> = _lastBackupTime.asStateFlow()

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    // ── Automatic backup ──────────────────────────────────────────────────────
    private val _autoBackupFrequency =
        MutableStateFlow(AutoBackupScheduler.getFrequency(application))
    val autoBackupFrequency: StateFlow<AutoBackupFrequency> = _autoBackupFrequency.asStateFlow()

    init {
        // Re-anchor the schedule to the next 2 AM on every launch. Idempotent, and it migrates any
        // pre-existing periodic schedule (which drifted off 2 AM) to the self-rescheduling job.
        AutoBackupScheduler.reapply(application)
    }

    /** Persist the chosen cadence and (re)schedule/cancel the background backup job. */
    fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        AutoBackupScheduler.setFrequency(getApplication(), frequency)
        _autoBackupFrequency.value = frequency
    }

    /** Account-picker intent for the screen's ActivityResult launcher. */
    fun driveSignInIntent(): Intent = backupManager.signInIntent()

    /** Re-read sign-in + last-backup state (call on entering the Backup screen). */
    fun refreshBackupState() {
        _signedInEmail.value = backupManager.signedInEmail()
        refreshUserName()
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
            // Auto-backup can't run without a connected account; cancel its schedule too.
            setAutoBackupFrequency(AutoBackupFrequency.OFF)
            refreshUserName()
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

/** A merchant with no letters (phone/ref number), blank, or the literal "Unknown" placeholder
 *  carries no usable identity — such rows are eligible for re-extraction repair. */
fun isUselessMerchantName(name: String): Boolean =
    name.isBlank() || name.equals("Unknown", ignoreCase = true) || name.none { it.isLetter() }

/** Resolved category + display name for one row during rule re-application. */
data class ReapplyDecision(val categoryId: Long, val merchant: String)

/**
 * Pure decision for re-applying rules to a single SMS-derived row (no DB/IO so it is unit-testable).
 *
 * Priority: rule > manual edit > default — BUT a manual edit made *after* a rule already existed
 * wins and is never overridden. That intent is carried by [userPinned] (the `categoryLocked` flag),
 * which the edit path sets only when a rule already matched the row at edit time.
 *
 * - [userPinned] → the row is returned untouched: the user deliberately customised a transaction the
 *   rule had already handled, so neither category nor name is changed.
 * - Otherwise category: a real user rule ([fromRule]) sets it; else the current category is kept (the
 *   built-in keyword fallback, fromRule = false, must never demote an unmatched row to "Others", and
 *   a manual category on a still-rule-less row is preserved so a rule created later can still apply).
 * - Otherwise merchant: a matching rule's label names the row; else a useless stored merchant (older
 *   imports saved as "Unknown" or a phone/ref number) is repaired from [reextractedMerchant]; else
 *   the existing name is kept.
 */
fun reapplyDecision(
    currentCategoryId: Long,
    currentMerchant: String,
    userPinned: Boolean,
    fromRule: Boolean,
    ruleCategoryId: Long,
    ruleLabel: String?,
    reextractedMerchant: String
): ReapplyDecision {
    if (userPinned) return ReapplyDecision(currentCategoryId, currentMerchant)
    val newCategoryId = if (fromRule && ruleCategoryId != 0L) ruleCategoryId else currentCategoryId
    val newMerchant = when {
        fromRule && !ruleLabel.isNullOrBlank() -> ruleLabel!!
        isUselessMerchantName(currentMerchant) ->
            reextractedMerchant.takeIf { !isUselessMerchantName(it) } ?: currentMerchant
        else -> currentMerchant
    }
    return ReapplyDecision(newCategoryId, newMerchant)
}

/** The category + merchant a "Force auto rule" would apply to a row. */
data class ForcibleRule(val categoryId: Long, val merchant: String)

/**
 * Pure check for whether a row is a manual override of an existing rule, and if so what re-applying
 * that rule would set (no DB/IO so it is unit-testable).
 *
 * Returns null when there is nothing to force: no real user rule matches ([fromRule] false /
 * [ruleCategoryId] 0), or the row already equals its rule's outcome. A non-null result means the
 * stored category/merchant diverge from the rule — which is exactly when the row should be pinned
 * (it's a deliberate override) and when the "Force auto rule" button should be offered.
 *
 * The rule's outcome merchant is its [ruleLabel] when set; a label-less rule only governs category,
 * so the row keeps its current merchant.
 */
fun forcibleRuleOutcome(
    currentCategoryId: Long,
    currentMerchant: String,
    fromRule: Boolean,
    ruleCategoryId: Long,
    ruleLabel: String?
): ForcibleRule? {
    if (!fromRule || ruleCategoryId == 0L) return null
    val targetMerchant = if (!ruleLabel.isNullOrBlank()) ruleLabel else currentMerchant
    return if (currentCategoryId != ruleCategoryId || currentMerchant != targetMerchant)
        ForcibleRule(ruleCategoryId, targetMerchant) else null
}

/** Normalized form of a rule label used for grouping: blank/null collapse to null. */
fun normalizeRuleLabel(label: String?): String? =
    label?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

/** Canonical form of one '|'-separated group, used only for de-dup comparison. */
private fun normalizeKeywordGroup(group: String): String =
    group.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.joinToString(",")

/**
 * Merges two rule keyword strings (each a set of '|'-separated alternative groups) into one,
 * preserving the original display casing of [existing] and appending only those groups from
 * [incoming] not already present (compared case-insensitively, so "Swiggy" won't be re-added
 * next to "swiggy"). Blank groups are dropped.
 */
fun mergeKeywordStrings(existing: String, incoming: String): String {
    val groups = existing.split('|').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    val seen = groups.map { normalizeKeywordGroup(it) }.toMutableSet()
    incoming.split('|').map { it.trim() }.filter { it.isNotEmpty() }.forEach { g ->
        val norm = normalizeKeywordGroup(g)
        if (norm.isNotEmpty() && seen.add(norm)) groups.add(g)
    }
    return groups.joinToString(" | ")
}

/** Survivors (with merged keyword) to update and the now-redundant rule ids to delete. */
data class RuleConsolidation(
    val updates: List<CategoryRule>,
    val deleteIds: List<Long>
)

/**
 * Groups rules by (categoryId, normalized label) and folds each duplicate group into a single
 * survivor (the lowest-id, i.e. earliest-created, rule) whose keyword is the '|'-merge of every
 * member's keywords. Pure (no DB) so it is unit-testable. A group of one yields nothing; a
 * survivor whose keyword is unchanged after merging is omitted from [updates].
 */
fun consolidateRules(rules: List<CategoryRule>): RuleConsolidation {
    val updates = mutableListOf<CategoryRule>()
    val deleteIds = mutableListOf<Long>()
    rules.groupBy { it.categoryId to normalizeRuleLabel(it.label) }.values.forEach { group ->
        if (group.size <= 1) return@forEach
        val sorted = group.sortedBy { it.id }
        val survivor = sorted.first()
        val mergedKeyword = sorted.drop(1).fold(survivor.keyword) { acc, r -> mergeKeywordStrings(acc, r.keyword) }
        if (mergedKeyword != survivor.keyword) updates.add(survivor.copy(keyword = mergedKeyword))
        sorted.drop(1).forEach { deleteIds.add(it.id) }
    }
    return RuleConsolidation(updates, deleteIds)
}
