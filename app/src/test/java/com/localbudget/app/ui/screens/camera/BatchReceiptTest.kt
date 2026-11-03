package com.localbudget.app.ui.screens.camera

import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BatchReceiptTest {
    private val date = LocalDate.of(2026, 10, 28)
    private val expense = Expense(date = date, amount = 500, storeName = "店", categoryId = 1)

    @Test fun `selected corrected and added items become fridge stock`() {
        val original = BatchReceipt(expense, listOf(ReceiptLineItem("牛乳", 200), ReceiptLineItem("袋", 5)))
        assertTrue(original.foodSettings[0].selected)
        assertFalse(original.foodSettings[1].selected)
        val changed = original.withItems(listOf(ReceiptLineItem("牛乳", 210), ReceiptLineItem("袋", 5), ReceiptLineItem("手作り食品", 100)))
        val settings = changed.foodSettings.toMutableList()
        settings[2] = BatchFoodSetting(FoodCategory.OTHER, StorageLocation.FREEZER, true)
        val foods = changed.copy(foodSettings = settings).fridgeItems()
        assertEquals(listOf("牛乳", "手作り食品"), foods.map { it.name })
        assertEquals(210L, foods[0].price)
        assertEquals(StorageLocation.FREEZER, foods[1].storage)
        assertEquals(date, foods[1].purchaseDate)
    }

    @Test fun `removing duplicate lines preserves the other line settings`() {
        val item = ReceiptLineItem("牛乳", 200)
        val receipt = BatchReceipt(expense, listOf(item, item), foodSettings = listOf(
            BatchFoodSetting(FoodCategory.DAIRY, StorageLocation.FRIDGE, false),
            BatchFoodSetting(FoodCategory.OTHER, StorageLocation.FREEZER, true)
        ))
        val remaining = receipt.removeItem(0)
        assertEquals(1, remaining.fridgeItems().size)
        assertEquals(StorageLocation.FREEZER, remaining.fridgeItems()[0].storage)
    }
}
