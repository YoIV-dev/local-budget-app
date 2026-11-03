package com.localbudget.app.domain.statement

import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.time.DateTimeException
import java.time.LocalDate

/** One purchase in a card statement. */
data class StatementRow(
    val date: LocalDate,
    val storeName: String,
    val amount: Long
)

data class ParsedStatement(
    /** The purchases that still stand once cancellations and the refunds in this file are taken off. */
    val rows: List<StatementRow>,
    /** Rows left out as cancelled: those marked 取消 and each refund with the purchase it pays back. */
    val cancelledCount: Int = 0,
    /** Refunds, as positive amounts, of purchases that are not in this file (an earlier statement's). */
    val refunds: List<StatementRow> = emptyList()
)

/**
 * Reads the purchases from a credit-card statement CSV. Most issuers (楽天カード, JCB, セゾン, イオン, …)
 * put a header row somewhere after a few lines of card details, so the date, store and amount columns
 * are found by name. Without such a header (三井住友カード / Vpass), each row is read as
 * "date, store, …, amount".
 */
object CardStatementParser {

    fun parse(text: String, today: LocalDate = LocalDate.now()): ParsedStatement {
        val records = CsvRecords.parse(text.removePrefix("\uFEFF"))
        val headerIndex = records.indexOfFirst { columnsOf(it) != null }
        val parsed = if (headerIndex >= 0) {
            val columns = columnsOf(records[headerIndex])!!
            records.drop(headerIndex + 1).mapNotNull { record ->
                rowByColumns(record, columns, today)?.let { it to isCancelled(record) }
            }
        } else {
            records.mapNotNull { record -> rowByPosition(record, today)?.let { it to isCancelled(record) } }
        }
        // A cancelled purchase is listed twice, as itself and as its negative, both marked 取消
        val marked = parsed.count { (_, cancelled) -> cancelled }
        val standing = parsed.filterNot { (_, cancelled) -> cancelled }.map { (row, _) -> row }
        val purchases = standing.filter { it.amount > 0 }.toMutableList()
        val refunds = mutableListOf<StatementRow>()
        for (refund in standing.filter { it.amount < 0 }.map { it.copy(amount = -it.amount) }) {
            val paidBack = paidBackPurchase(purchases, refund)
            if (paidBack != null) purchases.remove(paidBack) else refunds.add(refund)
        }
        val offset = standing.count { it.amount < 0 } - refunds.size
        return ParsedStatement(rows = purchases, cancelledCount = marked + offset * 2, refunds = refunds)
    }

    /** The purchase [refund] pays back: same store and amount, the latest one on or before it. */
    private fun paidBackPurchase(purchases: List<StatementRow>, refund: StatementRow): StatementRow? {
        val store = JapaneseTextNormalizer.looseKey(refund.storeName)
        val same = purchases.filter { it.amount == refund.amount && JapaneseTextNormalizer.looseKey(it.storeName) == store }
        return same.filter { !it.date.isAfter(refund.date) }.maxByOrNull { it.date } ?: same.firstOrNull()
    }

    private fun isCancelled(record: List<String>): Boolean =
        record.any { JapaneseTextNormalizer.nfkc(it).trim() in CANCEL_MARKS }

    private data class Columns(val date: Int, val store: Int, val amount: Int)

    private fun columnsOf(record: List<String>): Columns? {
        val names = record.map { headerName(it) }
        val date = names.indexOfFirst { name -> DATE_HEADERS.any { name.contains(it) } || name == "日付" }
        val store = names.indexOfFirst { name -> STORE_HEADERS.any { name.contains(it) } }
        // 利用金額 is what was spent; 支払金額 and the like can be one instalment of it
        val amount = names.indexOfFirst { it.contains("利用金額") }.takeIf { it >= 0 }
            ?: names.indexOfFirst { name -> name.contains("金額") && AMOUNT_EXCLUDED.none { name.contains(it) } }
        if (date < 0 || store < 0 || amount < 0) return null
        return Columns(date, store, amount)
    }

