package com.localbudget.app.domain.model

import java.time.LocalDate
import java.time.YearMonth

data class MonthlySummary(
    val yearMonth: String,
    val totalExpense: Long,
    val budgetAmount: Long?,
    val remainingBudget: Long?,
    val budgetUsageRate: Float?, // 0.0 to 1.0+
    val categorySummaries: List<CategoryExpenseSummary>,
    /** Spending in the month before, the base of [monthOverMonthRate]. */
    val previousMonthTotal: Long = 0,
    /** Spending in the same month a year earlier, the base of [yearOverYearRate]. */
    val lastYearTotal: Long = 0,
    /** Days left in the month counting today, see [remainingDaysOf]. */
    val remainingDays: Int = 0
) {
    /** Change from the previous month as a fraction (0.12 = 12% more), null when that month had no spending. */
    val monthOverMonthRate: Float? get() = changeRate(totalExpense, previousMonthTotal)

    /** Change from the same month last year as a fraction, null when that month had no spending. */
    val yearOverYearRate: Float? get() = changeRate(totalExpense, lastYearTotal)

    /** What can still be spent per remaining day within the budget, null without a budget, days or room left. */
    val dailyAllowance: Long?
        get() = remainingBudget?.takeIf { it > 0 && remainingDays > 0 }?.let { it / remainingDays }

    companion object {
        /** Days left in [month] counting [today]: all of it before it starts, none once it is over. */
        fun remainingDaysOf(month: YearMonth, today: LocalDate): Int {
            val thisMonth = YearMonth.from(today)
            return when {
                month < thisMonth -> 0
                month > thisMonth -> month.lengthOfMonth()
                else -> month.lengthOfMonth() - today.dayOfMonth + 1
            }
        }

        private fun changeRate(current: Long, previous: Long): Float? =
            if (previous > 0) (current - previous).toFloat() / previous.toFloat() else null
    }
}
