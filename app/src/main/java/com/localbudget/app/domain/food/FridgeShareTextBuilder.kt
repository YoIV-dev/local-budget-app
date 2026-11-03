package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FridgeShareTextBuilder {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d", Locale.JAPANESE)
    private val HEADER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPANESE)

    /**
     * Builds a formatted text list of fridge items grouped by category for OS share sheet.
     * [placeName] heads the list, e.g. 冷凍庫 when sharing the freezer.
     */
    fun build(items: List<FridgeItem>, today: LocalDate = LocalDate.now(), placeName: String = "冷蔵庫", includeStorage: Boolean = false): String {
        val activeItems = items.filter { it.isActive }
        if (activeItems.isEmpty()) {
            return "【${placeName}の在庫一覧 (${today.format(HEADER_DATE_FORMATTER)})】\n\n現在、登録されている食材はありません。"
        }

        val sb = StringBuilder()
        sb.append("【${placeName}の在庫一覧 (${today.format(HEADER_DATE_FORMATTER)})】\n\n")

        val grouped = activeItems.groupBy { it.category }

        // Ordered by category standard sequence: 野菜, 肉, 魚, 乳製品, その他
        val order = listOf(
            FoodCategory.VEGETABLE,
            FoodCategory.MEAT,
            FoodCategory.FISH,
            FoodCategory.DAIRY,
            FoodCategory.OTHER
        )

        for (category in order) {
            val categoryItems = grouped[category]
            if (!categoryItems.isNullOrEmpty()) {
                sb.append("■ ${category.displayName} (${categoryItems.size}件)\n")
                // Sort by purchase date ascending (older first so user knows to consume them first)
                val sorted = categoryItems.sortedBy { it.purchaseDate }
                for (item in sorted) {
                    val qtyStr = if (item.quantity > 1) " x${item.quantity}${item.unit}" else if (item.unit.isNotBlank()) " 1${item.unit}" else ""
                    val dateStr = item.purchaseDate.format(DATE_FORMATTER)
                    val storageStr = if (includeStorage) "・${item.storage.displayName}" else ""
                    sb.append("・${item.name}$qtyStr (${dateStr}購入$storageStr)\n")
                }
                sb.append("\n")
            }
        }

        sb.append("計 ${activeItems.size}品")
        return sb.toString().trimEnd()
    }
}
