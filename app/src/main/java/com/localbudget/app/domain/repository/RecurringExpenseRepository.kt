package com.localbudget.app.domain.repository

import com.localbudget.app.domain.model.RecurringExpense
import kotlinx.coroutines.flow.Flow

interface RecurringExpenseRepository {
    fun getAllRecurringExpenses(): Flow<List<RecurringExpense>>
    suspend fun getActiveRecurringExpenses(): List<RecurringExpense>
    suspend fun getRecurringExpenseById(id: Long): RecurringExpense?
    suspend fun insertRecurringExpense(recurringExpense: RecurringExpense): Long
    suspend fun updateRecurringExpense(recurringExpense: RecurringExpense)
    suspend fun deleteRecurringExpense(recurringExpense: RecurringExpense)
    suspend fun deleteRecurringExpenseById(id: Long)
}
