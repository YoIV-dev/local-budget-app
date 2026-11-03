package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.OcrTextRepair
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextRepairTest {

    @Test
    fun misreadYenSignBecomesYen() {
        assertEquals("ｷｬﾍﾞﾂ ¥198", OcrTextRepair.repairLine("ｷｬﾍﾞﾂ Y198"))
        assertEquals("牛乳 ¥228", OcrTextRepair.repairLine("牛乳 半228"))
        assertEquals("合計 ¥1,426", OcrTextRepair.repairLine("合計 羊1,426"))
    }

    @Test
    fun lookAlikeLettersInAmountsBecomeDigits() {
        assertEquals("ﾄﾏﾄ ¥198", OcrTextRepair.repairLine("ﾄﾏﾄ ¥l98"))
        assertEquals("合計 ¥1,408", OcrTextRepair.repairLine("合計 ¥1,4O8"))
        assertEquals("ﾓﾔｼ 108", OcrTextRepair.repairLine("ﾓﾔｼ 1O8"))
    }

    @Test
    fun periodReadForThousandsCommaBecomesComma() {
        assertEquals("豚ﾊﾞﾗ ¥1,280", OcrTextRepair.repairLine("豚ﾊﾞﾗ ¥1.280"))
    }

    @Test
    fun namesSizesDatesAndCodesAreLeftAlone() {
        val untouched = listOf(
            "ｺｶｺｰﾗ 1.5L ¥198",
            "牛乳 1L",
            "ｶﾙﾋﾟｽ 500ml",
            "Sｻｲｽﾞ",
            "2026.10.01 19:05",
            "TEL 03-1234-5678",
            "S1",
            "IO",
            "No.0123"
        )
        for (line in untouched) assertEquals(line, OcrTextRepair.repairLine(line))
    }

    @Test
    fun spaceAfterThousandsCommaIsMovedBehindTheAmount() {
        assertEquals("合計/ 7点 3,919 ", OcrTextRepair.repairLine("合計/ 7点 3, 919"))
        assertEquals("お釣り ¥6,101 ", OcrTextRepair.repairLine("お釣り F6, 1O1"))
        assertEquals("8%対象 ¥3,492  279", OcrTextRepair.repairLine("8%対象 ¥3, 492 279"))
        // Not a thousands group: two digits after the comma, or a list
        assertEquals("1, 23", OcrTextRepair.repairLine("1, 23"))
        assertEquals("1, 2, 3", OcrTextRepair.repairLine("1, 2, 3"))
    }

    @Test
    fun lengthIsKept() {
        // ReceiptItemParser maps offsets in the repaired text back onto the printed row
        val line = "ﾁｰｽﾞ Y1.48O"
        assertEquals(line.length, OcrTextRepair.repairLine(line).length)
        assertEquals("ﾁｰｽﾞ ¥1,480", OcrTextRepair.repairLine(line))
    }

    @Test
    fun detachedVoicingMarksJoinTheirKana() {
        // NFKC alone would leave "ホ ゚テト"
        assertEquals("ポテト", JapaneseTextNormalizer.nfkc(OcrTextRepair.repairKana("ホ゜テト")))
        assertEquals("バナナ", JapaneseTextNormalizer.nfkc(OcrTextRepair.repairKana("ハ゛ナナ")))
        assertEquals("ﾎﾟﾃﾄ", OcrTextRepair.repairKana("ﾎ゜ﾃﾄ"))
        assertEquals("ﾊﾞﾅﾅ", OcrTextRepair.repairKana("ﾊ ﾞﾅﾅ"))
        assertEquals("ﾍﾟｯﾄ", OcrTextRepair.repairKana("ﾍ°ｯﾄ"))
    }

    @Test
    fun longVowelReadAsDashOrKanjiOne() {
        assertEquals("ロース", OcrTextRepair.repairKana("ロ一ス"))
        assertEquals("ｺｰﾋｰ", OcrTextRepair.repairKana("ｺ-ﾋ-"))
        assertEquals("ﾁｰｽﾞ ¥298", OcrTextRepair.repairKana("ﾁ一ｽﾞ ¥298"))
        assertEquals("ラーメン", OcrTextRepair.repairKana("ラ—メン"))
    }

    @Test
    fun kanjiShapedLikeKatakanaInKatakanaWords() {
        assertEquals("マルエツ 新宿店", OcrTextRepair.repairKana("マル工ツ 新宿店"))
        assertEquals("ﾛｰｽ", OcrTextRepair.repairKana("口一ｽ"))
        assertEquals("ニンジン", OcrTextRepair.repairKana("二ンジン"))
        assertEquals("カニカマ", OcrTextRepair.repairKana("力二カマ"))
        assertEquals("ﾄﾏﾄ", OcrTextRepair.repairKana("卜ﾏﾄ"))
        assertEquals("ベーコン", OcrTextRepair.repairKana("べ一コン"))
        assertEquals("ｸﾘｰﾑ", OcrTextRepair.repairKana("ｸりｰﾑ"))
    }

    @Test
    fun kanaRepairLeavesKanjiWordsDashesAndAmounts() {
        val untouched = listOf(
            "一口カツ",
            "キャベツ千切り",
            "二丁目",
            "八ヶ岳",
            "3ヶ月",
            "セブン-イレブン 渋谷店",
            "ﾊﾞﾅﾅ -50",
            "ﾊﾞﾅﾅ- 50",
            "値引 -100",
            "おにぎり 鮭",
            "2026-10-01 19:05",
            "TEL 03-1234-5678",
            "ｺｰﾗ 1.5L ¥198",
            "25°C"
        )
        for (line in untouched) assertEquals(line, OcrTextRepair.repairKana(line))
    }
}
