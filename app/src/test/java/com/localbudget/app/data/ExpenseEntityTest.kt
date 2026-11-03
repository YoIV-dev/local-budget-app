package com.localbudget.app.data

import com.localbudget.app.data.local.entity.CategoryEntity
import com.localbudget.app.data.local.entity.ExpenseEntity
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

class ExpenseEntityTest {

    @Test
    fun testExpenseDomainToEntityMapping() {
        val date = LocalDate.of(2026, 9, 25)
        val expense = Expense(
            id = 42L,
            date = date,
            amount = 1280L,
            storeName = "ファミリーマート",
            categoryId = 1L,
            memo = "昼食代",
            sourceType = SourceType.MANUAL,
            hasReceiptImage = false
        )

        val entity = ExpenseEntity.fromDomain(expense)

        assertEquals(42L, entity.id)
        assertEquals(date.toEpochDay(), entity.epochDay)
        assertEquals("2026-09", entity.yearMonth)
        assertEquals(1280L, entity.amount)
        assertEquals("ファミリーマート", entity.storeName)
        assertEquals(1L, entity.categoryId)
        assertEquals("昼食代", entity.memo)
        assertEquals("MANUAL", entity.sourceType)

        val catEntity = CategoryEntity(id = 1L, name = "食費", iconName = "Restaurant", colorHex = "#FF5722")
        val backToDomain = entity.toDomain(catEntity.toDomain())

        assertEquals(expense.id, backToDomain.id)
        assertEquals(expense.date, backToDomain.date)
        assertEquals(expense.amount, backToDomain.amount)
        assertEquals(expense.storeName, backToDomain.storeName)
        assertEquals(expense.categoryId, backToDomain.categoryId)
        assertEquals(expense.memo, backToDomain.memo)
        assertEquals(expense.sourceType, backToDomain.sourceType)
        assertNotNull(backToDomain.category)
        assertEquals("食費", backToDomain.category?.name)
    }
}
