package com.localbudget.app.ui.screens.fridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.food.ConsumptionEstimator
import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionSuggestionText
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Callbacks shared by prompt cards and stock-check rows. */
class ConsumptionActions(
    val onEnd: (FridgeItem, FridgeItemStatus) -> Unit,
    val onStillThere: (FridgeItem) -> Unit
)

/** Prompt cards above the inventory list; collapsed to [COLLAPSED_PROMPTS] unless [expanded]. */
fun LazyListScope.consumptionPromptSection(
    prompts: List<ConsumptionEstimator.Suggestion>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: ConsumptionActions
) {
    if (prompts.isEmpty()) return
    item(key = "prompts_header") {
        Text(
            text = "確認してください（${prompts.size}件）",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    val shown = if (expanded) prompts else prompts.take(COLLAPSED_PROMPTS)
    items(shown, key = { "prompt_${it.item.id}" }) { suggestion ->
        ConsumptionPromptCard(suggestion, actions)
    }
    if (prompts.size > COLLAPSED_PROMPTS) {
        item(key = "prompts_toggle") {
            TextButton(onClick = onToggleExpanded) {
                Text(if (expanded) "閉じる" else "他${prompts.size - COLLAPSED_PROMPTS}件を表示")
            }
        }
    }
    item(key = "prompts_divider") {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
fun ConsumptionPromptCard(
    suggestion: ConsumptionEstimator.Suggestion,
    actions: ConsumptionActions,
    modifier: Modifier = Modifier
) {
    val item = suggestion.item
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (suggestion.kind == Kind.PAST_EXPIRY)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "「${item.name}」${ConsumptionSuggestionText.question(suggestion.kind)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ConsumptionSuggestionText.probability(suggestion.probability),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = ConsumptionSuggestionText.reason(suggestion),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            // The likelier answer goes first and gets the filled button
            val primary = if (suggestion.kind == Kind.PAST_EXPIRY) FridgeItemStatus.DISCARDED else FridgeItemStatus.CONSUMED
            val secondary = if (primary == FridgeItemStatus.CONSUMED) FridgeItemStatus.DISCARDED else FridgeItemStatus.CONSUMED
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(onClick = { actions.onEnd(item, primary) }) {
                    Text(endLabel(primary), fontSize = 13.sp)
                }
                TextButton(onClick = { actions.onStillThere(item) }) {
                    Text("まだある", fontSize = 13.sp)
                }
                TextButton(onClick = { actions.onEnd(item, secondary) }) {
                    Text(endLabel(secondary), fontSize = 13.sp)
                }
            }
        }
    }
}

/** Shown under the controls while the stock check has anything to ask. */
@Composable
fun StockCheckBanner(
    count: Int,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Checklist,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "在庫チェック: ${count}品の確認待ち",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onStart) {
                Text("始める", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Answered rows drop out as the event log updates; the dialog closes itself when empty. */
@Composable
fun StockCheckDialog(
    entries: List<ConsumptionEstimator.StockCheckEntry>,
    actions: ConsumptionActions,
    onDismiss: () -> Unit
) {
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在庫チェック") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    Text(
                        text = "冷蔵庫にまだあるか教えてください。答えるほど推定が家庭のペースに合っていきます。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(entries, key = { it.item.id }) { entry ->
                    val days = ChronoUnit.DAYS.between(entry.lastSeenOn, today)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(entry.item.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            text = buildString {
                                append("${entry.item.purchaseDate.format(dateFormatter)}購入・最後の確認から${days}日")
                                entry.probability?.let { append("・使い切り ${ConsumptionSuggestionText.probability(it)}") }
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = { actions.onStillThere(entry.item) }) {
                                Text("ある", fontSize = 13.sp)
                            }
                            TextButton(onClick = { actions.onEnd(entry.item, FridgeItemStatus.CONSUMED) }) {
                                Text("使った", fontSize = 13.sp)
                            }
                            TextButton(onClick = { actions.onEnd(entry.item, FridgeItemStatus.DISCARDED) }) {
                                Text("捨てた", fontSize = 13.sp)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

fun endLabel(status: FridgeItemStatus): String = when (status) {
    FridgeItemStatus.CONSUMED -> "使い切った"
    FridgeItemStatus.DISCARDED -> "捨てた"
    FridgeItemStatus.ACTIVE -> "まだある"
}

fun endedMessage(notice: EndedNotice): String = when (notice.status) {
    FridgeItemStatus.DISCARDED -> "「${notice.name}」を廃棄にしました"
    else -> "「${notice.name}」を使い切りにしました"
}

private const val COLLAPSED_PROMPTS = 3
