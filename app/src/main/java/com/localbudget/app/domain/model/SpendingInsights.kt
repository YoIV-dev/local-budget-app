package com.localbudget.app.domain.model

import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeeklySpending(val start: LocalDate, val end: LocalDate, val amount: Long)

/** Evidence of monthly card billing, not a confirmed subscription or a future expense. */
data class SubscriptionCandidate(val storeName: String, val charges: List<Expense>) {
    val monthlyAmount: Long get() = charges.last().amount
}

data class SpendingInsights(
    val foodWeeks: List<WeeklySpending>,
    val subscriptions: List<SubscriptionCandidate>
) {
    val foodTotal: Long get() = foodWeeks.sumOf { it.amount }
    val subscriptionTotal: Long get() = subscriptions.sumOf { it.monthlyAmount }

    companion object {
        fun from(month: YearMonth, expenses: List<Expense>, categories: List<Category>): SpendingInsights {
            val foodIds = categories.filter { it.name == "食費" || (it.isDefault && it.id == 1L) }
                .map { it.id }.toSet()
            val foodByDate = expenses.filter { YearMonth.from(it.date) == month && it.categoryId in foodIds }
                .groupBy { it.date }.mapValues { (_, entries) -> entries.sumOf { it.amount } }
            val weeks = mutableListOf<WeeklySpending>()
            var start = month.atDay(1)
            while (start <= month.atEndOfMonth()) {
                val end = minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), month.atEndOfMonth())
                weeks += WeeklySpending(start, end, foodByDate.filterKeys { it >= start && it <= end }.values.sum())
                start = end.plusDays(1)
            }

            val candidates = expenses.filter {
                it.sourceType == SourceType.CARD_CSV &&
                    YearMonth.from(it.date) >= month.minusMonths(5) && YearMonth.from(it.date) <= month
            }.groupBy { JapaneseTextNormalizer.matchKey(it.storeName) }
                .filterKeys { it.isNotBlank() }
                .mapNotNull { (_, entries) ->
                    val byMonth = entries.groupBy { YearMonth.from(it.date) }
                    val current = byMonth[month]?.singleOrNull()?.takeIf { it.amount in 1L..3000L } ?: return@mapNotNull null
                    val charges = mutableListOf(current)
                    for (offset in 1L..5L) {
                        val previous = byMonth[month.minusMonths(offset)]?.singleOrNull() ?: break
                        val daysBetween = ChronoUnit.DAYS.between(previous.date, charges.last().date)
                        if (previous.amount != current.amount || daysBetween !in 23L..38L) break
                        charges += previous
                    }
                    if (charges.size < 3) null else SubscriptionCandidate(current.storeName, charges.reversed())
                }.sortedWith(compareByDescending<SubscriptionCandidate> { it.monthlyAmount }.thenBy { it.storeName })
            return SpendingInsights(weeks, candidates)
        }
    }
}
