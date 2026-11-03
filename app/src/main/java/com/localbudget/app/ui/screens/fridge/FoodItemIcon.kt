package com.localbudget.app.ui.screens.fridge

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/** Recognizable food pictograms when the name is specific, with category icons as a fallback. */
internal object FoodItemIcon {
    private val icons = listOf(
        "🥚" to listOf("たまご", "卵", "玉子"), "🥛" to listOf("牛乳", "ミルク"),
        "🧀" to listOf("チーズ"), "🍞" to listOf("食パン", "パン"),
        "🍌" to listOf("バナナ"), "🍎" to listOf("りんご", "林檎"),
        "🍊" to listOf("みかん", "蜜柑"), "🍓" to listOf("いちご", "苺"),
        "🍅" to listOf("トマト"), "🥕" to listOf("にんじん", "人参"),
        "🧅" to listOf("たまねぎ", "玉ねぎ", "玉葱"), "🥔" to listOf("じゃがいも", "馬鈴薯"),
        "🥦" to listOf("ブロッコリー"), "🥬" to listOf("キャベツ", "白菜", "レタス"),
        "🍆" to listOf("なす", "茄子"), "🥒" to listOf("きゅうり", "胡瓜"),
        "🍄" to listOf("きのこ", "しめじ", "えのき", "まいたけ", "舞茸", "しいたけ"),
        "🐟" to listOf("鮭", "さけ", "サーモン", "さば", "鯖", "まぐろ", "鮪"),
        "🍗" to listOf("鶏肉", "とり肉", "鶏むね", "鶏もも"), "🥩" to listOf("豚肉", "牛肉", "ひき肉"),
        "🍚" to listOf("ごはん", "ご飯", "白米"), "🧈" to listOf("バター")
    ).map { (icon, words) -> icon to words.map(JapaneseTextNormalizer::matchKey) }

    fun forName(name: String, category: FoodCategory): String {
        val key = JapaneseTextNormalizer.matchKey(name)
        return icons.firstOrNull { (_, words) -> words.any { key.contains(it) } }?.first ?: category.iconEmoji
    }
}
