package com.localbudget.app.domain

import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.ocr.ReceiptItemParser
import com.localbudget.app.domain.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** Store names and receipt lines as earlier versions saved them on a Pixel 9a. */
class SavedScanTidyTest {

    @Test
    fun storeNamesBecomeTheChainAlone() {
        assertEquals("マルエツ", ReceiptParser.tidyStoreName("● maruetsu"))
        assertEquals("マルエツ", ReceiptParser.tidyStoreName("●4maruetsu"))
        assertEquals("丸善", ReceiptParser.tidyStoreName("MMARUZEN"))
        assertEquals("オーケー", ReceiptParser.tidyStoreName("オーケー 梶野町店"))
    }

    @Test
    fun storeNamesWithNothingToFixStayAsSaved() {
        // Typed by the user, or a branch with no chain to tell
        for (name in listOf("ファミマ", "オーケー", "小金井法政大学前店", "大衆酒場 さくら")) {
            assertEquals(name, ReceiptParser.tidyStoreName(name))
        }
    }

    @Test
    fun paymentRowsAndNotesSavedAsLinesAreDropped() {
        val saved = listOf(
            ReceiptLineItem("©手巻きソーセージドー", price = 180),
            ReceiptLineItem("8%対象」 4180)"),
            ReceiptLineItem("QU1CPay支払.", price = 180),
            ReceiptLineItem("「軽」は軽減税率対象商品です。"),
            ReceiptLineItem("各種キャンペーンの詳細は、")
        )

        assertEquals(
            listOf(ReceiptLineItem("手巻きソーセージドー", price = 180), null, null, null, null),
            ReceiptItemParser.tidySaved(saved)
        )
    }

    @Test
    fun savedNamesAreCorrectedAndPricesKept() {
        val saved = listOf(
            ReceiptLineItem("Fメイジフブけリアヨーグト", price = 156),
            ReceiptLineItem("Fシマタヤトケイタイラーメンショウュ", price = 287, discount = 8),
            ReceiptLineItem("Fピーマン", price = 120), // price corrected by the user
            ReceiptLineItem("Fニイカタコシイブキ 5kg", price = 2470)
        )

        assertEquals(
            listOf(
                ReceiptLineItem("明治ブルガリアヨーグルト", price = 156),
                ReceiptLineItem("シマダヤトケイタイラーメンショウユ", price = 287, discount = 8),
                ReceiptLineItem("ピーマン", price = 120),
                ReceiptLineItem("ニイガタコシイブキ 5kg", price = 2470)
            ),
            ReceiptItemParser.tidySaved(saved)
        )
    }
}
