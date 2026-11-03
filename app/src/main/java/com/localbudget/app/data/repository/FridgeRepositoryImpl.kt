package com.localbudget.app.data.repository

import com.localbudget.app.data.local.dao.FridgeDao
import com.localbudget.app.data.local.entity.FridgeEventEntity
import com.localbudget.app.data.local.entity.FridgeItemEntity
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.FridgeEvent
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import com.localbudget.app.domain.repository.FridgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

class FridgeRepositoryImpl(
    private val fridgeDao: FridgeDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Long = System::currentTimeMillis
) : FridgeRepository {

    override fun getActiveFridgeItems(): Flow<List<FridgeItem>> {
        return fridgeDao.getActiveFridgeItems().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAllFridgeItems(): Flow<List<FridgeItem>> {
        return fridgeDao.getAllFridgeItems().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getAllEvents(): Flow<List<FridgeEvent>> {
        return fridgeDao.getAllEvents().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getFridgeItemById(id: Long): FridgeItem? {
        return fridgeDao.getFridgeItemById(id)?.toDomain()
    }

    override suspend fun insertFridgeItem(item: FridgeItem): Long {
        val enriched = FridgeItemEnricher.enrich(item)
        return fridgeDao.insertWithEvent(FridgeItemEntity.fromDomain(enriched)) { id -> addedEvent(enriched, id) }
    }

    override suspend fun insertFridgeItems(items: List<FridgeItem>): List<Long> {
        val enriched = items.map { FridgeItemEnricher.enrich(it) }
        return fridgeDao.insertAllWithEvents(enriched.map { FridgeItemEntity.fromDomain(it) }) { index, id ->
            addedEvent(enriched[index], id)
        }
    }

    override suspend fun updateFridgeItem(item: FridgeItem) {
        // Status and storage only change through setStatus and moveTo, so they are taken from the stored row.
        // Name, category or date may have been edited: derive the food key and expiry again.
        val stored = fridgeDao.getFridgeItemById(item.id)?.toDomain() ?: return
        val enriched = FridgeItemEnricher.enrich(
            item.copy(
                status = stored.status,
                endedAt = stored.endedAt,
                storage = stored.storage,
                storedOn = stored.storedOn,
                normalizedName = "",
                estimatedExpiry = null
            )
        )
        fridgeDao.updateFridgeItem(FridgeItemEntity.fromDomain(enriched))
    }

    override suspend fun moveTo(id: Long, storage: StorageLocation) {
        val stored = fridgeDao.getFridgeItemById(id)?.toDomain() ?: return
        if (!stored.isActive || stored.storage == storage) return
        val now = clock()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val moved = FridgeItemEnricher.enrich(stored.copy(storage = storage, storedOn = today, estimatedExpiry = null))
        fridgeDao.updateWithEvent(
            FridgeItemEntity.fromDomain(moved),
            FridgeEventEntity.fromDomain(event(moved, id, FridgeEventType.MOVED, FridgeEventSource.USER, now))
        )
    }

    override suspend fun setStatus(id: Long, status: FridgeItemStatus, source: FridgeEventSource) {
        val stored = fridgeDao.getFridgeItemById(id)?.toDomain() ?: return
        if (stored.status == status) return
        val now = clock()
        val type = when (status) {
            FridgeItemStatus.ACTIVE -> FridgeEventType.REVERTED
            FridgeItemStatus.CONSUMED -> FridgeEventType.CONSUMED
            FridgeItemStatus.DISCARDED -> FridgeEventType.DISCARDED
        }
        fridgeDao.updateStatusWithEvent(
            id = id,
            status = status.name,
            endedAt = if (status == FridgeItemStatus.ACTIVE) null else now,
            event = FridgeEventEntity.fromDomain(event(stored, id, type, source, now))
        )
    }

    override suspend fun confirmPresent(id: Long) {
        val stored = fridgeDao.getFridgeItemById(id)?.toDomain() ?: return
        if (!stored.isActive) return
        fridgeDao.insertEvent(
            FridgeEventEntity.fromDomain(event(stored, id, FridgeEventType.CONFIRMED, FridgeEventSource.USER, clock()))
        )
    }

    override suspend fun deleteFridgeItem(item: FridgeItem) {
        fridgeDao.deleteFridgeItem(FridgeItemEntity.fromDomain(item))
    }

    override suspend fun deleteFridgeItemById(id: Long) {
        fridgeDao.deleteFridgeItemById(id)
    }

    override suspend fun clearEndedItems(before: Long) {
        fridgeDao.clearEndedItems(before)
    }

    private fun addedEvent(item: FridgeItem, id: Long): FridgeEventEntity {
        val source = if (item.expenseId != null) FridgeEventSource.RECEIPT else FridgeEventSource.USER
        // createdAt, not now: restoring a backup keeps each item's original add time
        return FridgeEventEntity.fromDomain(event(item, id, FridgeEventType.ADDED, source, item.createdAt))
    }

    private fun event(item: FridgeItem, id: Long, type: FridgeEventType, source: FridgeEventSource, at: Long) =
        FridgeEvent(
            itemId = id,
            foodKey = item.normalizedName.ifBlank { FridgeItemEnricher.foodKey(item.name) },
            category = item.category,
            storage = item.storage,
            type = type,
            source = source,
            occurredAt = at
        )
}
