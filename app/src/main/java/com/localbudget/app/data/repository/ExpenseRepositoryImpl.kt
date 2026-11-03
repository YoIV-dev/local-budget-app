package com.localbudget.app.data.repository

import com.localbudget.app.data.local.dao.ExpenseDao
import com.localbudget.app.data.local.entity.ExpenseEntity
import com.localbudget.app.data.local.entity.ReceiptItemEntity
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class ExpenseRepositoryImpl(
    private val expenseDao: ExpenseDao
) : ExpenseRepository {

    override fun getAllExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllExpenses().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getRecentExpenses(limit: Int): Flow<List<Expense>> {
        return expenseDao.getRecentExpenses(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getExpensesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>> {
        return expenseDao.getExpensesByDateRange(startDate.toEpochDay(), endDate.toEpochDay()).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getExpensesByMonth(year: Int, month: Int): Flow<List<Expense>> {
        val ym = String.format("%04d-%02d", year, month)
        return expenseDao.getExpensesByYearMonth(ym).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getExpenseById(id: Long): Expense? {
        return expenseDao.getExpenseById(id)?.toDomain()
    }

    override suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(ExpenseEntity.fromDomain(expense))
    }

    override suspend fun insertExpenses(expenses: List<Expense>) {
        expenseDao.insertExpenses(expenses.map { ExpenseEntity.fromDomain(it) })
    }

    override suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(ExpenseEntity.fromDomain(expense))
    }

    override suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(ExpenseEntity.fromDomain(expense))
    }

    override suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteExpenseById(id)
    }

    override suspend fun insertExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>): Long {
        return expenseDao.insertExpenseWithItems(
            ExpenseEntity.fromDomain(expense),
            // expense_id is filled in once the expense has its id
            items.mapIndexed { index, item -> ReceiptItemEntity.fromDomain(0, index, item) }
        )
    }

    override suspend fun insertReceiptExpense(expense: Expense, items: List<ReceiptLineItem>): Long {
        return expenseDao.insertReceiptReplacingCardImport(
            ExpenseEntity.fromDomain(expense),
            items.mapIndexed { index, item -> ReceiptItemEntity.fromDomain(0, index, item) }
        )
    }

    override suspend fun updateExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>) {
        expenseDao.updateExpenseWithItems(
            ExpenseEntity.fromDomain(expense),
            items.mapIndexed { index, item -> ReceiptItemEntity.fromDomain(expense.id, index, item) }
        )
    }

    override suspend fun getReceiptItems(expenseId: Long): List<ReceiptLineItem> {
        return expenseDao.getReceiptItems(expenseId).map { it.toDomain() }
    }

    override suspend fun getAllReceiptItems(): Map<Long, List<ReceiptLineItem>> {
        return expenseDao.getAllReceiptItems().groupBy({ it.expenseId }, { it.toDomain() })
    }
}
