package com.localbudget.app.ui.screens.fridge

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.StorageLocation
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FridgeScreen(
    viewModel: FridgeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()
    val itemsByCategory by viewModel.itemsByCategory.collectAsState()
    val activeItems by viewModel.activeItems.collectAsState()
    val storageFilter by viewModel.storageFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val consumption by viewModel.consumption.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<FridgeItem?>(null) }
    var itemToConsume by remember { mutableStateOf<FridgeItem?>(null) }
    var promptsExpanded by remember { mutableStateOf(false) }
    var showStockCheck by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val consumptionActions = remember(viewModel) {
        ConsumptionActions(
            onEnd = { item, status -> viewModel.end(item, status) },
            onStillThere = { item -> viewModel.confirmPresent(item) }
        )
    }
    // Prompts are hidden while searching so they never cover the result being looked for
    val isFiltered = searchQuery.isNotBlank() || storageFilter != null || categoryFilter != null
    val prompts = if (!isFiltered) consumption.prompts else emptyList()

    LaunchedEffect(viewModel) {
        // A newer notice cancels (and dismisses) the one on screen; only the latest can be undone
        viewModel.endedNotices.collectLatest { notice ->
            val result = snackbarHostState.showSnackbar(
                message = endedMessage(notice),
                actionLabel = "元に戻す",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(notice.itemId)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.notices.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    fun shareInventory() {
        if (activeItems.isEmpty()) {
            Toast.makeText(context, "共有できる在庫がありません", Toast.LENGTH_SHORT).show()
            return
        }
        val text = viewModel.buildShareText()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "食材の在庫を共有")
        context.startActivity(shareIntent)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "食材を追加")
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
            // View mode tabs
            PrimaryTabRow(
                selectedTabIndex = if (viewMode == FridgeViewMode.BY_CATEGORY) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = viewMode == FridgeViewMode.BY_CATEGORY,
                    onClick = { viewModel.setViewMode(FridgeViewMode.BY_CATEGORY) },
                    text = { Text("カテゴリ別", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = viewMode == FridgeViewMode.BY_DATE,
                    onClick = { viewModel.setViewMode(FridgeViewMode.BY_DATE) },
                    text = { Text("購入日順", fontWeight = FontWeight.Bold) }
                )
            }

            // Search Bar & Filter / Share Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("品名やカテゴリで検索...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "検索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "クリア")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var storageMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = storageFilter != null,
                            onClick = { storageMenu = true },
                            label = { Text(storageFilter?.displayName ?: "保存場所") },
                            trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenu(expanded = storageMenu, onDismissRequest = { storageMenu = false }) {
                            DropdownMenuItem(text = { Text("すべての保存場所") }, onClick = { viewModel.setStorageFilter(null); storageMenu = false })
                            StorageLocation.entries.forEach { location ->
                                DropdownMenuItem(text = { Text(location.displayName) }, onClick = { viewModel.setStorageFilter(location); storageMenu = false })
                            }
                        }
                    }
                    var categoryMenu by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(
                            selected = categoryFilter != null,
                            onClick = { categoryMenu = true },
                            label = { Text(categoryFilter?.displayName ?: "カテゴリ") },
                            trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text("すべてのカテゴリ") }, onClick = { viewModel.setCategoryFilter(null); categoryMenu = false })
                            FoodCategory.entries.forEach { category ->
                                DropdownMenuItem(text = { Text("${category.iconEmoji} ${category.displayName}") }, onClick = { viewModel.setCategoryFilter(category); categoryMenu = false })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Control row: Summary badge, share button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Inventory count badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "在庫: ${activeItems.size}品",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Share button
                    Button(
                        onClick = { shareInventory() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "共有",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("共有", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!isFiltered && consumption.stockCheck.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StockCheckBanner(
                        count = consumption.stockCheck.size,
                        onStart = { showStockCheck = true }
                    )
                }
            }

            // Inventory List Content
            val shownItems = filteredItems
            if (shownItems == null) {
                // Not read yet: leave the list blank rather than flash the empty message
            } else if (shownItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Kitchen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isFiltered) "一致する食材が見つかりません" else "食材がありません",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isFiltered) "検索や絞り込みの条件を変更してください"
                            else "レシートをスキャンして自動抽出するか、\n右下の「＋」ボタンから追加してください",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                when (viewMode) {
                    FridgeViewMode.BY_DATE -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 80.dp
                            )
                        ) {
                            consumptionPromptSection(
                                prompts = prompts,
                                expanded = promptsExpanded,
                                onToggleExpanded = { promptsExpanded = !promptsExpanded },
                                actions = consumptionActions
                            )
                            items(shownItems, key = { it.id }) { item ->
                                FridgeItemCard(
                                    item = item,
                                    onToggleConsumed = {
                                        if (item.isActive && item.quantity > 1) { itemToConsume = item }
                                        else viewModel.toggleConsumed(item)
                                    },
                                    onEdit = {
                                        editingItem = item
                                        showAddEditDialog = true
                                    },
                                    showDate = true
                                )
                            }
                        }
                    }
                    FridgeViewMode.BY_CATEGORY -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 80.dp
                            )
                        ) {
                            consumptionPromptSection(
                                prompts = prompts,
                                expanded = promptsExpanded,
                                onToggleExpanded = { promptsExpanded = !promptsExpanded },
                                actions = consumptionActions
                            )
                            itemsByCategory.forEach { (category, itemsInCat) ->
                                item(key = "header_${category.name}") {
                                    CategorySectionHeader(
                                        category = category,
                                        count = itemsInCat.size
                                    )
                                }
                                items(itemsInCat, key = { it.id }) { item ->
                                    FridgeItemCard(
                                        item = item,
                                        onToggleConsumed = {
                                            if (item.isActive && item.quantity > 1) { itemToConsume = item }
                                            else viewModel.toggleConsumed(item)
                                        },
                                        onEdit = {
                                            editingItem = item
                                            showAddEditDialog = true
                                        },
                                        showDate = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddEditDialog) {
        AddEditFridgeDialog(
            initialItem = editingItem,
            defaultStorage = StorageLocation.FRIDGE,
            onDismiss = {
                showAddEditDialog = false
                editingItem = null
            },
            onSave = { savedItem ->
                viewModel.saveItem(savedItem)
                showAddEditDialog = false
                editingItem = null
            }
        )
    }

    if (showStockCheck) {
        if (consumption.stockCheck.isEmpty()) {
            LaunchedEffect(Unit) { showStockCheck = false }
        } else {
            StockCheckDialog(
                entries = consumption.stockCheck,
                actions = consumptionActions,
                onDismiss = { showStockCheck = false }
            )
        }
    }

    itemToConsume?.let { item ->
        var quantityText by remember(item.id) { mutableStateOf("1") }
        val quantity = quantityText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { itemToConsume = null },
            title = { Text("${item.name}を使いましたか？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("現在の在庫: ${item.quantity}${item.unit}")
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { if (it.length <= 9 && it.all(Char::isDigit)) quantityText = it },
                        label = { Text("使った数${if (item.unit.isNotBlank()) "（${item.unit}）" else ""}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        supportingText = { Text("1〜${item.quantity}の数を入力") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.consumeQuantity(item, quantity!!); itemToConsume = null }, enabled = quantity != null && quantity in 1..item.quantity) {
                    Text("入力した数を消費")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { itemToConsume = null }) { Text("キャンセル") }
                    TextButton(onClick = { viewModel.consumeQuantity(item, item.quantity); itemToConsume = null }) { Text("全て使い切った") }
                }
            }
        )
    }


}

@Composable
fun CategorySectionHeader(
    category: FoodCategory,
    count: Int,
    modifier: Modifier = Modifier
) {
    val catColor = Color(category.colorHexLong)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(catColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.iconEmoji, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.displayName,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "(${count}件)",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FridgeItemCard(
    item: FridgeItem,
    onToggleConsumed: () -> Unit,
    onEdit: () -> Unit,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val catColor = Color(item.category.colorHexLong)
    // Counted from the move into the current storage, if it was moved after purchase
    val daysStored = remember(item.storedSince) {
        ChronoUnit.DAYS.between(item.storedSince, LocalDate.now())
    }
    val pastExpiry = item.estimatedExpiry?.let { LocalDate.now().isAfter(it) } ?: (daysStored >= 7)
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!item.isActive)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (!item.isActive) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for consumed status
            Checkbox(
                checked = !item.isActive,
                onCheckedChange = { onToggleConsumed() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.outline
                )
            )

            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(catColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(FoodItemIcon.forName(item.name, item.category), fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Item Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (!item.isActive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (!item.isActive) TextDecoration.LineThrough else null
                    )
                }

                if (showDate) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    // Purchase (or move) date & age
                    val dateText = item.storedOn?.let { "${it.format(dateFormatter)}に移動" }
                        ?: "${item.purchaseDate.format(dateFormatter)}購入"
                    val ageText = when {
                        daysStored == 0L -> "今日"
                        daysStored == 1L -> "昨日"
                        daysStored > 1L -> "${daysStored}日前"
                        else -> ""
                    }
                    Text(
                        text = if (ageText.isNotEmpty()) "$dateText ($ageText)" else dateText,
                        fontSize = 11.sp,
                        color = if (pastExpiry && item.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                    )
                    }
                }
            }

            // Quantity and item actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${item.quantity}${item.unit}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (!item.isActive) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )

            }
        }
    }
}

fun storageIcon(storage: StorageLocation): ImageVector = when (storage) {
    StorageLocation.FRIDGE -> Icons.Default.Kitchen
    StorageLocation.FREEZER -> Icons.Default.AcUnit
    StorageLocation.PANTRY -> Icons.Default.Inventory2
}
