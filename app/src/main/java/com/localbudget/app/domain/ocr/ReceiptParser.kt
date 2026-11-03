package com.localbudget.app.domain.ocr

import com.localbudget.app.domain.model.ParsedReceipt
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import com.localbudget.app.domain.text.KanaFuzzyMatcher
import java.time.LocalDate
import java.util.regex.Pattern

object ReceiptParser {

    private val NOISE_PATTERNS = listOf(
        Pattern.compile("^(領収書|領収証|レシート|買上票|お買上票|ご利用明細|お客様控え|預かり票).*"),
        Pattern.compile("^(TEL|tel|Tel|電話|FAX|No|NO|№|〒).*"),
        Pattern.compile("^(http|www|https).*"),
        Pattern.compile(".*(登録番号|インボイス|T[0-9]{13}).*"),
        Pattern.compile("^[0-9\\-\\s/]{8,}$") // Only phone numbers / codes
    )

    // Header rows that are never the store name
    private val GREETINGS = listOf(
        "ありがとう", "いらっしゃいませ", "ようこそ", "毎度", "来店", "お買い上げ", "お買上げ",
        "またの", "ご利用", "WELCOME", "THANK"
    )
    private val POSTAL = Regex("""^〒|^\d{3}-\d{4}""")
    private val PREFECTURE = Regex("""^(北海道|東京都|京都府|大阪府|\S{2,3}県)\S*[市区町村郡]""")
    private val STREET_ADDRESS = Regex("""[市区町村郡]\S*\d+(?:-\d+)+|\d+丁目|\d+番地?|番地""")
    private val COMPANY = Regex("""株式会社|有限会社|\(株\)|\(有\)""")
    private val STORE_SUFFIX = Regex("""(店|ストア|マート|スーパー|薬局|ドラッグ|商店|市場)$""")
    private val TRAILING_CONTACT = Regex("""\s*(TEL|Tel|tel|電話|☎).*$""")
    // The address ML Kit put on the store's row: "小金井法政大学前店 東京都小金井市緑町"
    private val TRAILING_ADDRESS = Regex("""\s+(北海道|東京都|京都府|大阪府|\S{2,3}県)\S*[市区町村郡].*$""")
    // Notices in the header ("オーケークラブ会員で現金払に限り、食料品は…") are sentences, never the store
    private val SENTENCE = Regex("""[、。,]""")

    // Chains whose name also shows up outside the header ("イオンリテール株式会社" in the footer),
    // used when the header only carries the branch ("新宿店") or nothing usable
    private val KNOWN_CHAINS = listOf(
        "セブン-イレブン", "セブンイレブン", "ローソン", "ファミリーマート", "ミニストップ", "デイリーヤマザキ",
        "NewDays", "イトーヨーカドー", "イオン", "西友", "業務スーパー", "成城石井", "まいばすけっと",
        "サミット", "マルエツ", "オーケー", "ヤオコー", "ベルク", "いなげや", "東急ストア", "京王ストア",
        "紀ノ国屋", "ロピア", "ライフ", "マツモトキヨシ", "ウエルシア", "ツルハ", "サンドラッグ",
        "スギ薬局", "ココカラファイン", "ダイソー", "セリア", "キャンドゥ", "無印良品", "ドン・キホーテ",
        "ニトリ", "カインズ", "コーナン", "丸善"
    )
    // Chains whose logo prints the name in Latin letters, to the name used in KNOWN_CHAINS
    private val CHAIN_ALIASES = mapOf(
        "FAMILYMART" to "ファミリーマート", "LAWSON" to "ローソン", "MINISTOP" to "ミニストップ",
        "AEON" to "イオン", "SEIYU" to "西友", "DAISO" to "ダイソー", "NITORI" to "ニトリ",
        "MARUETSU" to "マルエツ", "SUMMIT" to "サミット", "YAOKO" to "ヤオコー", "INAGEYA" to "いなげや",
        "ITOYOKADO" to "イトーヨーカドー", "SEIJOISHII" to "成城石井", "MATSUKIYO" to "マツモトキヨシ",
        "WELCIA" to "ウエルシア", "TSURUHA" to "ツルハ", "SUNDRUG" to "サンドラッグ", "CAINZ" to "カインズ",
        "KOHNAN" to "コーナン", "SERIA" to "セリア", "CANDO" to "キャンドゥ", "MARUZEN" to "丸善"
    )
    // Invoice registration numbers (T + 13 digits) are the company's, the same at every branch: a chain
    // is known by it when neither its logo nor its name was read. Numbers as printed on real receipts.
    private val INVOICE_CHAINS = mapOf(
        "5012401016536" to "ファミリーマート",
        "6010801001974" to "オーケー"
    )
    private val INVOICE_NUMBER = Regex("""T\s*([0-9][0-9\s-]{11,20}[0-9])""")
    // Katakana chain names long enough to be told apart from other words when misread
    private val KATAKANA_CHAINS = KNOWN_CHAINS.filter { name -> name.length >= 4 && name.all { it in 'ァ'..'ヺ' || it == 'ー' } }
    // Misreads a chain name may carry per character: about one lost voicing mark per two characters
    private const val CHAIN_COST_PER_CHAR = 0.15
    private val LEADING_MARKS = Regex("""^[^\p{L}\p{N}]+""")

