package com.localbudget.app.domain.ocr

/**
 * Undoes the glyph confusions ML Kit's Japanese model makes in receipt amounts, on NFKC text.
 *
 * Only whitespace-separated tokens that already look like an amount are touched, so item names
 * ("ｷｬﾍﾞﾂ", "1L", "500ml", "Sサイズ") stay as printed:
 *  - the yen sign read as Y / 半 / 羊 / F / \ : "Y198", "半228", "F6,101" → "¥198", "¥228", "¥6,101"
 *  - letters read for digits inside an amount: "¥l98" → "¥198", "1,4O8" → "1,408"
 *  - the thousands comma read as a period: "1.280" → "1,280" (yen amounts have no decimals)
 *  - a space read after the thousands comma: "3, 919" → "3,919 " (the space moves behind the amount)
 */
object OcrTextRepair {

    // Optional sign, optional (mis-read) yen sign, digits or look-alikes with separators, trailing tax marks
    private val AMOUNT_TOKEN = Regex("""^([-−▲△]?)([¥\\Y半羊F]?)([0-9OoDIl|SB][0-9OoDIl|SB,.]*)([円※*軽外内]*)$""")
    private val PERIOD_THOUSANDS = Regex("""(?<![0-9.])([0-9]{1,3})\.([0-9]{3})(?![0-9.])""")
    // "3, 919": digits, the comma, the stray spaces, then exactly three digits
    private val SPACED_THOUSANDS = Regex("""(?<![0-9,])([0-9OoDIl]{1,3}),( +)([0-9OoDIl]{3})(?![0-9OoDIl,])""")

    fun repairLine(line: String): String {
        if (line.isBlank()) return line
        // Same length: the spaces go behind the amount, so offsets into the row stay put
        val joined = SPACED_THOUSANDS.replace(line) { m ->
            val (head, spaces, tail) = m.destructured
            "$head,$tail$spaces"
        }
        // Keep the original spacing: replace token by token
        return Regex("""\S+""").replace(joined) { m -> repairToken(m.value) }
    }

    fun repairText(text: String): String = text.lines().joinToString("\n") { repairLine(it) }

    private fun repairToken(token: String): String {
        val m = AMOUNT_TOKEN.matchEntire(token) ?: return token
        val (sign, yen, body, marks) = m.destructured
        val realDigits = body.count { it.isDigit() }
        if (realDigits == 0) return token
        val lookAlikes = body.count { it in LOOK_ALIKES }
        // Without a yen sign, only fix a token that is mostly digits already ("1O8", not "IO" or "SB1")
        if (yen.isEmpty() && lookAlikes > 0 && (realDigits < 2 || lookAlikes * 2 > body.length)) return token
        // A bare "Y" next to digits could be a size/model code ("Y2"); require a plausible amount after it
        if (yen.isNotEmpty() && yen != "¥" && realDigits + lookAlikes < 2) return token

        var digits = buildString { for (ch in body) append(LOOK_ALIKES[ch] ?: ch) }
        digits = PERIOD_THOUSANDS.replace(digits) { "${it.groupValues[1]},${it.groupValues[2]}" }
        val fixedYen = if (yen.isEmpty()) "" else "¥"
        return sign + fixedYen + digits + marks
    }

