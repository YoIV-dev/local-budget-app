package com.localbudget.app.ui.screens.statement

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.statement.RefundTarget
import com.localbudget.app.domain.statement.StatementCandidate
import com.localbudget.app.domain.statement.StatementMatch
import com.localbudget.app.domain.statement.StatementPlan
import com.localbudget.app.ui.components.CategoryIconBadge
import com.localbudget.app.ui.theme.parseColorHex
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

// Download folders often label a CSV as plain text or as an unknown binary
private val STATEMENT_MIME_TYPES = arrayOf(
    "text/csv", "text/comma-separated-values", "text/plain", "application/csv",
    "application/vnd.ms-excel", "application/octet-stream"
)

/** Opens the file picker for a statement CSV; what is picked goes to [viewModel] to read. */
@Composable
fun rememberStatementPicker(viewModel: StatementImportViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.read(uris)
    }
    return remember(launcher) { { launcher.launch(STATEMENT_MIME_TYPES) } }
}

/** The progress and review dialogs of a statement import, and its toasts. Shown once, above every screen. */
@Composable
fun StatementImportHost(viewModel: StatementImportViewModel) {
    val state by viewModel.state.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    when (val current = state) {
        StatementImportState.Idle -> Unit
        StatementImportState.Reading -> ReadingDialog()
        is StatementImportState.Review -> StatementImportDialog(
            plan = current.plan,
            categories = categories,
            onDismiss = viewModel::dismiss,
            onImport = viewModel::import
        )
    }
}

@Composable
private fun ReadingDialog() {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("カード明細を読み込み中...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun StatementImportDialog(
    plan: StatementPlan,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onImport: (List<StatementCandidate>, List<RefundTarget>) -> Unit
) {
    val rows = remember(plan) { plan.candidates.toMutableStateList() }
    val refunds = remember(plan) { plan.refundTargets.toMutableStateList() }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/M/d (E)", Locale.JAPANESE) }
    val selected = rows.filter { it.selected }
    val selectedRefunds = refunds.filter { it.selected }
    val receiptMatches = rows.count { it.match == StatementMatch.RECEIPT }

    // Drawn over the app rather than in a dialog window: on edge-to-edge Android 15 a full-screen
    // dialog does not get the system bar insets and its buttons ran under the navigation bar
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Taps on the margin must not reach the screen underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("カード明細の取り込み", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "選択中: ¥${currencyFormatter.format(selected.sumOf { it.row.amount })} (${selected.size}/${rows.size}件)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val notes = buildList {
                    if (receiptMatches > 0) add("レシートで登録済みの${receiptMatches}件は、品目のあるレシートを残すため外しています")
                    if (plan.cancelledCount > 0) add("取消・返品で相殺された${plan.cancelledCount}行は取り込みません")
                    if (plan.unmatchedRefunds > 0) add("元の購入が見つからない返品${plan.unmatchedRefunds}件は取り込みません")
                }
                notes.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val allSelected = selected.size == rows.size
                    TextButton(onClick = {
                        rows.indices.forEach { rows[it] = rows[it].copy(selected = !allSelected) }
                    }) {
                        Text(if (allSelected) "選択を解除" else "すべて選択")
                    }
                }

                LazyColumn(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()) {
                    if (refunds.isNotEmpty()) {
                        item {
                            Text(
                                "返品（以前に取り込んだ支出を削除）",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        itemsIndexed(refunds, key = { index, _ -> "refund$index" }) { index, target ->
                            RefundRowItem(
                                target = target,
                                text = "${target.expense.date.format(dateFormatter)} の購入を ${target.refund.date.format(dateFormatter)} に返品",
                                amountText = "¥${currencyFormatter.format(target.refund.amount)}",
                                onToggle = { refunds[index] = target.copy(selected = !target.selected) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                        item {
                            Text(
                                "取り込む支出",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    itemsIndexed(rows, key = { index, _ -> index }) { index, candidate ->
                        StatementRowItem(
                            candidate = candidate,
                            categories = categories,
                            dateText = candidate.row.date.format(dateFormatter),
                            amountText = "¥${currencyFormatter.format(candidate.row.amount)}",
                            onToggle = { rows[index] = candidate.copy(selected = !candidate.selected) },
                            onCategoryChange = { rows[index] = candidate.copy(categoryId = it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = { onImport(selected, selectedRefunds) },
                        enabled = selected.isNotEmpty() || selectedRefunds.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${selected.size}件を取り込む")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRowItem(
    candidate: StatementCandidate,
    categories: List<Category>,
    dateText: String,
    amountText: String,
    onToggle: () -> Unit,
    onCategoryChange: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = candidate.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(dateText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = candidate.row.storeName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            candidate.match.label?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            CategoryPicker(
                category = categories.find { it.id == candidate.categoryId },
                categories = categories,
                onSelect = onCategoryChange
            )
        }
    }
}

@Composable
private fun RefundRowItem(target: RefundTarget, text: String, amountText: String, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = target.selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(text, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = target.expense.storeName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("−$amountText", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CategoryPicker(category: Category?, categories: List<Category>, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val color = category?.let { parseColorHex(it.colorHex) } ?: MaterialTheme.colorScheme.outline
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(color.copy(alpha = 0.15f))
                .border(0.5.dp, color, RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            category?.let {
                CategoryIconBadge(iconName = it.iconName, colorHex = it.colorHex, badgeSize = 18.dp, iconSize = 11.dp)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(category?.name ?: "カテゴリ", fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    leadingIcon = {
                        CategoryIconBadge(iconName = cat.iconName, colorHex = cat.colorHex, badgeSize = 24.dp, iconSize = 14.dp)
                    },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
