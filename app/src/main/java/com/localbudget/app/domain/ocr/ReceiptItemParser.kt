package com.localbudget.app.domain.ocr

import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/**
 * Splits receipt rows into purchased line items with their prices.
 *
 * Handles the layouts Japanese POS receipts commonly use:
 *  - `ｷｬﾍﾞﾂ  ¥198` (name and price on one row)
 *  - a quantity row under the item: `2個 × 単128  ¥256`, `@128 × 2`
 *  - a discount row under the item: `値引  -50`
 *  - names and prices split into two runs (ML Kit block order without row reconstruction):
 *    the run of price-only rows is paired in order with the names right before it
 * Reading stops at the first subtotal/total row, so payment lines are never items.
 * Unpriced rows before the first priced item are the header (store name, address, date) and are dropped.
 * Rows written as sentences ("「軽」は軽減税率対象商品です。") are notes, never items.
 * Misread katakana words in names are put back from a word list, see [ItemNameCorrector].
 * Glyphs ML Kit misreads in katakana names (ﾛ一ｽ, ﾏﾙ工ﾂ) and amounts (Y198, ¥l98, 1.280) are repaired first,
 * see [OcrTextRepair].
 */
object ReceiptItemParser {

    // 支払: the payment row ("QUICPay支払 180") when the total row itself was not read
    private val STOP_KEYWORDS = listOf("小計", "合計", "台計", "お買上計", "現計", "総額", "お会計", "支払", "TOTAL")
        .map { JapaneseTextNormalizer.matchKey(it) }

    private val DISCOUNT_KEYWORDS = listOf("値引", "割引", "クーポン")
        .map { JapaneseTextNormalizer.matchKey(it) }

    // Structure / header rows that are never items (matched width/kana-insensitively: ﾚｼﾞ, ﾎﾟｲﾝﾄ)
    private val SKIP_KEYWORDS = listOf(
        "お買上", "お預り", "お釣", "預り", "釣",
        "消費税", "外税", "内税", "対象", "レシート", "領収書", "領収証",
        "登録番号", "インボイス", "TEL", "電話", "クレジット",
        "ポイント", "残高", "会員", "レジ", "係"
    ).map { JapaneseTextNormalizer.matchKey(it) }

    // Dates, times and phone numbers mark header rows: "2026/09/20", "2026年10月1日", "18:42", "03-1234-5678"
    private val HEADER_PATTERN = Regex("""\d{2,4}\s*[/年.]\s*\d{1,2}\s*[/月.]\s*\d{1,2}|\d{1,2}\s*:\s*\d{2}|\d{2,4}-\d{2,4}-\d{3,4}""")

    private const val NUMBER = """[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+"""

    // On NFKC text. The price must be set off by whitespace or ¥ so "ゴミ袋 45L" / "19:05" are not prices.
    // A leading -, − or ▲ marks a negative amount; trailing tax marks (※, *, 軽, 外, 内) are allowed.
    // 車: 軽 as ML Kit sometimes reads it ("¥180車軽")
    private val TRAILING_PRICE = Regex("""(?:^|\s|¥|\\|[※*軽])\s*([-−▲△]?)\s*[¥\\]?\s*($NUMBER)\s*円?\s*[※*軽車外内税]*\s*$""")

    // "2個 × 単128", "2点x@128", "2 X 128"  → quantity, unit price
    private val QTY_THEN_UNIT = Regex("""(\d+)\s*(?:個|点|コ|本|袋|パック|P)?\s*[×xX*]\s*(?:単価?|@)?\s*¥?\s*($NUMBER)""")
    // "@128 × 2", "単価128 x 2個" → unit price, quantity
    private val UNIT_THEN_QTY = Regex("""(?:単価?|@)\s*¥?\s*($NUMBER)\s*[×xX*]\s*(\d+)\s*(?:個|点|コ|本|袋|パック|P)?""")

