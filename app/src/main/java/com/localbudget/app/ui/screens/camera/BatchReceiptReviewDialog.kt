package com.localbudget.app.ui.screens.camera

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.localbudget.app.domain.model.Category
import com.localbudget.app.ui.components.CategoryIconBadge
import com.localbudget.app.ui.components.ReceiptItemsList
import com.localbudget.app.ui.theme.parseColorHex
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchReceiptReviewDialog(
    initialReceipts: List<BatchReceipt>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveAll: (List<BatchReceipt>) -> Unit
) {
    val receipts = remember { mutableStateListOf(*initialReceipts.toTypedArray()) }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "連続スキャン確認",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "合計: ¥${currencyFormatter.format(receipts.sumOf { it.expense.amount })} (${receipts.size}件)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(onClick = onDismiss) {
                        Text("破棄")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable List of Parsed Receipts
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(receipts, key = { _, receipt -> receipt.key }) { index, receipt ->
                        val item = receipt.expense
                        var storeName by remember { mutableStateOf(item.storeName) }
                        var amountText by remember { mutableStateOf(item.amount.toString()) }
                        var selectedCategoryId by remember { mutableStateOf(item.categoryId) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${index + 1} レシート (${item.date})",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    IconButton(
                                        onClick = { receipts.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "削除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // In the receipt's own order: store, items, then the total
                                OutlinedTextField(
                                    value = storeName,
                                    onValueChange = {
                                        storeName = it
                                        receipts[index] = receipt.copy(expense = item.copy(storeName = it))
                                    },
                                    label = { Text("店舗名") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                ReceiptItemsList(
                                    items = receipt.items,
                                    foodSettings = receipt.foodSettings.map { it.category to it.storage },
                                    selectedFoodIndices = receipt.foodSettings.indices.filter { receipt.foodSettings[it].selected },
                                    onItemsChange = { items ->
                                        receipts[index] = receipts[index].withItems(items)
                                    },
                                    onItemDelete = { line ->
                                        receipts[index] = receipts[index].removeItem(line)
                                    },
                                    onFoodSettingsChange = { line, category, storage, selected ->
                                        val current = receipts[index]
                                        val settings = current.foodSettings.toMutableList()
                                        settings[line] = BatchFoodSetting(category, storage, selected)
                                        receipts[index] = current.copy(foodSettings = settings)
                                    }
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = {
                                        if (it.all { c -> c.isDigit() }) {
                                            amountText = it
                                            val amt = it.toLongOrNull() ?: 0L
                                            receipts[index] = receipt.copy(expense = item.copy(amount = amt), amountWarning = null)
                                        }
                                    },
                                    label = { Text("金額 (円)") },
                                    prefix = { Text("¥ ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = receipt.amountWarning != null,
                                    supportingText = receipt.amountWarning?.let { warning -> { Text(warning) } },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Category chips
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    categories.forEach { cat ->
                                        val isSelected = cat.id == selectedCategoryId
                                        val catColor = parseColorHex(cat.colorHex)

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isSelected) catColor.copy(alpha = 0.25f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                                    color = if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    selectedCategoryId = cat.id
                                                    receipts[index] = receipt.copy(expense = item.copy(categoryId = cat.id))
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CategoryIconBadge(
                                                iconName = cat.iconName,
                                                colorHex = cat.colorHex,
                                                badgeSize = 20.dp,
                                                iconSize = 12.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = cat.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Button
                Button(
                    onClick = { onSaveAll(receipts.toList()) },
                    enabled = receipts.isNotEmpty() && receipts.all { it.expense.amount > 0 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("確定してすべて保存 (${receipts.size}件)")
                }
            }
        }
    }
}
