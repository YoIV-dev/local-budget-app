package com.localbudget.app.domain

import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.statement.SubscriptionChoiceRanking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SubscriptionChoiceRankingTest {
    private fun expense(date: String, store: String, amount: Long = 500) = Expense(
        date = LocalDate.parse(date), storeName = store, amount = amount, categoryId = 1
    )

    @Test
    fun recurringAndKnownServicesRiseWithoutRemovingOtherMerchants() {
        val choices = SubscriptionChoiceRanking.rank(listOf(
            expense("2026-10-03", "スーパー"),
            expense("2026-08-05", "動画サービス"), expense("2026-09-05", "動画サービス"), expense("2026-10-05", "動画サービス"),
            expense("2026-10-02", "Ｇｏｏｇｌｅ")
        ))
        assertEquals("動画サービス", choices.first().expense.storeName)
        assertEquals(3, choices.first().recurringMonths)
        assertTrue(choices[1].knownService)
        assertEquals("スーパー", choices.last().expense.storeName)
    }

    @Test
    fun differentAmountsAndMissingMonthsStayDistinct() {
        val choices = SubscriptionChoiceRanking.rank(listOf(
            expense("2026-08-05", "Amazon"), expense("2026-10-05", "amazon"),
            expense("2026-10-05", "amazon", 1000)
        ))
        assertEquals(2, choices.size)
        assertTrue(choices.all { it.recurringMonths == 1 })
    }
}