    private val TAX_INCLUSIVE_TOTAL_KEYWORDS = listOf(
        "税込合計", "税込計", "支払総額", "総額", "お買上金額", "お買上額", "ご請求額", "請求金額",
        "領収金額", "お支払金額", "お支払額", "TOTAL", "Total"
    )
    // 台計: ML Kit often reads 合 as 台
    private val TOTAL_KEYWORDS = listOf("合計", "台計", "お買上計", "現計", "お会計", "お支払", "ご利用金額", "ご利用額")
    private val EXCLUDE_KEYWORDS = listOf(
        "預り", "お預り", "預かり", "釣", "お釣り",
        "消費税", "内消費税", "外税", "内税",
        "税抜", "税別", "本体",
        "対象", "ポイント", "残高", "値引", "割引", "点数"
    )
    private val TENDERED_KEYWORDS = listOf("お預り", "お預かり", "預り", "預かり")
    private val CHANGE_KEYWORDS = listOf("お釣", "釣銭", "釣")
    // 支払 covers "QUICPay支払" read as "QU1CPay支払"
    private val PAYMENT_KEYWORDS = listOf(
        "クレジット", "カード", "電子マネー", "交通系", "Suica", "PASMO", "iD", "QUICPay", "nanaco", "WAON",
        "Edy", "PayPay", "楽天ペイ", "d払い", "au PAY", "メルペイ", "QR", "支払"
    )

    private val KEYWORD_PATTERNS = HashMap<String, Regex>()

    // A number that is an amount: not part of a longer number, not a count (2点), rate (8%) or date part
    private val AMOUNT_REGEX = Regex("""(?<![0-9,])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})(?![0-9,]|\s*(?:点|個|コ|品|%|:|年|月|日|/|-|L|ml|g|kg))""")
    // Spans that look like amounts but are dates, times, phone numbers, register / receipt numbers
    private val NON_AMOUNT_SPANS = Regex(
        """(?:20)?\d{2}\s*[年/.\-]\s*\d{1,2}\s*[月/.\-]\s*\d{1,2}日?|\d{1,2}\s*:\s*\d{2}(?::\d{2})?|""" +
            """\d{2,4}-\d{2,4}-\d{3,4}|T\d{13}|(?:No|NO|№|#|レジ|ﾚｼﾞ|係|責)\.?\s*\d+"""
    )

    private class Amount(val value: Long, val start: Int)

    private class Total(val amount: Long, val check: TotalCheck = TotalCheck())

