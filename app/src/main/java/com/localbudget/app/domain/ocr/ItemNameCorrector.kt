package com.localbudget.app.domain.ocr

import com.localbudget.app.domain.food.FoodDictionary
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import com.localbudget.app.domain.text.KanaFuzzyMatcher

/**
 * Puts known products and katakana words back into misread item names
 * ("メイシプ励リアヨークルト" → "明治ブルガリアヨーグルト", "ラーメンショウュ" → "ラーメンショウユ").
 *
 * A whole product in [PRODUCTS] is looked for first, across the name's spaces ("メイシ フけリアヨーグト"),
 * and written by its usual name. Otherwise each whitespace-separated part of the name is compared
 * with [WORDS] (longest first) by [KanaFuzzyMatcher]; a close enough span is replaced by the word.
 * A part that changes is written in NFKC (full-width kana); a part nothing matched is kept as printed.
 */
object ItemNameCorrector {

    /**
     * Products as receipts print them (katakana, maker first) → the name people know them by.
     * Long and specific, so they may be found through heavier misreads than a single word.
     */
    private val PRODUCTS = mapOf(
        "メイジブルガリアヨーグルト" to "明治ブルガリアヨーグルト",
        "メイジオイシイギュウニュウ" to "明治おいしい牛乳",
        "メイジタケノコノサト" to "明治たけのこの里",
        "メイジキノコノヤマ" to "明治きのこの山"
    ).entries.sortedByDescending { it.key.length }

    // Misreads a whole product may carry per character: about one clean miss per three characters
    private const val PRODUCT_COST_PER_CHAR = 0.3

    // Makers and everyday words on supermarket / convenience-store receipts, as printed in katakana
    private val PRODUCT_WORDS = listOf(
        "タケノコノサト", "キノコノヤマ", "ポテトチップス", "カップヌードル", "コシヒカリ", "コシイブキ",
        "アキタコマチ", "ヒトメボレ", "ニイガタ", "ギュウニュウ", "トウニュウ",
        // Makers
        "メイジ", "モリナガ", "グリコ", "カルビー", "ヤマザキ", "ニッスイ", "マルハニチロ", "ニチレイ",
        "アジノモト", "キッコーマン", "ミツカン", "ハウス", "エスビー", "キユーピー", "カゴメ", "デルモンテ",
        "サントリー", "アサヒ", "キリン", "サッポロ", "イトウエン", "コカコーラ", "ネスレ", "ロッテ", "ブルボン",
        "プリマハム", "イトウハム", "ニッポンハム", "マルコメ", "ヤクルト", "ダノン", "シマダヤ", "ニッシン",
        "ペヤング", "ヤマサ", "ヒガシマル", "ミツウロコ", "タカナシ", "ユキジルシ",
        // Everyday words
        "ブルガリア", "ヨーグルト", "ラーメン", "ショウユ", "トンコツ", "ミソ", "ココア", "コーヒー", "チョコレート",
        "チョコ", "ビスケット", "クッキー", "ポテト", "チップス", "ソーセージ", "ウインナー", "ベーコン", "ハム",
        "ドーナツ", "サンドイッチ", "オニギリ", "ベントウ", "サラダ", "ドレッシング", "マヨネーズ", "ケチャップ",
        "ソース", "レモン", "オレンジ", "グレープ", "アップル", "ビール", "サワー", "チューハイ", "ハイボール",
        "ミネラルウォーター", "テイシボウ", "シボリタテ", "ヒキニク", "コマギレ", "ムネニク", "モモニク",
        "キリミ", "フィレ", "スライス", "パック", "テマキ"
    )

    private val WORDS: List<String> = (PRODUCT_WORDS + FoodDictionary.allKeywords)
        .map { JapaneseTextNormalizer.nfkc(it) }
        .filter { word -> word.length >= 3 && word.all { it in 'ァ'..'ヺ' || it == 'ー' } }
        .distinct()
        .sortedByDescending { it.length }

    fun correct(name: String): String {
        if (name.isBlank()) return name
        val compact = JapaneseTextNormalizer.nfkc(name).replace(Regex("""\s+"""), "")
        for ((printed, known) in PRODUCTS) {
            val match = KanaFuzzyMatcher.find(compact, printed, printed.length * PRODUCT_COST_PER_CHAR) ?: continue
            if (!readsAsKana(compact.substring(match.start, match.end))) continue
            // What is around the product ("400g", "2個") keeps its own corrections
            return listOf(compact.substring(0, match.start), known, compact.substring(match.end))
                .filter { it.isNotBlank() }
                .joinToString(" ") { if (it == known) it else correctWords(it) }
        }
        return correctWords(name)
    }

    private fun correctWords(name: String): String = Regex("""\S+""").replace(name) { m -> correctPart(m.value) }

    /** Whether at least half of [span] is kana, i.e. it can be a misread of a katakana word. */
    private fun readsAsKana(span: String): Boolean =
        span.count { it in 'ァ'..'ヺ' || it in 'ぁ'..'ゖ' || it == 'ー' } * 2 >= span.length

    private fun correctPart(part: String): String {
        var text = JapaneseTextNormalizer.nfkc(part)
        // Spans already set to a dictionary word, so a shorter word does not rewrite inside them
        val settled = mutableListOf<IntRange>()
        var changed = false
        for (word in WORDS) {
            if (text.length < word.length - 2) continue
            val match = KanaFuzzyMatcher.find(text, word, KanaFuzzyMatcher.allowedCost(word.length)) ?: continue
            if (settled.any { it.first < match.end && match.start <= it.last }) continue
            val span = text.substring(match.start, match.end)
            // Only a span that already reads mostly as kana is a misread of the word
            if (!readsAsKana(span)) continue
            // A short word only by swapped characters: "ライス" is not "スライス" missing its ス
            if (word.length <= 5 && span.length != word.length) continue
            if (match.cost > 0) {
                text = text.substring(0, match.start) + word + text.substring(match.end)
                changed = true
            }
            val shift = word.length - span.length
            settled.replaceAll { if (it.first >= match.end) (it.first + shift)..(it.last + shift) else it }
            settled.add(match.start until match.start + word.length)
        }
        return if (changed) text else part
    }
}
