package com.localbudget.app.domain.text

import java.text.Normalizer

/**
 * Finds a known word inside OCR text despite the ways ML Kit misreads katakana, on NFKC text.
 *
 * Edit distance where the usual misreads cost little: a lost or added voicing mark (シ/ジ, ク/グ,
 * プ/ブ) or a small kana read large (ュ/ユ) costs [VOICING_COST], a look-alike shape (シ/ツ, ソ/ン,
 * ル/レ) costs [SHAPE_COST], anything else 1. The word may sit anywhere in the text.
 */
object KanaFuzzyMatcher {

    const val VOICING_COST = 0.3
    const val SHAPE_COST = 0.5

    /** Where [word] was found in the text and how far that span is from it. */
    data class Match(val start: Int, val end: Int, val cost: Double)

    /** The span of [text] closest to [word], or null when even that is further than [maxCost]. */
    fun find(text: String, word: String, maxCost: Double): Match? {
        if (word.isEmpty() || text.isEmpty()) return null
        val n = word.length
        // Semi-global alignment: the word must be used whole, the text may start and end anywhere
        var prev = DoubleArray(text.length + 1)
        var prevStart = IntArray(text.length + 1) { it }
        for (i in 1..n) {
            val cur = DoubleArray(text.length + 1)
            val curStart = IntArray(text.length + 1)
            cur[0] = i.toDouble()
            curStart[0] = 0
            for (j in 1..text.length) {
                val sub = prev[j - 1] + substitution(word[i - 1], text[j - 1])
                val del = prev[j] + 1.0 // a word character missing from the text
                val ins = cur[j - 1] + 1.0 // an extra character in the text
                when {
                    sub <= del && sub <= ins -> { cur[j] = sub; curStart[j] = prevStart[j - 1] }
                    del <= ins -> { cur[j] = del; curStart[j] = prevStart[j] }
                    else -> { cur[j] = ins; curStart[j] = curStart[j - 1] }
                }
            }
            prev = cur
            prevStart = curStart
        }
        var bestEnd = -1
        for (j in 1..text.length) if (bestEnd < 0 || prev[j] < prev[bestEnd]) bestEnd = j
        val cost = prev[bestEnd]
        return if (cost <= maxCost) Match(prevStart[bestEnd], bestEnd, cost) else null
    }

    /**
     * How far a misread may stray from a word of [length] characters: up to five only by voicing
     * marks and look-alikes ("ルルモン" is not "ホルモン"), longer ones also by one clean miss per
     * four characters.
     */
    fun allowedCost(length: Int): Double = when {
        length < 3 -> 0.0
        length <= 5 -> 2 * VOICING_COST
        else -> length * 0.25
    }

    fun substitution(a: Char, b: Char): Double = when {
        a == b -> 0.0
        base(a) == base(b) -> VOICING_COST
        SHAPES.any { a in it && b in it } -> SHAPE_COST
        else -> 1.0
    }

    /** The kana without its voicing mark and at full size: ジ → シ, パ → ハ, ュ → ユ. */
    private fun base(c: Char): Char {
        val stripped = Normalizer.normalize(c.toString(), Normalizer.Form.NFD).first()
        return SMALL_TO_LARGE[stripped] ?: stripped
    }

    private val SMALL_TO_LARGE = mapOf(
        'ァ' to 'ア', 'ィ' to 'イ', 'ゥ' to 'ウ', 'ェ' to 'エ', 'ォ' to 'オ',
        'ッ' to 'ツ', 'ャ' to 'ヤ', 'ュ' to 'ユ', 'ョ' to 'ヨ', 'ヮ' to 'ワ'
    )

    // Groups of characters ML Kit takes for one another
    private val SHAPES = listOf(
        "シツ", "ソン", "ルレ", "クケタ", "ウワ", "ヌス", "ナメ", "チテ", "コユ", "ロ口0O", "ニ二=",
        "エ工", "カ力", "ト卜", "ハ八", "タ夕", "ヒ匕", "ー一-", "ヘへ", "リり", "1lIユ"
    )
}
