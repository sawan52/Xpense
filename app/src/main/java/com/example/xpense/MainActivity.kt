package com.example.xpense

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xpense.ui.*
import com.example.xpense.ui.components.AddExpenseBottomSheet
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    companion object {
        // Carries the merchant from a notification tap so the UI can pre-fill the Add-Rule dialog.
        const val EXTRA_RULE_KEYWORD = "rule_keyword"
    }

    // Intents arriving while the activity is alive (singleTop) come through onNewIntent, not a new
    // onCreate; routing both through this flow lets the Compose tree react to a notification tap.
    private val intentFlow = MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentFlow.value = intent
    }

    // Bumped on every resume so the UI re-checks permissions the user may have just granted in
    // Settings (granting from Settings doesn't restart the app, so nothing else would notice).
    private val resumeCount = MutableStateFlow(0)

    override fun onResume() {
        super.onResume()
        resumeCount.value++
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(true)
        intentFlow.value = intent
        setContent {
            val viewModel: ExpenseViewModel = viewModel()
            val dark by viewModel.isDarkTheme.collectAsState()
            LaunchedEffect(dark) { applySystemBars(dark) }

            XpenseTheme(dark = dark) {
                val toast = remember { ToastState() }
                CompositionLocalProvider(LocalToast provides toast) {
                    XpenseApp(viewModel, toast)
                }
            }
        }
    }

    @Composable
    private fun XpenseApp(viewModel: ExpenseViewModel, toast: ToastState) {
        val currentScreen by viewModel.currentScreen.collectAsState()
        val canNavigateBack by viewModel.canNavigateBack.collectAsState()
        val categories by viewModel.allCategories.collectAsState()
        val searchScope by viewModel.searchScope.collectAsState()

        // Global back: pop the navigation history one screen at a time so back retraces the path
        // the user took. Disabled at the HOME root so the system default runs and the app exits.
        // Screen-level handlers (selection mode, search) compose deeper and take priority.
        BackHandler(enabled = canNavigateBack) { viewModel.navigateBack() }

        val context = LocalContext.current
        fun hasPermission(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

        val smsPermissions = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        fun smsGranted() = smsPermissions.all { hasPermission(it) }
        var hasSmsPermission by remember { mutableStateOf(smsGranted()) }
        // True once Android refuses without showing a dialog: "Don't allow" chosen twice, or (for an
        // APK installed from a file on Android 13+) SMS is a restricted setting. Only App info can
        // unblock it then, so the permission screen switches to Settings instructions.
        var smsRequestBlocked by rememberSaveable { mutableStateOf(false) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            hasSmsPermission = results.values.all { it }
            smsRequestBlocked = !hasSmsPermission && smsPermissions.none { shouldShowRequestPermissionRationale(it) }
        }
        LaunchedEffect(Unit) {
            if (!hasSmsPermission) permissionLauncher.launch(smsPermissions)
        }
        val resumes by resumeCount.collectAsState()
        LaunchedEffect(resumes) { hasSmsPermission = smsGranted() }

        // Notification permission is requested separately and its result is intentionally ignored —
        // denial must never block the app, unlike SMS access above.
        val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Route a notification tap into a pre-filled Add-Rule dialog.
        val pendingRuleKeyword by viewModel.pendingRuleKeyword.collectAsState()
        val latestIntent by intentFlow.collectAsState()
        LaunchedEffect(latestIntent) {
            latestIntent?.getStringExtra(EXTRA_RULE_KEYWORD)?.let { keyword ->
                viewModel.requestRulePrefill(keyword)
                // Consume it so a config change / recomposition doesn't re-open the dialog.
                latestIntent?.removeExtra(EXTRA_RULE_KEYWORD)
            }
        }

        var showAddSheet by remember { mutableStateOf(false) }

        GlowBackground(Modifier.fillMaxSize()) {
            if (hasSmsPermission) {
                Crossfade(currentScreen, Modifier.fillMaxSize().statusBarsPadding(), animationSpec = tween(220), label = "screen") { screen ->
                    when (screen) {
                        Screen.HOME            -> SummaryScreen(viewModel)
                        Screen.INSIGHTS        -> ExpenseScreen(viewModel)
                        Screen.INSIGHTS_DETAIL -> InsightsDetailScreen(viewModel)
                        Screen.CATEGORY_DETAIL -> CategoryDetailScreen(viewModel)
                        Screen.PROFILE         -> ProfileScreen(viewModel)
                        Screen.CATEGORY_RULES  -> CategoryRuleScreen(viewModel)
                        Screen.RULE_DETAIL     -> RuleDetailScreen(viewModel)
                        Screen.IGNORED         -> IgnoredTransactionsScreen(viewModel)
                        Screen.BACKUP          -> BackupScreen(viewModel)
                        Screen.NOTIFICATIONS   -> NotificationsScreen(viewModel)
                        Screen.HELP            -> HelpScreen(viewModel)
                    }
                }
                FloatingNavBar(
                    current = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) },
                    onAdd = { showAddSheet = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                // Sync dialogs hoisted here so they appear over any screen that triggers a sync.
                SyncDialogs(viewModel)
            } else {
                PermissionScreen(
                    blocked = smsRequestBlocked,
                    onGrant = { permissionLauncher.launch(smsPermissions) },
                    onOpenSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                    }
                )
            }

            AnimatedVisibility(searchScope != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                // Swallow touches so nothing underneath reacts while search is open.
                Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}) {
                    searchScope?.let { SearchOverlay(viewModel, it) }
                }
            }

            ToastHost(toast, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp))
        }

        if (showAddSheet) {
            AddExpenseBottomSheet(
                categories = categories,
                onDismiss = { showAddSheet = false },
                onConfirm = { amount, merchant, categoryId, date, note ->
                    viewModel.addExpense(amount, merchant, categoryId, date, note)
                    showAddSheet = false
                    toast.show("Expense saved")
                },
                onAddCategory = { name, icon -> viewModel.addCategory(name, icon) }
            )
        }

        EditExpenseHost(viewModel, toast)

        // Opened from a "new uncategorized transaction" notification tap (or the Notifications
        // inbox / edit sheet): the Add-Rule dialog pre-filled with the merchant as the keyword.
        pendingRuleKeyword?.let { keyword ->
            if (hasSmsPermission) {
                DarkAddRuleDialog(
                    categories = categories,
                    initialKeyword = keyword,
                    title = "Create rule for “$keyword”",
                    onDismiss = { viewModel.clearRulePrefill() },
                    onConfirm = { kw, categoryId, label ->
                        viewModel.addRule(kw, categoryId, label)
                        viewModel.clearRulePrefill()
                        toast.show("Rule added")
                    }
                )
            }
        }
    }
}

