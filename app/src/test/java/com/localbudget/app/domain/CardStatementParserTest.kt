package com.localbudget.app.domain

import com.localbudget.app.domain.statement.CardStatementParser
import com.localbudget.app.domain.statement.StatementRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CardStatementParserTest {

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun rakutenCardColumnsAreFoundByName() {
        val csv = """
            "利用日","利用店名・商品名","利用者","支払方法","利用金額","支払手数料","支払総額","11月支払金額","12月繰越残高","新規サイン"
            "2026/10/01","セブン－イレブン　渋谷店","本人","1回払い","550","0","550","550","0","*"
            "2026/10/03","ＡＭＡＺＯＮ．ＣＯ．ＪＰ","本人","分割","36,000","1,200","37,200","3,100","34,100",""
        """.trimIndent()

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(
            listOf(
                StatementRow(LocalDate.of(2026, 10, 1), "セブン-イレブン 渋谷店", 550),
                // What was spent, not this month's instalment
                StatementRow(LocalDate.of(2026, 10, 3), "AMAZON.CO.JP", 36000)
            ),
            parsed.rows
        )
    }

    @Test
    fun jcbHeaderAfterCardDetailsIsFound() {
        val csv = """
            "","","今回のお支払日","2026/10/13"
            "","","今回のお支払金額合計(￥)","93,743"
            ""
            "ご利用者","カテゴリ","ご利用日","ご利用先など","ご利用金額(￥)","支払区分","今回回数","訂正サイン","お支払い金額(￥)","国内／海外","摘要","備考"
            "","≪ショッピング取組（国内）≫","2026/09/15","ﾄｳｷﾖｳﾃﾞﾝﾘﾖｸ","8,420","１回","","","8,420","国内","","* 2"
        """.trimIndent()

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(listOf(StatementRow(LocalDate.of(2026, 9, 15), "トウキヨウデンリヨク", 8420)), parsed.rows)
    }

    @Test
    fun cancelledPurchaseAndItsNegativeAreBothLeftOut() {
        val csv = """
            "ご利用者","カテゴリ","ご利用日","ご利用先など","ご利用金額(￥)","支払区分","今回回数","訂正サイン","お支払い金額(￥)"
            "","","2026/09/02","サンドラッグ","3,623","１回","","取消",""
            "","","2026/09/02","サンドラッグ","2,805","１回","","","2,805"
            "","","2026/09/02","サンドラッグ","-3,623","１回","","取消",""
        """.trimIndent()

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(listOf(StatementRow(LocalDate.of(2026, 9, 2), "サンドラッグ", 2805)), parsed.rows)
        assertEquals(2, parsed.cancelledCount)
        assertTrue(parsed.refunds.isEmpty())
    }

    @Test
    fun refundTakesOffThePurchaseItPaysBack() {
        val csv = """
            "ご利用日","ご利用先など","ご利用金額(￥)","訂正サイン"
            "2026/08/28","ＴＯＹＯＴＡ　ＳＨＡＲＥ","660",""
            "2026/09/08","ＴＯＹＯＴＡ　ＳＨＡＲＥ","9,130",""
            "2026/09/15","ＴＯＹＯＴＡ　ＳＨＡＲＥ","-9,130",""
            "2026/08/28","ＳＫＹＴＩＣＫＥＴ","-5,950",""
        """.trimIndent()

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(listOf(StatementRow(LocalDate.of(2026, 8, 28), "TOYOTA SHARE", 660)), parsed.rows)
        assertEquals(2, parsed.cancelledCount)
        // Its purchase was on an earlier statement
        assertEquals(listOf(StatementRow(LocalDate.of(2026, 8, 28), "SKYTICKET", 5950)), parsed.refunds)
    }

    @Test
    fun vpassWithoutHeaderReadsDateStoreAndAmountByPosition() {
        val csv = """
            山田　太郎　様,4980-****-****-****,三井住友カード
            2026/10/05,ローソン,398,１,１,398,
            2026/10/06,"ＮＥＴＦＬＩＸ, ＩＮＣ.",1490,１,１,1490,
            ,,,,,1888,
        """.trimIndent()

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(
            listOf(
                StatementRow(LocalDate.of(2026, 10, 5), "ローソン", 398),
                StatementRow(LocalDate.of(2026, 10, 6), "NETFLIX, INC.", 1490)
            ),
            parsed.rows
        )
    }

    @Test
    fun bomAndCrLfAreHandled() {
        val csv = "\uFEFF日付,摘要,金額\r\n2026-10-02,マツモトキヨシ,1280\r\n"

        val parsed = CardStatementParser.parse(csv, today)

        assertEquals(listOf(StatementRow(LocalDate.of(2026, 10, 2), "マツモトキヨシ", 1280)), parsed.rows)
    }

    @Test
    fun datesInTheFormsStatementsUse() {
        assertEquals(LocalDate.of(2026, 10, 1), CardStatementParser.parseDate("2026/10/1", today))
        assertEquals(LocalDate.of(2026, 10, 1), CardStatementParser.parseDate("２０２６年１０月１日", today))
        assertEquals(LocalDate.of(2026, 10, 1), CardStatementParser.parseDate("26/10/01", today))
        assertEquals(LocalDate.of(2026, 10, 1), CardStatementParser.parseDate("20261001", today))
        // Without a year: a day after today was last year
        assertEquals(LocalDate.of(2025, 12, 30), CardStatementParser.parseDate("12/30", today))
        assertNull(CardStatementParser.parseDate("2026/13/01", today))
        assertNull(CardStatementParser.parseDate("ローソン", today))
    }

    @Test
    fun amountsWithSeparatorsAndSigns() {
        assertEquals(12345L, CardStatementParser.parseAmount("¥12,345"))
        assertEquals(12345L, CardStatementParser.parseAmount("１２，３４５円"))
        assertEquals(-480L, CardStatementParser.parseAmount("▲480"))
        assertEquals(1L, CardStatementParser.parseAmount("１"))
        assertNull(CardStatementParser.parseAmount(""))
        assertNull(CardStatementParser.parseAmount("1回払い"))
    }

    @Test
    fun fileWithoutPurchasesGivesNoRows() {
        assertTrue(CardStatementParser.parse("お知らせ\nご利用ありがとうございます", today).rows.isEmpty())
    }
}
