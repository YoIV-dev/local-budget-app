package com.localbudget.app.domain

import com.localbudget.app.domain.food.FridgeShareTextBuilder
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FridgeShareTextBuilderTest {

    private val today = LocalDate.of(2026, 9, 25)

    @Test
    fun testBuildShareTextGroupedByCategory() {
        val items = listOf(
            FridgeItem(
                id = 1,
                name = "キャベツ 1玉",
                category = FoodCategory.VEGETABLE,
                quantity = 1,
                unit = "個",
                purchaseDate = LocalDate.of(2026, 9, 20)
            ),
            FridgeItem(
                id = 2,
                name = "国産牛こま切れ",
                category = FoodCategory.MEAT,
                quantity = 1,
                unit = "",
                purchaseDate = LocalDate.of(2026, 9, 21)
            ),
            FridgeItem(
                id = 3,
                name = "成分無調整牛乳",
                category = FoodCategory.DAIRY,
                quantity = 2,
                unit = "本",
                purchaseDate = LocalDate.of(2026, 9, 22)
            ),
            FridgeItem(
                id = 4,
                name = "使い切った卵",
                category = FoodCategory.OTHER,
                purchaseDate = LocalDate.of(2026, 9, 10),
                status = FridgeItemStatus.CONSUMED
            )
        )

        val text = FridgeShareTextBuilder.build(items, today)

        // Header carries the share date
        assertTrue(text.startsWith("【冷蔵庫の在庫一覧 (2026/09/25)】"))

        // Category sections with counts, in standard order
        assertTrue(text.contains("■ 野菜 (1件)\n・キャベツ 1玉 1個 (9/20購入)"))
        assertTrue(text.contains("■ 肉 (1件)\n・国産牛こま切れ (9/21購入)"))
        assertTrue(text.contains("■ 乳製品 (1件)\n・成分無調整牛乳 x2本 (9/22購入)"))
        assertTrue(text.indexOf("■ 野菜") < text.indexOf("■ 肉"))
        assertTrue(text.indexOf("■ 肉") < text.indexOf("■ 乳製品"))

        // Consumed items are excluded from the list and the total
        assertFalse(text.contains("使い切った卵"))
        assertTrue(text.endsWith("計 3品"))
    }

    @Test
    fun testBuildEmptyInventory() {
        val text = FridgeShareTextBuilder.build(emptyList(), today)
        assertEquals("【冷蔵庫の在庫一覧 (2026/09/25)】\n\n現在、登録されている食材はありません。", text)
    }

    @Test
    fun combinedInventoryShareIncludesStorageLocations() {
        val items = listOf(
            FridgeItem(name = "牛乳", category = FoodCategory.DAIRY, purchaseDate = today, storage = StorageLocation.FRIDGE),
            FridgeItem(name = "冷凍野菜", category = FoodCategory.VEGETABLE, purchaseDate = today, storage = StorageLocation.FREEZER),
            FridgeItem(name = "缶詰", category = FoodCategory.OTHER, purchaseDate = today, storage = StorageLocation.PANTRY)
        )
        val text = FridgeShareTextBuilder.build(items, today, placeName = "食材", includeStorage = true)
        items.forEach { assertTrue(text.contains("${it.name} (9/25購入・${it.storage.displayName})")) }
        assertTrue(text.endsWith("計 3品"))
    }
}
