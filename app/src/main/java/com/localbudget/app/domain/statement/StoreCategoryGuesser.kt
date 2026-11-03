package com.localbudget.app.domain.statement

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/**
 * Guesses the category of a card-statement store name: first from how the user filed that store
 * before (a branch of the same chain counts), then from a dictionary of chains and services.
 * Names are compared by [JapaneseTextNormalizer.looseKey], so statement spellings still match.
 */
class StoreCategoryGuesser(existing: List<Expense>, private val categories: List<Category>) {

    // Newest last, so the latest choice for a store wins
    private val history: List<Pair<String, Long>> = existing.sortedBy { it.date }
        .map { JapaneseTextNormalizer.looseKey(it.storeName) to it.categoryId }
        .filter { it.first.isNotEmpty() }

    private val fallback = categoryId(OTHER) ?: categories.lastOrNull()?.id ?: 9L

    fun guess(storeName: String): Long {
        val key = JapaneseTextNormalizer.looseKey(storeName)
        return fromHistory(key)?.takeIf { id -> categories.any { it.id == id } }
            ?: fromDictionary(key)?.let { categoryId(it) }
            ?: fallback
    }

    /** The past expense sharing the longest start with [key]: "セブン-イレブン小金井店" learns from "セブン-イレブン府中店". */
    private fun fromHistory(key: String): Long? {
        var best: Long? = null
        var bestLength = 0
        for ((pastKey, categoryId) in history) {
            val common = key.commonPrefixWith(pastKey).length
            val isSameStore = common >= MIN_SHARED_PREFIX || (common >= 3 && common == minOf(key.length, pastKey.length))
            if (isSameStore && common >= bestLength) {
                best = categoryId
                bestLength = common
            }
        }
        return best
    }

    /** The category of the longest dictionary word in [key], so "Amazonプライム" beats "Amazon". */
    private fun fromDictionary(key: String): String? =
        DICTIONARY.filter { (word, _) -> contains(key, word) }
            .maxByOrNull { (word, _) -> word.length }
            ?.second

    private fun contains(key: String, word: String): Boolean {
        if (word.length > 3 || !word.all { it in 'a'..'z' }) return key.contains(word)
        // Short latin words (jr, au, ana) only as words of their own, not inside "banana"
        return Regex("""(?<![a-z])$word(?![a-z])""").containsMatchIn(key)
    }

    /** By name, so renamed categories still work as long as the default name or id is there. */
    private fun categoryId(name: String): Long? =
        categories.find { it.name == name }?.id
            ?: DEFAULT_IDS[name]?.takeIf { id -> categories.any { it.id == id } }

