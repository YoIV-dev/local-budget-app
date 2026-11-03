package com.localbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.localbudget.app.data.local.entity.FridgeEventEntity
import com.localbudget.app.data.local.entity.FridgeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FridgeDao {

    @Query("SELECT * FROM fridge_items WHERE status = 'ACTIVE' ORDER BY purchase_epoch_day DESC, id DESC")
    abstract fun getActiveFridgeItems(): Flow<List<FridgeItemEntity>>

    @Query("SELECT * FROM fridge_items ORDER BY status = 'ACTIVE' DESC, purchase_epoch_day DESC, id DESC")
    abstract fun getAllFridgeItems(): Flow<List<FridgeItemEntity>>

    @Query("SELECT * FROM fridge_items WHERE expense_id IS NOT NULL ORDER BY expense_id, id")
    abstract suspend fun getItemsFromReceipts(): List<FridgeItemEntity>

    @Query("UPDATE fridge_items SET name = :name WHERE id = :id")
    abstract suspend fun updateName(id: Long, name: String)

    @Query("SELECT * FROM fridge_items WHERE id = :id LIMIT 1")
    abstract suspend fun getFridgeItemById(id: Long): FridgeItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFridgeItem(item: FridgeItemEntity): Long

    @Update
    abstract suspend fun updateFridgeItem(item: FridgeItemEntity)

    @Query("UPDATE fridge_items SET status = :status, ended_at = :endedAt WHERE id = :id")
    abstract suspend fun updateStatus(id: Long, status: String, endedAt: Long?)

    @Delete
    abstract suspend fun deleteFridgeItem(item: FridgeItemEntity)

    @Query("DELETE FROM fridge_items WHERE id = :id")
    abstract suspend fun deleteFridgeItemById(id: Long)

    /** Ended items from before v4 have no end time; they go too. */
    @Query("DELETE FROM fridge_items WHERE status != 'ACTIVE' AND (ended_at IS NULL OR ended_at < :before)")
    abstract suspend fun clearEndedItems(before: Long)

    @Insert
    abstract suspend fun insertEvent(event: FridgeEventEntity): Long

    @Query("SELECT * FROM fridge_events ORDER BY occurred_at ASC, id ASC")
    abstract fun getAllEvents(): Flow<List<FridgeEventEntity>>

    /** Inserts the item and its ADDED event together; [event] gets the new item id. */
    @Transaction
    open suspend fun insertWithEvent(item: FridgeItemEntity, event: (Long) -> FridgeEventEntity): Long {
        val id = insertFridgeItem(item)
        insertEvent(event(id))
        return id
    }

    @Transaction
    open suspend fun insertAllWithEvents(items: List<FridgeItemEntity>, event: (index: Int, id: Long) -> FridgeEventEntity): List<Long> =
        items.mapIndexed { index, item -> insertWithEvent(item) { id -> event(index, id) } }

    @Transaction
    open suspend fun updateWithEvent(item: FridgeItemEntity, event: FridgeEventEntity) {
        updateFridgeItem(item)
        insertEvent(event)
    }

    @Transaction
    open suspend fun updateStatusWithEvent(id: Long, status: String, endedAt: Long?, event: FridgeEventEntity) {
        updateStatus(id, status, endedAt)
        insertEvent(event)
    }
}
