package com.localbudget.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.food.FoodDictionary
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.StorageLocation
import java.text.NumberFormat
import java.util.Locale

/**
 * The purchased lines read from a receipt, as printed: name, quantity, price and any discount.
 * Shown next to the parsed total so the user can see what the scan actually picked up.
 * When editable, users can correct, add and remove lines.
 */
@Composable
fun ReceiptItemsList(
    items: List<ReceiptLineItem>,
    modifier: Modifier = Modifier,
    onItemsChange: ((List<ReceiptLineItem>) -> Unit)? = null,
    foodSettings: List<Pair<FoodCategory, StorageLocation>>? = null,
    onFoodSettingsChange: ((Int, FoodCategory, StorageLocation, Boolean) -> Unit)? = null,
    selectedFoodIndices: List<Int> = emptyList(),
    onItemDelete: ((Int) -> Unit)? = null
) {
    val formatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    if (adding || editingIndex != null) {
        val index = editingIndex
        val item = index?.let { items.getOrNull(it) } ?: ReceiptLineItem("")
        ItemEditDialog(item,
            initialCategory = index?.let { foodSettings?.getOrNull(it)?.first } ?: FoodDictionary.guessCategory(item.name),
            initialStorage = index?.let { foodSettings?.getOrNull(it)?.second } ?: FridgeItemEnricher.guessStorage(item.name),
            showFoodSettings = onFoodSettingsChange != null,
            initiallySelected = index?.let { it in selectedFoodIndices } ?: true,
            onDismiss = { adding = false; editingIndex = null },
            onDelete = index?.let { selected -> {
                if (onItemDelete != null) onItemDelete(selected)
                else onItemsChange?.invoke(items.filterIndexed { i, _ -> i != selected })
                editingIndex = null
            } }) { updated, category, storage, selected ->
            val changed = items.toMutableList()
            if (index == null) changed.add(updated) else if (index in changed.indices) changed[index] = updated
            onItemsChange?.invoke(changed)
            onFoodSettingsChange?.invoke(index ?: changed.lastIndex, category, storage, selected)
            adding = false
            editingIndex = null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "読み取った商品 (${items.size}件)",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (items.isEmpty()) {
            Text(
                text = "商品行を読み取れませんでした",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (onItemsChange != null) {
            Text(
                text = "商品をタップすると名前と金額を修正できます",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Column(modifier = Modifier.padding(top = 4.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).clickable(enabled = onItemsChange != null) { editingIndex = index }.padding(vertical = 4.dp)) {
                        Text(text = item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(foodSettings?.getOrNull(index)?.first?.displayName ?: FoodDictionary.matchCategory(item.name)?.displayName ?: "未分類", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (item.quantity > 1) {
                        Text(
                            text = "×${item.quantity}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Row(
                        modifier = if (onItemsChange != null) {
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { editingIndex = index }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.price?.let { "¥${formatter.format(it)}" } ?: "¥ ―",
                            fontSize = 16.sp,
                            color = if (item.price == null) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (onItemsChange != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "${item.name}の金額を修正",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (item.discount > 0) {
                    Text(
                        text = "値引 -¥${formatter.format(item.discount)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp)
                    )
                }
            }

            if (onItemsChange != null) { TextButton(onClick = { adding = true }) { Text("商品を追加") } }
            val pricedTotal = items.sumOf { it.netPrice ?: 0L }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "商品の合計 (外税は含まず)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "¥${formatter.format(pricedTotal)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ItemEditDialog(item: ReceiptLineItem, initialCategory: FoodCategory, initialStorage: StorageLocation,
    showFoodSettings: Boolean, initiallySelected: Boolean, onDismiss: () -> Unit, onDelete: (() -> Unit)?,
    onConfirm: (ReceiptLineItem, FoodCategory, StorageLocation, Boolean) -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var price by remember { mutableStateOf(item.price?.toString() ?: "") }
    var selected by remember { mutableStateOf(initiallySelected) }
    var category by remember { mutableStateOf(initialCategory) }
    var storage by remember { mutableStateOf(initialStorage) }
    var categoryMenu by remember { mutableStateOf(false) }
    var storageMenu by remember { mutableStateOf(false) }
    val validPrice = price.isEmpty() || price.toLongOrNull() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.name.isEmpty()) "商品を追加" else "商品を修正") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("商品名") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { if (it.all(Char::isDigit)) price = it },
                    label = { Text("金額 (円)") }, supportingText = { Text("値引前の金額。空欄は金額なし") },
                    isError = !validPrice, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (showFoodSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = selected, onCheckedChange = { selected = it })
                        Text("在庫に登録する")
                    }
                    Box {
                        TextButton(onClick = { categoryMenu = true }) { Text("カテゴリ: ${category.displayName} ▾") }
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            FoodCategory.entries.forEach { option -> DropdownMenuItem(text = { Text(option.displayName) }, onClick = { category = option; categoryMenu = false }) }
                        }
                    }
                    Box {
                        TextButton(onClick = { storageMenu = true }) { Text("保存場所: ${storage.placeName} ▾") }
                        DropdownMenu(expanded = storageMenu, onDismissRequest = { storageMenu = false }) {
                            StorageLocation.entries.forEach { option -> DropdownMenuItem(text = { Text(option.placeName) }, onClick = { storage = option; storageMenu = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && validPrice, onClick = { onConfirm(item.withPrice(price.toLongOrNull()).copy(name = name.trim()), category, storage, selected) }) { Text("OK") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("削除", color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )
}
