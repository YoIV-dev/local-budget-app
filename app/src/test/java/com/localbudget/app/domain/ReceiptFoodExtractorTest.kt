package com.localbudget.app.domain

import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.model.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptFoodExtractorTest {

    @Test
    fun testExtractFoodItemsFromSupermarketReceipt() {
        val ocrText = """
            イオンモール 幕張新都心店
            電話 043-000-1111
            お買上票
            
            2026/09/20 18:42 レジ05 係012
            
            国産牛こま切れ           ¥980
            キャベツ 1玉             ¥198
            牛乳 1L                  ¥228
            プレーンヨーグルト       ¥158
            ゴミ袋 45L               ¥298
            
            小計                   ¥1,862
            外税対象額             ¥1,862
            消費税等                 ¥148
            合計                   ¥2,010
            現計                   ¥2,010
            お預り                 ¥3,000
            お釣り                   ¥990
        """.trimIndent()

        val date = LocalDate.of(2026, 9, 20)
        val extracted = ReceiptFoodExtractor.extract(ocrText, date)

        // 4 food items should be extracted (ゴミ袋 excluded, summary lines excluded)
        assertEquals(4, extracted.size)

        // Preserves verbatim names from receipt
        assertEquals("国産牛こま切れ", extracted[0].name)
        assertEquals(FoodCategory.MEAT, extracted[0].category)
        assertEquals(date, extracted[0].purchaseDate)

        assertEquals("キャベツ 1玉", extracted[1].name)
        assertEquals(FoodCategory.VEGETABLE, extracted[1].category)

        assertEquals("牛乳 1L", extracted[2].name)
        assertEquals(FoodCategory.DAIRY, extracted[2].category)

        assertEquals("プレーンヨーグルト", extracted[3].name)
        assertEquals(FoodCategory.DAIRY, extracted[3].category)
    }

    @Test
    fun testExtractFoodItemsConvenienceStore() {
        val ocrText = """
            セブン-イレブン 渋谷店
            
            おにぎり 鮭             ¥150
            緑茶 500ml              ¥160
            サンドイッチ             ¥320
            
            小計                    ¥630
            合計                    ¥680
        """.trimIndent()

        val date = LocalDate.of(2026, 9, 25)
        val extracted = ReceiptFoodExtractor.extract(ocrText, date)

        // "おにぎり 鮭" is extracted, but the longer keyword おにぎり wins over 鮭 → OTHER, not FISH
        assertTrue(extracted.any { it.name == "おにぎり 鮭" && it.category == FoodCategory.OTHER })
    }

    @Test
    fun testHalfWidthKanaReceipt() {
        val ocrText = """
            ﾏﾙｴﾂ 新宿店
            ﾚｼﾞ02 責No.123
            ｷｬﾍﾞﾂ                   ￥１９８
            ﾓﾔｼ                      ¥38
            国産豚ﾛｰｽ ｽﾗｲｽ          ¥498
            ｶﾙﾋﾞｰ ﾎﾟﾃﾄﾁｯﾌﾟｽ         ¥148
            ﾎﾟｲﾝﾄ ﾁｰｽﾞ増量ｷｬﾝﾍﾟｰﾝ
            合計                    ¥882
        """.trimIndent()

        val extracted = ReceiptFoodExtractor.extract(ocrText, LocalDate.of(2026, 10, 1))

        // Names stay exactly as printed; the snack and the ﾎﾟｲﾝﾄ line are skipped
        assertEquals(listOf("ｷｬﾍﾞﾂ", "ﾓﾔｼ", "国産豚ﾛｰｽ ｽﾗｲｽ"), extracted.map { it.name })
        assertEquals(
            listOf(FoodCategory.VEGETABLE, FoodCategory.VEGETABLE, FoodCategory.MEAT),
            extracted.map { it.category }
        )
    }

    @Test
    fun testDuplicateSpellingsCollapse() {
        val ocrText = """
            ｷｬﾍﾞﾂ        ¥198
            キャベツ      ¥198
        """.trimIndent()

        val extracted = ReceiptFoodExtractor.extract(ocrText, LocalDate.of(2026, 10, 1))
        assertEquals(1, extracted.size)
    }

    @Test
    fun testNoFoodInReceipt() {
        val ocrText = """
            マツモトキヨシ 薬局
            目薬                   ¥680
            バンドエイド           ¥350
            シャンプー             ¥780
            合計                 ¥1,810
        """.trimIndent()

        val extracted = ReceiptFoodExtractor.extract(ocrText, LocalDate.now())
        assertEquals(0, extracted.size)
    }
}
