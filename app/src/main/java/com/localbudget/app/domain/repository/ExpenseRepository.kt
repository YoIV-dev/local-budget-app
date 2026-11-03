package com.localbudget.app.domain.repository

import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExpenseRepository {
    fun getAllExpenses(): Flow<List<Expense>>
    /** The newest [limit] expenses, newest first. */
    fun getRecentExpenses(limit: Int): Flow<List<Expense>>
    fun getExpensesByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Expense>>
    fun getExpensesByMonth(year: Int, month: Int): Flow<List<Expense>>
    suspend fun getExpenseById(id: Long): Expense?
    suspend fun insertExpense(expense: Expense): Long
    suspend fun insertExpenses(expenses: List<Expense>)
    suspend fun updateExpense(expense: Expense)
    suspend fun deleteExpense(expense: Expense)
    suspend fun deleteExpenseById(id: Long)
    /** Inserts [expense] and the lines read from its receipt together. */
    suspend fun insertExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>): Long
    /** Like [insertExpenseWithItems], replacing a card-statement import of the same day and amount. */
    suspend fun insertReceiptExpense(expense: Expense, items: List<ReceiptLineItem>): Long
    /** Updates [expense] and replaces the receipt lines saved with it by [items]. */
    suspend fun updateExpenseWithItems(expense: Expense, items: List<ReceiptLineItem>)
    suspend fun getReceiptItems(expenseId: Long): List<ReceiptLineItem>
    /** Every saved receipt line, by expense id. */
    suspend fun getAllReceiptItems(): Map<Long, List<ReceiptLineItem>>
}