/** The edit sheet, shared by every screen that lists transactions (see ExpenseViewModel.editExpense). */
@Composable
private fun EditExpenseHost(viewModel: ExpenseViewModel, toast: ToastState) {
    val editingId by viewModel.editingExpenseId.collectAsState()
    val all by viewModel.allExpenses.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val id = editingId ?: return
    val editing = all.find { it.expense.id == id }?.expense ?: return
    fun close() {
        viewModel.closeEditExpense()
        viewModel.exitSelectionMode()
    }
    key(id) {
        val manual = editing.rawSms == "Manual Entry" || editing.rawSms == "Manual Update"
        AddExpenseBottomSheet(
            expense = editing,
            categories = categories,
            onDismiss = { close() },
            onConfirm = { amount, merchant, categoryId, date, note ->
                viewModel.updateExpense(editing.id, amount, merchant, categoryId, date, note)
                close()
                toast.show("Changes saved")
            },
            onAddCategory = { name, icon -> viewModel.addCategory(name, icon) },
            showAddRule = !manual && !viewModel.hasUserRuleFor(editing),
            onAddRule = { close(); viewModel.requestRulePrefill(editing.merchant) },
            showForceRule = viewModel.forcibleRuleFor(editing) != null,
            onForceRule = { close(); viewModel.forceRule(editing.id) },
            onArchive = if (editing.ignored) null else ({ viewModel.setIgnored(editing.id, true); close(); toast.show("Archived") }),
            onDelete = { viewModel.deleteExpense(editing.id); close(); toast.show("Transaction deleted") }
        )
    }
}

