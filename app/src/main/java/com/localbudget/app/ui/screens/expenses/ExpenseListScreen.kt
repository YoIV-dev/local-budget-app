package com.localbudget.app.ui.screens.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.ui.components.CategoryIconBadge
import com.localbudget.app.ui.components.ExpenseCard
import com.localbudget.app.ui.components.MonthYearSelector
import com.localbudget.app.ui.theme.parseColorHex
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onNavigateToCamera: () -> Unit = {},
    onNavigateToGallery: () -> Unit = {},
    onImportStatement: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCatId by viewModel.selectedCategoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val results by viewModel.results.collectAsState()
    val isSearching = searchQuery.isNotBlank()
    val currentResults = results?.takeIf {
        it.query == searchQuery && it.categoryId == selectedCatId && (isSearching || it.month == currentMonth)
    }
    val groupedExpenses = currentResults?.grouped.orEmpty()
    val total = groupedExpenses.values.sumOf { entries -> entries.sumOf { it.amount } }

    var showChoiceDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale.JAPAN) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("M月d日 (E)", Locale.JAPANESE) }
    val searchDateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 (E)", Locale.JAPANESE) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Month Selector
            if (!isSearching) {
                MonthYearSelector(
                    currentMonth = currentMonth,
                    onSelectMonth = viewModel::setMonth,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("店舗名、カテゴリ、メモで検索") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All" chip
                val isAllSelected = selectedCatId == null
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAllSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { viewModel.setCategoryFilter(null) }
                ) {
                    Text(
                        text = "すべて",
                        fontSize = 12.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                categories.forEach { cat ->
                    val isSelected = cat.id == selectedCatId
                    val catColor = parseColorHex(cat.colorHex)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) catColor else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { viewModel.setCategoryFilter(cat.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            CategoryIconBadge(
                                iconName = cat.iconName,
                                colorHex = cat.colorHex,
                                badgeSize = 18.dp,
                                iconSize = 10.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = cat.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Summary Subheader
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalCount = groupedExpenses.values.sumOf { it.size }
                Text(
                    text = if (isSearching) "全期間の検索結果 ${totalCount}件" else "${totalCount}件の支出",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "合計: ¥${currencyFormatter.format(total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Grouped Expense List
            if (currentResults == null) {
                // Wait until the current search or month has loaded.
            } else if (groupedExpenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank() || selectedCatId != null) "条件に一致する支出がありません"
                        else "この月の支出はまだ記録されていません",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    groupedExpenses.forEach { (date, expensesForDate) ->
                        val dayTotal = expensesForDate.sumOf { it.amount }

                        // Date Header
                        item(key = "header_${date}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = date.format(if (isSearching) searchDateFormatter else dayFormatter),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "¥${currencyFormatter.format(dayTotal)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Expense items for this date
                        items(expensesForDate, key = { it.id }) { expense ->
                            ExpenseCard(
                                expense = expense,
                                onClick = { editingExpense = expense },
                                onDelete = { viewModel.deleteExpense(expense.id) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

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

    if (showChoiceDialog) {
        com.localbudget.app.ui.components.ExpenseInputChoiceDialog(
            onDismiss = { showChoiceDialog = false },
            onCamera = { showChoiceDialog = false; onNavigateToCamera() },
            onStatement = { showChoiceDialog = false; onImportStatement() },
            onManual = { showChoiceDialog = false; showAddDialog = true }
        )
    }
}
