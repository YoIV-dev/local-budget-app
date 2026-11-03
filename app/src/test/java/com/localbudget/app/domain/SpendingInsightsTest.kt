package com.localbudget.app.domain

import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.model.SpendingInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SpendingInsightsTest {
    private val month = YearMonth.of(2026, 10)
    private val categories = listOf(Category(1, "食費", "Restaurant", "#FF5722"))

    private fun expense(date: String, amount: Long = 1000, store: String = "動画サービス", categoryId: Long = 7,
                        source: SourceType = SourceType.CARD_CSV) =
        Expense(date = LocalDate.parse(date), amount = amount, storeName = store, categoryId = categoryId, sourceType = source)

    private fun monthlyCharges() = listOf("2026-08-15", "2026-09-15", "2026-10-15").map { expense(it) }

    @Test
    fun `food weeks clip to month and sum all input sources without other categories`() {
        val expenses = listOf(
            expense("2026-09-30", 9999, categoryId = 1),
            expense("2026-10-01", 100, categoryId = 1),
            expense("2026-10-04", 200, categoryId = 1, source = SourceType.RECEIPT_OCR),
            expense("2026-10-05", 300, categoryId = 1, source = SourceType.MANUAL),
            expense("2026-10-31", 400, categoryId = 1),
            expense("2026-11-01", 9999, categoryId = 1),
            expense("2026-10-02", 9999)
        )
        val insights = SpendingInsights.from(month, expenses, categories)
        assertEquals(listOf(300L, 300L, 0L, 0L, 400L), insights.foodWeeks.map { it.amount })
        assertEquals(LocalDate.parse("2026-10-01"), insights.foodWeeks.first().start)
        assertEquals(LocalDate.parse("2026-10-04"), insights.foodWeeks.first().end)
        assertEquals(LocalDate.parse("2026-10-31"), insights.foodWeeks.last().end)
        assertEquals(1000L, insights.foodTotal)
    }

    @Test
    fun `leap day and six week month are covered exactly once`() {
        for (ym in listOf(YearMonth.of(2024, 2), YearMonth.of(2026, 3))) {
            val expenses = (1..ym.lengthOfMonth()).map { expense(ym.atDay(it).toString(), 1, categoryId = 1) }
            val insights = SpendingInsights.from(ym, expenses, categories)
            assertEquals(ym.lengthOfMonth().toLong(), insights.foodTotal)
            assertTrue(insights.foodWeeks.zipWithNext().all { (a, b) -> a.end.plusDays(1) == b.start })
            assertEquals(ym.atEndOfMonth(), insights.foodWeeks.last().end)
        }
        assertEquals(6, SpendingInsights.from(YearMonth.of(2026, 3), emptyList(), categories).foodWeeks.size)
    }

    @Test
    fun `three months of matching card charges produce one monthly amount not history total`() {
        val insights = SpendingInsights.from(month, monthlyCharges(), categories)
        assertEquals(1, insights.subscriptions.size)
        assertEquals(3, insights.subscriptions.single().charges.size)
        assertEquals(1000L, insights.subscriptionTotal)
    }

    @Test
    fun `monthly charges up to 3000 yen are candidates for any merchant`() {
        for (store in listOf("Google", "Amazon", "動画サービス")) {
            val dates = listOf("2026-08-15", "2026-09-15", "2026-10-15")
            assertEquals(1, SpendingInsights.from(month, dates.map { expense(it, 3000, store) }, categories).subscriptions.size)
            assertTrue(SpendingInsights.from(month, dates.map { expense(it, 3001, store) }, categories).subscriptions.isEmpty())
        }
    }

    @Test
    fun `merchant width case and spacing are normalized but different merchants stay separate`() {
        val charges = listOf(expense("2026-08-15", store = "ＳＥＲＶＩＣＥ"),
            expense("2026-09-15", store = "Service"), expense("2026-10-15", store = "service "))
        val result = SpendingInsights.from(month, charges + monthlyCharges(), categories)
        assertEquals(2, result.subscriptions.size)
        assertEquals(2000L, result.subscriptionTotal)
    }

    @Test
    fun `missing month old charges changed amounts and multiple monthly purchases are not candidates`() {
        val charges = monthlyCharges()
        val cases = listOf(
            charges.take(2),
            charges.drop(1),
            charges.filterNot { it.date.monthValue == 9 },
            charges.dropLast(1) + charges.last().copy(amount = 1200),
            charges + expense("2026-09-20"),
            charges + expense("2026-10-15", -1000),
            charges.map { it.copy(storeName = "　 ") },
            charges.map { it.copy(sourceType = SourceType.MANUAL) },
            charges.dropLast(1) + charges.last().copy(sourceType = SourceType.RECEIPT_OCR),
            charges.map { it.copy(amount = 0) }
        )
        cases.forEach { assertTrue(SpendingInsights.from(month, it, categories).subscriptions.isEmpty()) }
    }

    @Test
    fun `billing can fall on last day of different length months and across year boundary`() {
        val charges = listOf("2023-12-31", "2024-01-31", "2024-02-29").map { expense(it) }
        assertEquals(1, SpendingInsights.from(YearMonth.of(2024, 2), charges, categories).subscriptions.size)
    }

    @Test
    fun `irregular dates and future records do not create candidates`() {
        val charges = listOf("2026-08-01", "2026-09-30", "2026-10-01").map { expense(it) }
        assertTrue(SpendingInsights.from(month, charges, categories).subscriptions.isEmpty())
        assertTrue(SpendingInsights.from(YearMonth.of(2026, 9), monthlyCharges(), categories).subscriptions.isEmpty())
    }

    @Test
    fun `history stops at six months and default food category can be renamed`() {
        val charges = (0L..8L).map { expense(month.minusMonths(it).atDay(15).toString(), categoryId = 1) }
        val insights = SpendingInsights.from(month, charges, listOf(categories.single().copy(name = "食料品", isDefault = true)))
        assertEquals(6, insights.subscriptions.single().charges.size)
        assertEquals(1000L, insights.foodTotal)
        assertEquals(1000L, insights.subscriptionTotal)
    }
}
