package com.example.xpense.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.xpense.data.entity.Expense
import com.example.xpense.data.model.Category
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDialog(
    expense: Expense? = null,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, merchant: String, category: Category, date: Long) -> Unit
) {
    var amount by remember { mutableStateOf(expense?.amount?.toString() ?: "") }
    var merchant by remember { mutableStateOf(expense?.merchant ?: "") }
    var category by remember { mutableStateOf(expense?.category ?: Category.OTHERS) }
    var dateMillis by remember { mutableStateOf(expense?.date ?: System.currentTimeMillis()) }
    
    var categoryExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= System.currentTimeMillis()
            }
        }
    )

    val calendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(32.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (expense == null) "New Expense" else "Edit Expense",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B)
                )

                PremiumTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Amount",
                    keyboardType = KeyboardType.Decimal,
                    placeholder = "0.00"
                )

                PremiumTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = "Merchant",
                    placeholder = "Where did you spend?"
                )
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    PremiumTextField(
                        value = category.name,
                        onValueChange = {},
                        label = "Category",
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        Category.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PremiumTextField(
                        value = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateMillis)),
                        onValueChange = {},
                        label = "Date",
                        readOnly = true,
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true }
                    )
                    
                    PremiumTextField(
                        value = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(dateMillis)),
                        onValueChange = {},
                        label = "Time",
                        readOnly = true,
                        modifier = Modifier.weight(1f).clickable { showTimePicker = true }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            onConfirm(amt, merchant, category, dateMillis)
                        },
                        enabled = amount.isNotEmpty() && merchant.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (amount.isNotEmpty() && merchant.isNotEmpty())
                                    Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF6366F1)))
                                else
                                    Brush.linearGradient(listOf(Color.LightGray, Color.LightGray))
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Save Transaction", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val currentCalendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                        val selectedCalendar = Calendar.getInstance().apply { timeInMillis = it }
                        selectedCalendar.set(Calendar.HOUR_OF_DAY, currentCalendar.get(Calendar.HOUR_OF_DAY))
                        selectedCalendar.set(Calendar.MINUTE, currentCalendar.get(Calendar.MINUTE))
                        dateMillis = selectedCalendar.timeInMillis
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Time",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            val selectedCalendar = Calendar.getInstance().apply { timeInMillis = dateMillis }
                            selectedCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            selectedCalendar.set(Calendar.MINUTE, timePickerState.minute)
                            dateMillis = selectedCalendar.timeInMillis
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.LightGray) },
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF4F46E5),
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC)
            ),
            singleLine = true
        )
    }
}
