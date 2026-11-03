package com.localbudget.app.domain

import com.localbudget.app.domain.food.FoodDictionary
import com.localbudget.app.domain.model.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodDictionaryTest {

    @Test
    fun testVegetableDetection() {
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("キャベツ 1玉"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("国産トマト"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("玉ねぎ 3個入"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("長ネギ"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("にんじん"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("ほうれん草"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.guessCategory("えのき茸"))
    }

    @Test
    fun testMeatDetection() {
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("国産牛こま切れ"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("豚バラ薄切り肉"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("若鶏もも肉"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("合い挽きミンチ"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("あらびきソーセージ"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.guessCategory("ロースハム"))
    }

    @Test
    fun testFishDetection() {
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("銀鮭切り身"))
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("本マグロ刺身"))
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("むきえび"))
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("あじフライ"))
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("生たらこ"))
        assertEquals(FoodCategory.FISH, FoodDictionary.guessCategory("塩サバ"))
    }

    @Test
    fun testDairyDetection() {
        assertEquals(FoodCategory.DAIRY, FoodDictionary.guessCategory("成分無調整牛乳 1L"))
        assertEquals(FoodCategory.DAIRY, FoodDictionary.guessCategory("とろけるスライスチーズ"))
        assertEquals(FoodCategory.DAIRY, FoodDictionary.guessCategory("プレーンヨーグルト"))
        assertEquals(FoodCategory.DAIRY, FoodDictionary.guessCategory("北海道バター"))
    }

    @Test
    fun testOtherAndUnknown() {
        assertEquals(FoodCategory.OTHER, FoodDictionary.guessCategory("絹ごし豆腐"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.guessCategory("納豆 3パック"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.guessCategory("白米 5kg"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.guessCategory("食パン 6枚切"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.guessCategory("マヨネーズ 400g"))
    }

    @Test
    fun testIsFoodItem() {
        assertTrue(FoodDictionary.isFoodItem("豚バラ肉"))
        assertTrue(FoodDictionary.isFoodItem("レタス"))
        assertTrue(FoodDictionary.isFoodItem("牛乳"))
        assertTrue(FoodDictionary.isFoodItem("鮭"))
        assertTrue(FoodDictionary.isFoodItem("豆腐"))

        // Non-food items should return false
        org.junit.Assert.assertFalse(FoodDictionary.isFoodItem("ゴミ袋 45L"))
        org.junit.Assert.assertFalse(FoodDictionary.isFoodItem("単3アルカリ乾電池"))
        org.junit.Assert.assertFalse(FoodDictionary.isFoodItem("ティッシュペーパー"))
    }

    @Test
    fun testHalfWidthKatakanaReceiptNames() {
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("ｷｬﾍﾞﾂ"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("ﾓﾔｼ 1袋"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("国産豚ﾛｰｽ"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("ｱﾗﾋﾞｷｳｲﾝﾅｰ"))
        assertEquals(FoodCategory.FISH, FoodDictionary.matchCategory("ﾁﾘ産ｻｰﾓﾝ"))
        assertEquals(FoodCategory.DAIRY, FoodDictionary.matchCategory("ﾌﾟﾚｰﾝﾖｰｸﾞﾙﾄ"))
    }

    @Test
    fun testKanaVariantsMatchWithoutDictionaryDuplicates() {
        // These keywords are listed only in katakana; hiragana and half-width spellings match too
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("きゃべつ"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("べーこん"))
        assertEquals(FoodCategory.DAIRY, FoodDictionary.matchCategory("北海道ﾊﾞﾀｰ"))
    }

    @Test
    fun testFalsePositivesAreRejectedOrRecategorized() {
        // Not fridge food even though a food keyword appears
        assertNull(FoodDictionary.matchCategory("ポテトチップス うすしお"))
        assertNull(FoodDictionary.matchCategory("ｶﾙﾋﾞｰ ﾎﾟﾃﾄﾁｯﾌﾟｽ"))
        assertNull(FoodDictionary.matchCategory("えびせん"))
        assertNull(FoodDictionary.matchCategory("ミルクチョコレート"))
        assertNull(FoodDictionary.matchCategory("ドッグフード ビーフ味"))
        assertNull(FoodDictionary.matchCategory("ハムスター用床材"))
        assertNull(FoodDictionary.matchCategory("フライパン 26cm"))

        // Compound beats the shorter keyword inside it
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("日清サラダ油"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("サラダチキン プレーン"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("たい焼き"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("冷凍たこ焼"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("カットすいか"))
    }

    @Test
    fun testNoKeywordConflictsAfterNormalization() {
        assertEquals(emptyList<String>(), FoodDictionary.conflictingKeywords())
    }

    @Test
    fun testFoodCategoriesHaveRequiredValues() {
        assertEquals(5, FoodCategory.entries.size)
        val names = FoodCategory.entries.map { it.displayName }
        assertTrue(names.contains("野菜"))
        assertTrue(names.contains("肉"))
        assertTrue(names.contains("魚"))
        assertTrue(names.contains("乳製品"))
        assertTrue(names.contains("その他"))
    }

    @Test
    fun testFruitAndReadyMeals() {
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("ﾊﾞﾅﾅ"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("ｻﾝふじりんご"))
        assertEquals(FoodCategory.VEGETABLE, FoodDictionary.matchCategory("あまおう 苺"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("ﾐｯｸｽｻﾝﾄﾞｲｯﾁ"))
        assertEquals(FoodCategory.OTHER, FoodDictionary.matchCategory("のり弁当"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("国産豚もも切り落とし"))
        assertEquals(FoodCategory.MEAT, FoodDictionary.matchCategory("若鶏の唐揚"))
    }

    @Test
    fun testDrinksAndLookAlikesAreNotFridgeFood() {
        assertNull(FoodDictionary.matchCategory("ﾚﾓﾝﾃｨｰ 500ml"))
        assertNull(FoodDictionary.matchCategory("ｵﾚﾝｼﾞｼﾞｭｰｽ"))
        assertNull(FoodDictionary.matchCategory("缶ﾋﾞｰﾙ 350"))
        assertNull(FoodDictionary.matchCategory("ﾌﾟﾘﾝﾀｲﾝｸ"))
        assertNull(FoodDictionary.matchCategory("ラムネ"))
        assertNull(FoodDictionary.matchCategory("おもちゃ"))
    }
}