    private val MARKERS = Regex("""[※*#＊]\s*""")
    // Marks printed in front of the name ("©手巻き…", "◎ﾄﾏﾄ", "●牛乳", "③だし…"): anything but a letter,
    // digit or bracket, and circled numbers, which are marks rather than part of the name
    private val LEADING_SYMBOLS = Regex("""^(?:[^\p{L}\p{N}(（「\[]|[\u2460-\u24FF\u2776-\u2793])+""")
    // A one-letter column flag before the name ("Fﾋﾟｰﾏﾝ" at OK for reduced tax), see [withoutFlagLetter]
    private val FLAG_LETTER = Regex("""^([A-Z])(?=[\p{IsHiragana}\p{IsKatakana}\p{IsHan}ｦ-ﾟ])""")
    // JAN / PLU codes some POS systems print before the name: "4901234567890 鶏もも肉", "001 玉ねぎ"
    private val LEADING_CODE = Regex("""^\d{3,13}\s+""")
    // Tax marks left at the end of the name once the price is cut off: "にんじん 軽", "牛乳 外"
    private val TRAILING_TAX_MARK = Regex("""\s+[軽外内税]$""")
    private val BRACKETS_ONLY = Regex("""^[\s()（）\[\]]*$""")
    private val SENTENCE = Regex("""[、。]""")

    fun parse(text: String): List<ReceiptLineItem> {
        val items = mutableListOf<ReceiptLineItem>()
        // Indexes into [items] of unpriced names since the last priced row, and the price-only rows after them
        val pendingNames = mutableListOf<Int>()
        val priceRun = mutableListOf<Long>()

        fun flushPriceRun() {
            if (priceRun.isEmpty()) return
            // Pair the run with the names right before it; extra leading names (headers) stay unpriced
            if (priceRun.size <= pendingNames.size) {
                val names = pendingNames.takeLast(priceRun.size)
                names.zip(priceRun).forEach { (i, price) -> items[i] = items[i].copy(price = price) }
            }
            pendingNames.clear()
            priceRun.clear()
        }

        for (rawRow in text.lines()) {
            // The repaired row stands in for the printed one, so names read "ﾛｰｽ", not "ﾛ一ｽ"
            val row = OcrTextRepair.repairKana(rawRow.trim())
            if (row.isEmpty()) continue
            // Repair keeps the length, so offsets into it still map back onto the printed row
            val normalized = OcrTextRepair.repairLine(JapaneseTextNormalizer.nfkc(row))
            val key = JapaneseTextNormalizer.matchKey(row)

            if (STOP_KEYWORDS.any { key.contains(it) }) break

            // Quantity first, so the "2" in "@98 × 2" is not read as a price
            val quantity = parseQuantity(normalized)
            val price = TRAILING_PRICE.find(normalized, quantity?.range?.last?.plus(1) ?: 0)

            if (DISCOUNT_KEYWORDS.any { key.contains(it) }) {
                flushPriceRun()
                val amount = price?.groupValues?.get(2)?.toAmount()
                val last = items.lastIndex
                if (amount != null && last >= 0) {
                    items[last] = items[last].copy(discount = items[last].discount + amount)
                }
                continue
            }
            if (SKIP_KEYWORDS.any { key.contains(it) } || HEADER_PATTERN.containsMatchIn(normalized) ||
                SENTENCE.containsMatchIn(normalized)
            ) continue

            val nameEnd = quantity?.range?.first ?: price?.range?.first
            val name = cleanName(if (nameEnd != null) prefixOf(row, normalized, nameEnd) else row)

            if (name.isEmpty()) {
                val amount = price?.groupValues?.get(2)?.toAmount()
                when {
                    // Quantity row: applies to the item above it
                    quantity != null && items.isNotEmpty() -> {
                        flushPriceRun()
                        val last = items.lastIndex
                        val (qty, unit) = quantity.value
                        items[last] = items[last].copy(
                            quantity = qty,
                            unitPrice = unit,
                            price = amount ?: items[last].price ?: qty * unit
                        )
                    }
                    // Price-only row: part of a run to pair with the preceding names
                    price != null && amount != null && price.groupValues[1].isEmpty() -> priceRun.add(amount)
                }
                continue
            }

            flushPriceRun()
            val amount = price?.groupValues?.get(2)?.toAmount()
            val item = if (quantity != null) {
                val (qty, unit) = quantity.value
                ReceiptLineItem(name, price = amount ?: qty * unit, quantity = qty, unitPrice = unit)
            } else {
                ReceiptLineItem(name, price = amount)
            }
            items.add(item)
            if (item.price == null) pendingNames.add(items.lastIndex) else pendingNames.clear()
        }
        flushPriceRun()
        return withoutFlagLetter(items.dropWhile { it.price == null })
            .map { it.copy(name = ItemNameCorrector.correct(it.name)) }
    }