    fun parse(rawText: String): ParsedReceipt {
        // Full-width digits/symbols (１，９８０, ￥, ＴＯＴＡＬ) and half-width kana otherwise slip past the regexes;
        // then undo ML Kit's look-alike glyphs in katakana words (マル工ツ, ロ一ス, ホ゜テト) and amounts (Y198, ¥l98, 1.280)
        val text = OcrTextRepair.repairText(JapaneseTextNormalizer.nfkc(OcrTextRepair.repairKana(rawText)))
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val storeName = extractStoreName(lines)
        val date = extractDate(text) ?: LocalDate.now()
        val total = extractTotalAmount(lines, text)
        val suggestedCategoryId = suggestCategory(storeName, text)

        return ParsedReceipt(
            storeName = storeName,
            date = date,
            amount = total.amount,
            suggestedCategoryId = suggestedCategoryId,
            rawText = rawText,
            totalCheck = total.check
        )
    }

    /**
     * The chain's name alone ("オーケー", not "オーケー 梶野町店") when the receipt names a known chain;
     * otherwise the store's own name from the header, without a trailing branch ("…駅前店").
     */
    private fun extractStoreName(lines: List<String>): String {
        val candidates = mutableListOf<String>()
        val companies = mutableListOf<String>()
        var headerChain: String? = null
        for (line in lines.take(8)) {
            // The header ends where the items or totals begin
            if (TOTAL_KEYWORDS.any { findKeyword(line, it) != null } || line.contains("小計")) break
            if (amountsIn(line).isNotEmpty() && Regex("""[¥\\]\s*\d""").containsMatchIn(line)) break

            if (NOISE_PATTERNS.any { it.matcher(line).find() }) continue
            if (extractDate(line) != null) continue
            if (line.replace(Regex("[,¥\\s円\\-:/.()]"), "").all { it.isDigit() }) continue
            if (line.none { it.isLetterOrDigit() }) continue // ******** / -------
            val upper = line.uppercase()
            if (GREETINGS.any { upper.contains(it) }) continue
            if (POSTAL.containsMatchIn(line) || PREFECTURE.containsMatchIn(line)) continue
            if (STREET_ADDRESS.containsMatchIn(line) && !STORE_SUFFIX.containsMatchIn(line)) continue
            if (SENTENCE.containsMatchIn(line)) continue

            val cleaned = line
                .replace(Regex("^(No\\.\\d+|#\\d+)\\s*"), "")
                .replace(TRAILING_CONTACT, "")
                .replace(TRAILING_ADDRESS, "")
                .trim()
            if (cleaned.length < 2) continue
            val named = withChainName(cleaned)
            if (headerChain == null && !COMPANY.containsMatchIn(named)) headerChain = KNOWN_CHAINS.firstOrNull { named.contains(it) }
            if (COMPANY.containsMatchIn(named)) companies.add(named) else candidates.add(named)
        }

        // Anywhere on the receipt, e.g. "イオンリテール株式会社" in the footer when the header has only a logo
        val chain = lines.firstNotNullOfOrNull { line -> chainIn(line) }
        val picked = candidates.firstOrNull { STORE_SUFFIX.containsMatchIn(it) } ?: candidates.firstOrNull()

        return when {
            headerChain != null -> headerChain!!
            // Only the branch is printed as text (the chain name is a logo): the chain from elsewhere
            picked != null && chain != null && picked.endsWith("店") -> chain
            picked != null -> withoutBranch(picked)
            chain != null -> chain
            else -> companies.firstOrNull()?.let(::withoutBranch).orEmpty()
        }
    }

    /**
     * A store name saved from an earlier scan, written the way [parse] writes it now:
     * "●4maruetsu" / "オーケー 梶野町店" → the chain alone. Unchanged when there is nothing to fix.
     */
    fun tidyStoreName(saved: String): String {
        val name = JapaneseTextNormalizer.nfkc(OcrTextRepair.repairKana(saved)).trim()
        val tidied = chainIn(withChainName(name)) ?: withoutBranch(name)
        return if (tidied == name) saved else tidied
    }

