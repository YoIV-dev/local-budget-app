package com.localbudget.app.domain

import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.ocr.ReceiptItemParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptItemParserTest {

    @Test
    fun priceOnSameRow() {
        val items = ReceiptItemParser.parse(
            """
            ｷｬﾍﾞﾂ                   ￥１９８
            牛乳 1L ¥228 ※
            おにぎり 鮭 150
            ゴミ袋 45L
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceiptLineItem("ｷｬﾍﾞﾂ", price = 198),
                ReceiptLineItem("牛乳 1L", price = 228),
                ReceiptLineItem("おにぎり 鮭", price = 150),
                ReceiptLineItem("ゴミ袋 45L", price = null)
            ),
            items
        )
    }

    @Test
    fun quantityRowBelowItem() {
        val items = ReceiptItemParser.parse(
            """
            牛乳 1L ¥456
              2個 × 単228
            にんじん
              @98 × 2
            ﾖｰｸﾞﾙﾄ
              (3点 x 単価100)   ¥300
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceiptLineItem("牛乳 1L", price = 456, quantity = 2, unitPrice = 228),
                ReceiptLineItem("にんじん", price = 196, quantity = 2, unitPrice = 98),
                ReceiptLineItem("ﾖｰｸﾞﾙﾄ", price = 300, quantity = 3, unitPrice = 100)
            ),
            items
        )
    }

    @Test
    fun quantityOnItemRow() {
        val items = ReceiptItemParser.parse("ﾀﾏｺﾞ 2個×198 ¥396")

        assertEquals(listOf(ReceiptLineItem("ﾀﾏｺﾞ", price = 396, quantity = 2, unitPrice = 198)), items)
    }

    @Test
    fun discountRowAppliesToItemAbove() {
        val items = ReceiptItemParser.parse(
            """
            国産鶏もも肉 ¥612
              値引 -100
            ｸｰﾎﾟﾝ ▲50
            """.trimIndent()
        )

        assertEquals(1, items.size)
        assertEquals(612L, items[0].price)
        assertEquals(150L, items[0].discount)
        assertEquals(462L, items[0].netPrice)
    }

    @Test
    fun splitNameAndPriceRunsArePairedInOrder() {
        // ML Kit block order without row reconstruction; the unpriced store name is dropped as header
        val items = ReceiptItemParser.parse(
            """
            ﾏﾙｴﾂ 新宿店
            ｷｬﾍﾞﾂ
            ﾓﾔｼ
            ¥198
            ¥38
            合計
            ¥236
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceiptLineItem("ｷｬﾍﾞﾂ", price = 198),
                ReceiptLineItem("ﾓﾔｼ", price = 38)
            ),
            items
        )
    }

    @Test
    fun stopsAtTotalAndSkipsStructureRows() {
        val items = ReceiptItemParser.parse(
            """
            2026年10月01日(水) 19:05
            ﾚｼﾞ02 責No.123
            ﾓﾔｼ ¥38
            内消費税 ¥2
            小計 ¥38
            ﾎﾟｲﾝﾄ ¥1
            ﾓﾔｼ ¥38
            """.trimIndent()
        )

        assertEquals(
            listOf(ReceiptLineItem("ﾓﾔｼ", price = 38)),
            items
        )
    }

    @Test
    fun headerRowsAreNotItems() {
        val items = ReceiptItemParser.parse(
            """
            イオン 幕張新都心店
            千葉市美浜区豊砂1-1
            043-000-1111
            2026/09/20 18:42 No.0123
            国産牛こま切れ ¥980
            ゴミ袋 45L
            """.trimIndent()
        )

        assertEquals(
            listOf(ReceiptLineItem("国産牛こま切れ", price = 980), ReceiptLineItem("ゴミ袋 45L")),
            items
        )
    }

    @Test
    fun misreadPricesAreRepaired() {
        val items = ReceiptItemParser.parse(
            """
            ｷｬﾍﾞﾂ Y198
            ﾄﾏﾄ ¥l98
            豚ﾊﾞﾗ ¥1.280
            合計 ¥1,676
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceiptLineItem("ｷｬﾍﾞﾂ", price = 198),
                ReceiptLineItem("ﾄﾏﾄ", price = 198),
                ReceiptLineItem("豚ﾊﾞﾗ", price = 1280)
            ),
            items
        )
    }

    @Test
    fun productCodesAndTaxMarksAreNotPartOfTheName() {
        val items = ReceiptItemParser.parse(
            """
            4901234567890 鶏もも肉 ¥598
            001 玉ねぎ ¥98
            にんじん 軽 ※198
            豆腐 *88
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ReceiptLineItem("鶏もも肉", price = 598),
                ReceiptLineItem("玉ねぎ", price = 98),
                ReceiptLineItem("にんじん", price = 198),
                ReceiptLineItem("豆腐", price = 88)
            ),
            items
        )
    }

    @Test
    fun billRowIsNotAnItem() {
        val items = ReceiptItemParser.parse(
            """
            枝豆 ¥380
            お会計 ¥3,280
            """.trimIndent()
        )

        assertEquals(listOf(ReceiptLineItem("枝豆", price = 380)), items)
    }

    @Test
    fun katakanaMisreadsInNamesAreRepaired() {
        val items = ReceiptItemParser.parse(
            """
            ﾎ゜ﾃﾄﾁｯﾌ゜ｽ ¥148
            国産豚ﾛ一ｽ ¥498
            二ンジン ¥98
            """.trimIndent()
        )

        assertEquals(listOf("ﾎﾟﾃﾄﾁｯﾌﾟｽ", "国産豚ﾛｰｽ", "ニンジン"), items.map { it.name })
        assertEquals(listOf(148L, 498L, 98L), items.map { it.price })
    }

    @Test
    fun paymentRowsAndNotesAreNotItems() {
        val items = ReceiptItemParser.parse(
            """
            ©手巻きソーセージドー ¥180軽
            8%対象」 4180)
            QU1CPay支払. 180
            「軽」は軽減税率対象商品です。
            ホイント ************1929
            """.trimIndent()
        )

        assertEquals(listOf(ReceiptLineItem("手巻きソーセージドー", price = 180)), items)
    }

    @Test
    fun marksBeforeTheNameAreNotPartOfIt() {
        val items = ReceiptItemParser.parse(
            """
            ©手巻きソーセージドー ¥180車軽
            ◎ﾄﾏﾄ ¥198
            ● 牛乳 1L ¥228
            ③だしを味わう鍋焼うど ¥370
            """.trimIndent()
        )

        assertEquals(listOf("手巻きソーセージドー", "ﾄﾏﾄ", "牛乳 1L", "だしを味わう鍋焼うど"), items.map { it.name })
        assertEquals(listOf(180L, 198L, 228L, 370L), items.map { it.price })
    }

    @Test
    fun flagLetterSharedBySeveralItemsIsDropped() {
        // OK prints F before reduced-tax (food) items
        val items = ReceiptItemParser.parse(
            """
            Fピーマン ¥100
            Fミルクココア200g ¥399
            サントリーサピールレモン7%R ¥135
            Tシャツ ¥990
            """.trimIndent()
        )

        // サピール is also put back to ビール (ItemNameCorrector)
        assertEquals(listOf("ピーマン", "ミルクココア200g", "サントリーサビールレモン7%R", "Tシャツ"), items.map { it.name })
    }

    @Test
    fun aSingleLetterBeforeANameIsKept() {
        val items = ReceiptItemParser.parse("Tシャツ ¥990\nLサイズ卵 ¥258")

        assertEquals(listOf("Tシャツ", "Lサイズ卵"), items.map { it.name })
    }
}
