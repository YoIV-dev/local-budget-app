package com.localbudget.app.ui.screens.camera

import com.localbudget.app.domain.food.FoodDictionary
import com.localbudget.app.domain.food.FoodUnit
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.StorageLocation
import java.util.UUID

data class BatchFoodSetting(
    val category: FoodCategory,
    val storage: StorageLocation,
    val selected: Boolean
) {
    companion object {
        fun fromItem(item: ReceiptLineItem, selected: Boolean = FoodDictionary.matchCategory(item.name) != null) =
            BatchFoodSetting(FoodDictionary.guessCategory(item.name), FridgeItemEnricher.guessStorage(item.name), selected)
    }
}

data class BatchReceipt(
    val expense: Expense,
    val items: List<ReceiptLineItem>,
    val amountWarning: String? = null,
    val foodSettings: List<BatchFoodSetting> = items.map { BatchFoodSetting.fromItem(it) },
    val key: String = UUID.randomUUID().toString()
) {
    fun withItems(updated: List<ReceiptLineItem>): BatchReceipt {
        require(updated.size == items.size || updated.size == items.size + 1)
        val settings = if (updated.size > items.size) foodSettings + BatchFoodSetting.fromItem(updated.last(), true) else foodSettings
        return copy(items = updated, foodSettings = settings)
    }

    fun removeItem(index: Int): BatchReceipt = copy(
        items = items.filterIndexed { i, _ -> i != index },
        foodSettings = foodSettings.filterIndexed { i, _ -> i != index }
    )

    fun fridgeItems(): List<FridgeItem> = items.zip(foodSettings).mapNotNull { (item, setting) ->
        if (!setting.selected) null else FridgeItem(
            name = item.name, category = setting.category, quantity = item.quantity,
            unit = FoodUnit.guess(item.name, setting.category), purchaseDate = expense.date,
            price = item.netPrice, storage = setting.storage
        )
    }
}
