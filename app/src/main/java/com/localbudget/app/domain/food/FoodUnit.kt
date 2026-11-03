package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/** The unit a food is usually counted in (牛乳 → 本, キャベツ → 玉), so not everything defaults to 個. */
object FoodUnit {

    /** Offered as one-tap choices when adding an item; free input stays available. */
    val COMMON: List<String> = listOf("個", "本", "袋", "パック", "玉", "束", "丁", "切", "尾", "枚", "g")

    /** Keys are [FoodDictionary] keywords (any spelling). */
    private val UNIT_MAP: Map<String, String> = buildMap {
        fun counted(unit: String, vararg keywords: String) = keywords.forEach { put(it, unit) }

        counted("本", "にんじん", "人参", "きゅうり", "胡瓜", "ネギ", "長ネギ", "長葱", "大根", "なす", "茄子",
            "ごぼう", "セロリ", "さつまいも", "牛乳", "豆乳", "ミルク",
            "マヨネーズ", "ケチャップ", "醤油", "しょうゆ", "みりん", "料理酒", "つゆ", "めんつゆ", "ソース",
            "ドレッシング", "オリーブオイル", "ごま油", "サラダ油")
        counted("玉", "キャベツ", "レタス", "白菜")
        counted("袋", "もやし", "ピーマン", "じゃがいも", "馬鈴薯", "えのき", "オクラ", "ソーセージ", "ウインナー",
            "ウィンナー", "ちくわ", "竹輪", "スライスチーズ", "ピザ用チーズ", "パン", "食パン", "ロールパン",
            "うどん", "そば", "蕎麦", "焼きそば", "パスタ", "スパゲッティ", "小麦粉", "パン粉", "片栗粉", "砂糖", "お米")
        counted("束", "ほうれん草", "小松菜", "ニラ", "水菜", "アスパラ")
        counted("パック", "卵", "たまご", "玉子", "納豆", "なっとう", "しめじ", "まいたけ", "舞茸", "エリンギ",
            "刺身", "さしみ", "お刺身", "しらす", "ハム", "ベーコン", "餃子", "ギョーザ", "シュウマイ", "焼売", "あさり", "しじみ")
        counted("丁", "豆腐", "とうふ")
        counted("切", "鮭", "切り身", "ぶり", "鰤", "たら", "鱈")
        counted("尾", "アジ", "鯵", "サンマ", "秋刀魚", "いわし", "鰯")
        counted("枚", "油揚げ", "厚揚げ")
    }

    private val NORMALIZED_UNITS: Map<String, String> =
        UNIT_MAP.mapKeys { JapaneseTextNormalizer.matchKey(it.key) }

    /** Unit for [name]; meat and fish not listed come in a パック, anything else in 個. */
    fun guess(name: String, category: FoodCategory = FoodDictionary.guessCategory(name)): String {
        FoodDictionary.matchKeyword(name)?.let { key -> NORMALIZED_UNITS[key]?.let { return it } }
        return when (category) {
            FoodCategory.MEAT, FoodCategory.FISH -> "パック"
            else -> "個"
        }
    }

    /** Keywords here that are not in [FoodDictionary], which [guess] could never match (should be empty). */
    internal fun unknownKeywords(): List<String> = UNIT_MAP.keys.filterNot(FoodDictionary::hasKeyword)
}
