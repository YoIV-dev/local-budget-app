package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptParser
import com.localbudget.app.domain.ocr.TotalCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TotalCheckTest {

    @Test
    fun totalTheRestOfTheReceiptAgreesWithIsNotPointedOut() {
        val parsed = ReceiptParser.parse(
            """
            サミット 中野店
            2026/10/02
            ﾄﾏﾄ ¥198
            合計 ¥198
            お預り ¥1,000
            お釣 ¥802
            """.trimIndent()
        )

        assertTrue(parsed.totalCheck.corroborated)
        // Even a faint row is trusted once another figure agrees
        assertNull(parsed.totalCheck.copy(confidence = 0.3f).warning(parsed.amount))
    }

    @Test
    fun droppedLeadingDigitSaysWhatWasRead() {
        val parsed = ReceiptParser.parse(
            """
            ローソン 新宿店
            2026/10/02
            おにぎり ¥150
            合計 11,280
            クレジット ¥1,280
            """.trimIndent()
        )

        assertEquals(1280L, parsed.amount)
        assertEquals(11280L, parsed.totalCheck.asRead)
        assertEquals(
            "合計を ¥11,280 と読みましたが、ほかの金額と合う ¥1,280 にしました。レシートと見比べてください",
            parsed.amountWarning
        )
    }

    @Test
    fun uncorroboratedTotalIsPointedOutUnlessReadClearly() {
        // The only item is unpriced, so nothing can be compared with the total
        val parsed = ReceiptParser.parse(
            """
            喫茶 さくら
            2026/10/02
            ブレンド
            合計 ¥520
            """.trimIndent()
        )

        assertFalse(parsed.totalCheck.corroborated)
        assertNotNull(parsed.totalCheck.copy(confidence = 0.55f).warning(parsed.amount))
        assertNotNull(parsed.totalCheck.copy(confidence = null).warning(parsed.amount))
        assertNull(parsed.totalCheck.copy(confidence = 0.95f).warning(parsed.amount))
    }

    @Test
    fun missingTotalAsksForIt() {
        assertEquals("合計を読み取れませんでした。金額を入力してください", TotalCheck().warning(0))
    }

    @Test
    fun confidenceComesFromTheLinesShowingTheTotal() {
        val lines = listOf(
            OcrLine("ﾄﾏﾄ", 0, 0, 100, 20, confidence = 0.9f),
            OcrLine("Y1.969", 200, 0, 300, 20, confidence = 0.95f), // another amount: an item at 1,969 too
            OcrLine("合 計", 0, 100, 100, 120, confidence = 0.7f),
            OcrLine("¥1,969", 200, 100, 300, 120, confidence = 0.6f),
            OcrLine("¥19,690", 200, 200, 300, 220, confidence = 0.1f) // contains the digits, not the amount
        )

        assertEquals(0.6f, TotalCheck.confidenceOf(lines, 1969)!!, 0.0001f)
        assertNull(TotalCheck.confidenceOf(lines, 500))
        assertNull(TotalCheck.confidenceOf(listOf(OcrLine("¥1,969", 0, 0, 10, 10)), 1969))
    }
}