    /**
     * Lines saved from an earlier scan, read again by today's rules: one entry per line, null for a
     * line that is not an item after all (a payment row, "8%対象 ¥378)", the notes below the total),
     * otherwise the line with its name cleaned and corrected. Prices and quantities are kept.
     */
    fun tidySaved(items: List<ReceiptLineItem>): List<ReceiptLineItem?> {
        var stopped = false
        val kept = items.map { item ->
            val printed = OcrTextRepair.repairKana(item.name.trim())
            val normalized = OcrTextRepair.repairLine(JapaneseTextNormalizer.nfkc(printed))
            val key = JapaneseTextNormalizer.matchKey(printed)
            // Everything from the total or payment row on was read past the end of the items
            if (stopped || STOP_KEYWORDS.any { key.contains(it) }) {
                stopped = true
                return@map null
            }
            if (SKIP_KEYWORDS.any { key.contains(it) } || HEADER_PATTERN.containsMatchIn(normalized) ||
                SENTENCE.containsMatchIn(normalized)
            ) return@map null
            cleanName(printed).takeIf { it.isNotEmpty() }?.let { item.copy(name = it) }
        }
        val tidied = withoutFlagLetter(kept.filterNotNull())
            .map { it.copy(name = ItemNameCorrector.correct(it.name)) }
            .iterator()
        return kept.map { if (it == null) null else tidied.next() }
    }

    /**
     * Drops a letter some stores print before item names as a flag ("Fﾋﾟｰﾏﾝ", "Fﾐﾙｸｺｺｱ": F = reduced
     * tax at OK), once two or more names start with the same letter. A single "Tｼｬﾂ" keeps its T.
     */
    private fun withoutFlagLetter(items: List<ReceiptLineItem>): List<ReceiptLineItem> {
        val flags = items.mapNotNull { FLAG_LETTER.find(JapaneseTextNormalizer.nfkc(it.name))?.groupValues?.get(1) }
        val flag = flags.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys.firstOrNull() ?: return items
        return items.map { item ->
            val match = FLAG_LETTER.find(JapaneseTextNormalizer.nfkc(item.name))
            // The flag is one character in the printed name too (F or its full-width Ｆ)
            if (match?.groupValues?.get(1) == flag) item.copy(name = item.name.substring(1).trim()) else item
        }
    }

    private class Quantity(val range: IntRange, val value: Pair<Int, Long>)

    private fun parseQuantity(normalized: String): Quantity? {
        // The @/単 form is more specific: "@98 × 2" would otherwise read as 98 units of 2
        UNIT_THEN_QTY.find(normalized)?.let { m ->
            val unit = m.groupValues[1].toAmount() ?: return@let
            val qty = m.groupValues[2].toIntOrNull() ?: return@let
            return Quantity(m.range, qty to unit)
        }
        QTY_THEN_UNIT.find(normalized)?.let { m ->
            val qty = m.groupValues[1].toIntOrNull() ?: return@let
            val unit = m.groupValues[2].toAmount() ?: return@let
            return Quantity(m.range, qty to unit)
        }
        return null
    }

    /**
     * The printed (un-normalized) text that normalizes to [normalized]'s first [normalizedEnd] chars.
     * NFKC can shorten text (ｶﾞ → ガ), so take the longest original prefix that fits; that keeps a
     * trailing ﾞ/ﾟ with its kana.
     */
    private fun prefixOf(original: String, normalized: String, normalizedEnd: Int): String {
        if (original.length == normalized.length) return original.substring(0, normalizedEnd)
        var end = 0
        while (end < original.length && JapaneseTextNormalizer.nfkc(original.substring(0, end + 1)).length <= normalizedEnd) {
            end++
        }
        return original.substring(0, end)
    }

    private fun cleanName(raw: String): String {
        val name = raw.replace(MARKERS, "").trim()
            .replace(LEADING_SYMBOLS, "")
            .replace(LEADING_CODE, "")
            .replace(TRAILING_TAX_MARK, "")
            .trim()
        return if (BRACKETS_ONLY.matches(name)) "" else name
    }

    private fun String.toAmount(): Long? = replace(",", "").toLongOrNull()
}
