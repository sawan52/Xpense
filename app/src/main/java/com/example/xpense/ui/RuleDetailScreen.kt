package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils

/**
 * The keyword list for a single auto-rule, reached by tapping a rule on the Auto-Rules tab.
 *
 * Replaces editing a rule through one text field holding the whole `|`-separated keyword string —
 * with 56 alternatives in a 1,115-character field, a single deleted separator silently fused two
 * keywords into one that matched nothing. Here each alternative is its own row, added, edited and
 * removed individually, so the separators are never typed by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(viewModel: ExpenseViewModel) {
    val rules by viewModel.allRules.collectAsState()
    val categories by viewModel.allCategories.collectAsState()
    val selectedId by viewModel.selectedRuleId.collectAsState()

    val rule = rules.find { it.id == selectedId }

    // The rule can disappear underneath us (deleted from here, or a Merge/Restore elsewhere). Only
    // treat it as gone once the rules flow has actually loaded, so a cold empty emission can't
    // bounce the user straight back out.
    LaunchedEffect(rule == null, rules.isEmpty()) {
        if (rule == null && rules.isNotEmpty()) viewModel.navigateBack()
    }
    if (rule == null) {
        Box(Modifier.fillMaxSize().background(DarkBg))
        return
    }

    val category = categories.find { it.id == rule.categoryId }
        ?: Category(id = rule.categoryId, name = "Unknown", iconName = "Category")
    val keywords = splitAlternatives(rule.keyword)
    val color = CategoryUtils.getCategoryColor(category)

    var menuExpanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteRule by remember { mutableStateOf(false) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var newKeyword by remember { mutableStateOf("") }

    // Index of the row being edited in place, plus its working text.
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }

    // Deleting is deliberately behind long-press multi-select — the same gesture the transaction
    // lists use — so a stray tap can never remove a keyword. There is no per-row delete button.
    // Selection mode is its own flag rather than "something is selected", so clearing the last
    // checkbox (or toggling Select all off) leaves you in the mode to pick different rows instead
    // of throwing you out of it.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val allSelected = keywords.isNotEmpty() && selectedIndices.size >= keywords.size

    fun exitSelection() {
        selectionMode = false
        selectedIndices = emptySet()
    }

    // Takes priority over MainActivity's navigation BackHandler while selecting, so back clears the
    // selection first instead of leaving the screen.
    BackHandler(enabled = selectionMode) { exitSelection() }

    // A rule edit rebuilds the list, so drop any in-progress edit or selection rather than let them
    // point at rows that have shifted.
    LaunchedEffect(rule.keyword) {
        editingIndex = null
        exitSelection()
    }

    Scaffold(
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "${selectedIndices.size} selected"
                        else rule.label?.takeIf { it.isNotBlank() } ?: "Rule",
                        color = TextPrimary, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selectionMode) exitSelection() else viewModel.navigateBack() }
                    ) {
                        Icon(
                            if (selectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            if (selectionMode) "Cancel selection" else "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    if (selectionMode) {
                        // Toggles: select every keyword, or clear the selection when all are on.
                        IconButton(
                            onClick = {
                                selectedIndices =
                                    if (allSelected) emptySet() else keywords.indices.toSet()
                            }
                        ) {
                            Icon(
                                if (allSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                if (allSelected) "Deselect all" else "Select all",
                                tint = PurpleLight
                            )
                        }
                        val canDelete = selectedIndices.isNotEmpty()
                        IconButton(onClick = { showDeleteSelected = true }, enabled = canDelete) {
                            Icon(
                                Icons.Default.Delete, "Delete selected",
                                tint = if (canDelete) RedNegative else TextMuted
                            )
                        }
                    } else {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "More options", tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = DarkSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename rule", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Edit, null, tint = PurpleLight, modifier = Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; showRename = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Change category", color = TextPrimary) },
                                leadingIcon = { Icon(CategoryUtils.getCategoryIcon(category), null, tint = color, modifier = Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; showCategoryPicker = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete rule", color = RedNegative) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = RedNegative, modifier = Modifier.size(18.dp)) },
                                onClick = { menuExpanded = false; showDeleteRule = true }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Category + count ──────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(color.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(CategoryUtils.getCategoryIcon(category), null, tint = color, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        "${category.name} • ${keywords.size} keyword${if (keywords.size == 1) "" else "s"}",
                        color = TextSecondary, fontSize = 13.sp
                    )
                }
            }

            // ── Add a keyword (hidden while selecting) ────────────────────────
            if (!selectionMode) item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("Add a keyword", color = TextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = ruleFieldColors(),
                        modifier = Modifier.weight(1f)
                    )
                    val canAdd = newKeyword.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (canAdd) PurplePrimary else DarkSurface)
                            .clickable(enabled = canAdd) {
                                viewModel.addKeyword(rule, newKeyword)
                                newKeyword = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add, "Add keyword",
                            tint = if (canAdd) androidx.compose.ui.graphics.Color.White else TextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ── Keyword rows ──────────────────────────────────────────────────
            itemsIndexed(keywords, key = { index, kw -> "$index-$kw" }) { index, keyword ->
                if (editingIndex == index && !selectionMode) {
                    KeywordEditRow(
                        value = editingText,
                        onValueChange = { editingText = it },
                        onConfirm = {
                            viewModel.updateKeywordAt(rule, index, editingText)
                            editingIndex = null
                        },
                        onCancel = { editingIndex = null }
                    )
                } else {
                    KeywordRow(
                        keyword = keyword,
                        selectionMode = selectionMode,
                        selected = index in selectedIndices,
                        onClick = {
                            if (selectionMode) {
                                selectedIndices = if (index in selectedIndices)
                                    selectedIndices - index else selectedIndices + index
                            } else {
                                editingIndex = index
                                editingText = keyword
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedIndices = setOf(index)
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Delete the selected keywords (or the whole rule, if that's all of them) ──
    if (showDeleteSelected) {
        val count = selectedIndices.size
        val all = allSelected
        ConfirmDialog(
            title = if (all) "Delete rule?" else "Delete $count keyword${if (count == 1) "" else "s"}?",
            message = if (all)
                "That's every keyword in this rule, so the whole rule will be deleted. " +
                    "Existing transactions keep their current category."
            else
                "Transactions matched only by " +
                    (if (count == 1) "this keyword" else "these keywords") +
                    " will no longer be categorized by this rule.",
            confirmLabel = "Delete",
            onConfirm = {
                if (all) {
                    viewModel.deleteRule(rule.id)
                    viewModel.navigateBack()
                } else {
                    viewModel.removeKeywordsAt(rule, selectedIndices)
                }
                exitSelection()
                showDeleteSelected = false
            },
            onDismiss = { showDeleteSelected = false }
        )
    }

    if (showDeleteRule) {
        ConfirmDialog(
            title = "Delete rule",
            message = "Delete \"${rule.label ?: rule.keyword}\" and all ${keywords.size} of its " +
                "keywords? Existing transactions keep their current category.",
            onConfirm = {
                showDeleteRule = false
                viewModel.deleteRule(rule.id)
                viewModel.navigateBack()
            },
            onDismiss = { showDeleteRule = false }
        )
    }

    if (showRename) {
        RenameRuleDialog(
            initial = rule.label ?: "",
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.updateRule(rule.id, rule.keyword, rule.categoryId, name)
                showRename = false
            }
        )
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            selectedId = rule.categoryId,
            onDismiss = { showCategoryPicker = false },
            onPick = { categoryId ->
                viewModel.updateRule(rule.id, rule.keyword, categoryId, rule.label)
                showCategoryPicker = false
            }
        )
    }
}

/**
 * A stored keyword, shown exactly as it is stored so what you read is what you edit.
 *
 * Tap edits it in place; long-press starts a multi-select for deletion — the same gesture pair the
 * transaction lists use. There is deliberately no per-row delete button: one mis-tap used to be
 * enough to drop a keyword out of a rule silently.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeywordRow(
    keyword: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) PurplePrimary.copy(alpha = 0.18f) else DarkCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(keyword, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // A comma is an AND-group: every term has to appear in the message. Rare (2 of 144
            // rules), so it is called out rather than given its own syntax in the UI.
            if (keyword.contains(',')) {
                Text("all words must match", color = TextMuted, fontSize = 11.sp)
            }
        }
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = PurplePrimary)
            )
        } else {
            IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Edit, "Edit keyword", tint = PurpleLight, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** The same row while being edited in place. */
@Composable
private fun KeywordEditRow(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = ruleFieldColors(),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onConfirm, enabled = value.isNotBlank(), modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Check, "Save keyword",
                tint = if (value.isNotBlank()) GreenPositive else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Close, "Cancel", tint = TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RenameRuleDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Rename rule", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display name", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = ruleFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Transactions matched by this rule are shown under this name.",
                    color = TextMuted, fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedId: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text("Change category", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                categories.forEach { cat ->
                    val isSel = cat.id == selectedId
                    val c = CategoryUtils.getCategoryColor(cat)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) PurplePrimary.copy(alpha = 0.15f) else DarkSurface)
                            .clickable { onPick(cat.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(c.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(CategoryUtils.getCategoryIcon(cat), null, tint = c, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            cat.name,
                            color = if (isSel) PurpleLight else TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSel) Icon(Icons.Default.Check, null, tint = PurpleLight, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ruleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurplePrimary,
    unfocusedBorderColor = DarkBorder,
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = PurpleLight
)
