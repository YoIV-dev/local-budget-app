package com.localbudget.app.domain

import com.localbudget.app.domain.food.FoodUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodUnitTest {

    @Test
    fun guessesUsualUnitFromDictionary() {
        assertEquals("本", FoodUnit.guess("成分無調整牛乳 1L"))
        assertEquals("玉", FoodUnit.guess("キャベツ"))
        assertEquals("袋", FoodUnit.guess("もやし"))
        assertEquals("束", FoodUnit.guess("ほうれん草"))
        assertEquals("パック", FoodUnit.guess("卵 10個入"))
        assertEquals("丁", FoodUnit.guess("絹ごし豆腐"))
        assertEquals("切", FoodUnit.guess("銀鮭切身"))
        assertEquals("本", FoodUnit.guess("長ネギ"))
    }

    @Test
    fun longerKeywordWins() {
        // 食パン over パン, たらこ (not listed) over たら
        assertEquals("袋", FoodUnit.guess("食パン 6枚切"))
        assertEquals("パック", FoodUnit.guess("たらこ"))
    }

    @Test
    fun fallsBackByCategory() {
        assertEquals("パック", FoodUnit.guess("国産牛こま切れ"))
        assertEquals("個", FoodUnit.guess("トマト"))
        assertEquals("個", FoodUnit.guess("謎の食材"))
    }

    @Test
    fun everyKeywordIsInDictionary() {
        assertEquals(emptyList<String>(), FoodUnit.unknownKeywords())
    }
}