    /** "喫茶さくら 駅前店" → "喫茶さくら"; a name that is only "駅前店" is all there is, so it stays. */
    private fun withoutBranch(name: String): String {
        val parts = name.split(Regex("""\s+"""))
        return if (parts.size >= 2 && parts.last().endsWith("店")) parts.dropLast(1).joinToString(" ") else name
    }

    /**
     * The chain named in [line]: by its Japanese name, misread or not ("マルエシ"), by the Latin
     * one on its logo ("FamilyMart", "●4maruetsu"), or by its invoice registration number.
     */
    private fun chainIn(line: String): String? {
        KNOWN_CHAINS.firstOrNull { line.contains(it) }?.let { return it }
        latinChainIn(line)?.let { return it }
        KATAKANA_CHAINS.firstOrNull { misreadChainIn(line, it) != null }?.let { return it }
        return INVOICE_NUMBER.findAll(line)
            .map { it.groupValues[1].filter(Char::isDigit) }
            .firstNotNullOfOrNull { INVOICE_CHAINS[it] }
    }

    private fun latinChainIn(line: String): String? {
        val latin = line.uppercase().replace(Regex("[^A-Z]"), "")
        return CHAIN_ALIASES.entries.firstOrNull { latin.contains(it.key) }?.value
    }

    /** Where [chain] is misread in [line], allowing only voicing marks and look-alikes ("マルエシ"). */
    private fun misreadChainIn(line: String, chain: String): KanaFuzzyMatcher.Match? =
        KanaFuzzyMatcher.find(line, chain, chain.length * CHAIN_COST_PER_CHAR)?.takeIf { it.cost > 0 }

    /**
     * [candidate] with the chain written the way the chain writes it: a logo read as Latin letters
     * ("●4maruetsu", "ーFamilyMart 新宿店") becomes the Japanese name, a misread one is corrected.
     */
    private fun withChainName(candidate: String): String {
        if (KNOWN_CHAINS.any { candidate.contains(it) }) return candidate
        latinChainIn(candidate)?.let { chain ->
            // What is left once the logo's letters, digits and marks are gone, e.g. the branch
            val rest = candidate.replace(Regex("""[\x21-\x7E]+"""), " ").trim().replace(LEADING_MARKS, "").trim()
            return if (rest.length < 2) chain else "$chain $rest"
        }
        for (chain in KATAKANA_CHAINS) {
            val match = misreadChainIn(candidate, chain) ?: continue
            return candidate.substring(0, match.start) + chain + candidate.substring(match.end)
        }
        return candidate
    }

    private fun extractDate(text: String): LocalDate? {
        // Pattern 1: YYYY年MM月DD日 or YYYY/MM/DD or YYYY-MM-DD or YYYY.MM.DD (OCR often adds spaces: 2026年10月 1日)
        val fullYearRegex = Regex("""(20\d{2})\s*[年/.\-]\s*(\d{1,2})\s*[月/.\-]\s*(\d{1,2})\s*日?""")
        for (match in fullYearRegex.findAll(text)) {
            val (y, m, d) = match.destructured
            tryParseDate(y.toInt(), m.toInt(), d.toInt())?.let { return it }
        }

        // Pattern 2: Reiwa era: 令和R年M月D日 or R6.9.25
        val reiwaRegex = Regex("""(?:令和|R)\s*(\d{1,2})\s*[年/.\-]\s*(\d{1,2})\s*[月/.\-]\s*(\d{1,2})\s*日?""")
        for (match in reiwaRegex.findAll(text)) {
            val (r, m, d) = match.destructured
            tryParseDate(2018 + r.toInt(), m.toInt(), d.toInt())?.let { return it }
        }

        // Pattern 3: YY/MM/DD (e.g. 26/09/25)
        val shortYearRegex = Regex("""(?<![0-9])([2-3]\d)[/.\-](\d{1,2})[/.\-](\d{1,2})(?![0-9])""")
        for (match in shortYearRegex.findAll(text)) {
            val (yy, m, d) = match.destructured
            tryParseDate(2000 + yy.toInt(), m.toInt(), d.toInt())?.let { return it }
        }

        return null
    }