    private companion object {
        const val MIN_SHARED_PREFIX = 5
        const val FOOD = "食費"
        const val DAILY = "日用品"
        const val TRANSPORT = "交通費"
        const val HOUSING = "住居費"
        const val UTILITIES = "水道・光熱費"
        const val PHONE = "通信費"
        const val LEISURE = "交際・娯楽費"
        const val MEDICAL = "医療・ヘルスケア"
        const val OTHER = "その他"

        val DEFAULT_IDS = mapOf(
            FOOD to 1L, DAILY to 2L, TRANSPORT to 3L, HOUSING to 4L, UTILITIES to 5L,
            PHONE to 6L, LEISURE to 7L, MEDICAL to 8L, OTHER to 9L
        )

        val WORDS = mapOf(
            FOOD to listOf(
                // Convenience stores
                "セブンイレブン", "ファミリーマート", "ローソン", "ミニストップ", "デイリーヤマザキ", "ニューデイズ",
                "newdays", "セイコーマート", "ポプラ", "セブン",
                // Supermarkets
                "イオン", "マックスバリュ", "イトーヨーカドー", "ヨークフーズ", "ヨークマート", "マルエツ", "西友",
                "ライフ", "サミット", "オーケーストア", "まいばすけっと", "成城石井", "業務スーパー", "ダイエー",
                "東急ストア", "京王ストア", "いなげや", "ベルク", "ヤオコー", "生協", "ゆめマート",
                "ゆめタウン", "マルキョウ", "トライアル", "ハローデイ", "ロピア", "ビッグエー", "紀ノ国屋",
                "明治屋", "フーズ", "フードスタイル", "ステーションフード", "食品", "精肉", "青果", "鮮魚",
                "市場",
                // Restaurants and cafes
                "松屋", "すき家", "吉野家", "なか卯", "マクドナルド", "モスバーガー", "ゼッテリア", "ロッテリア",
                "ケンタッキー", "バーガーキング", "ミスタードーナツ", "スターバックス", "ドトール", "タリーズ",
                "コメダ", "サイゼリヤ", "ガスト", "バーミヤン", "ジョナサン", "デニーズ", "ココス", "丸亀製麺",
                "はなまるうどん", "日高屋", "大戸屋", "やよい軒", "てんや", "ココイチ", "壱番屋", "一蘭", "一風堂",
                "くら寿司", "スシロー", "はま寿司", "かっぱ寿司", "鳥貴族", "牛角", "ミニヨン", "ウーバーイーツ",
                "ubereats", "出前館", "wolt", "焼肉", "ラーメン", "らーめん", "うどん", "そば", "寿司", "すし",
                "食堂", "レストラン", "カフェ", "cafe", "珈琲", "コーヒー", "coffee", "ベーカリー", "パン屋",
                "弁当", "惣菜", "居酒屋", "ダイニング", "キッチン", "グリル", "ピザ", "餃子", "カレー", "定食",
                "バーガー", "ドーナツ", "製麺"
            ),
            DAILY to listOf(
                "ドラッグ", "サンドラッグ", "マツモトキヨシ", "マツキヨ", "ウエルシア", "ツルハ", "スギ薬局",
                "ココカラファイン", "ダイレックス", "コスモス薬品", "クリエイト", "トモズ", "ダイソー", "セリア",
                "キャンドゥ", "ワッツ", "カインズ", "コーナン", "ニトリ", "無印良品", "ロフト", "ハンズ",
                "ドンキホーテ", "ドンキ", "ホームセンター", "amazon", "アマゾン", "楽天市場", "yahoo", "メルカリ",
                "zozotown", "ユニクロ", "uniqlo", "gu", "しまむら", "ワークマン"
            ),
            TRANSPORT to listOf(
                "jr", "ジエイアール", "スマートex", "えきねっと", "suica", "pasmo", "icoca", "新幹線", "鉄道",
                "電鉄", "地下鉄", "メトロ", "バス", "タクシー", "ana", "jal", "peach", "ジェットスター",
                "スカイマーク", "skyticket", "スカイチケット", "航空", "タイムズカー", "timescar", "タイムズ",
                "toyotashare", "トヨタシェア", "カーシェア", "レンタカー", "駐車", "パーキング", "eneos", "出光",
                "コスモ石油", "ガソリン", "nexco", "高速道路", "charichari", "チャリチャリ", "ハローサイクリング",
                "バイクシェア", "luup", "uber"
            ),
            HOUSING to listOf("家賃", "不動産", "管理費", "賃貸"),
            UTILITIES to listOf("電力", "電気", "でんき", "ガス", "水道", "エネルギー"),
            PHONE to listOf(
                "docomo", "ドコモ", "au", "kddi", "softbank", "ソフトバンク", "楽天モバイル", "ymobile",
                "ワイモバイル", "uqモバイル", "ahamo", "povo", "linemo", "nuro", "プロバイダ", "googleone",
                "icloud", "dropbox"
            ),
            LEISURE to listOf(
                "netflix", "spotify", "amazonプライム", "primevideo", "applemusic", "youtube", "disney", "hulu",
                "unext", "dazn", "nintendo", "playstation", "steam", "itunes", "kindle", "映画", "シネマ", "toho",
                "カラオケ", "ラウンドワン", "書店", "紀伊國屋", "丸善", "tsutaya", "ブックオフ", "アニメイト",
                "ホテル", "旅館", "agoda", "booking", "expedia", "じゃらん", "おみやげ", "omiyage", "お土産", "土産", "銘品",
                "ギャラリー", "展望", "美術館", "博物館", "水族館", "動物園", "スタジアム", "チケット", "ぴあ"
            ),
            MEDICAL to listOf(
                "病院", "医院", "クリニック", "歯科", "眼科", "皮ふ科", "皮膚科", "内科", "外科", "耳鼻", "小児科",
                "調剤", "薬局", "整骨", "接骨", "鍼灸"
            )
        )

        // Compared loosely, so a word must stay distinctive once long vowels are dropped: スーパー
        // would become すぱ, which is in スパ and スパゲッティ
        val DICTIONARY: List<Pair<String, String>> = WORDS.flatMap { (category, words) ->
            words.map { JapaneseTextNormalizer.looseKey(it) to category }
        }
    }
}
