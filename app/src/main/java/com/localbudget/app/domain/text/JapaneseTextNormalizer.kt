package com.localbudget.app.domain.text

import java.text.Normalizer

object JapaneseTextNormalizer {

    /**
     * Unicode NFKC: half-width katakana (ｷｬﾍﾞﾂ) → full-width (キャベツ),
     * full-width digits/latin (１９８, ＴＯＴＡＬ) → ASCII, ￥ → ¥.
     */
    fun nfkc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)

    /**
     * Key for dictionary lookups only (never shown to the user):
     * NFKC + lowercase + katakana folded to hiragana + whitespace removed,
     * so that "ﾆﾝｼﾞﾝ", "ニンジン" and "にん じん" all compare equal.
     */
    fun matchKey(text: String): String {
        val normalized = nfkc(text).lowercase()
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            when {
                ch.isWhitespace() -> Unit
                ch in KATAKANA_FOLDABLE -> sb.append(ch - KATAKANA_TO_HIRAGANA_OFFSET)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * A looser [matchKey] for store names, which card statements spell their own way: small kana
     * made full size (ﾌｧﾐﾘｰ in a statement reads フアミリ-) and long vowels, hyphens, dots and
     * spaces dropped. "フアミリ-マ-ト" and "ファミリーマート" both give "ふあみりまと".
     */
    fun looseKey(text: String): String =
        matchKey(text)
            .map { SMALL_KANA[it] ?: it }
            .filterNot { it in LOOSE_DROPPED }
            .joinToString("")

    private val SMALL_KANA = mapOf(
        'ぁ' to 'あ', 'ぃ' to 'い', 'ぅ' to 'う', 'ぇ' to 'え', 'ぉ' to 'お', 'っ' to 'つ',
        'ゃ' to 'や', 'ゅ' to 'ゆ', 'ょ' to 'よ', 'ゎ' to 'わ', 'ゕ' to 'か', 'ゖ' to 'け'
    )
    private const val LOOSE_DROPPED = "ー-‐−–—~〜・･.,'’*/()（）「」〔〕[]"

    // ァ (U+30A1) .. ヶ (U+30F6) map 1:1 onto ぁ (U+3041) .. ゖ (U+3096); ー and ・ are left as-is
    private val KATAKANA_FOLDABLE = 'ァ'..'ヶ'
    private const val KATAKANA_TO_HIRAGANA_OFFSET = 0x60
}