    private fun tryParseDate(year: Int, month: Int, day: Int): LocalDate? {
        return try {
            if (month in 1..12 && day in 1..31) {
                LocalDate.of(year, month, day)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTotalAmount(lines: List<String>, text: String): Total {
        // The fallbacks are read first: they also vouch for (or correct) the total row
        // Priority 3: Cash paid minus change (お預り ¥2,000 / お釣 ¥311)
        val tendered = labeledAmount(lines, TENDERED_KEYWORDS)
        val change = labeledAmount(lines, CHANGE_KEYWORDS)
        val cashPaid = if (tendered != null && change != null && tendered > change) tendered - change else null
        // Priority 4: Subtotal + Tax (外税・消費税を加算)
        val subtotal = subtotal(lines)
        val subtotalWithTax = subtotal?.let { it + (extractTaxAmount(lines) ?: 0L) }
        // Priority 5: The items add up to it
        val itemSum = ReceiptItemParser.parse(text).mapNotNull { it.netPrice }.sum().takeIf { it > 0 }

        // Priority 1: Tax-inclusive total keywords (税込合計, 支払総額, 総額, etc.)
        // Priority 2: General total keywords (合計, お買上計, 現計, etc.)
        val labeled = labeledTotals(lines)
        // A total row ("お支払 ¥1,969") is not also its own payment row
        val payments = paymentAmounts(lines, skip = labeled.keys)
        labeled.values.firstOrNull()?.let { total ->
            val others = labeled.values.drop(1) + payments + listOfNotNull(cashPaid, tendered, subtotal, subtotalWithTax, itemSum)
            return withoutMisreadLeadingDigit(total, others)
        }
        // Without a total row, a figure is still vouched for when one worked out another way agrees
        cashPaid?.let { return Total(it, TotalCheck(corroborated = it in payments + listOfNotNull(subtotalWithTax, itemSum))) }
        // What the card / QR row says was charged, when the total row itself was not read
        payments.firstOrNull()?.let { return Total(it, TotalCheck(corroborated = it in listOfNotNull(subtotalWithTax, itemSum))) }
        subtotalWithTax?.let { return Total(it, TotalCheck(corroborated = it in payments + listOfNotNull(itemSum))) }
        itemSum?.let { return Total(it, TotalCheck(corroborated = it in payments)) }

        // Priority 6: Fallback - Maximum reasonable amount excluding noise (never a year, time or phone number)
        var maxAmount = 0L
        for (line in lines) {
            if (EXCLUDE_KEYWORDS.any { line.replace(Regex("\\s"), "").contains(it) }) continue
            for (a in amountsIn(line)) {
                if (a.value in 1..2_000_000 && a.value > maxAmount) maxAmount = a.value
            }
        }
        return Total(maxAmount)
    }

    /** Amounts of the total rows by row index, tax-inclusive labels first, one per row. */
    private fun labeledTotals(lines: List<String>): Map<Int, Long> {
        val totals = LinkedHashMap<Int, Long>()
        for (keywords in listOf(TAX_INCLUSIVE_TOTAL_KEYWORDS, TOTAL_KEYWORDS)) {
            for (i in lines.indices) {
                if (i in totals) continue
                val keywordEnd = keywords.firstNotNullOfOrNull { kw -> totalKeywordEnd(lines[i], kw) } ?: continue
                amountForLabel(lines, i, keywordEnd)?.let { totals[i] = it }
            }
        }
        return totals
    }

    /**
     * ML Kit reads the yen sign in front of the large-font total as a digit now and then
     * (¥1,969 → 71,969 / 11,969). [total] stands when anything else on the receipt agrees with it;
     * when [others] (another total row, the card / e-money line, cash paid − change, subtotal (+ tax),
     * the items) agree with it minus its first digit instead, that is the total.
     */
    private fun withoutMisreadLeadingDigit(total: Long, others: List<Long>): Total {
        if (total in others) return Total(total, TotalCheck(corroborated = true))
        if (total < 10) return Total(total)
        var magnitude = 1L
        while (magnitude * 10 <= total) magnitude *= 10
        val trimmed = total % magnitude
        return if (trimmed > 0 && trimmed in others) {
            Total(trimmed, TotalCheck(corroborated = true, asRead = total))
        } else {
            Total(total)
        }
    }

    private fun subtotal(lines: List<String>): Long? {
        for (i in lines.indices) {
            val keywordEnd = findKeyword(lines[i], "小計") ?: continue
            if (EXCLUDE_KEYWORDS.any { lines[i].contains(it) }) continue
            amountForLabel(lines, i, keywordEnd)?.let { return it }
        }
        return null
    }

    /** What the card / e-money / QR payment row says was charged. */
    private fun paymentAmounts(lines: List<String>, skip: Set<Int>): List<Long> = lines.indices.mapNotNull { i ->
        if (i in skip || EXCLUDE_KEYWORDS.any { lines[i].contains(it) }) return@mapNotNull null
        val end = PAYMENT_KEYWORDS.firstNotNullOfOrNull { findKeyword(lines[i], it) } ?: return@mapNotNull null
        amountForLabel(lines, i, end)
    }

    /** End of [keyword] in [line] when it labels the total, i.e. not 外税合計 / 値引合計 / 合計点数. */
    private fun totalKeywordEnd(line: String, keyword: String): Int? {
        val end = findKeyword(line, keyword) ?: return null
        val labelPart = line.substring(0, end).replace(Regex("\\s"), "")
        if (EXCLUDE_KEYWORDS.any { labelPart.contains(it) }) return null
        if (Regex("""^\s*(点数|点|対象)""").containsMatchIn(line.substring(end))) return null
        return end
    }

    /**
     * The amount a label row stands for: the first amount after the label on the same row,
     * else the next row (when it is not another label), else a price-only row just above
     * (the big-font total printed slightly higher than its label).
     */
    private fun amountForLabel(lines: List<String>, index: Int, labelEnd: Int): Long? {
        amountsIn(lines[index]).firstOrNull { it.start >= labelEnd && it.value > 0 }?.let { return it.value }

        lines.getOrNull(index + 1)?.let { next ->
            val isOtherLabel = EXCLUDE_KEYWORDS.any { next.contains(it) } || next.contains("小計") ||
                TOTAL_KEYWORDS.any { findKeyword(next, it) != null }
            if (!isOtherLabel) amountsIn(next).lastOrNull { it.value > 0 }?.let { return it.value }
        }

        lines.getOrNull(index - 1)?.let { prev ->
            if (isPriceOnly(prev)) amountsIn(prev).lastOrNull { it.value > 0 }?.let { return it.value }
        }
        return null
    }

    private fun labeledAmount(lines: List<String>, keywords: List<String>): Long? {
        for (i in lines.indices) {
            val end = keywords.firstNotNullOfOrNull { findKeyword(lines[i], it) } ?: continue
            amountForLabel(lines, i, end)?.let { return it }
        }
        return null
    }

    private fun extractTaxAmount(lines: List<String>): Long? {
        val taxKeywords = listOf("外税", "消費税", "税等", "税額")
        val taxExclude = listOf("対象", "内税", "内消費税", "非課税", "税抜")
        for (i in lines.indices) {
            val normalized = lines[i].replace("\\s".toRegex(), "")
            if (taxKeywords.any { normalized.contains(it) } && !taxExclude.any { normalized.contains(it) }) {
                val end = taxKeywords.firstNotNullOfOrNull { findKeyword(lines[i], it) } ?: 0
                val amt = amountForLabel(lines, i, end)
                if (amt != null && amt > 0) return amt
            }
        }
        return null
    }

    /** Amounts in the row, skipping dates, times, phone and register numbers. */
    private fun amountsIn(line: String): List<Amount> {
        val masked = NON_AMOUNT_SPANS.replace(line) { " ".repeat(it.value.length) }
        return AMOUNT_REGEX.findAll(masked).mapNotNull { m ->
            m.groupValues[1].replace(",", "").toLongOrNull()?.let { Amount(it, m.range.first) }
        }.toList()
    }

    private fun isPriceOnly(line: String): Boolean =
        amountsIn(line).isNotEmpty() && line.replace(Regex("""[0-9,¥\\円\s*※軽]"""), "").isEmpty()

    /** End index of [keyword] in [line], allowing OCR-inserted spaces between its characters ("合 計"). */
    private fun findKeyword(line: String, keyword: String): Int? {
        val pattern = KEYWORD_PATTERNS.getOrPut(keyword) {
            Regex(keyword.map { Regex.escape(it.toString()) }.joinToString("""\s*"""))
        }
        return pattern.find(line)?.range?.last?.plus(1)
    }

    internal fun suggestCategory(storeName: String, fullText: String): Long? {
        val target = "$storeName $fullText".lowercase()

        // 1: 食費
        val foodKeywords = listOf(
            "セブン", "ローソン", "ファミマ", "ファミリーマート", "ミニストップ", "デイリーヤマザキ",
            "イオン", "ライフ", "西友", "業務スーパー", "成城石井", "まいばすけっと", "サミット", "マルエツ",
            "スーパー", "食品", "食料品", "カフェ", "スタバ", "スターバックス", "ドトール", "タリーズ",
            "マクドナルド", "モスバーガー", "ガスト", "サイゼリヤ", "すき家", "吉野家", "松屋",
            "レストラン", "食堂", "ベーカリー", "パン", "惣菜", "弁当", "居酒屋", "焼肉", "ラーメン"
        )
        if (foodKeywords.any { target.contains(it.lowercase()) }) return 1L

        // 2: 日用品
        val dailyKeywords = listOf(
            "マツモトキヨシ", "マツキヨ", "ウエルシア", "ツルハ", "サンドラッグ", "スギ薬局", "ココカラファイン",
            "ダイソー", "セリア", "キャンドゥ", "無印良品", "ドン・キホーテ", "ドンキ", "ニトリ",
            "カインズ", "コーナン", "島忠", "ホームセンター", "ドラッグストア"
        )
        if (dailyKeywords.any { target.contains(it.lowercase()) }) return 2L

        // 3: 交通費
        val transportKeywords = listOf(
            "jr", "メトロ", "地下鉄", "suica", "pasmo", "icoca", "タクシー", "バス", "鉄道",
            "ガソリン", "eneos", "出光", "コスモ石油", "タイムズ", "駐車場", "パーキング"
        )
        if (transportKeywords.any { target.contains(it.lowercase()) }) return 3L

        // 7: 交際・娯楽費
        val entertainmentKeywords = listOf(
            "toho", "映画", "シネマ", "カラオケ", "ラウンドワン", "ボウリング", "遊園地",
            "書店", "本屋", "紀伊國屋", "丸善", "tsutaya", "ブックオフ", "アニメイト"
        )
        if (entertainmentKeywords.any { target.contains(it.lowercase()) }) return 7L

        // 8: 医療・ヘルスケア
        val medicalKeywords = listOf(
            "クリニック", "病院", "医院", "調剤薬局", "処方", "歯科", "眼科", "皮膚科", "内科"
        )
        if (medicalKeywords.any { target.contains(it.lowercase()) }) return 8L

        return 1L // Default to 食費 if unknown
    }
}
