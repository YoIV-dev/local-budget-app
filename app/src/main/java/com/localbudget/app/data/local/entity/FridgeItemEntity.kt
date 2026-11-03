package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import java.time.LocalDate

@Entity(
    tableName = "fridge_items",
    indices = [
        Index(value = ["purchase_epoch_day"]),
        Index(value = ["category"]),
        Index(value = ["status"]),
        Index(value = ["normalized_name"])
    ]
)
data class FridgeItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: String, // VEGETABLE, MEAT, FISH, DAIRY, OTHER
    val quantity: Int = 1,
    val unit: String = "",
    @ColumnInfo(name = "purchase_epoch_day")
    val purchaseEpochDay: Long,
    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: String = FridgeItemStatus.ACTIVE.name,
    @ColumnInfo(name = "expense_id")
    val expenseId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "normalized_name", defaultValue = "")
    val normalizedName: String = "",
    @ColumnInfo(name = "price")
    val price: Long? = null,
    @ColumnInfo(name = "storage", defaultValue = "FRIDGE")
    val storage: String = StorageLocation.FRIDGE.name,
    @ColumnInfo(name = "expiry_epoch_day")
    val expiryEpochDay: Long? = null,
    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,
    @ColumnInfo(name = "stored_epoch_day")
    val storedEpochDay: Long? = null
) {
    fun toDomain(): FridgeItem = FridgeItem(
        id = id,
        name = name,
        category = FoodCategory.fromString(category),
        quantity = quantity,
        unit = unit,
        purchaseDate = LocalDate.ofEpochDay(purchaseEpochDay),
        status = FridgeItemStatus.fromString(status),
        expenseId = expenseId,
        createdAt = createdAt,
        normalizedName = normalizedName,
        price = price,
        storage = StorageLocation.fromString(storage),
        estimatedExpiry = expiryEpochDay?.let { LocalDate.ofEpochDay(it) },
        endedAt = endedAt,
        storedOn = storedEpochDay?.let { LocalDate.ofEpochDay(it) }
    )

    companion object {
        fun fromDomain(model: FridgeItem): FridgeItemEntity = FridgeItemEntity(
            id = model.id,
            name = model.name,
            category = model.category.name,
            quantity = model.quantity,
            unit = model.unit,
            purchaseEpochDay = model.purchaseDate.toEpochDay(),
            status = model.status.name,
            expenseId = model.expenseId,
            createdAt = model.createdAt,
            normalizedName = model.normalizedName,
            price = model.price,
            storage = model.storage.name,
            expiryEpochDay = model.estimatedExpiry?.toEpochDay(),
            endedAt = model.endedAt,
            storedEpochDay = model.storedOn?.toEpochDay()
        )
    }
}
