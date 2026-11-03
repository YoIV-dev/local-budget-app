package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.StorageLocation
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/**
 * Typical days an opened/bought item keeps, by food and storage. These are the starting
 * values for ConsumptionEstimator, which replaces them with the household's own history
 * as events accumulate.
 */
object ShelfLife {

    private val CATEGORY_DAYS: Map<StorageLocation, Map<FoodCategory, Int>> = mapOf(
        StorageLocation.FRIDGE to mapOf(
            FoodCategory.VEGETABLE to 5,
            FoodCategory.MEAT to 3,
            FoodCategory.FISH to 2,
            FoodCategory.DAIRY to 7,
            FoodCategory.OTHER to 5
        ),
        StorageLocation.FREEZER to FoodCategory.entries.associateWith { 30 },
        StorageLocation.PANTRY to mapOf(
            FoodCategory.VEGETABLE to 7,
            FoodCategory.MEAT to 1,
            FoodCategory.FISH to 1,
            FoodCategory.DAIRY to 1,
            // Rice, dry goods and seasonings go slowly
            FoodCategory.OTHER to 60
        )
    )

    /** Refrigerated foods that keep notably shorter or longer than their category. */
    private val FRIDGE_OVERRIDES: Map<String, Int> = mapOf(
        "もやし" to 2,
        "刺身" to 1,
        "ひき肉" to 2,
        "挽肉" to 2,
        "レタス" to 4,
        "ほうれん草" to 3,
        "しめじ" to 5,
        "キャベツ" to 14,
        "白菜" to 10,
        "大根" to 10,
        "にんじん" to 14,
        "玉ねぎ" to 30,
        "じゃがいも" to 30,
        "かぼちゃ" to 14,
        "ハム" to 7,
        "ベーコン" to 7,
        "牛乳" to 7,
        "ヨーグルト" to 10,
        "チーズ" to 21,
        "バター" to 60,
        "卵" to 14,
        "たまご" to 14,
        "玉子" to 14,
        "豆腐" to 5,
        "納豆" to 7
    ).mapKeys { JapaneseTextNormalizer.matchKey(it.key) }

    /** Vegetables that keep far longer at room temperature than the category's week. */
    private val PANTRY_OVERRIDES: Map<String, Int> = mapOf(
        "玉ねぎ" to 30,
        "じゃがいも" to 30,
        "さつまいも" to 30,
        "かぼちゃ" to 30
    ).mapKeys { JapaneseTextNormalizer.matchKey(it.key) }

    /** [foodKey] is FoodDictionary.matchKeyword's result, or blank for unknown foods. */
    fun days(foodKey: String, category: FoodCategory, storage: StorageLocation): Int {
        val override = when (storage) {
            StorageLocation.FRIDGE -> FRIDGE_OVERRIDES[foodKey]
            StorageLocation.PANTRY -> PANTRY_OVERRIDES[foodKey]
            StorageLocation.FREEZER -> null
        }
        return override ?: CATEGORY_DAYS.getValue(storage).getValue(category)
    }

    /** Override keywords, for checking that each is a FoodDictionary keyword. */
    internal val overrideKeys: Set<String> get() = FRIDGE_OVERRIDES.keys + PANTRY_OVERRIDES.keys
}
