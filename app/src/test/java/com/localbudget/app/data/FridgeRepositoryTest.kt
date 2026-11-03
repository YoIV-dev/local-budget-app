package com.localbudget.app.data

import com.localbudget.app.data.local.dao.FridgeDao
import com.localbudget.app.data.local.entity.FridgeEventEntity
import com.localbudget.app.data.local.entity.FridgeItemEntity
import com.localbudget.app.data.repository.FridgeRepositoryImpl
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FridgeRepositoryTest {

    private val dao = InMemoryFridgeDao()
    private var now = 1_000L
    private val repo = FridgeRepositoryImpl(dao, ZoneOffset.UTC) { now }

    private val cabbage = FridgeItem(
        name = "ｷｬﾍﾞﾂ 1玉",
        category = FoodCategory.VEGETABLE,
        purchaseDate = LocalDate.of(2026, 10, 1),
        expenseId = 7,
        createdAt = 500
    )

    @Test
    fun insertFillsDerivedFieldsAndLogsAdded() = runTest {
        val id = repo.insertFridgeItem(cabbage)

        val stored = repo.getFridgeItemById(id)!!
        assertEquals(FridgeItemEnricher.foodKey("キャベツ"), stored.normalizedName)
        assertEquals(LocalDate.of(2026, 10, 15), stored.estimatedExpiry) // キャベツ keeps 14 days

        val event = repo.getAllEvents().first().single()
        assertEquals(id, event.itemId)
        assertEquals(FridgeEventType.ADDED, event.type)
        assertEquals(FridgeEventSource.RECEIPT, event.source) // came with an expense
        assertEquals(500L, event.occurredAt)
    }

    @Test
    fun bulkInsertKeepsIdenticalItemsApart() = runTest {
        val ids = repo.insertFridgeItems(listOf(cabbage, cabbage))

        assertEquals(2, ids.distinct().size)
        assertEquals(ids, repo.getAllEvents().first().map { it.itemId })
    }

    @Test
    fun statusChangesAreLoggedAndUndoable() = runTest {
        val id = repo.insertFridgeItem(cabbage)

        now = 2_000
        repo.setStatus(id, FridgeItemStatus.CONSUMED)
        assertEquals(2_000L, repo.getFridgeItemById(id)!!.endedAt)

        now = 3_000
        repo.setStatus(id, FridgeItemStatus.ACTIVE)
        assertNull(repo.getFridgeItemById(id)!!.endedAt)

        repo.setStatus(id, FridgeItemStatus.ACTIVE) // no change, no event
        assertEquals(
            listOf(FridgeEventType.ADDED, FridgeEventType.CONSUMED, FridgeEventType.REVERTED),
            repo.getAllEvents().first().map { it.type }
        )
    }

    @Test
    fun editKeepsStatusAndRederivesExpiry() = runTest {
        val id = repo.insertFridgeItem(cabbage)
        repo.setStatus(id, FridgeItemStatus.DISCARDED)

        val stored = repo.getFridgeItemById(id)!!
        repo.updateFridgeItem(stored.copy(name = "ﾓﾔｼ", status = FridgeItemStatus.ACTIVE))

        val edited = repo.getFridgeItemById(id)!!
        assertEquals(FridgeItemStatus.DISCARDED, edited.status)
        assertEquals(LocalDate.of(2026, 10, 3), edited.estimatedExpiry) // もやし keeps 2 days
    }

    @Test
    fun moveRestartsExpiryFromTodayAndIsLogged() = runTest {
        val id = repo.insertFridgeItem(cabbage)
        now = LocalDate.of(2026, 10, 5).toEpochDay() * 86_400_000L + 3_600_000L

        repo.moveTo(id, StorageLocation.FREEZER)
        repo.moveTo(id, StorageLocation.FREEZER) // already there, no event

        val moved = repo.getFridgeItemById(id)!!
        assertEquals(StorageLocation.FREEZER, moved.storage)
        assertEquals(LocalDate.of(2026, 10, 5), moved.storedOn)
        assertEquals(LocalDate.of(2026, 11, 4), moved.estimatedExpiry) // frozen keeps 30 days
        val event = repo.getAllEvents().first().last()
        assertEquals(FridgeEventType.MOVED, event.type)
        assertEquals(StorageLocation.FREEZER, event.storage)
        assertEquals(now, event.occurredAt)
        assertEquals(2, repo.getAllEvents().first().size)
    }

    @Test
    fun editDoesNotMoveTheItem() = runTest {
        val id = repo.insertFridgeItem(cabbage)
        repo.moveTo(id, StorageLocation.PANTRY)
        val stored = repo.getFridgeItemById(id)!!

        repo.updateFridgeItem(stored.copy(quantity = 3, storage = StorageLocation.FRIDGE, storedOn = null))

        val edited = repo.getFridgeItemById(id)!!
        assertEquals(3, edited.quantity)
        assertEquals(StorageLocation.PANTRY, edited.storage)
        assertEquals(stored.storedOn, edited.storedOn)
        assertEquals(stored.estimatedExpiry, edited.estimatedExpiry)
    }

    @Test
    fun clearingEndedItemsKeepsRecentOnesAndAllHistory() = runTest {
        val old = repo.insertFridgeItem(cabbage)
        val recent = repo.insertFridgeItem(cabbage)
        val active = repo.insertFridgeItem(cabbage)
        now = 2_000
        repo.setStatus(old, FridgeItemStatus.CONSUMED)
        now = 5_000
        repo.setStatus(recent, FridgeItemStatus.DISCARDED)

        repo.clearEndedItems(before = 3_000)

        assertNull(repo.getFridgeItemById(old))
        assertEquals(FridgeItemStatus.DISCARDED, repo.getFridgeItemById(recent)!!.status)
        assertEquals(FridgeItemStatus.ACTIVE, repo.getFridgeItemById(active)!!.status)
        assertEquals(5, repo.getAllEvents().first().size)
    }

    /** Implements only the abstract queries; the @Transaction helpers run as written. */
    private class InMemoryFridgeDao : FridgeDao() {
        private val items = MutableStateFlow<Map<Long, FridgeItemEntity>>(emptyMap())
        private val events = MutableStateFlow<List<FridgeEventEntity>>(emptyList())
        private var nextId = 1L

        override fun getActiveFridgeItems(): Flow<List<FridgeItemEntity>> =
            items.map { m -> m.values.filter { it.status == FridgeItemStatus.ACTIVE.name } }
        override fun getAllFridgeItems(): Flow<List<FridgeItemEntity>> = items.map { it.values.toList() }
        override suspend fun getFridgeItemById(id: Long) = items.value[id]
        override suspend fun getItemsFromReceipts() =
            items.value.values.filter { it.expenseId != null }.sortedWith(compareBy({ it.expenseId }, { it.id }))
        override suspend fun updateName(id: Long, name: String) {
            val item = items.value[id] ?: return
            items.value = items.value + (id to item.copy(name = name))
        }
        override suspend fun insertFridgeItem(item: FridgeItemEntity): Long {
            val id = if (item.id == 0L) nextId++ else item.id
            items.value = items.value + (id to item.copy(id = id))
            return id
        }
        override suspend fun updateFridgeItem(item: FridgeItemEntity) {
            items.value = items.value + (item.id to item)
        }
        override suspend fun updateStatus(id: Long, status: String, endedAt: Long?) {
            val item = items.value[id] ?: return
            items.value = items.value + (id to item.copy(status = status, endedAt = endedAt))
        }
        override suspend fun deleteFridgeItem(item: FridgeItemEntity) = deleteFridgeItemById(item.id)
        override suspend fun deleteFridgeItemById(id: Long) {
            items.value = items.value - id
        }
        override suspend fun clearEndedItems(before: Long) {
            items.value = items.value.filterValues {
                it.status == FridgeItemStatus.ACTIVE.name || (it.endedAt != null && it.endedAt >= before)
            }
        }
        override suspend fun insertEvent(event: FridgeEventEntity): Long {
            events.value = events.value + event.copy(id = events.value.size + 1L)
            return events.value.size.toLong()
        }
        override fun getAllEvents(): Flow<List<FridgeEventEntity>> = events
    }
}
