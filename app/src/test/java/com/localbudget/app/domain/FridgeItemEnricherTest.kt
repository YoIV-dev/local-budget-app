package com.localbudget.app.domain

import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class FridgeItemEnricherTest {

    @Test
    fun guessesStorageFromTheName() {
        assertEquals(StorageLocation.FREEZER, FridgeItemEnricher.guessStorage("冷凍 ほうれん草"))
        assertEquals(StorageLocation.PANTRY, FridgeItemEnricher.guessStorage("新じゃがいも"))
        assertEquals(StorageLocation.PANTRY, FridgeItemEnricher.guessStorage("濃口しょうゆ 1L"))
        assertEquals(StorageLocation.FRIDGE, FridgeItemEnricher.guessStorage("牛乳"))
        assertEquals(StorageLocation.FRIDGE, FridgeItemEnricher.guessStorage("謎の食材"))
    }

    @Test
    fun everyPantryKeywordIsInDictionary() {
        assertEquals(emptyList<String>(), FridgeItemEnricher.unknownPantryKeywords())
    }
}
