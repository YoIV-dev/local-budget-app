package com.localbudget.app.domain.repository

import com.localbudget.app.domain.model.FridgeEvent
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import kotlinx.coroutines.flow.Flow

/**
 * Fridge stock. Adding an item and changing its status always append to the event log,
 * so the history used by ConsumptionEstimator cannot drift from the items.
 */
interface FridgeRepository {
    fun getActiveFridgeItems(): Flow<List<FridgeItem>>
    fun getAllFridgeItems(): Flow<List<FridgeItem>>
    fun getAllEvents(): Flow<List<FridgeEvent>>
    suspend fun getFridgeItemById(id: Long): FridgeItem?
    /** Missing derived fields (food key, expiry) are filled in; logs ADDED. */
    suspend fun insertFridgeItem(item: FridgeItem): Long
    suspend fun insertFridgeItems(items: List<FridgeItem>): List<Long>
    /** Edits fields other than status and storage; use [setStatus] and [moveTo] for those. */
    suspend fun updateFridgeItem(item: FridgeItem)
    /** Moves an active item to [storage] from today, re-deriving its expiry; logs MOVED. */
    suspend fun moveTo(id: Long, storage: StorageLocation)
    /** Logs CONSUMED / DISCARDED, or REVERTED when going back to ACTIVE. */
    suspend fun setStatus(id: Long, status: FridgeItemStatus, source: FridgeEventSource = FridgeEventSource.USER)
    suspend fun deleteFridgeItem(item: FridgeItem)
    suspend fun deleteFridgeItemById(id: Long)
    /** Logs that the user checked the item is still there (stock check). */
    suspend fun confirmPresent(id: Long)
    /** Removes items consumed or discarded before [before] (epoch millis); their events are kept. */
    suspend fun clearEndedItems(before: Long)
}
