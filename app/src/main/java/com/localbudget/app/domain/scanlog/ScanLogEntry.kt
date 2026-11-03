package com.localbudget.app.domain.scanlog

import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.model.ParsedReceipt
import com.localbudget.app.domain.ocr.DetectionResult
import java.time.LocalDate

/** How the receipt image was obtained. */
enum class ScanSource { AUTO_CAPTURE, MANUAL_CAPTURE, GALLERY }

/** What the user saved on the confirmation screen. */
data class ConfirmedReceipt(
    val storeName: String,
    val date: LocalDate,
    val amount: Long,
    val categoryId: Long,
    /** Names of the extracted food items left checked for the fridge. */
    val keptFoodNames: List<String>
)

/**
 * One scan recorded in verification mode: what the app read, and what the user kept or fixed.
 * [confirmed] is null when the user closed the confirmation screen without saving.
 */
data class ScanLogEntry(
    val capturedAtMillis: Long,
    val source: ScanSource,
    /** Live-detector scores of the frame that triggered the capture; null for gallery images. */
    val detection: DetectionResult?,
    val rotationDegrees: Int,
    val parsed: ParsedReceipt,
    val predictedFood: List<ReceiptFoodExtractor.FoodLine>,
    val confirmed: ConfirmedReceipt?
) {
    /** Which fields the user changed before saving; empty when discarded. */
    val corrections: Set<Field>
        get() {
            val c = confirmed ?: return emptySet()
            return buildSet {
                if (c.storeName != parsed.storeName) add(Field.STORE)
                if (c.date != parsed.date) add(Field.DATE)
                if (c.amount != parsed.amount) add(Field.AMOUNT)
                if (c.categoryId != parsed.suggestedCategoryId) add(Field.CATEGORY)
                if (c.keptFoodNames.size != predictedFood.size) add(Field.FOOD)
            }
        }

    enum class Field { STORE, DATE, AMOUNT, CATEGORY, FOOD }
}
