package com.localbudget.app.domain

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.statement.ParsedStatement
import com.localbudget.app.domain.statement.StatementImportPlanner
import com.localbudget.app.domain.statement.StatementMatch
import com.localbudget.app.domain.statement.StatementRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatementImportPlannerTest {

    private val categories = listOf(
        Category(id = 1, name = "食費", iconName = "Restaurant", colorHex = "#FF5722"),
        Category(id = 2, name = "日用品", iconName = "ShoppingCart", colorHex = "#4CAF50"),
        Category(id = 3, name = "交通費", iconName = "DirectionsSubway", colorHex = "#2196F3"),
        Category(id = 5, name = "水道・光熱費", iconName = "Bolt", colorHex = "#FF9800"),
        Category(id = 6, name = "通信費", iconName = "Wifi", colorHex = "#00BCD4"),
        Category(id = 7, name = "交際・娯楽費", iconName = "SportsEsports", colorHex = "#E91E63"),
        Category(id = 8, name = "医療・ヘルスケア", iconName = "LocalHospital", colorHex = "#9C27B0"),
        Category(id = 9, name = "その他", iconName = "MoreHoriz", colorHex = "#607D8B")
    )
    private val day = LocalDate.of(2026, 10, 5)

    private fun expense(amount: Long, store: String, source: SourceType, categoryId: Long = 1, date: LocalDate = day) =
        Expense(id = amount, date = date, amount = amount, storeName = store, categoryId = categoryId, sourceType = source)

    private fun plan(rows: List<StatementRow>, existing: List<Expense> = emptyList(), refunds: List<StatementRow> = emptyList()) =
        StatementImportPlanner.plan(ParsedStatement(rows, refunds = refunds), existing, categories)

    @Test
    fun rowMatchingAReceiptIsLeftOutSoTheReceiptIsKept() {
        val receipt = expense(1280, "イオン 品川店", SourceType.RECEIPT_OCR)

        val candidate = plan(listOf(StatementRow(day, "イオンリテール", 1280)), listOf(receipt)).candidates.single()

        assertEquals(StatementMatch.RECEIPT, candidate.match)
        assertFalse(candidate.selected)
    }

    @Test
    fun reimportingTheSameStatementFindsEveryRowAlreadyImported() {
        val rows = listOf(StatementRow(day, "ローソン", 398), StatementRow(day, "ローソン", 398))
        val imported = rows.map { expense(it.amount, it.storeName, SourceType.CARD_CSV) }

        val candidates = plan(rows, imported).candidates

        assertTrue(candidates.all { it.match == StatementMatch.ALREADY_IMPORTED && !it.selected })
    }

    @Test
    fun eachSavedExpenseAccountsForOneRowOnly() {
        // Two coffees on the same day, one of them already scanned from its receipt
        val rows = listOf(StatementRow(day, "スターバックス", 450), StatementRow(day, "スターバックス", 450))
        val receipt = expense(450, "スターバックス", SourceType.RECEIPT_OCR)

        val candidates = plan(rows, listOf(receipt)).candidates

        assertEquals(listOf(StatementMatch.RECEIPT, StatementMatch.NONE), candidates.map { it.match })
        assertEquals(listOf(false, true), candidates.map { it.selected })
    }

    @Test
    fun handEnteredExpenseOfTheSameDayAndAmountIsFlagged() {
        val manual = expense(1490, "Netflix", SourceType.MANUAL, categoryId = 7)

        val candidate = plan(listOf(StatementRow(day, "NETFLIX.COM", 1490)), listOf(manual)).candidates.single()

        assertEquals(StatementMatch.OTHER, candidate.match)
        assertFalse(candidate.selected)
    }

    @Test
    fun differentDayOrAmountIsNotADuplicate() {
        val receipt = expense(1280, "イオン", SourceType.RECEIPT_OCR)
        val rows = listOf(StatementRow(day.plusDays(1), "イオン", 1280), StatementRow(day, "イオン", 1281))

        val candidates = plan(rows, listOf(receipt)).candidates

        assertTrue(candidates.all { it.match == StatementMatch.NONE && it.selected })
    }

    @Test
    fun categoryIsLearnedFromAnotherBranchOfTheSameChain() {
        val past = listOf(
            expense(800, "ﾏﾂﾓﾄｷﾖｼ 新宿店", SourceType.CARD_CSV, categoryId = 8, date = day.minusDays(30)),
            expense(900, "マツモトキヨシ 渋谷店", SourceType.MANUAL, categoryId = 1, date = day.minusDays(10))
        )

        val candidate = plan(listOf(StatementRow(day, "マツモトキヨシチェーンえきマチ1丁目唐津", 1500)), past).candidates.single()

        // The most recent choice wins
        assertEquals(1L, candidate.categoryId)
    }

    @Test
    fun categoryFromTheChainDictionary() {
        val expected = mapOf(
            "フアミリ-マ-トコガネイホウセイダイカ" to 1L,
            "セブン-イレブン小金井東町4丁目店" to 1L,
            "イオンフードスタイル" to 1L,
            "ゆめマート熊本 長崎駅店" to 1L,
            "丸亀製麺 羽田空港第2ビル店" to 1L,
            "ミスタードーナツ えきマチ姪浜店" to 1L,
            "長崎街道かもめ市場" to 1L,
            "サンドラッグ" to 2L,
            "ダイレックス" to 2L,
            "AMAZON.CO.JP" to 2L,
            "TOYOTA SHARE" to 3L,
            "タイムズカー" to 3L,
            "Charichari" to 3L,
            "スマートEX〔JR西日本〕" to 3L,
            "楽天トラベル高速バス・バスツアー" to 3L,
            "東京電力エナジーパートナー" to 5L,
            "povoご利用料金" to 6L,
            "GOOGLE *Google One" to 6L,
            "Amazonプライム会費" to 7L,
            "SPOTIFY JAPAN" to 7L,
            "AGODA.COM HARBOUR CITY," to 7L,
            "佐世保銘品蔵" to 7L,
            "門司港レトロ展望室" to 7L,
            "ひがこ南口皮ふ科" to 8L,
            "ABC SHOP" to 9L,
            "BANANA REPUBLIC" to 9L
        )

        val candidates = plan(expected.keys.map { StatementRow(day, it, 100) }).candidates

        assertEquals(expected, candidates.associate { it.row.storeName to it.categoryId })
    }

    @Test
    fun refundOfAPurchaseFromAnEarlierStatementRemovesThatImport() {
        val earlier = expense(5950, "SKYTICKET", SourceType.CARD_CSV, categoryId = 3, date = LocalDate.of(2026, 7, 20))
        val refund = StatementRow(LocalDate.of(2026, 8, 28), "ＳＫＹＴＩＣＫＥＴ", 5950)

        val plan = plan(listOf(StatementRow(LocalDate.of(2026, 8, 20), "ローソン", 300)), listOf(earlier), listOf(refund))

        assertEquals(listOf(earlier), plan.refundTargets.map { it.expense })
        assertEquals(0, plan.unmatchedRefunds)
    }

    @Test
    fun refundNeverRemovesAPurchaseDatedWithinTheStatement() {
        // Importing the same statement again: the purchase on 8/20 is this statement's own
        val sameStatement = expense(5950, "SKYTICKET", SourceType.CARD_CSV, date = LocalDate.of(2026, 8, 20))
        val refund = StatementRow(LocalDate.of(2026, 8, 28), "SKYTICKET", 5950)

        val plan = plan(listOf(StatementRow(LocalDate.of(2026, 8, 20), "ローソン", 300)), listOf(sameStatement), listOf(refund))

        assertTrue(plan.refundTargets.isEmpty())
        assertEquals(1, plan.unmatchedRefunds)
    }

    @Test
    fun importedExpenseIsMarkedAsFromTheCardStatement() {
        val expense = plan(listOf(StatementRow(day, "ローソン", 398))).candidates.single().toExpense()

        assertEquals(SourceType.CARD_CSV, expense.sourceType)
        assertEquals(398L, expense.amount)
        assertEquals(day, expense.date)
    }
}