// ── Floating bottom navigation ───────────────────────────────────────────────

private data class NavTab(val screen: Screen, val label: String, val icon: ImageVector)

private val leftTabs = listOf(NavTab(Screen.HOME, "Home", Icons.Rounded.Home), NavTab(Screen.INSIGHTS, "Insights", Icons.Rounded.BarChart))
private val rightTabs = listOf(NavTab(Screen.CATEGORY_RULES, "Categories", Icons.Rounded.Category), NavTab(Screen.PROFILE, "Profile", Icons.Rounded.Person))

/** Which tab a (possibly nested) screen belongs to, for the active indicator. */
private fun tabOf(screen: Screen): Screen = when (screen) {
    Screen.INSIGHTS_DETAIL, Screen.CATEGORY_DETAIL -> Screen.INSIGHTS
    Screen.RULE_DETAIL -> Screen.CATEGORY_RULES
    Screen.IGNORED, Screen.BACKUP, Screen.HELP -> Screen.PROFILE
    else -> screen
}

@Composable
private fun FloatingNavBar(current: Screen, onNavigate: (Screen) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = XpenseTheme.colors
    val active = tabOf(current)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp).height(86.dp)) {
        val shape = RoundedCornerShape(26.dp)
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(72.dp)
                .shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape).background(c.nav).border(1.dp, c.line, shape),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftTabs.forEach { NavItem(it, it.screen == active, Modifier.weight(1f)) { onNavigate(it.screen) } }
            Spacer(Modifier.width(76.dp))
            rightTabs.forEach { NavItem(it, it.screen == active, Modifier.weight(1f)) { onNavigate(it.screen) } }
        }
        val fab = RoundedCornerShape(20.dp)
        Box(
            Modifier.align(Alignment.TopCenter).size(72.dp).clip(RoundedCornerShape(26.dp)).background(c.bg).padding(6.dp)
                .shadow(16.dp, fab, ambientColor = c.ac, spotColor = c.ac)
                .clip(fab).background(Brush.linearGradient(listOf(c.ac, c.ac2)))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center
        ) { XIcon(Icons.Rounded.Add, 30.dp, Color.White) }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    val tint = if (selected) c.ac else c.tx2
    Column(
        modifier.fillMaxHeight().clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        XIcon(tab.icon, 24.dp, tint)
        Text(tab.label, style = XType.nav, color = tint)
        Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) c.ac else Color.Transparent))
    }
}

@Composable
private fun PermissionScreen(blocked: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    val c = XpenseTheme.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        DialogBadge(Icons.Rounded.Sms, size = 72.dp)
        Text("SMS access needed", style = XType.h2, color = c.tx)
        Text(
            "Xpense reads your bank's transaction SMS to track spending automatically. Messages never leave your phone.",
            style = XType.body, color = c.tx2, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (blocked) {
            // Android won't show the permission dialog any more; only App info can grant it.
            GlassCard(Modifier.fillMaxWidth(), radius = 18.dp, contentPadding = PaddingValues(16.dp)) {
                Text("Allow SMS from Settings", style = XType.bodyStrong, color = c.tx)
                listOf(
                    "Tap Open app settings below.",
                    "If SMS is greyed out, tap ⋮ (top right) → Allow restricted settings first.",
                    "Tap Permissions → SMS → Allow.",
                    "Come back here and Xpense opens automatically."
                ).forEachIndexed { i, step ->
                    Text("${i + 1}.  $step", style = XType.small, color = c.tx2, modifier = Modifier.padding(top = 8.dp))
                }
            }
            XButton("Open app settings", onOpenSettings, Modifier.padding(top = 8.dp).fillMaxWidth(), icon = Icons.Rounded.Settings, height = 54.dp, radius = 18.dp)
            XButton("Try again", onGrant, Modifier.fillMaxWidth(), style = BtnStyle.Secondary, height = 48.dp, radius = 18.dp)
        } else {
            XButton("Grant permission", onGrant, Modifier.padding(top = 8.dp).fillMaxWidth(), height = 54.dp, radius = 18.dp)
        }
    }
}
