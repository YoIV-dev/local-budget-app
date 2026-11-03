package com.localbudget.app.domain

import com.localbudget.app.domain.text.JapaneseTextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class JapaneseTextNormalizerTest {

    @Test
    fun testNfkcWidens() {
        assertEquals("キャベツ", JapaneseTextNormalizer.nfkc("ｷｬﾍﾞﾂ"))
        assertEquals("¥1,980", JapaneseTextNormalizer.nfkc("￥１，９８０"))
        assertEquals("TOTAL", JapaneseTextNormalizer.nfkc("ＴＯＴＡＬ"))
    }

    @Test
    fun testMatchKeyFoldsSpellingVariants() {
        val expected = JapaneseTextNormalizer.matchKey("にんじん")
        assertEquals(expected, JapaneseTextNormalizer.matchKey("ニンジン"))
        assertEquals(expected, JapaneseTextNormalizer.matchKey("ﾆﾝｼﾞﾝ"))
        assertEquals(expected, JapaneseTextNormalizer.matchKey("にん じん"))
    }

    @Test
    fun testMatchKeyKeepsLongVowelAndKanji() {
        assertEquals("ぽーく", JapaneseTextNormalizer.matchKey("ﾎﾟｰｸ"))
        assertEquals("国産ぎゅう", JapaneseTextNormalizer.matchKey("国産ギュウ"))
        assertEquals("tel", JapaneseTextNormalizer.matchKey("ＴＥＬ"))
    }

    @Test
    fun looseKeyMatchesHowCardStatementsSpellStoreNames() {
        assertEquals("ふあみりまと", JapaneseTextNormalizer.looseKey("フアミリ-マ-ト"))
        assertEquals("ふあみりまと", JapaneseTextNormalizer.looseKey("ファミリーマート"))
        assertEquals("ふあみりまと", JapaneseTextNormalizer.looseKey("ﾌｧﾐﾘｰﾏｰﾄ"))
        assertEquals("せぶんいれぶん", JapaneseTextNormalizer.looseKey("セブン－イレブン"))
        assertEquals("amazoncojp", JapaneseTextNormalizer.looseKey("ＡＭＡＺＯＮ．ＣＯ．ＪＰ"))
    }
}
