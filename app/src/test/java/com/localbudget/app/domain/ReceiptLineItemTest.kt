package com.localbudget.app.domain

import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.model.ReceiptLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ReceiptLineItemTest {

    private val milk = ReceiptLineItem(name = "牛乳", price = 416, quantity = 2, unitPrice = 238, discount = 20)

    @Test
    fun correctingThePriceKeepsAUnitPriceThatStillAddsUp() {
        val fixed = milk.withPrice(476)

        assertEquals(476L, fixed.price)
        assertEquals(238L, fixed.unitPrice)
        assertEquals(456L, fixed.netPrice)
        assertEquals(milk.copy(price = 476), fixed)
    }

    @Test
    fun aUnitPriceThatNoLongerAddsUpIsDropped() {
        assertNull(milk.withPrice(500).unitPrice)
    }

    @Test
    fun clearingThePriceLeavesTheLineWithoutOne() {
        val cleared = milk.withPrice(null)

        assertNull(cleared.price)
        assertNull(cleared.unitPrice)
        assertNull(cleared.netPrice)
    }

    @Test
    fun fridgeItemsTakeTheCorrectedPrice() {
        val items = listOf(ReceiptLineItem(name = "キャベツ", price = 1980).withPrice(198))

        val fridge = ReceiptFoodExtractor.extract(items, LocalDate.of(2026, 10, 27))

        assertEquals(listOf(198L), fridge.map { it.price })
    }
}
