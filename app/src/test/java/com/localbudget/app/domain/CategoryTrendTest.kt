package com.localbudget.app.domain

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.CategoryTrend
import com.localbudget.app.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CategoryTrendTest {

    private val food = Category(id = 1, name = "食費", iconName = "Restaurant", colorHex = "#FF5722")
    private val daily = Category(id = 2, name = "日用品", iconName = "ShoppingCart", colorHex = "#4CAF50")
    private val leisure = Category(id = 3, name = "娯楽", iconName = "Movie", colorHex = "#2196F3")

    private fun expense(categoryId: Long, amount: Long) =
        Expense(date = LocalDate.of(2026, 10, 1), amount = amount, storeName = "", categoryId = categoryId)

    @Test
    fun `categories are ordered by their total over all months, largest first`() {
        val trend = CategoryTrend.from(
            labels = listOf("9月", "10月"),
            expensesPerMonth = listOf(
                listOf(expense(1, 3000), expense(2, 1000)),
                listOf(expense(2, 4000), expense(3, 500))
            ),
            allCategories = listOf(food, daily, leisure)
        )

        assertEquals(listOf(daily, food, leisure), trend.categories)
    }

    @Test
    fun `each month sums by category in the shared order and leaves out empty ones`() {
        val trend = CategoryTrend.from(
            labels = listOf("9月", "10月"),
            expensesPerMonth = listOf(
                listOf(expense(1, 3000), expense(1, 2000), expense(2, 1000)),
                listOf(expense(2, 4000))
            ),
            allCategories = listOf(food, daily)
        )

        assertEquals("9月", trend.months[0].label)
        assertEquals(listOf(food to 5000L, daily to 1000L), trend.months[0].amounts)
        assertEquals(6000L, trend.months[0].total)
        assertEquals(listOf(daily to 4000L), trend.months[1].amounts)
    }

    @Test
    fun `a month without spending has no amounts and a zero total`() {
        val trend = CategoryTrend.from(
            labels = listOf("9月", "10月"),
            expensesPerMonth = listOf(emptyList(), listOf(expense(1, 800))),
            allCategories = listOf(food)
        )

        assertEquals(emptyList<Pair<Category, Long>>(), trend.months[0].amounts)
        assertEquals(0L, trend.months[0].total)
        assertEquals(800L, trend.months[1].total)
    }

    @Test
    fun `expenses in an unknown category are skipped`() {
        val trend = CategoryTrend.from(
            labels = listOf("10月"),
            expensesPerMonth = listOf(listOf(expense(1, 800), expense(99, 5000))),
            allCategories = listOf(food)
        )

        assertEquals(listOf(food), trend.categories)
        assertEquals(800L, trend.months[0].total)
    }

    @Test
    fun `equal totals fall back to category id order`() {
        val trend = CategoryTrend.from(
            labels = listOf("10月"),
            expensesPerMonth = listOf(listOf(expense(2, 1000), expense(1, 1000))),
            allCategories = listOf(food, daily)
        )

        assertEquals(listOf(food, daily), trend.categories)
    }
}
