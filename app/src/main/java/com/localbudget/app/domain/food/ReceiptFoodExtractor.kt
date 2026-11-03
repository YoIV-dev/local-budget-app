package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.ocr.ReceiptItemParser
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.time.LocalDate

object ReceiptFoodExtractor {

    /** A receipt line item that the food dictionary recognized. */
    data class FoodLine(val item: ReceiptLineItem, val category: FoodCategory)

    /**
     * Food line items on the receipt, in printed order. Lines naming the same food
     * (ｷｬﾍﾞﾂ / キャベツ, or one item scanned twice) are merged and their quantities and prices summed.
     * Names are kept verbatim from the receipt (Requirement 4.1).
     */
    fun extractLines(rawText: String): List<FoodLine> = extractLines(ReceiptItemParser.parse(rawText))

    /** Food lines among receipt [items] already read (and possibly corrected by the user). */
    fun extractLines(items: List<ReceiptLineItem>): List<FoodLine> {
        val merged = LinkedHashMap<String, FoodLine>()
        for (item in items) {
            if (item.name.length < 2) continue
            val category = FoodDictionary.matchCategory(item.name) ?: continue
            val key = JapaneseTextNormalizer.matchKey(item.name)
            val existing = merged[key]
            merged[key] = if (existing == null) {
                FoodLine(item, category)
            } else {
                existing.copy(item = existing.item.copy(
                    quantity = existing.item.quantity + item.quantity,
                    price = sumOrNull(existing.item.price, item.price),
                    discount = existing.item.discount + item.discount,
                    unitPrice = existing.item.unitPrice.takeIf { it == item.unitPrice }
                ))
            }
        }
        return merged.values.toList()
    }

    /** Extracts food items from receipt OCR text, for the fridge. */
    fun extract(rawText: String, purchaseDate: LocalDate): List<FridgeItem> =
        extract(ReceiptItemParser.parse(rawText), purchaseDate)

    /** Food items among receipt [items], for the fridge. */
    fun extract(items: List<ReceiptLineItem>, purchaseDate: LocalDate): List<FridgeItem> =
        extractLines(items).map { line ->
            FridgeItem(
                name = line.item.name,
                category = line.category,
                quantity = line.item.quantity,
                unit = FoodUnit.guess(line.item.name, line.category),
                purchaseDate = purchaseDate,
                price = line.item.netPrice,
                storage = FridgeItemEnricher.guessStorage(line.item.name)
            )
        }

    private fun sumOrNull(a: Long?, b: Long?): Long? = if (a == null || b == null) null else a + b
}
