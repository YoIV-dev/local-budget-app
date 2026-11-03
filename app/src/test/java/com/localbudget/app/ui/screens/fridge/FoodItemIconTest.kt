package com.localbudget.app.ui.screens.fridge

import com.localbudget.app.domain.model.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodItemIconTest {
    @Test fun recognizesNamesAndFallsBackToCategory() {
        assertEquals("🥕", FoodItemIcon.forName("国産ﾆﾝｼﾞﾝ", FoodCategory.VEGETABLE))
        assertEquals("🥛", FoodItemIcon.forName("牛乳", FoodCategory.DAIRY))
        assertEquals(FoodCategory.OTHER.iconEmoji, FoodItemIcon.forName("調味料", FoodCategory.OTHER))
    }
}
