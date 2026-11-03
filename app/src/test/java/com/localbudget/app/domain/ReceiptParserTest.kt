package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {

    @Test
    fun testParseConvenienceStoreReceipt() {
        val ocrText = """
            セブン-イレブン 渋谷店
            TEL 03-1234-5678
            領収書 (レシート)
            登録番号: T1234567890123
            
            2026年9月25日(金) 12:35
            
            おにぎり 鮭             ¥150
            緑茶 500ml              ¥160
            サンドイッチ             ¥320
            
            小計                    ¥630
            消費税等 (8%)            ¥50
            合計                    ¥680
            お預り                 ¥1,000
            お釣                    ¥320
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("セブン-イレブン", parsed.storeName)
        assertEquals(LocalDate.of(2026, 9, 25), parsed.date)
        assertEquals(680L, parsed.amount)
        assertEquals(1L, parsed.suggestedCategoryId) // 1 = 食費
    }

    @Test
    fun testParseSupermarketReceipt() {
        val ocrText = """
            イオンモール 幕張新都心店
            電話 043-000-1111
            お買上票
            
            2026/09/20 18:42 レジ05 係012
            
            国産牛こま切れ           ¥980
            キャベツ 1玉             ¥198
            牛乳 1L                  ¥228
            ヨーグルト               ¥158
            
            小計                   ¥1,564
            外税対象額             ¥1,564
            消費税等                 ¥125
            合計                   ¥1,689
            現計                   ¥1,689
            お預り                 ¥2,000
            お釣り                   ¥311
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("イオン", parsed.storeName)
        assertEquals(LocalDate.of(2026, 9, 20), parsed.date)
        assertEquals(1689L, parsed.amount)
        assertEquals(1L, parsed.suggestedCategoryId) // 1 = 食費
    }

    @Test
    fun testParseDrugstoreReceipt() {
        val ocrText = """
            マツモトキヨシ 新宿東口店
            No. 0042
            
            2026-08-15 14:10
            
            シャンプー 詰替          ¥598
            ボックスティッシュ 5箱   ¥348
            目薬                     ¥780
            
            小計                   ¥1,726
            合計                   ¥1,898
            クレジット支払         ¥1,898
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("マツモトキヨシ", parsed.storeName)
        assertEquals(LocalDate.of(2026, 8, 15), parsed.date)
        assertEquals(1898L, parsed.amount)
        assertEquals(2L, parsed.suggestedCategoryId) // 2 = 日用品
    }

    @Test
    fun testParseGasStationReceipt() {
        val ocrText = """
            ENEOS Dr.Drive 港北店
            
            2026.07.04 09:30
            
            レギュラー 30.5L        ¥5,185
            
            合計                   ¥5,185
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("ENEOS Dr.Drive", parsed.storeName)
        assertEquals(LocalDate.of(2026, 7, 4), parsed.date)
        assertEquals(5185L, parsed.amount)
        assertEquals(3L, parsed.suggestedCategoryId) // 3 = 交通費
    }

    @Test
    fun testParseFullWidthAndHalfWidthKana() {
        val ocrText = """
            ﾏﾙｴﾂ 新宿店
            ２０２６年１０月１日
            ｷｬﾍﾞﾂ       ￥１９８
            合計         ￥１，９８０
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("マルエツ", parsed.storeName)
        assertEquals(LocalDate.of(2026, 10, 1), parsed.date)
        assertEquals(1980L, parsed.amount)
        assertEquals(ocrText, parsed.rawText) // raw OCR text is kept untouched
    }

    @Test
    fun testParseNextLineAmount() {
        val ocrText = """
            ファミリーマート
            2026/09/24
            合計
            ¥ 1,250
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals("ファミリーマート", parsed.storeName)
        assertEquals(LocalDate.of(2026, 9, 24), parsed.date)
        assertEquals(1250L, parsed.amount)
        assertEquals(1L, parsed.suggestedCategoryId)
    }

    @Test
    fun testTaxInclusiveTotalChosenOverTaxExclusiveSubtotal() {
        val ocrText = """
            ライフ 新宿店
            2026/10/10
            税抜合計     ¥1,564
            消費税等       ¥125
            税込合計     ¥1,689
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)
        assertEquals(1689L, parsed.amount)
    }

    @Test
    fun testSubtotalAndTaxWithoutTotalKeyword() {
        val ocrText = """
            OKストア
            2026/10/10
            小計         ¥1,564
            外税           ¥125
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)
        assertEquals(1689L, parsed.amount)
    }

    @Test
    fun testIgnoreExternalTaxTotalKeyword() {
        val ocrText = """
            スーパーABC
            2026/10/10
            小計         ¥1,564
            外税合計       ¥125
            合計         ¥1,689
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)
        assertEquals(1689L, parsed.amount)
    }

    @Test
    fun testSpaceInTotalKeyword() {
        val ocrText = """
            セブン-イレブン
            2026/10/10
            合　計       ¥1,689
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)
        assertEquals(1689L, parsed.amount)
    }

    @Test
    fun testItemCountRowIsNotTheTotal() {
        val ocrText = """
            ライフ 練馬店
            2026/10/03
            ｷｬﾍﾞﾂ ¥198
            ﾓﾔｼ ¥38
            合計点数 2点
            小計 ¥236
            外税 ¥18
            合計 ¥254
        """.trimIndent()

        assertEquals(254L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testCountOnTotalRowIsSkipped() {
        val ocrText = """
            マツモトキヨシ 新宿東口店
            2026-08-15
            合計 3点 ¥1,898
        """.trimIndent()

        assertEquals(1898L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testInnerTaxOnTotalRowDoesNotHideTheTotal() {
        val ocrText = """
            ローソン 新宿三丁目店
            2026/10/04 08:11
            ｻﾝﾄﾞｲｯﾁ ¥398
            合計 ¥398 (内消費税等 ¥29)
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)
        assertEquals("ローソン", parsed.storeName)
        assertEquals(398L, parsed.amount)
    }

    @Test
    fun testYearIsNeverTheTotal() {
        // No total row was read: the year in the date must not win the "largest amount" fallback
        val ocrText = """
            西友 荻窪店
            2026/10/08 18:30
            ﾀﾏｺﾞ ¥228
        """.trimIndent()

        assertEquals(228L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testTotalPrintedAboveItsLabel() {
        val ocrText = """
            西友 荻窪店
            2026/10/08
            ﾀﾏｺﾞ ¥228
            ¥228
            合計
        """.trimIndent()

        assertEquals(228L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testCashTenderedMinusChangeWhenNoTotalRow() {
        val ocrText = """
            八百屋 たなか
            2026/10/10
            大根 150
            人参 120
            お預り 500
            お釣 230
        """.trimIndent()

        assertEquals(270L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testMisreadYenAndDigits() {
        val ocrText = """
            サミット 中野店
            2026/10/02
            ﾄﾏﾄ ¥l98
            豚ﾊﾞﾗ Y1.280
            合 計 Y1,4O8
        """.trimIndent()

        assertEquals(1408L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testRestaurantBill() {
        val ocrText = """
            大衆酒場 さくら
            2026/10/11 21:30
            生ビール 2 x 500 ¥1,000
            お会計 ¥3,280
        """.trimIndent()

        assertEquals(3280L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testSpacedDate() {
        val ocrText = """
            ライフ 練馬店
            2026年10月 3日(土) 17:20
            合計 ¥254
        """.trimIndent()

        assertEquals(LocalDate.of(2026, 10, 3), ReceiptParser.parse(ocrText).date)
    }

    @Test
    fun testGreetingAndAddressAreNotTheStoreName() {
        val ocrText = """
            毎度ありがとうございます
            〒160-0023
            東京都新宿区西新宿1-2-3
            まいばすけっと 西新宿店
            2026/10/05 20:01
            ﾊﾞﾅﾅ ¥178
            合計 ¥178
        """.trimIndent()

        assertEquals("まいばすけっと", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testPhoneNumberIsCutFromTheStoreName() {
        val ocrText = """
            領収書
            株式会社ローソン
            ローソン 新宿店 TEL03-1234-5678
            2026/10/12
            合計 ¥150
        """.trimIndent()

        assertEquals("ローソン", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testChainFromFooterWhenOnlyTheBranchIsPrinted() {
        // The chain name is a logo ML Kit cannot read; it only appears as the company in the footer
        val ocrText = """
            ようこそ
            新宿店
            2026/10/09
            ﾋﾟｰﾏﾝ ¥98
            合計 ¥98
            イオンリテール株式会社
        """.trimIndent()

        assertEquals("イオン", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testYenSignReadAsLeadingDigitIsDroppedWhenCashPaidAgrees() {
        // "¥1,969" in the large font came back as "71,969"
        val ocrText = """
            サミット 中野店
            2026/10/02
            ﾄﾏﾄ ¥198
            豚ﾊﾞﾗ ｽﾗｲｽ ¥1,280
            小計 ¥1,824
            外税 ¥145
            合計 71,969
            お預り ¥2,000
            お釣 ¥31
        """.trimIndent()

        assertEquals(1969L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testYenSignReadAsLeadingDigitIsDroppedWhenCardLineAgrees() {
        val ocrText = """
            ローソン 新宿店
            2026/10/02
            おにぎり ¥150
            合計 11,280
            クレジット ¥1,280
        """.trimIndent()

        assertEquals(1280L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testYenSignReadAsLeadingDigitIsDroppedWhenItemsAddUp() {
        val ocrText = """
            ﾏﾙｴﾂ 新宿店
            2026/10/01
            ｷｬﾍﾞﾂ ¥198
            ﾓﾔｼ ¥38
            合計 4236
        """.trimIndent()

        assertEquals(236L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testFiveDigitTotalIsKeptWhenCashPaidAgrees() {
        // A real five-digit total is never cut down when the rest of the receipt agrees with it
        val ocrText = """
            ヨドバシカメラ
            2026/10/02
            イヤホン ¥12,800
            合計 ¥12,800
            お預り ¥20,000
            お釣 ¥7,200
        """.trimIndent()

        assertEquals(12800L, ReceiptParser.parse(ocrText).amount)
    }

    @Test
    fun testKatakanaStoreMisreadAsKanji() {
        val ocrText = """
            ﾏﾙ工ﾂ 新宿店
            2026/10/01
            ﾛ一ｽ ¥498
            合計 ¥498
        """.trimIndent()

        assertEquals("マルエツ", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testSpaceAfterThousandsCommaAndYenReadAsF() {
        // Rows as ML Kit read an OK store receipt on a Pixel 9a: "3, 919" came out as 3 and 919
        val ocrText = """
            OK高品質·Everyday Low Price
            オーケークラフ、会員で現金払に限り、 食料品は
            オーケー 梶野町店
            2026年10月27日(火)11:35 レジ0806
            Fピーマン ¥100
            Fニイカタコシイプキ5kg 2, 470
            割引前合計 ¥3,728
            小計 43, 627
            合計/ 7点 3, 919
            お預り ¥10, 020
            お釣り F6, 1O1
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals(3919L, parsed.amount)
        assertEquals(true, parsed.totalCheck.corroborated)
        // The member notice names the chain too, but it is a sentence
        assertEquals("オーケー", parsed.storeName)
    }

    @Test
    fun testPaymentRowStandsInForAnUnreadTotalRow() {
        // FamilyMart on a Pixel 9a: the large "合計" label was not read, only its amount
        val ocrText = """
            ーFamilyMart
            小金井法政大学前店 東京都小金井市緑町
            2026年10月27日 (火) 23:15
            ©手巻きソーセージドー ¥180軽
            180O
            8%対象」 4180)
            (内消費税等 13)
            QU1CPay支払. 180
            「軽」は軽減税率対象商品です。
            楽天ポイント 米***1929
            カードが無効の場合、ボイントは
        """.trimIndent()

        val parsed = ReceiptParser.parse(ocrText)

        assertEquals(180L, parsed.amount)
        assertEquals(true, parsed.totalCheck.corroborated)
        assertEquals("ファミリーマート", parsed.storeName)
    }

    @Test
    fun testChainLogoReadAsLatinLettersWithoutTheBranch() {
        val ocrText = """
            ●4maruetsu
            新宿店
            2026/10/15
            ﾄﾏﾄ ¥198
            合計 ¥198
        """.trimIndent()

        assertEquals("マルエツ", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testLogoAloneBecomesTheChainName() {
        val ocrText = """
            ●4maruetsu
            2026/10/15
            ﾄﾏﾄ ¥198
            合計 ¥198
        """.trimIndent()

        assertEquals("マルエツ", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testMisreadKatakanaChainIsCorrected() {
        val ocrText = """
            マルエシ 新宿店
            2026/10/15
            ﾄﾏﾄ ¥198
            合計 ¥198
        """.trimIndent()

        assertEquals("マルエツ", ReceiptParser.parse(ocrText).storeName)
    }

    @Test
    fun testChainKnownByItsInvoiceNumberWhenOnlyTheBranchWasRead() {
        // FamilyMart on a Pixel 9a: neither the logo nor the footer's name was read
        val ocrText = """
            小金キ法政大学前店 東京都小金井市緑町
            電話: 042-382-3626
            登録番号:T5012401016536
            2026年10月27日 (火) 23:15
            ©手巻きソーセージドー ¥180軽
            QUICPay支払 180
        """.trimIndent()

        assertEquals("ファミリーマート", ReceiptParser.parse(ocrText).storeName)
    }
}
