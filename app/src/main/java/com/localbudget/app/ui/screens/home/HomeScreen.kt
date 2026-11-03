package com.localbudget.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.model.CategoryExpenseSummary
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.ui.components.CategoryIconBadge
import com.localbudget.app.ui.components.DonutChart
import com.localbudget.app.ui.components.ExpenseCard
import com.localbudget.app.ui.components.MonthYearSelector
import com.localbudget.app.ui.components.MonthlyStackedBarChart
import com.localbudget.app.ui.screens.expenses.ExpenseAddEditDialog
import com.localbudget.app.ui.theme.parseColorHex
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToExpenses: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onImportStatement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val loadedSummary by viewModel.monthlySummary.collectAsState()
    val summary = loadedSummary?.takeIf { it.yearMonth == currentMonth.toString() }
    val categories by viewModel.categories.collectAsState()
    val monthlyTrend by viewModel.monthlyTrend.collectAsState()
    val monthExpenses by viewModel.monthExpenses.collectAsState()
    val cardExpenses by viewModel.cardExpenses.collectAsState()
    val subscriptionKeys by viewModel.subscriptionKeys.collectAsState()

    var showChoiceDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val animationScope = rememberCoroutineScope()
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swipeWidth by remember { mutableFloatStateOf(1f) }
    var swipeAnimation by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showChoiceDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "支出を追加")
            }
        },
        // The app's outer Scaffold already pads for the status and navigation bars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clipToBounds()
                .pointerInput(viewModel) {
                    val threshold = 64.dp.toPx()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    swipeWidth = width
                    fun animateSwipe(changeMonth: Boolean) {
                        val start = swipeOffset
                        swipeAnimation?.cancel()
                        swipeAnimation = animationScope.launch {
                            val offset = Animatable(start)
                            if (changeMonth) {
                                val direction = if (start > 0) 1f else -1f
                                offset.animateTo(direction * width * 0.32f, tween(160, easing = FastOutSlowInEasing)) { swipeOffset = value }
                                if (direction > 0) viewModel.previousMonth() else viewModel.nextMonth()
                                offset.snapTo(-direction * width * 0.16f)
                                swipeOffset = offset.value
                            }
                            offset.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) { swipeOffset = value }
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { swipeAnimation?.cancel() },
                        onHorizontalDrag = { _, distance -> swipeOffset = (swipeOffset + distance).coerceIn(-width * 0.32f, width * 0.32f) },
                        onDragEnd = { animateSwipe(abs(swipeOffset) >= threshold) },
                        onDragCancel = { animateSwipe(false) }
                    )
                }
                .graphicsLayer {
                    translationX = swipeOffset
                    alpha = 1f - (abs(swipeOffset) / (swipeWidth * 0.32f)).coerceIn(0f, 1f) * 0.2f
                }
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                MonthYearSelector(
                    currentMonth = currentMonth,
                    onSelectMonth = viewModel::setMonth,
                )
            }

            // This month's spending, how it compares and what the budget leaves
            item {
                val totalExpense = summary?.totalExpense ?: 0L
                val budgetAmount = summary?.budgetAmount
                val usageRate = summary?.budgetUsageRate ?: 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "${currentMonth.monthValue}月の支出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "¥${currencyFormatter.format(totalExpense)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        SelectedSubscriptionsSection(
                            cardExpenses, monthExpenses, currentMonth, subscriptionKeys,
                            viewModel::setSubscriptions, onImportStatement, currencyFormatter
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            ChangeRateItem(
                                label = "前月比",
                                rate = summary?.monthOverMonthRate,
                                baseText = summary?.previousMonthTotal?.takeIf { it > 0 }
                                    ?.let { "前月 ¥${currencyFormatter.format(it)}" } ?: "前月の記録なし",
                                modifier = Modifier.weight(1f)
                            )
                            ChangeRateItem(
                                label = "前年比",
                                rate = summary?.yearOverYearRate,
                                baseText = summary?.lastYearTotal?.takeIf { it > 0 }
                                    ?.let { "前年同月 ¥${currencyFormatter.format(it)}" } ?: "前年同月の記録なし",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (budgetAmount != null && budgetAmount > 0) {
                            val remaining = budgetAmount - totalExpense
                            val isOverBudget = remaining < 0
                            val remainingDays = summary?.remainingDays ?: 0
                            val dailyAllowance = summary?.dailyAllowance

                            val progressColor = when {
                                isOverBudget -> Color(0xFFE53935)
                                usageRate > 0.8f -> Color(0xFFFB8C00)
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        isOverBudget -> "予算を ¥${currencyFormatter.format(-remaining)} 超えています"
                                        remainingDays > 0 -> "あと ¥${currencyFormatter.format(remaining)} 使えます"
                                        else -> "予算の残り ¥${currencyFormatter.format(remaining)}"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverBudget) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { showBudgetDialog = true }) {
                                    Text("予算を変更")
                                }
                            }

                            LinearProgressIndicator(
                                progress = { usageRate.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "予算 ¥${currencyFormatter.format(budgetAmount)}（${(usageRate * 100).toInt()}%使用）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (dailyAllowance != null) {
                                    Text(
                                        text = "1日 ¥${currencyFormatter.format(dailyAllowance)} まで（残り${remainingDays}日）",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            TextButton(onClick = { showBudgetDialog = true }) {
                                Text(
                                    text = "＋ 月の予算を設定する",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Six-month trend, each bar stacked by category
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "月別支出推移 (直近6ヶ月)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        MonthlyStackedBarChart(
                            trend = if (summary != null) monthlyTrend else com.localbudget.app.domain.model.CategoryTrend.EMPTY,
                            currentMonthLabel = "${currentMonth.monthValue}月"
                        )
                    }
                }
            }

            // Analysis for the selected month (formerly the 集計・分析 tab)
            item {
                val s = summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "カテゴリ別の月額",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = "カテゴリの自動分類には誤りがある場合があります。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                        )
                        if (s == null) {
                            // Not read yet: blank rather than flash "no data"
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (s.categorySummaries.isEmpty()) {
                            Text(
                                text = "支出データがありません",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            DonutChart(
                                summaries = s.categorySummaries,
                                totalAmount = s.totalExpense,
                                chartSize = 200.dp,
                                strokeWidth = 28.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            s.categorySummaries.forEach { catSummary ->
                                CategoryBreakdownRow(catSummary, currencyFormatter, monthExpenses.filter {
                                    it.categoryId == catSummary.category.id && java.time.YearMonth.from(it.date) == currentMonth
                                }, currentMonth.toString())
                            }
                        }
                    }
                }
            }


            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || editingExpense != null) {
        val savedItems by produceState(emptyList<ReceiptLineItem>(), editingExpense?.id) {
            value = editingExpense?.id?.takeIf { it != 0L }?.let { viewModel.getReceiptItems(it) } ?: emptyList()
        }
        ExpenseAddEditDialog(
            initialExpense = editingExpense,
            categories = categories,
            savedItems = savedItems,
            onPickImage = if (editingExpense == null) ({ showAddDialog = false; onNavigateToGallery() }) else null,
            onDismiss = {
                showAddDialog = false
                editingExpense = null
            },
            onSave = { expense, items ->
                viewModel.saveExpense(expense, items)
                showAddDialog = false
                editingExpense = null
            }
        )
    }

    // Budget Dialog
    if (showBudgetDialog) {
        var budgetInput by remember {
            mutableStateOf(summary?.budgetAmount?.toString() ?: "")
        }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("月の予算を設定") },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { if (it.all { c -> c.isDigit() }) budgetInput = it },
                    label = { Text("予算金額 (円)") },
                    prefix = { Text("¥ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = budgetInput.toLongOrNull() ?: 0L
                        viewModel.setBudget(amount)
                        showBudgetDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBudgetDialog = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // Input Choice Dialog
    if (showChoiceDialog) {
        com.localbudget.app.ui.components.ExpenseInputChoiceDialog(
            onDismiss = { showChoiceDialog = false },
            onCamera = { showChoiceDialog = false; onNavigateToCamera() },
            onStatement = { showChoiceDialog = false; onImportStatement() },
            onManual = { showChoiceDialog = false; showAddDialog = true }
        )
    }
}

/** One comparison under the month's total: a rise in spending in red, a fall in the primary color. */
@Composable
private fun ChangeRateItem(label: String, rate: Float?, baseText: String, modifier: Modifier = Modifier) {
    val percent = rate?.let { Math.round(it * 100) }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when {
                percent == null -> "—"
                percent > 0 -> "▲ $percent%"
                percent < 0 -> "▼ ${-percent}%"
                else -> "± 0%"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = when {
                percent == null || percent == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                percent > 0 -> Color(0xFFE53935)
                else -> MaterialTheme.colorScheme.primary
            }
        )
        Text(
            text = baseText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One category's share of the month: amount, count and a bar in the category color. */
@Composable
private fun CategoryBreakdownRow(item: CategoryExpenseSummary, currencyFormatter: NumberFormat, expenses: List<Expense>, monthKey: String) {
    var expanded by remember(item.category.id, monthKey) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconBadge(
                iconName = item.category.iconName,
                colorHex = item.category.colorHex,
                badgeSize = 32.dp,
                iconSize = 18.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "支出先を閉じる" else "支出先を見る",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${item.count}件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "¥${currencyFormatter.format(item.totalAmount)}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${(item.percentage * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { item.percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = parseColorHex(item.category.colorHex),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (expanded) {
            expenses.groupBy { it.storeName.trim().ifEmpty { "名称なし" } }
                .entries.sortedByDescending { (_, entries) -> entries.sumOf { it.amount } }
                .forEach { (store, entries) ->
                    Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(store, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("¥${currencyFormatter.format(entries.sumOf { it.amount })}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
        }
    }
}