    private fun rowByColumns(record: List<String>, columns: Columns, today: LocalDate): StatementRow? {
        val date = record.getOrNull(columns.date)?.let { parseDate(it, today) } ?: return null
        val amount = record.getOrNull(columns.amount)?.let { parseAmount(it) } ?: return null
        return StatementRow(date, storeNameOf(record.getOrNull(columns.store).orEmpty()), amount)
    }

    /** "2026/10/01,セブン－イレブン,550,１,１,550,": the first date, the text after it, then its amount. */
    private fun rowByPosition(record: List<String>, today: LocalDate): StatementRow? {
        val dateIndex = record.indexOfFirst { parseDate(it, today) != null }
        if (dateIndex < 0) return null
        val storeIndex = (dateIndex + 1 until record.size)
            .firstOrNull { record[it].isNotBlank() && parseAmount(record[it]) == null } ?: return null
        val amount = (storeIndex + 1 until record.size)
            .firstNotNullOfOrNull { parseAmount(record[it]) } ?: return null
        return StatementRow(parseDate(record[dateIndex], today)!!, storeNameOf(record[storeIndex]), amount)
    }

    private fun headerName(cell: String): String =
        JapaneseTextNormalizer.nfkc(cell).filterNot { it.isWhitespace() }

    /** Half-width katakana and full-width latin, common in statements, read as the app shows them elsewhere. */
    private fun storeNameOf(cell: String): String =
        JapaneseTextNormalizer.nfkc(cell).trim().replace(Regex("""\s+"""), " ").ifEmpty { "カード利用" }

    internal fun parseDate(cell: String, today: LocalDate): LocalDate? {
        val text = JapaneseTextNormalizer.nfkc(cell).trim()
        FULL_DATE.matchEntire(text)?.let { m ->
            val year = m.groupValues[1].toInt().let { if (it < 100) 2000 + it else it }
            return dateOf(year, m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        COMPACT_DATE.matchEntire(text)?.let { m ->
            return dateOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        MONTH_DAY.matchEntire(text)?.let { m ->
            // A statement covers past purchases: a month and day later than today were last year
            val date = dateOf(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) ?: return null
            return if (date.isAfter(today)) date.minusYears(1) else date
        }
        return null
    }

    internal fun parseAmount(cell: String): Long? {
        val text = JapaneseTextNormalizer.nfkc(cell).replace(Regex("""[\s,¥\\円]"""), "")
        val negative = text.startsWith("-") || text.startsWith("▲") || text.startsWith("△")
        val digits = if (negative) text.drop(1) else text
        if (digits.isEmpty() || !digits.all { it.isDigit() } || digits.length > 10) return null
        val value = digits.toLong()
        return if (negative) -value else value
    }

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day).takeIf { year in 2000..2100 }
    } catch (_: DateTimeException) {
        null
    }

    private val CANCEL_MARKS = setOf("取消", "取消し", "取り消し")
    private val DATE_HEADERS = listOf("利用日", "利用年月日", "取引日", "購入日", "売上日")
    private val STORE_HEADERS = listOf("利用店", "店名", "利用先", "加盟店", "摘要", "利用内容", "商品名", "内容")
    private val AMOUNT_EXCLUDED = listOf("手数料", "残高", "繰越")

    private val FULL_DATE = Regex("""(\d{2}|\d{4})[/\-.年](\d{1,2})[/\-.月](\d{1,2})日?""")
    private val COMPACT_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val MONTH_DAY = Regex("""(\d{1,2})[/月](\d{1,2})日?""")
}

/** RFC 4180 records: quoted fields may hold commas, doubled quotes and line breaks. */
internal object CsvRecords {
    fun parse(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endRecord() {
            fields.add(field.toString())
            field.clear()
            if (fields.any { it.isNotBlank() }) records.add(fields)
            fields = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> field.append(c)
                c == ',' -> { fields.add(field.toString()); field.clear() }
                c == '\r' -> { if (text.getOrNull(i + 1) == '\n') i++; endRecord() }
                c == '\n' -> endRecord()
                else -> field.append(c)
            }
            i++
        }
        endRecord()
        return records
    }
}
