package com.localbudget.app.domain

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.CategoryExpenseSummary
import com.localbudget.app.domain.model.MonthlySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthlySummaryTest {

    @Test
    fun testMonthlySummaryCalculations() {
        val catFood = Category(id = 1, name = "食費", iconName = "Restaurant", colorHex = "#FF5722")
        val catDaily = Category(id = 2, name = "日用品", iconName = "ShoppingCart", colorHex = "#4CAF50")

        val foodSummary = CategoryExpenseSummary(
            category = catFood,
            totalAmount = 30000L,
            count = 15,
            percentage = 0.6f
        )

        val dailySummary = CategoryExpenseSummary(
            category = catDaily,
            totalAmount = 20000L,
            count = 5,
            percentage = 0.4f
        )

        val totalExpense = 50000L
        val budgetAmount = 80000L
        val remainingBudget = budgetAmount - totalExpense
        val usageRate = totalExpense.toFloat() / budgetAmount.toFloat()

        val summary = MonthlySummary(
            yearMonth = "2026-09",
            totalExpense = totalExpense,
            budgetAmount = budgetAmount,
            remainingBudget = remainingBudget,
            budgetUsageRate = usageRate,
            categorySummaries = listOf(foodSummary, dailySummary)
        )

        assertEquals("2026-09", summary.yearMonth)
        assertEquals(50000L, summary.totalExpense)
        assertEquals(80000L, summary.budgetAmount)
        assertEquals(30000L, summary.remainingBudget)
        assertNotNull(summary.budgetUsageRate)
        assertEquals(0.625f, summary.budgetUsageRate!!, 0.001f)
        assertEquals(2, summary.categorySummaries.size)
        assertEquals("食費", summary.categorySummaries[0].category.name)
    }

    @Test
    fun testZeroExpenseSummary() {
        val summary = MonthlySummary(
            yearMonth = "2026-09",
            totalExpense = 0L,
            budgetAmount = 100000L,
            remainingBudget = 100000L,
            budgetUsageRate = 0f,
            categorySummaries = emptyList()
        )

        assertEquals(0L, summary.totalExpense)
        assertEquals(100000L, summary.remainingBudget)
        assertEquals(0f, summary.budgetUsageRate)
    }

    private fun summary(total: Long, budget: Long? = null, previous: Long = 0, lastYear: Long = 0, days: Int = 0) =
        MonthlySummary(
            yearMonth = "2026-10",
            totalExpense = total,
            budgetAmount = budget,
            remainingBudget = budget?.let { it - total },
            budgetUsageRate = budget?.let { total.toFloat() / it },
            categorySummaries = emptyList(),
            previousMonthTotal = previous,
            lastYearTotal = lastYear,
            remainingDays = days
        )

    @Test
    fun `change rates compare with the previous month and the same month last year`() {
        val s = summary(total = 12000, previous = 10000, lastYear = 16000)

        assertEquals(0.2f, s.monthOverMonthRate!!, 0.0001f)
        assertEquals(-0.25f, s.yearOverYearRate!!, 0.0001f)
    }

    @Test
    fun `change rate is unknown when the earlier month had no spending`() {
        val s = summary(total = 12000, previous = 0, lastYear = 0)

        assertNull(s.monthOverMonthRate)
        assertNull(s.yearOverYearRate)
    }

    @Test
    fun `daily allowance splits what is left of the budget over the remaining days`() {
        assertEquals(10000L, summary(total = 10000, budget = 50000, days = 4).dailyAllowance)
        assertEquals(13333L, summary(total = 10000, budget = 50000, days = 3).dailyAllowance)
    }

    @Test
    fun `no daily allowance without a budget, days left or room in the budget`() {
        assertNull(summary(total = 10000, days = 4).dailyAllowance)
        assertNull(summary(total = 10000, budget = 50000, days = 0).dailyAllowance)
        assertNull(summary(total = 60000, budget = 50000, days = 4).dailyAllowance)
    }

    @Test
    fun `remaining days count today and cover whole future months`() {
        val today = LocalDate.of(2026, 10, 28)

        assertEquals(4, MonthlySummary.remainingDaysOf(YearMonth.of(2026, 10), today))
        assertEquals(30, MonthlySummary.remainingDaysOf(YearMonth.of(2026, 11), today))
        assertEquals(0, MonthlySummary.remainingDaysOf(YearMonth.of(2026, 9), today))
    }
}
