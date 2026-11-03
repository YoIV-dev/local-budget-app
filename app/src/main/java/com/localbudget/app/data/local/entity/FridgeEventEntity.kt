package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeEvent
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.StorageLocation

/** No foreign key to fridge_items: the history must survive deleting or clearing items. */
@Entity(
    tableName = "fridge_events",
    indices = [
        Index(value = ["item_id"]),
        Index(value = ["food_key"])
    ]
)
data class FridgeEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "food_key")
    val foodKey: String,
    val category: String,
    val storage: String,
    val type: String,
    val source: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long
) {
    fun toDomain(): FridgeEvent = FridgeEvent(
        id = id,
        itemId = itemId,
        foodKey = foodKey,
        category = FoodCategory.fromString(category),
        storage = StorageLocation.fromString(storage),
        type = FridgeEventType.valueOf(type),
        source = FridgeEventSource.valueOf(source),
        occurredAt = occurredAt
    )

    companion object {
        fun fromDomain(model: FridgeEvent): FridgeEventEntity = FridgeEventEntity(
            id = model.id,
            itemId = model.itemId,
            foodKey = model.foodKey,
            category = model.category.name,
            storage = model.storage.name,
            type = model.type.name,
            source = model.source.name,
            occurredAt = model.occurredAt
        )
    }
}
