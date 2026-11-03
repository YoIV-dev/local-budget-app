package com.localbudget.app.ui.screens.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.domain.food.ConsumptionEstimator
import com.localbudget.app.domain.food.FridgeShareTextBuilder
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import com.localbudget.app.domain.repository.FridgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FridgeViewMode {
    BY_DATE,
    BY_CATEGORY
}

/** What the estimator wants to ask: prompts shown above the list, and the stock-check list. */
data class ConsumptionState(
    val prompts: List<ConsumptionEstimator.Suggestion> = emptyList(),
    val stockCheck: List<ConsumptionEstimator.StockCheckEntry> = emptyList()
)

/** An item that was just ended, for the "元に戻す" snackbar. */
data class EndedNotice(val itemId: Long, val name: String, val status: FridgeItemStatus)


/** Inventory across the fridge, freezer and pantry. */
class FridgeViewModel(
    private val fridgeRepository: FridgeRepository,
    private val estimator: ConsumptionEstimator = ConsumptionEstimator(),
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val viewMode = MutableStateFlow(FridgeViewMode.BY_CATEGORY)
    val storageFilter = MutableStateFlow<StorageLocation?>(null)
    val categoryFilter = MutableStateFlow<FoodCategory?>(null)

    // All storages: repurchases and learned lifetimes span them.
    // The stock tabs are preloaded at launch (see AppNavGraph), so this is read once, eagerly, and
    // shared by everything below instead of each list querying the table on its own.
    private val allItemsFlow = fridgeRepository.getAllFridgeItems()
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    // Active items matching the search query; null until the stock is first
    // read, so the screen does not show its empty message before the items arrive
    val filteredItems: StateFlow<List<FridgeItem>?> = combine(
        allItemsFlow,
        searchQuery,
        storageFilter,
        categoryFilter
    ) { items, query, storage, category ->
        items.filter { item ->
            val statusMatch = item.isActive
            val queryMatch = query.isBlank() ||
                    item.name.contains(query, ignoreCase = true) ||
                    item.category.displayName.contains(query, ignoreCase = true)
            statusMatch && queryMatch && (storage == null || item.storage == storage) &&
                (category == null || item.category == category)
        }.sortedWith(
            compareBy<FridgeItem> { it.purchaseDate }
                .thenBy { it.createdAt }
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Active items across all storage locations, for sharing / summary count
    val activeItems: StateFlow<List<FridgeItem>> = allItemsFlow.map { items ->
        items.filter { it.isActive }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Grouped by FoodCategory for BY_CATEGORY mode
    val itemsByCategory: StateFlow<Map<FoodCategory, List<FridgeItem>>> = filteredItems.filterNotNull().combine(viewMode) { items, _ ->
        val map = linkedMapOf<FoodCategory, MutableList<FridgeItem>>()
        FoodCategory.entries.forEach { map[it] = mutableListOf() }
        items.forEach { item ->
            map.getOrPut(item.category) { mutableListOf() }.add(item)
        }
        map.filterValues { it.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // Recomputed whenever an item or event changes, so answered items drop out at once.
    // Estimated over every storage.
    val consumption: StateFlow<ConsumptionState> = combine(
        allItemsFlow,
        fridgeRepository.getAllEvents()
    ) { items, events ->
        val now = clock()
        val suggestions = estimator.suggest(items, events, now)
        ConsumptionState(
            prompts = suggestions.filter { it.kind != ConsumptionEstimator.Kind.UNSURE },
            stockCheck = estimator.stockCheck(items, events, suggestions, now)
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConsumptionState())

    private val _endedNotices = MutableSharedFlow<EndedNotice>(extraBufferCapacity = 1)
    val endedNotices: SharedFlow<EndedNotice> = _endedNotices.asSharedFlow()

    /** Short messages for things that leave the list on screen: a move, or an add to another storage. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    fun setViewMode(mode: FridgeViewMode) {
        viewMode.value = mode
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setStorageFilter(storage: StorageLocation?) { storageFilter.value = storage }
    fun setCategoryFilter(category: FoodCategory?) { categoryFilter.value = category }

    fun toggleConsumed(item: FridgeItem) {
        if (item.isActive) end(item, FridgeItemStatus.CONSUMED) else restore(item.id)
    }

    /** Answer to a prompt or stock check: used up or thrown away. */
    fun end(item: FridgeItem, status: FridgeItemStatus) {
        viewModelScope.launch {
            fridgeRepository.setStatus(item.id, status)
            _endedNotices.tryEmit(EndedNotice(item.id, item.name, status))
        }
    }

    /** Answer to a prompt or stock check: still there. */
    fun confirmPresent(item: FridgeItem) {
        viewModelScope.launch {
            fridgeRepository.confirmPresent(item.id)
        }
    }

    /** Undoes an end; logged as REVERTED, which also counts as the item being seen. */
    fun restore(itemId: Long) {
        viewModelScope.launch {
            fridgeRepository.setStatus(itemId, FridgeItemStatus.ACTIVE)
        }
    }

    fun consumeQuantity(item: FridgeItem, quantity: Int) {
        viewModelScope.launch {
            val current = fridgeRepository.getFridgeItemById(item.id) ?: return@launch
            if (!current.isActive || quantity <= 0) return@launch
            if (quantity >= current.quantity) {
                end(current, FridgeItemStatus.CONSUMED)
            } else {
                fridgeRepository.updateFridgeItem(current.copy(quantity = current.quantity - quantity))
            }
        }
    }

    fun saveItem(item: FridgeItem) {
        viewModelScope.launch {
            if (item.id == 0L) {
                fridgeRepository.insertFridgeItem(item)
            } else {
                val previous = fridgeRepository.getFridgeItemById(item.id) ?: return@launch
                fridgeRepository.updateFridgeItem(item)
                if (previous.isActive && previous.storage != item.storage) {
                    fridgeRepository.moveTo(item.id, item.storage)
                }
            }
        }
    }

    fun deleteItem(item: FridgeItem) {
        viewModelScope.launch {
            fridgeRepository.deleteFridgeItem(item)
        }
    }

    init {
        // Nobody looks back at used-up items, so their rows go once the undo window is long past.
        // Their events stay: estimates learn from those, not from the rows.
        viewModelScope.launch {
            fridgeRepository.clearEndedItems(before = clock() - ENDED_ITEM_KEEP_MILLIS)
        }
    }

    fun buildShareText(): String {
        return FridgeShareTextBuilder.build(activeItems.value, placeName = "食材", includeStorage = true)
    }

    private companion object {
        const val ENDED_ITEM_KEEP_MILLIS = 60 * 60 * 1000L
    }

    class Factory(
        private val fridgeRepository: FridgeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FridgeViewModel(fridgeRepository) as T
        }
    }
}
