package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import com.example.xpense.ui.utils.CurrencyUtils
import java.util.*

@Composable
fun CategoryRuleScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
    val rules      by viewModel.allRules.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val active     by viewModel.activeExpenses.collectAsState()
    // Tab lives in the ViewModel so it survives opening a rule (which destroys this screen).
    val selectedTab by viewModel.ruleScreenTab.collectAsState()
    val expanded    by viewModel.expandedRuleCategories.collectAsState()
    val toast = LocalToast.current

    var showAddRule by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    var showMergeConfirm by remember { mutableStateOf(false) }

    // Back returns to the Categories sub-tab before leaving the screen.
    BackHandler(enabled = selectedTab == 1) { viewModel.selectRuleScreenTab(0) }

    val reapplyResult by viewModel.reapplyResult.collectAsState()
    LaunchedEffect(reapplyResult) {
        reapplyResult?.let { n ->
            toast.show(if (n == 0) "All transactions already match" else "Recategorized $n transaction${if (n == 1) "" else "s"}")
            viewModel.clearReapplyResult()
        }
    }
    val mergeResult by viewModel.mergeResult.collectAsState()
    LaunchedEffect(mergeResult) {
        mergeResult?.let { n ->
            toast.show(if (n == 0) "No duplicate rules to merge" else "Merged $n duplicate rule${if (n == 1) "" else "s"}")
            viewModel.clearMergeResult()
        }
    }

    // This month's spend per category for the tiles.
    val monthSpend = remember(active) {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH)
        active.filter { cal.timeInMillis = it.expense.date; cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) == m }
            .groupBy { it.category.id }.mapValues { (_, l) -> l.sumOf { it.expense.amount } }
    }
    // Busiest categories first, then alphabetical; rules inside a section sorted by display name.
    val grouped = remember(rules, categories) {
        rules.groupBy { it.categoryId }
            .map { (catId, catRules) ->
                (categories.find { it.id == catId } ?: Category(id = catId, name = "Unknown", iconName = "Category")) to
                    catRules.sortedBy { (it.label ?: it.keyword).lowercase() }
            }
            .sortedWith(compareByDescending<Pair<Category, List<CategoryRule>>> { it.second.size }.thenBy { it.first.name.lowercase() })
    }
    val duplicateCount = remember(rules) { consolidateRules(rules).deleteIds.size }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = screenPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TitleHeader("Categories") {
                HeaderButton(Icons.Rounded.Search, { viewModel.openSearch(if (selectedTab == 0) SearchScope.CATEGORIES else SearchScope.RULES) }, contentDescription = "Search")
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.height(42.dp).shadow(12.dp, shape, ambientColor = c.ac, spotColor = c.ac).clip(shape).background(c.ac)
                        .clickable { if (selectedTab == 0) showAddCategory = true else showAddRule = true }
                        .padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    XIcon(Icons.Rounded.Add, 20.dp, Color.White)
                    Text(if (selectedTab == 0) "Category" else "Rule", style = XType.smallStrong, color = Color.White)
                }
            }
        }
        item {
            SegmentedControl(listOf("Categories", "Auto-Rules"), selectedTab, { viewModel.selectRuleScreenTab(it) }, Modifier.padding(top = 8.dp, bottom = 8.dp))
        }

        if (selectedTab == 0) {
            items(categories.chunked(2), key = { row -> row.first().id }) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { cat ->
                        CategoryTile(
                            cat, monthSpend[cat.id] ?: 0.0, Modifier.weight(1f),
                            canDelete = !cat.name.equals("Others", ignoreCase = true),
                            onOpen = { viewModel.openCategory(cat.id) },
                            onEdit = { editingCategory = cat },
                            onDelete = { deletingCategory = cat }
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            item {
                val shape = RoundedCornerShape(22.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(c.acSoft, Color.Transparent)))
                        .border(1.dp, c.line, shape).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconTile(Icons.Rounded.Autorenew, Color.White, size = 42.dp, radius = 14.dp, iconSize = 21.dp, background = c.ac)
                    Column(Modifier.weight(1f)) {
                        Text("Re-apply rules", style = XType.bodyStrong, color = c.tx)
                        Text("Recategorize past transactions", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                    }
                    XButton("Run", { viewModel.reapplyRulesToExistingTransactions() }, style = BtnStyle.Inverse, height = 34.dp, radius = 12.dp, textStyle = XType.captionStrong)
                }
            }
            if (duplicateCount > 0) {
                item {
                    GlassCard(Modifier.fillMaxWidth(), radius = 22.dp, contentPadding = PaddingValues(16.dp), onClick = { showMergeConfirm = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile(Icons.AutoMirrored.Rounded.MergeType, c.ac, size = 42.dp, radius = 14.dp, iconSize = 21.dp, background = c.acSoft)
                            Column(Modifier.weight(1f)) {
                                Text("Merge $duplicateCount duplicate rule${if (duplicateCount == 1) "" else "s"}", style = XType.bodyStrong, color = c.tx)
                                Text("Same category & display name", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
            items(grouped, key = { "g_${it.first.id}" }) { (category, catRules) ->
                RuleGroupCard(category, catRules, category.id in expanded, { viewModel.toggleRuleCategory(category.id) }) { viewModel.openRule(it.id) }
            }
            if (rules.isEmpty()) {
                item { EmptyState(Icons.Rounded.Rule, "No auto-rules yet", "Rules map SMS keywords to a category automatically.", actionText = "Add a rule", onAction = { showAddRule = true }) }
            }
        }
    }

    if (showAddRule) {
        DarkAddRuleDialog(categories = categories, onDismiss = { showAddRule = false }, onConfirm = { kw, catId, label ->
            viewModel.addRule(kw, catId, label); showAddRule = false; toast.show("Rule added")
        })
    }
    if (showMergeConfirm) {
        ConfirmDialog(
            title = "Merge duplicate rules?",
            message = "Rules sharing a category and display name will be combined into one (keywords joined with “|”). Existing transactions are unaffected.",
            confirmLabel = "Merge",
            icon = Icons.AutoMirrored.Rounded.MergeType,
            destructive = false,
            onConfirm = { viewModel.mergeDuplicateRules(); showMergeConfirm = false },
            onDismiss = { showMergeConfirm = false }
        )
    }
    if (showAddCategory) {
        CategoryEditorDialog(onDismiss = { showAddCategory = false }, onConfirm = { name, icon ->
            viewModel.addCategory(name, icon); showAddCategory = false; toast.show("Category created")
        })
    }
    editingCategory?.let { cat ->
        CategoryEditorDialog(cat.name, cat.iconName, editing = true, color = CategoryUtils.getCategoryColor(cat), onDismiss = { editingCategory = null }, onConfirm = { name, icon ->
            viewModel.updateCategory(cat.id, name, icon); editingCategory = null
        })
    }
    deletingCategory?.let { cat ->
        ConfirmDialog(
            title = "Delete “${cat.name}”?",
            message = "Its transactions and auto-rules will move to “Others”.",
            onConfirm = { viewModel.deleteCategory(cat); deletingCategory = null },
            onDismiss = { deletingCategory = null }
        )
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    spent: Double,
    modifier: Modifier,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = XpenseTheme.colors
    val color = CategoryUtils.getCategoryColor(category)
    var menu by remember { mutableStateOf(false) }
    GlassCard(
        modifier, radius = 22.dp, contentPadding = PaddingValues(16.dp),
        borderColor = if (menu) c.ac else c.line,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        onClick = onOpen,
        decoration = Modifier.topRightGlow(color, 0.25f, 55.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(CategoryUtils.getCategoryIcon(category), color, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f))
            Box {
                Box(
                    Modifier.offset(x = 8.dp, y = (-6).dp).size(32.dp).clip(RoundedCornerShape(11.dp))
                        .background(if (menu) c.acSoft else Color.Transparent).clickable { menu = true },
                    contentAlignment = Alignment.Center
                ) { XIcon(Icons.Rounded.MoreHoriz, 20.dp, if (menu) c.ac else c.tx2) }
                DropdownMenu(
                    expanded = menu, onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(18.dp), containerColor = c.bg2, border = BorderStroke(1.dp, c.line),
                    offset = DpOffset(0.dp, 4.dp)
                ) {
                    MenuRow(Icons.Rounded.Edit, "Edit category") { menu = false; onEdit() }
                    MenuRow(Icons.Rounded.ReceiptLong, "View transactions") { menu = false; onOpen() }
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(160.dp).height(1.dp).background(c.line))
                    MenuRow(Icons.Rounded.Delete, if (canDelete) "Delete" else "Can't delete", tint = if (canDelete) c.neg else c.tx3, enabled = canDelete) { menu = false; onDelete() }
                }
            }
        }
        Column {
            Text(category.name, style = XType.bodyStrong, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(CurrencyUtils.rupees(spent), style = XType.monoS, color = c.tx2, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = XpenseTheme.colors.tx, enabled: Boolean = true, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    DropdownMenuItem(
        text = { Text(text, style = XType.smallStrong, color = tint) },
        leadingIcon = { XIcon(icon, 18.dp, if (tint == c.tx) c.tx2 else tint) },
        onClick = onClick,
        enabled = enabled
    )
}

@Composable
private fun RuleGroupCard(category: Category, rules: List<CategoryRule>, open: Boolean, onToggle: () -> Unit, onRule: (CategoryRule) -> Unit) {
    val c = XpenseTheme.colors
    val color = CategoryUtils.getCategoryColor(category)
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(CategoryUtils.getCategoryIcon(category), color, size = 38.dp, radius = 12.dp, iconSize = 19.dp)
            Text(category.name, style = XType.bodyStrong, color = c.tx, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rules.size.toString(), style = XType.monoS, color = c.tx2, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.card2).padding(horizontal = 8.dp, vertical = 3.dp))
            XIcon(Icons.Rounded.ExpandMore, 20.dp, c.tx2, Modifier.rotate(rot))
        }
        if (open) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rules.forEach { rule ->
                    val n = splitAlternatives(rule.keyword).size
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card2).clickable { onRule(rule) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label?.takeIf { it.isNotBlank() } ?: rule.keyword, style = XType.smallStrong, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$n keyword${if (n == 1) "" else "s"}", style = XType.micro, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
                        }
                        XIcon(Icons.Rounded.ChevronRight, 18.dp, c.ac)
                    }
                }
            }
        }
    }
}

/** "Map keyword" dialog: keywords (with `,` / `|` syntax), optional display name, category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DarkAddRuleDialog(
    categories: List<Category>,
    initialKeyword: String = "",
    initialLabel: String = "",
    initialCategoryId: Long? = null,
    title: String = "Map keyword",
    confirmLabel: String = "Add rule",
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String?) -> Unit
) {
    val c = XpenseTheme.colors
    var keyword by remember { mutableStateOf(initialKeyword) }
    var label by remember { mutableStateOf(initialLabel) }
    var selectedCategoryId by remember {
        mutableStateOf(initialCategoryId ?: categories.firstOrNull { it.name.equals("Others", true).not() }?.id ?: categories.firstOrNull()?.id ?: 0L)
    }
    XDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.Rule, c.ac, size = 48.dp, radius = 16.dp, iconSize = 24.dp, background = c.acSoft)
            Column {
                Text(title, style = XType.h3.copy(fontSize = XType.h3.fontSize * 0.95f), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Auto-categorize matching SMS", style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            XTextField(keyword, { keyword = it }, "Keywords", leadingIcon = Icons.Rounded.Key, leadingTint = c.ac, mono = true)
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SyntaxKey(","); Text("all must match", style = XType.micro, color = c.tx2)
                Spacer(Modifier.width(6.dp))
                SyntaxKey("|"); Text("alternatives", style = XType.micro, color = c.tx2)
            }
        }
        XTextField(label, { label = it }, "Display name (optional, e.g. MF SIP)", leadingIcon = Icons.AutoMirrored.Rounded.Label)
        Overline("Category", Modifier.offset(x = (-4).dp))
        FlowRow(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { cat ->
                CategoryChip(cat.name, CategoryUtils.getCategoryIcon(cat), CategoryUtils.getCategoryColor(cat), cat.id == selectedCategoryId, { selectedCategoryId = cat.id })
            }
        }
        DialogButtons(confirmLabel, { onConfirm(keyword.trim(), selectedCategoryId, label.ifBlank { null }) }, onDismiss, confirmEnabled = keyword.isNotBlank())
    }
}

@Composable
private fun SyntaxKey(k: String) {
    val c = XpenseTheme.colors
    Text(k, style = XType.monoS, color = c.tx, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.card2).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** Create or edit a category: live preview, name, and an icon grid. */
@Composable
fun CategoryEditorDialog(
    initialName: String = "",
    initialIcon: String = "Category",
    editing: Boolean = false,
    color: Color? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val c = XpenseTheme.colors
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    // A new category's colour is assigned from its id once saved, so preview it in the accent.
    val preview = color ?: c.ac
    XDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(56.dp).shadow(12.dp, RoundedCornerShape(18.dp), ambientColor = preview, spotColor = preview)
                    .clip(RoundedCornerShape(18.dp)).background(c.bg2).background(preview.copy(alpha = 0.14f)).border(1.dp, preview, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) { XIcon(CategoryUtils.getIconByName(icon), 28.dp, preview) }
            Column {
                Text(if (editing) "Edit category" else "New category", style = XType.h3, color = c.tx)
                Text(name.ifBlank { "Category name" }, style = XType.caption, color = c.tx2, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        XTextField(name, { name = it }, "Category name")
        Overline("Icon", Modifier.offset(x = (-4).dp))
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.height(196.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(CategoryUtils.availableIcons) { ic ->
                val on = ic == icon
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.aspectRatio(1f).clip(shape).background(if (on) c.acSoft else c.card2).border(1.dp, if (on) c.ac else c.line, shape).clickable { icon = ic },
                    contentAlignment = Alignment.Center
                ) { XIcon(CategoryUtils.getIconByName(ic), 22.dp, if (on) c.ac else c.tx2) }
            }
        }
        DialogButtons(if (editing) "Save" else "Create category", { onConfirm(name.trim(), icon) }, onDismiss, confirmEnabled = name.isNotBlank())
    }
}

/** Kept for callers that create a category inline (the expense sheet). */
@Composable
fun DarkAddCategoryDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) =
    CategoryEditorDialog(onDismiss = onDismiss, onConfirm = onConfirm)
