package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.ItemNameCorrector
import com.localbudget.app.domain.text.KanaFuzzyMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemNameCorrectorTest {

    @Test
    fun misreadsSeenOnAPixel9aAreCorrected() {
        assertEquals("明治ブルガリアヨーグルト", ItemNameCorrector.correct("メイシプ励リアヨークルト"))
        assertEquals("シマダヤ トケイタイラーメンショウユ", ItemNameCorrector.correct("シマダヤ トケイタイラーメンショウュ"))
        assertEquals("明治たけのこの里", ItemNameCorrector.correct("メイジタケコサト"))
        assertEquals("ニイガタコシイブキ 5kg", ItemNameCorrector.correct("ニイカタコシイプキ 5kg"))
        // Short words only through voicing marks and look-alikes: ルモン is レモン, never ホルモン
        assertEquals("サントリーザビールレモン", ItemNameCorrector.correct("サントリーザヒールルモン"))
    }

    @Test
    fun yogurtReadEveryWayOnThePixel9aIsTheSameProduct() {
        val readings = listOf(
            "メイジフブルカリアヨーグルト", "メイシブけリアヨーグト", "メイシ フけリアヨーグト",
            "メイシプ励リアヨーグト", "メイジブカリアヨーグト", "ﾒｲｼﾞﾌﾞﾙｶﾞﾘｱﾖｰｸﾞﾙﾄ"
        )
        for (reading in readings) assertEquals(reading, "明治ブルガリアヨーグルト", ItemNameCorrector.correct(reading))
        assertEquals("明治ブルガリアヨーグルト 400g", ItemNameCorrector.correct("メイジブルガリアヨーグルト 400g"))
    }

    @Test
    fun halfWidthNamesComeBackFullWidthOnlyWhenCorrected() {
        assertEquals("ヨーグルト", ItemNameCorrector.correct("ﾖｰｸﾙﾄ"))
        assertEquals("ｷｬﾍﾞﾂ", ItemNameCorrector.correct("ｷｬﾍﾞﾂ"))
    }

    @Test
    fun namesThatAreNotMisreadsAreLeftAlone() {
        val untouched = listOf(
            "国産牛こま切れ", "おにぎり 鮭", "牛乳 1L", "ゴミ袋 45L", "Tシャツ", "緑茶 500ml",
            "カレー", "ソース", "ライス", "トマト", "手巻きソーセージドー"
        )
        for (name in untouched) assertEquals(name, ItemNameCorrector.correct(name))
    }

    @Test
    fun voicingMarksAndLookAlikesCostLessThanOtherMisreads() {
        assertEquals(KanaFuzzyMatcher.VOICING_COST, KanaFuzzyMatcher.substitution('シ', 'ジ'), 0.0)
        assertEquals(KanaFuzzyMatcher.VOICING_COST, KanaFuzzyMatcher.substitution('ュ', 'ユ'), 0.0)
        assertEquals(KanaFuzzyMatcher.SHAPE_COST, KanaFuzzyMatcher.substitution('シ', 'ツ'), 0.0)
        assertEquals(1.0, KanaFuzzyMatcher.substitution('ア', 'カ'), 0.0)
        // A three-letter word may only lose voicing marks
        assertNull(KanaFuzzyMatcher.find("ライス", "ライム", KanaFuzzyMatcher.allowedCost(3)))
    }
}