    private val LOOK_ALIKES = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0',
        'I' to '1', 'l' to '1', '|' to '1',
        'S' to '5', 'B' to '8'
    )

    /**
     * Undoes the confusions ML Kit makes inside katakana words. Runs on the printed text, before NFKC,
     * and keeps a half-width word half-width:
     *  - a detached voicing mark: "ホ゜テト", "ﾊ゛ﾅﾅ", "ホ ﾟ" (NFKC would leave "ホ ゚テト") → ポテト, ﾊﾞﾅﾅ
     *  - the long-vowel mark read as 一 or a dash: "ロ一ス", "ｺ-ﾋ-" → ロース, ｺｰﾋｰ
     *    (not after ン / ッ, where no long vowel follows, so "セブン-イレブン" keeps its hyphen)
     *  - kanji and hiragana shaped like a katakana, next to katakana: "マル工ツ", "口ース", "二ンジン" → マルエツ, ロース, ニンジン
     * A look-alike between kanji ("一口カツ", "千切り") is left as printed.
     */
    fun repairKana(text: String): String {
        // Look-alikes side by side ("力二カマ") are settled from the katakana inwards, one per pass
        var repaired = text
        repeat(3) {
            val next = repairKanaOnce(repaired)
            if (next == repaired) return repaired
            repaired = next
        }
        return repaired
    }

    private fun repairKanaOnce(text: String): String {
        val out = StringBuilder(text.length)
        for (i in text.indices) {
            val c = text[i]
            val prev = out.lastOrNull()
            val inKanaWord = if (c in KANA_LOOK_ALIKES) kanaWordWidth(prev, text, i) else null
            when {
                c in VOICING_MARKS -> {
                    // "ホ ゜": the mark belongs to the kana before the space
                    if (prev == ' ' && out.length >= 2 && isKana(out[out.length - 2])) out.setLength(out.length - 1)
                    val kana = out.lastOrNull()
                    val semi = c in "゜ﾟ°"
                    when {
                        kana == null || !isKana(kana) || (c == '°' && kana !in HA_ROW) -> out.append(c)
                        isHalfWidth(kana) -> out.append(if (semi) 'ﾟ' else 'ﾞ')
                        else -> out.append(if (semi) '\u309A' else '\u3099')
                    }
                }
                c in LONG_VOWEL_LOOK_ALIKES && prev != null && isKatakana(prev) && prev !in NO_LONG_VOWEL_AFTER &&
                    endsLongVowel(text, i) -> out.append(if (isHalfWidth(prev)) 'ｰ' else 'ー')
                inKanaWord != null -> {
                    val (full, half) = KANA_LOOK_ALIKES.getValue(c)
                    out.append(if (inKanaWord) half else full)
                }
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /**
     * A dash after a katakana is a long vowel when the word goes on ("ｺ-ﾋ-") or ends there,
     * but not before a number ("ﾊﾞﾅﾅ -50", "ﾊﾞﾅﾅ- 50" keep their minus sign).
     */
    private fun endsLongVowel(text: String, i: Int): Boolean {
        val next = text.getOrNull(i + 1) ?: return true
        return when {
            isKatakana(next) || next == '\n' || next == '\r' -> true
            next == ' ' || next == '\u3000' -> text.getOrNull(i + 2)?.isLetter() == true
            else -> false
        }
    }

    /**
     * Whether the look-alike at [i] sits in a katakana word: a katakana on one side and a katakana
     * or a word boundary on the other ("り", "=", "0", "O" need katakana on both sides).
     * Returns whether that word is half-width, or null when the look-alike is not in a katakana word.
     */
    private fun kanaWordWidth(prev: Char?, text: String, i: Int): Boolean? {
        var next = text.getOrNull(i + 1)
        // "口一ス": the long vowel after it is not repaired yet
        val afterNext = text.getOrNull(i + 2)
        if (next != null && next in LONG_VOWEL_LOOK_ALIKES && afterNext != null && isKatakana(afterNext)) {
            next = if (isHalfWidth(afterNext)) 'ｰ' else 'ー'
        }
        val left = prev?.takeIf(::isKatakana)
        val right = next?.takeIf(::isKatakana)
        val inWord = if (text[i] in NEEDS_KANA_BOTH_SIDES) {
            left != null && right != null
        } else {
            // Another look-alike before it may be the start of the word ("力二カマ"), repaired on the next pass
            (left != null && (right != null || isBoundary(next))) ||
                (right != null && (isBoundary(prev) || prev in KANA_LOOK_ALIKES))
        }
        return if (inWord) isHalfWidth(left ?: right!!) else null
    }

    private fun isBoundary(c: Char?): Boolean = c == null || !c.isLetter()

    // ヵ / ヶ are counters between kanji ("八ヶ岳", "3ヶ月"), not part of a katakana word
    private fun isKatakana(c: Char): Boolean =
        (c in '\u30A1'..'\u30FA' && c != 'ヵ' && c != 'ヶ') || c == 'ー' || isHalfWidth(c) ||
            c == '\u3099' || c == '\u309A'

    private fun isKana(c: Char): Boolean = isKatakana(c) || c in 'ぁ'..'ゖ'

    private fun isHalfWidth(c: Char): Boolean = c in '\uFF66'..'\uFF9F'

    // ゛゜ standing alone, half-width ﾞﾟ after a space, and ° (read for ゜ after ハ行)
    private const val VOICING_MARKS = "゛゜ﾞﾟ°"
    private const val HA_ROW = "ハヒフヘホﾊﾋﾌﾍﾎはひふへほ"
    private const val LONG_VOWEL_LOOK_ALIKES = "一-‐−–—―─━－"
    private const val NO_LONG_VOWEL_AFTER = "ンッーﾝｯｰ"
    private const val NEEDS_KANA_BOTH_SIDES = "り=0O"

    // Look-alike → (full-width, half-width) katakana
    private val KANA_LOOK_ALIKES = mapOf(
        '口' to ('ロ' to 'ﾛ'), '二' to ('ニ' to 'ﾆ'), '工' to ('エ' to 'ｴ'), '力' to ('カ' to 'ｶ'),
        '卜' to ('ト' to 'ﾄ'), '八' to ('ハ' to 'ﾊ'), '夕' to ('タ' to 'ﾀ'), '才' to ('オ' to 'ｵ'),
        '千' to ('チ' to 'ﾁ'), 'へ' to ('ヘ' to 'ﾍ'), 'べ' to ('ベ' to 'ベ'), 'ぺ' to ('ペ' to 'ペ'),
        'り' to ('リ' to 'ﾘ'), '=' to ('ニ' to 'ﾆ'), '0' to ('ロ' to 'ﾛ'), 'O' to ('ロ' to 'ﾛ')
    )
}
