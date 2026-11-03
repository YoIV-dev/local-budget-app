package com.localbudget.app.domain.model

/**
 * CONFIRMED: the user said the item is still there (stock check); REVERTED: an end was undone;
 * MOVED: the item went to another storage, which the event's storage names.
 */
enum class FridgeEventType { ADDED, CONFIRMED, CONSUMED, DISCARDED, REVERTED, MOVED }

/** Who caused an event. Only USER/RECEIPT events are ground truth; ESTIMATED ones came from the app. */
enum class FridgeEventSource { USER, ESTIMATED, RECEIPT }

/**
 * Append-only history of fridge items: the training and evaluation data for consumption
 * estimates. Carries its own food key and storage so it outlives deletion of the item row.
 */
data class FridgeEvent(
    val id: Long = 0,
    val itemId: Long,
    val foodKey: String,
    val category: FoodCategory,
    val storage: StorageLocation,
    val type: FridgeEventType,
    val source: FridgeEventSource,
    val occurredAt: Long
)
