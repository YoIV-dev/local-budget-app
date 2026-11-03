package com.localbudget.app.domain.manager

import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.repository.ExpenseRepository
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class RecurringExpenseManager(
    private val recurringRepository: RecurringExpenseRepository,
    private val expenseRepository: ExpenseRepository
) {

    /**
     * Checks all active recurring expenses for the given month, and generates an Expense
     * record for any that have not yet been posted in this month.
     *
     * @return Number of new expenses generated.
     */
    suspend fun applyRecurringExpensesForMonth(yearMonth: YearMonth): Int {
        val activeRecurring = recurringRepository.getActiveRecurringExpenses()
        if (activeRecurring.isEmpty()) return 0

        val existingExpenses = expenseRepository.getExpensesByMonth(yearMonth.year, yearMonth.monthValue).first()

        var generatedCount = 0

        for (recurring in activeRecurring) {
            val tag = "[固定支出#${recurring.id}]"
            val alreadyPosted = existingExpenses.any { it.memo.contains(tag) }

            if (!alreadyPosted) {
                val clampedDay = recurring.dayOfMonth.coerceIn(1, yearMonth.lengthOfMonth())
                val expenseDate = LocalDate.of(yearMonth.year, yearMonth.monthValue, clampedDay)

                val expense = Expense(
                    date = expenseDate,
                    amount = recurring.amount,
                    storeName = recurring.name,
                    categoryId = recurring.categoryId,
                    memo = "$tag ${recurring.memo}".trim(),
                    sourceType = SourceType.MANUAL
                )
                expenseRepository.insertExpense(expense)
                generatedCount++
            }
        }

        return generatedCount
    }
}
