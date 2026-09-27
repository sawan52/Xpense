package com.example.xpense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.Category
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils

/**
 * The keyword list for a single auto-rule, reached by tapping a rule on the Auto-Rules tab.
 *
 * Replaces editing a rule through one text field holding the whole `|`-separated keyword string —
 * with 56 alternatives in a 1,115-character field, a single deleted separator silently fused two
 * keywords into one that matched nothing. Here each alternative is its own row, added, edited and
 * removed individually, so the separators are never typed by hand.
 */
@Composable
fun RuleDetailScreen(viewModel: ExpenseViewModel) {
    val c = XpenseTheme.colors
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
    if (rule == null) return

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

    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        contentPadding = screenPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            val title = if (selectionMode) "${selectedIndices.size} selected" else rule.label?.takeIf { it.isNotBlank() } ?: "Rule"
            BackHeader(
                title,
                { if (selectionMode) exitSelection() else viewModel.navigateBack() },
                subtitle = "${category.name} · ${keywords.size} keyword${if (keywords.size == 1) "" else "s"}",
                titleStyle = XType.h3
            ) {
                if (selectionMode) {
                    HeaderButton(
                        if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                        { selectedIndices = if (allSelected) emptySet() else keywords.indices.toSet() },
                        tint = c.ac, contentDescription = if (allSelected) "Deselect all" else "Select all"
                    )
                    HeaderButton(
                        Icons.Rounded.Delete, { if (selectedIndices.isNotEmpty()) showDeleteSelected = true },
                        tint = if (selectedIndices.isNotEmpty()) c.neg else c.tx3, contentDescription = "Delete selected"
                    )
                } else {
                    Box {
                        HeaderButton(Icons.Rounded.MoreHoriz, { menuExpanded = true }, contentDescription = "More options")
                        DropdownMenu(
                            expanded = menuExpanded, onDismissRequest = { menuExpanded = false },
                            shape = RoundedCornerShape(18.dp), containerColor = c.bg2, border = BorderStroke(1.dp, c.line)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename rule", style = XType.smallStrong, color = c.tx) },
                                leadingIcon = { XIcon(Icons.Rounded.Edit, 18.dp, c.tx2) },
                                onClick = { menuExpanded = false; showRename = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Change category", style = XType.smallStrong, color = c.tx) },
                                leadingIcon = { XIcon(CategoryUtils.getCategoryIcon(category), 18.dp, color) },
                                onClick = { menuExpanded = false; showCategoryPicker = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete rule", style = XType.smallStrong, color = c.neg) },
                                leadingIcon = { XIcon(Icons.Rounded.Delete, 18.dp, c.neg) },
                                onClick = { menuExpanded = false; showDeleteRule = true }
                            )
                        }
                    }
                }
            }
        }

        // ── Add a keyword (hidden while selecting) ────────────────────────
        if (!selectionMode) {
            item {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XTextField(newKeyword, { newKeyword = it }, "Add a keyword", Modifier.weight(1f), fill = c.card, height = 50.dp)
                    val canAdd = newKeyword.isNotBlank()
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(if (canAdd) c.ac else c.card2)
                            .clickable(enabled = canAdd) { viewModel.addKeyword(rule, newKeyword); newKeyword = "" },
                        contentAlignment = Alignment.Center
                    ) { XIcon(Icons.Rounded.Add, 22.dp, if (canAdd) Color.White else c.tx3) }
                }
            }
            item {
                Text(
                    "Tip: use , for all-must-match and | for alternatives. Tap a keyword to edit it; long-press to select and delete.",
                    style = XType.caption, color = c.tx2, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // ── Keyword rows ──────────────────────────────────────────────────
        itemsIndexed(keywords, key = { index, kw -> "$index-$kw" }) { index, keyword ->
            if (editingIndex == index && !selectionMode) {
                KeywordEditRow(
                    value = editingText,
                    onValueChange = { editingText = it },
                    onConfirm = { viewModel.updateKeywordAt(rule, index, editingText); editingIndex = null },
                    onCancel = { editingIndex = null }
                )
            } else {
                KeywordRow(
                    keyword = keyword,
                    selectionMode = selectionMode,
                    selected = index in selectedIndices,
                    onClick = {
                        if (selectionMode) {
                            selectedIndices = if (index in selectedIndices) selectedIndices - index else selectedIndices + index
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
            title = "Delete this rule?",
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
private fun KeywordRow(keyword: String, selectionMode: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val c = XpenseTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) c.acSoft else c.card)
            .border(1.dp, if (selected) c.ac else c.line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        XIcon(Icons.Rounded.Key, 18.dp, c.ac)
        Column(Modifier.weight(1f)) {
            Text(keyword, style = XType.mono.copy(fontSize = XType.small.fontSize, fontWeight = FontWeight.Normal), color = c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // A comma is an AND-group: every term has to appear in the message.
            if (keyword.contains(',')) Text("all words must match", style = XType.micro, color = c.tx2)
        }
        if (selectionMode) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(if (selected) c.ac else Color.Transparent)
                    .border(1.5.dp, if (selected) c.ac else c.tx3, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (selected) XIcon(Icons.Rounded.Check, 15.dp, Color.White) }
        } else {
            XIcon(Icons.Rounded.Edit, 17.dp, c.tx3)
        }
    }
}

/** The same row while being edited in place. */
@Composable
private fun KeywordEditRow(value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = XpenseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XTextField(value, onValueChange, "Keyword", Modifier.weight(1f), leadingIcon = Icons.Rounded.Key, leadingTint = c.ac, mono = true, fill = c.acSoft, height = 50.dp)
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (value.isNotBlank()) c.pos.copy(alpha = 0.16f) else c.card2)
                .clickable(enabled = value.isNotBlank(), onClick = onConfirm),
            contentAlignment = Alignment.Center
        ) { XIcon(Icons.Rounded.Check, 20.dp, if (value.isNotBlank()) c.pos else c.tx3) }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.card2).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) { XIcon(Icons.Rounded.Close, 20.dp, c.tx2) }
    }
}

@Composable
private fun RenameRuleDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    XDialog(onDismiss) {
        DialogBadge(Icons.AutoMirrored.Rounded.Label)
        DialogTitle("Rename rule", "Transactions matched by this rule are shown under this name.")
        XTextField(name, { name = it }, "Display name")
        DialogButtons("Save", { onConfirm(name) }, onDismiss, confirmEnabled = name.isNotBlank())
    }
}

@Composable
private fun CategoryPickerDialog(categories: List<Category>, selectedId: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val c = XpenseTheme.colors
    XDialog(onDismiss) {
        DialogTitle("Change category")
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { cat ->
                val on = cat.id == selectedId
                val col = CategoryUtils.getCategoryColor(cat)
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(if (on) c.acSoft else c.card2)
                        .border(1.dp, if (on) c.ac else Color.Transparent, shape)
                        .clickable { onPick(cat.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconTile(CategoryUtils.getCategoryIcon(cat), col, size = 32.dp, radius = 10.dp, iconSize = 17.dp)
                    Text(cat.name, style = XType.bodyStrong, color = c.tx, modifier = Modifier.weight(1f))
                    if (on) XIcon(Icons.Rounded.Check, 18.dp, c.ac)
                }
            }
        }
        XButton("Cancel", onDismiss, Modifier.fillMaxWidth(), style = BtnStyle.Secondary)
    }
}
