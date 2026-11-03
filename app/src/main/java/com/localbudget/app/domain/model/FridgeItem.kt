package com.localbudget.app.domain.model

import java.time.LocalDate

/** Where an item is kept; shelf life depends on it. */
enum class StorageLocation(val displayName: String, val placeName: String) {
    FRIDGE("冷蔵", "冷蔵庫"),
    FREEZER("冷凍", "冷凍庫"),
    PANTRY("常温", "常温");

    companion object {
        fun fromString(value: String?): StorageLocation =
            entries.firstOrNull { it.name == value } ?: FRIDGE
    }
}

/** Lifecycle of an item: in stock, or ended by being used up or thrown away. */
enum class FridgeItemStatus {
    ACTIVE,
    CONSUMED,
    DISCARDED;

    companion object {
        fun fromString(value: String?): FridgeItemStatus =
            entries.firstOrNull { it.name == value } ?: ACTIVE
    }
}

/**
 * [normalizedName] is the dictionary food key (e.g. "きゃべつ" for "ｷｬﾍﾞﾂ 1玉") that groups
 * repurchases of the same food; [estimatedExpiry] comes from the shelf-life table.
 * Both are filled in by FridgeItemEnricher when left empty.
 */
data class FridgeItem(
    val id: Long = 0,
    val name: String,
    val category: FoodCategory,
    val quantity: Int = 1,
    val unit: String = "",
    val purchaseDate: LocalDate,
    val status: FridgeItemStatus = FridgeItemStatus.ACTIVE,
    val expenseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val normalizedName: String = "",
    /** Amount paid for the line (after discounts), when known from a receipt. */
    val price: Long? = null,
    val storage: StorageLocation = StorageLocation.FRIDGE,
    val estimatedExpiry: LocalDate? = null,
    /** When the item was marked consumed or discarded (epoch millis). */
    val endedAt: Long? = null,
    /** Day the item was moved into its current [storage]; null if it has been there since purchase. */
    val storedOn: LocalDate? = null
) {
    val isActive: Boolean get() = status == FridgeItemStatus.ACTIVE

    /** Start of the time in the current storage, which shelf life and age count from. */
    val storedSince: LocalDate get() = storedOn ?: purchaseDate
}
