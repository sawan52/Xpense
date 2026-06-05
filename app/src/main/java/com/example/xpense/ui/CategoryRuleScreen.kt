package com.example.xpense.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.CategoryRule
import com.example.xpense.ui.components.ConfirmDialog
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRuleScreen(viewModel: ExpenseViewModel) {
    val rules      by viewModel.allRules.collectAsState()
    val categories by viewModel.allCategories.collectAsState()

    var selectedTab          by remember { mutableIntStateOf(0) }
    var showAddRuleDialog    by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory      by remember { mutableStateOf<Category?>(null) }
    var deletingCategory     by remember { mutableStateOf<Category?>(null) }
    var editingRule          by remember { mutableStateOf<CategoryRule?>(null) }
    var deletingRule         by remember { mutableStateOf<CategoryRule?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val reapplyResult by viewModel.reapplyResult.collectAsState()
    LaunchedEffect(reapplyResult) {
        reapplyResult?.let { count ->
            snackbarHostState.showSnackbar(
                if (count == 0) "All transactions already match the rules"
                else "Recategorized $count transaction${if (count == 1) "" else "s"}"
            )
            viewModel.clearReapplyResult()
        }
    }

    Scaffold(
        containerColor = DarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateTo(Screen.PROFILE) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(PurplePrimary, PurpleLight)))
                    .clickable {
                        if (selectedTab == 0) showAddCategoryDialog = true else showAddRuleDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkBg,
                contentColor = PurpleLight
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    selectedContentColor = PurpleLight, unselectedContentColor = TextMuted) {
                    Text("Categories", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    selectedContentColor = PurpleLight, unselectedContentColor = TextMuted) {
                    Text("Auto-Rules", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                }
            }

            if (selectedTab == 1) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(PurplePrimary.copy(alpha = 0.12f))
                                .clickable { viewModel.reapplyRulesToExistingTransactions() }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = PurpleLight, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Re-apply rules", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Recategorize existing transactions with these rules", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                    items(rules, key = { it.id }) { rule ->
                        val category = categories.find { it.id == rule.categoryId }
                            ?: Category(name = "Unknown", iconName = "Category")
                        DarkRuleItem(
                            rule = rule,
                            category = category,
                            onEdit = { editingRule = rule },
                            onDelete = { deletingRule = rule }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        DarkCategoryItem(
                            category = category,
                            canDelete = !category.name.equals("Others", ignoreCase = true),
                            onEdit = { editingCategory = category },
                            onDelete = { deletingCategory = category }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        DarkAddRuleDialog(
            categories = categories,
            onDismiss = { showAddRuleDialog = false },
            onConfirm = { keyword, categoryId, label ->
                viewModel.addRule(keyword, categoryId, label)
                showAddRuleDialog = false
            }
        )
    }

    editingRule?.let { rule ->
        DarkAddRuleDialog(
            categories = categories,
            initialKeyword = rule.keyword,
            initialLabel = rule.label ?: "",
            initialCategoryId = rule.categoryId,
            title = "Edit Rule",
            confirmLabel = "Save",
            onDismiss = { editingRule = null },
            onConfirm = { keyword, categoryId, label ->
                viewModel.updateRule(rule.id, keyword, categoryId, label)
                editingRule = null
            }
        )
    }

    deletingRule?.let { rule ->
        ConfirmDialog(
            title = "Delete Rule",
            message = "Delete the rule for \"${rule.keyword}\"? This won't change existing transactions.",
            onConfirm = {
                viewModel.deleteRule(rule.id)
                deletingRule = null
            },
            onDismiss = { deletingRule = null }
        )
    }

    if (showAddCategoryDialog) {
        DarkAddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, icon ->
                viewModel.addCategory(name, icon)
                showAddCategoryDialog = false
            }
        )
    }

    editingCategory?.let { cat ->
        DarkAddCategoryDialog(
            initialName = cat.name,
            initialIcon = cat.iconName,
            title = "Edit Category",
            confirmLabel = "Save",
            onDismiss = { editingCategory = null },
            onConfirm = { name, icon ->
                viewModel.updateCategory(cat.id, name, icon)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { cat ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            containerColor = DarkCard,
            title = { Text("Delete Category", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete \"${cat.name}\"? Its transactions and auto-rules will be moved to \"Others\".",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(cat)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedNegative)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

@Composable
fun DarkRuleItem(rule: CategoryRule, category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    val color = CategoryUtils.getCategoryColor(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(CategoryUtils.getCategoryIcon(category), null, tint = color, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.keyword, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            val subtitle = rule.label?.let { "${category.name} • $it" } ?: category.name
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, null, tint = PurpleLight, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, null, tint = RedNegative.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DarkCategoryItem(
    category: Category,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = CategoryUtils.getCategoryColor(category)
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true }
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(CategoryUtils.getCategoryIcon(category), null, tint = color, modifier = Modifier.size(20.dp))
            }
            Text(
                category.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.MoreVert,
                "More options",
                tint = TextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { menuExpanded = true }
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = DarkSurface
        ) {
            DropdownMenuItem(
                text = { Text("Edit", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = PurpleLight, modifier = Modifier.size(18.dp)) },
                onClick = {
                    menuExpanded = false
                    onEdit()
                }
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = { Text("Delete", color = RedNegative) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = RedNegative, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkAddRuleDialog(
    categories: List<Category>,
    initialKeyword: String = "",
    initialLabel: String = "",
    initialCategoryId: Long? = null,
    title: String = "Map Keyword to Category",
    confirmLabel: String = "Add Rule",
    onDismiss: () -> Unit,
    onConfirm: (String, Long, String?) -> Unit
) {
    var keyword by remember { mutableStateOf(initialKeyword) }
    var label by remember { mutableStateOf(initialLabel) }
    var selectedCategoryId by remember {
        mutableStateOf(initialCategoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keywords (comma = all must match, e.g. nach, groww invest)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Display name (optional, e.g. MF SIP)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "Select",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurplePrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        containerColor = DarkSurface
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, color = TextPrimary) },
                                onClick = { selectedCategoryId = cat.id ; expanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keyword, selectedCategoryId, label.ifBlank { null }) },
                enabled = keyword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
fun DarkAddCategoryDialog(
    initialName: String = "",
    initialIcon: String = "Category",
    title: String = "Create Category",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedIcon by remember { mutableStateOf(initialIcon) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCard,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PurplePrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Text("Pick an Icon", color = TextSecondary, fontSize = 13.sp)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.height(140.dp)
                ) {
                    items(CategoryUtils.availableIcons) { iconName ->
                        val isSel = selectedIcon == iconName
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PurplePrimary.copy(alpha = 0.2f) else DarkSurface)
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                CategoryUtils.getIconByName(iconName), null,
                                tint = if (isSel) PurpleLight else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, selectedIcon) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
