package com.localbudget.app.domain

import com.localbudget.app.domain.manager.RecurringExpenseManager
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.RecurringExpense
import com.localbudget.app.domain.repository.ExpenseRepository
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringExpenseManagerTest {

    private class FakeRecurringRepository(
        private val activeList: List<RecurringExpense>
    ) : RecurringExpenseRepository {
        override fun getAllRecurringExpenses(): Flow<List<RecurringExpense>> = flowOf(activeList)
        override suspend fun getActiveRecurringExpenses(): List<RecurringExpense> = activeList
        override suspend fun getRecurringExpenseById(id: Long): RecurringExpense? = activeList.find { it.id == id }
        override suspend fun insertRecurringExpense(recurringExpense: RecurringExpense): Long = 1L
        override suspend fun updateRecurringExpense(recurringExpense: RecurringExpense) {}
        override suspend fun deleteRecurringExpense(recurringExpense: RecurringExpense) {}
        override suspend fun deleteRecurringExpenseById(id: Long) {}
    }

    private class FakeExpenseRepository(
        val existingExpenses: MutableList<Expense>
    ) : ExpenseRepository {
        override fun getAllExpenses(): Flow<List<Expense>> = flowOf(existingExpenses)
        override fun getRecentExpenses(limit: Int): Flow<List<Expense>> = flowOf(existingExpenses.take(limit))
        override fun getExpensesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>> = flowOf(existingExpenses)
        override fun getExpensesByMonth(year: Int, month: Int): Flow<List<Expense>> {
            return flowOf(existingExpenses.filter { it.date.year == year && it.date.monthValue == month })
        }
        override suspend fun getExpenseById(id: Long): Expense? = existingExpenses.find { it.id == id }
        override suspend fun insertExpense(expense: Expense): Long {
            existingExpenses.add(expense)
            return expense.id
        }
        override suspend fun insertExpenses(expenses: List<Expense>) {
            existingExpenses.addAll(expenses)
        }
        override suspend fun updateExpense(expense: Expense) {}
        override suspend fun deleteExpense(expense: Expense) {}
        override suspend fun deleteExpenseById(id: Long) {}
        override suspend fun insertExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>): Long = insertExpense(expense)
        override suspend fun insertReceiptExpense(expense: Expense, items: List<ReceiptLineItem>): Long = insertExpense(expense)
        override suspend fun updateExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>) {}
        override suspend fun getReceiptItems(expenseId: Long): List<ReceiptLineItem> = emptyList()
        override suspend fun getAllReceiptItems(): Map<Long, List<ReceiptLineItem>> = emptyMap()
    }

    @Test
    fun testGenerateRecurringExpensesWhenNotPosted() = runTest {
        val rec1 = RecurringExpense(id = 1, name = "家賃", amount = 70000L, categoryId = 4, dayOfMonth = 27)
        val rec2 = RecurringExpense(id = 2, name = "Netflix", amount = 1490L, categoryId = 7, dayOfMonth = 15)

        val recurringRepo = FakeRecurringRepository(listOf(rec1, rec2))
        val expenseRepo = FakeExpenseRepository(mutableListOf())

        val manager = RecurringExpenseManager(recurringRepo, expenseRepo)

        val generatedCount = manager.applyRecurringExpensesForMonth(YearMonth.of(2026, 9))

        assertEquals(2, generatedCount)
        assertEquals(2, expenseRepo.existingExpenses.size)
        assertEquals("家賃", expenseRepo.existingExpenses[0].storeName)
        assertEquals(LocalDate.of(2026, 9, 27), expenseRepo.existingExpenses[0].date)
        assertEquals("Netflix", expenseRepo.existingExpenses[1].storeName)
        assertEquals(LocalDate.of(2026, 9, 15), expenseRepo.existingExpenses[1].date)
    }

    @Test
    fun testDoNotDuplicateAlreadyPostedRecurringExpense() = runTest {
        val rec1 = RecurringExpense(id = 1, name = "家賃", amount = 70000L, categoryId = 4, dayOfMonth = 27)

        val existingExpense = Expense(
            id = 100,
            date = LocalDate.of(2026, 9, 27),
            amount = 70000L,
            storeName = "家賃",
            categoryId = 4,
            memo = "[固定支出#1]"
        )

        val recurringRepo = FakeRecurringRepository(listOf(rec1))
        val expenseRepo = FakeExpenseRepository(mutableListOf(existingExpense))

        val manager = RecurringExpenseManager(recurringRepo, expenseRepo)

        val generatedCount = manager.applyRecurringExpensesForMonth(YearMonth.of(2026, 9))

        assertEquals(0, generatedCount)
        assertEquals(1, expenseRepo.existingExpenses.size)
    }
}
