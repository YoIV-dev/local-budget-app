package com.localbudget.app.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.food.FoodDictionary
import com.localbudget.app.domain.food.FoodUnit
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.StorageLocation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.ocr.ReceiptItemParser
import com.localbudget.app.ui.components.CategoryIconBadge
import com.localbudget.app.ui.components.ReceiptItemsList
import com.localbudget.app.ui.theme.parseColorHex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseAddEditDialog(
    initialExpense: Expense? = null,
    categories: List<Category>,
    rawOcrText: String? = null,
    // What the scan says about its total (see TotalCheck), shown on the amount until the user edits it
    amountWarning: String? = null,
    savedItems: List<ReceiptLineItem> = emptyList(),
    onPickImage: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    // The receipt lines with the user's price corrections, or null when none were corrected
    onSave: (Expense, List<ReceiptLineItem>?) -> Unit,
    onSaveWithFridgeItems: ((Expense, List<FridgeItem>, List<ReceiptLineItem>?) -> Unit)? = null
) {
    var amountText by remember { mutableStateOf(initialExpense?.amount?.toString() ?: "") }
    var amountEdited by remember { mutableStateOf(false) }
    val showAmountWarning = amountWarning != null && !amountEdited
    var storeName by remember { mutableStateOf(initialExpense?.storeName ?: "") }
    var memo by remember { mutableStateOf(initialExpense?.memo ?: "") }
    var selectedCategoryId by remember {
        mutableStateOf(initialExpense?.categoryId ?: categories.firstOrNull()?.id ?: 1L)
    }
    var selectedDate by remember { mutableStateOf(initialExpense?.date ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val receiptItems = remember(rawOcrText) {
        if (!rawOcrText.isNullOrBlank()) ReceiptItemParser.parse(rawOcrText) else emptyList()
    }
    // A fresh scan shows what it just read; a saved expense shows the lines kept with it.
    val shownItems = if (!rawOcrText.isNullOrBlank()) receiptItems else savedItems
    val editedItems = remember(shownItems) { mutableStateListOf(*shownItems.toTypedArray()) }
    var itemsEdited by remember(shownItems) { mutableStateOf(false) }
    val extractedFoodItems = remember(editedItems.toList(), selectedDate) {
        editedItems.map { item ->
            val category = FoodDictionary.guessCategory(item.name)
            FridgeItem(name = item.name, category = category, quantity = item.quantity,
                unit = FoodUnit.guess(item.name, category), purchaseDate = selectedDate,
                price = item.netPrice, storage = FridgeItemEnricher.guessStorage(item.name))
        }
    }
    val selectedItemIndices = remember(shownItems) {
        mutableStateListOf<Int>().apply { addAll(shownItems.indices.filter { FoodDictionary.matchCategory(shownItems[it].name) != null }) }
    }
    val foodSettings = remember(shownItems) {
        mutableStateListOf(*shownItems.map { FoodDictionary.guessCategory(it.name) to FridgeItemEnricher.guessStorage(it.name) }.toTypedArray())
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPANESE) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("キャンセル")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialExpense == null) "支出を記録" else "支出を編集",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (onPickImage != null) {
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                        Text("ギャラリーのレシートを読み取る")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // Store Name Field
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text("店舗名 / サービス名") },
                    singleLine = true,
                    placeholder = { Text("例: セブンイレブン, イオン") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Date Field
                OutlinedTextField(
                    value = selectedDate.format(dateFormatter),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("日付") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = "日付選択")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Everything the scan read off the receipt, not only food, in the receipt's own order:
                // store and date above, items, then the total
                if (!rawOcrText.isNullOrBlank() || savedItems.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        ReceiptItemsList(
                            items = editedItems,
                            modifier = Modifier.padding(12.dp),
                            foodSettings = if (!rawOcrText.isNullOrBlank()) foodSettings else null,
                            selectedFoodIndices = selectedItemIndices,
                            onFoodSettingsChange = if (!rawOcrText.isNullOrBlank()) { index, category, storage, selected ->
                                foodSettings[index] = category to storage
                                if (selected && index !in selectedItemIndices) selectedItemIndices.add(index)
                                if (!selected) selectedItemIndices.remove(index)
                            } else null,
                            onItemsChange = { items ->
                                if (items.size < editedItems.size) {
                                    val removed = editedItems.indices.firstOrNull { i ->
                                        editedItems.filterIndexed { j, _ -> j != i } == items
                                    } ?: editedItems.lastIndex
                                    foodSettings.removeAt(removed)
                                    val retained = selectedItemIndices.filter { it != removed }.map { if (it > removed) it - 1 else it }
                                    selectedItemIndices.clear()
                                    selectedItemIndices.addAll(retained)
                                } else if (items.size > editedItems.size) {
                                    val added = items.last()
                                    foodSettings.add(FoodDictionary.guessCategory(added.name) to FridgeItemEnricher.guessStorage(added.name))
                                    selectedItemIndices.add(items.lastIndex)
                                }
                                editedItems.clear()
                                editedItems.addAll(items)
                                itemsEdited = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Amount Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            amountText = input
                            amountEdited = true
                        }
                    },
                    label = { Text("金額 (円)") },
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = showAmountWarning,
                    supportingText = if (showAmountWarning) {
                        { Text(amountWarning.orEmpty()) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category Selection
                Text(
                    text = "カテゴリ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = category.id == selectedCategoryId
                        val catColor = parseColorHex(category.colorHex)

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategoryId = category.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CategoryIconBadge(
                                iconName = category.iconName,
                                colorHex = category.colorHex,
                                badgeSize = 24.dp,
                                iconSize = 14.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = category.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Memo Field
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("メモ (任意)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )


            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0) {
                        val expense = Expense(
                            id = initialExpense?.id ?: 0L,
                            date = selectedDate,
                            amount = amount,
                            storeName = storeName.trim(),
                            categoryId = selectedCategoryId,
                            memo = memo.trim(),
                            sourceType = initialExpense?.sourceType ?: SourceType.MANUAL,
                            hasReceiptImage = initialExpense?.hasReceiptImage ?: false
                        )
                        val items = editedItems.toList().takeIf { itemsEdited }
                        val selectedFridgeItems = selectedItemIndices.map { index ->
                            extractedFoodItems[index].copy(category = foodSettings[index].first, storage = foodSettings[index].second, unit = FoodUnit.guess(extractedFoodItems[index].name, foodSettings[index].first))
                        }
                        if (onSaveWithFridgeItems != null && selectedFridgeItems.isNotEmpty()) {
                            onSaveWithFridgeItems(expense, selectedFridgeItems, items)
                        } else {
                            onSave(expense, items)
                        }
                    }
                },
                enabled = (amountText.toLongOrNull() ?: 0L) > 0
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
