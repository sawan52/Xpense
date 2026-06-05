package com.example.xpense.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xpense.data.entity.Category
import com.example.xpense.data.entity.Expense
import com.example.xpense.ui.theme.*
import com.example.xpense.ui.utils.CategoryUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseBottomSheet(
    expense: Expense? = null,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, merchant: String, categoryId: Long, date: Long) -> Unit
) {
    var amount by remember { mutableStateOf(expense?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var merchant by remember { mutableStateOf(expense?.merchant ?: "") }
    var selectedCategoryId by remember {
        mutableStateOf(expense?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var dateMillis by remember { mutableStateOf(expense?.date ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis <= System.currentTimeMillis()
        }
    )
    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE)
    )
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isValid = amount.toDoubleOrNull() != null && merchant.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Open fully expanded so the multi-field form has room and fields stay clear of the keyboard.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkCard,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp).height(4.dp)
                    .background(DarkBorder, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scroll + imePadding so the keyboard never hides the focused field: the content
                // scrolls and the focused text field is auto-brought into view above the keyboard.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (expense == null) "Add Expense" else "Edit Expense",
                        color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Text("Track every rupee you spend ✨", color = TextSecondary, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(DarkSurface, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Amount
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Amount", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(16.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("₹", color = TextSecondary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (amount.isEmpty()) {
                            Text("0.00", color = TextMuted, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                        BasicTextField(
                            value = amount,
                            onValueChange = { raw ->
                                val clean = raw.filter { it.isDigit() || it == '.' }
                                if (clean.count { it == '.' } <= 1) amount = clean
                            },
                            textStyle = TextStyle(
                                color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            cursorBrush = SolidColor(PurpleLight)
                        )
                    }
                    Icon(Icons.Default.Calculate, null, tint = TextMuted, modifier = Modifier.size(22.dp))
                }
            }

            // Category pills
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Category", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Choose a category", color = TextMuted, fontSize = 12.sp)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(categories) { cat ->
                        val isSelected = cat.id == selectedCategoryId
                        val color = CategoryUtils.getCategoryColor(cat)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) PurplePrimary.copy(alpha = 0.12f) else DarkSurface,
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) PurplePrimary else DarkBorder,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedCategoryId = cat.id }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(color.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    CategoryUtils.getCategoryIcon(cat), null,
                                    tint = color, modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(
                                cat.name,
                                color = if (isSelected) PurpleLight else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Merchant
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Merchant", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                SheetTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    placeholder = "Where did you spend?",
                    leadingIcon = {
                        Icon(Icons.Default.Store, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                )
            }

            // Date + Time
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Date", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    SheetTextField(
                        value = dateFmt.format(Date(dateMillis)),
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    SheetTextField(
                        value = timeFmt.format(Date(dateMillis)),
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(Icons.Default.Schedule, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        },
                        modifier = Modifier.clickable { showTimePicker = true }
                    )
                }
            }

            // Note
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Note (Optional)", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                SheetTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Add a note",
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Notes, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                    }
                )
            }

            // Save button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isValid) Brush.linearGradient(listOf(PurplePrimary, PurpleLight))
                        else Brush.linearGradient(listOf(DarkSurface, DarkSurface))
                    )
                    .clickable(enabled = isValid) {
                        onConfirm(amount.toDouble(), merchant.trim(), selectedCategoryId, dateMillis)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Save Expense",
                    color = if (isValid) Color.White else TextMuted,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { picked ->
                        val cur = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        val sel = Calendar.getInstance().apply { timeInMillis = picked }
                        sel.set(Calendar.HOUR_OF_DAY, cur.get(Calendar.HOUR_OF_DAY))
                        sel.set(Calendar.MINUTE, cur.get(Calendar.MINUTE))
                        dateMillis = sel.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK", color = PurpleLight) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextSecondary) }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkCard)
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = DarkCard,
            title = { Text("Select Time", color = TextPrimary) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    dateMillis = c.timeInMillis
                    showTimePicker = false
                }) { Text("OK", color = PurpleLight) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }
}

// ── Shared outlined text field style for the sheet ──────────────────────────
@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        leadingIcon = leadingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PurplePrimary,
            unfocusedBorderColor = DarkBorder,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = PurpleLight
        )
    )
}
