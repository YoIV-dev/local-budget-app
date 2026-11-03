package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.StorageLocation
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/** Fills the derived fields of a fridge item that the caller left empty. */
object FridgeItemEnricher {

    /** Dictionary food key, or the whole name's match key for foods not in the dictionary. */
    fun foodKey(name: String): String =
        FoodDictionary.matchKeyword(name) ?: JapaneseTextNormalizer.matchKey(name)

    /** 冷凍 in the name means frozen; rice, dry goods and seasonings stay out; everything else starts in the fridge. */
    fun guessStorage(name: String): StorageLocation = when {
        JapaneseTextNormalizer.matchKey(name).contains(FROZEN) -> StorageLocation.FREEZER
        FoodDictionary.matchKeyword(name) in PANTRY_KEYS -> StorageLocation.PANTRY
        else -> StorageLocation.FRIDGE
    }

    fun enrich(item: FridgeItem): FridgeItem {
        val key = item.normalizedName.ifBlank { foodKey(item.name) }
        val expiry = item.estimatedExpiry
            ?: item.storedSince.plusDays(ShelfLife.days(key, item.category, item.storage).toLong())
        return item.copy(normalizedName = key, estimatedExpiry = expiry)
    }

    private val FROZEN = JapaneseTextNormalizer.matchKey("冷凍")

    /** Dictionary keywords for foods kept at room temperature (opened seasonings that need chilling are the user's call). */
    private val PANTRY_KEYWORDS = listOf(
        "お米", "パスタ", "スパゲッティ", "小麦粉", "パン粉", "片栗粉", "砂糖", "コショウ",
        "醤油", "しょうゆ", "みりん", "料理酒", "オリーブオイル", "ごま油", "サラダ油",
        "じゃがいも", "ジャガイモ", "馬鈴薯", "さつまいも", "サツマイモ", "玉ねぎ", "たまねぎ", "玉葱"
    )
    private val PANTRY_KEYS = PANTRY_KEYWORDS.map(JapaneseTextNormalizer::matchKey).toSet()

    /** Pantry keywords that are not in FoodDictionary, which [guessStorage] could never match (should be empty). */
    internal fun unknownPantryKeywords(): List<String> = PANTRY_KEYWORDS.filterNot(FoodDictionary::hasKeyword)
}
