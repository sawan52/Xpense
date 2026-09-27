package com.example.xpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.DarkAddCategoryDialog
import com.example.xpense.ui.components.design.*
import com.example.xpense.ui.theme.XType
import com.example.xpense.ui.theme.XpenseTheme
import com.example.xpense.ui.utils.CategoryUtils
import java.text.SimpleDateFormat
import java.util.*

/** Add (expense == null) or edit an expense. */
@Composable
fun AddExpenseBottomSheet(
    expense: Expense? = null,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, merchant: String, categoryId: Long, date: Long, note: String?) -> Unit,
    // When provided, a trailing "+ New" chip in the category row creates a category inline.
    onAddCategory: ((name: String, icon: String) -> Unit)? = null,
    // When true, a secondary "Add a rule for this" button appears (for SMS rows with no rule yet).
    showAddRule: Boolean = false,
    onAddRule: () -> Unit = {},
    // When true, a "Force auto rule" button appears (for SMS rows whose matching rule the user has
    // manually overridden). Mutually exclusive with showAddRule.
    showForceRule: Boolean = false,
    onForceRule: () -> Unit = {},
    // Edit mode only: archive / delete this transaction.
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val c = XpenseTheme.colors
    var amount by remember { mutableStateOf(expense?.amount?.takeIf { it > 0 }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var merchant by remember { mutableStateOf(expense?.merchant ?: "") }
    var selectedCategoryId by remember { mutableStateOf(expense?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var dateMillis by remember { mutableStateOf(expense?.date ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(expense?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAddCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Name of a just-created category to auto-select once it appears in the reactive list.
    var pendingSelectName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categories) {
        pendingSelectName?.let { nm ->
            categories.find { it.name.equals(nm, ignoreCase = true) }?.let {
                selectedCategoryId = it.id
                pendingSelectName = null
            }
        }
    }

    val parsed = amount.toDoubleOrNull()
    val isValid = parsed != null && parsed > 0
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    XBottomSheet(
        onDismiss = onDismiss,
        title = if (expense == null) "Add expense" else "Edit expense",
        subtitle = if (expense == null) "Track every rupee you spend" else "Changes apply to this transaction only"
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Amount
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("₹", style = XType.displayS.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.tx2)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width(IntrinsicSize.Min).widthIn(min = 60.dp, max = 240.dp), contentAlignment = Alignment.Center) {
                    val style = XType.display.copy(fontSize = XType.display.fontSize * 1.4f, textAlign = TextAlign.Center, color = c.tx)
                    if (amount.isEmpty()) Text("0", style = style.copy(color = c.tx3))
                    BasicTextField(
                        value = amount,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() || it == '.' }
                            if (clean.count { it == '.' } <= 1 && clean.length <= 10) amount = clean
                        },
                        textStyle = style,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(c.ac),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Category chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { cat ->
                    CategoryChip(cat.name, CategoryUtils.getCategoryIcon(cat), CategoryUtils.getCategoryColor(cat), cat.id == selectedCategoryId, { selectedCategoryId = cat.id }, large = true)
                }
                if (onAddCategory != null) {
                    item { CategoryChip("New", Icons.Rounded.Add, c.ac, false, { showAddCategory = true }, large = true) }
                }
            }

            XTextField(merchant, { merchant = it }, "Where did you spend?", leadingIcon = Icons.Rounded.Storefront, bordered = false)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerTile(Icons.Rounded.CalendarToday, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMillis)), false, Modifier.weight(1f)) { showDatePicker = true }
                PickerTile(Icons.Rounded.Schedule, SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(dateMillis)), true, Modifier.weight(1f)) { showTimePicker = true }
            }

            XTextField(note, { note = it }, "Add a note (optional)", leadingIcon = Icons.AutoMirrored.Rounded.Notes, bordered = false)

            XButton(
                "Save expense",
                {
                    val name = merchant.trim().ifBlank { selectedCategory?.name ?: "Expense" }
                    onConfirm(parsed!!, name, selectedCategoryId, dateMillis, note.trim().ifBlank { null })
                },
                Modifier.fillMaxWidth(),
                enabled = isValid,
                height = 56.dp,
                radius = 18.dp,
                textStyle = XType.section
            )

            if (showAddRule) XButton("Add a rule for this", onAddRule, Modifier.fillMaxWidth(), style = BtnStyle.Outline, icon = Icons.Rounded.AddTask)
            if (showForceRule) XButton("Force auto rule", onForceRule, Modifier.fillMaxWidth(), style = BtnStyle.Outline, icon = Icons.Rounded.AutoFixHigh)

            if (onArchive != null || onDelete != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onArchive != null) XButton("Archive", onArchive, Modifier.weight(1f), style = BtnStyle.Secondary, icon = Icons.Rounded.Archive)
                    if (onDelete != null) {
                        Row(
                            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(16.dp)).background(c.neg.copy(alpha = 0.12f))
                                .clickable { confirmDelete = true },
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            XIcon(Icons.Rounded.Delete, 20.dp, c.neg)
                            Text("Delete", style = XType.bodyStrong, color = c.neg)
                        }
                    }
                }
            }
        }
    }

    if (showAddCategory && onAddCategory != null) {
        DarkAddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, icon ->
                onAddCategory(name, icon)
                pendingSelectName = name
                showAddCategory = false
            }
        )
    }
    if (showDatePicker) {
        XDatePickerDialog(dateMillis, { showDatePicker = false }) { dateMillis = it; showDatePicker = false }
    }
    if (showTimePicker) {
        XTimePickerDialog(dateMillis, { showTimePicker = false }) { dateMillis = it; showTimePicker = false }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete this transaction?",
            message = "This can't be undone. Archive it instead to just hide it from your totals.",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun PickerTile(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, mono: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = XpenseTheme.colors
    Row(
        modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.card2).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        XIcon(icon, 20.dp, c.ac)
        Text(text, style = if (mono) XType.mono.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal) else XType.body, color = c.tx, maxLines = 1)
    }
}
