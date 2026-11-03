package com.localbudget.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localbudget.app.data.statement.SubscriptionSelections
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.statement.SubscriptionChoiceRanking
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.text.NumberFormat
import java.time.YearMonth

@Composable
internal fun SelectedSubscriptionsSection(
    allCardExpenses: List<Expense>, monthExpenses: List<Expense>, month: YearMonth,
    selectedKeys: Set<String>, onSelect: (Set<String>) -> Unit, onImport: () -> Unit,
    formatter: NumberFormat
) {
    var showPicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val choices = remember(allCardExpenses) {
        SubscriptionChoiceRanking.rank(allCardExpenses)
    }
    val charges = monthExpenses.filter {
        YearMonth.from(it.date) == month && it.sourceType == SourceType.CARD_CSV && SubscriptionSelections.key(it) in selectedKeys
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selectedKeys.isEmpty()) "サブスクを選択" else "うち サブスク",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedKeys.isNotEmpty()) {
                Text(
                    "  ¥${formatter.format(charges.sumOf { it.amount })}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "サブスクの詳細を閉じる" else "サブスクの詳細を開く",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            val chargesByKey = charges.groupBy { SubscriptionSelections.key(it) }
            val selectedChoices = choices.filter { SubscriptionSelections.key(it.expense) in selectedKeys }
            Column(Modifier.fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedKeys.isEmpty()) {
                    Text("カード明細からサブスクを選べます。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                selectedChoices.forEach { choice ->
                    val expense = choice.expense
                    val group = chargesByKey[SubscriptionSelections.key(expense)].orEmpty()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(expense.storeName, style = MaterialTheme.typography.bodyMedium)
                            Text("選択した明細 ¥${formatter.format(expense.amount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (group.isEmpty()) {
                            Text("この月の明細なし", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("¥${formatter.format(group.sumOf { it.amount })}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (selectedChoices.size < selectedKeys.size) {
                    Text("選択した明細の一部が見つかりません。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showPicker = true }) { Text("サブスクを選択・変更") }
            }
        }
    }
    if (showPicker) {
        var draft by remember(showPicker) { mutableStateOf(selectedKeys) }
        var query by remember(showPicker) { mutableStateOf("") }
        val searchKey = JapaneseTextNormalizer.matchKey(query)
        val filteredChoices = choices.filter { choice ->
            searchKey.isBlank() || JapaneseTextNormalizer.matchKey(choice.expense.storeName).contains(searchKey) ||
                choice.expense.amount.toString().contains(searchKey)
        }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("サブスクを選択") },
            text = {
                Column {
                    Text("取り込み済みのカード明細から、利用先と金額を選択してください。金額が変わった場合は再選択してください。", style = MaterialTheme.typography.bodySmall)
                    if (choices.isEmpty()) {
                        Text("カード明細がありません。", modifier = Modifier.padding(top = 16.dp))
                        TextButton(onClick = { showPicker = false; onImport() }) { Text("カード明細を取り込む") }
                    } else {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            label = { Text("利用先・金額を検索") }, singleLine = true
                        )
                        Text("支払い履歴や利用先を参考に並べています。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        if (filteredChoices.isEmpty()) {
                            Text("該当する明細はありません。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                        }
                        LazyColumn(Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                            items(filteredChoices, key = { SubscriptionSelections.key(it.expense) }) { choice ->
                                val expense = choice.expense
                                val key = SubscriptionSelections.key(expense)
                                val checked = key in draft
                                Row(Modifier.fillMaxWidth().clickable { draft = if (checked) draft - key else draft + key }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = checked, onCheckedChange = { draft = if (it) draft + key else draft - key })
                                    Column(Modifier.weight(1f)) {
                                        Text(expense.storeName, style = MaterialTheme.typography.bodyMedium)
                                        if (choice.recurringMonths >= 2) {
                                            Text("${choice.recurringMonths}か月連続", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text("¥${formatter.format(expense.amount)}", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSelect(draft); showPicker = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("キャンセル") } }
        )
    }
}
