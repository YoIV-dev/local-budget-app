package com.localbudget.app.data.repository

import com.localbudget.app.data.local.dao.RecurringExpenseDao
import com.localbudget.app.data.local.entity.RecurringExpenseEntity
import com.localbudget.app.domain.model.RecurringExpense
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecurringExpenseRepositoryImpl(
    private val recurringExpenseDao: RecurringExpenseDao
) : RecurringExpenseRepository {

    override fun getAllRecurringExpenses(): Flow<List<RecurringExpense>> {
        return recurringExpenseDao.getAllRecurringExpenses().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getActiveRecurringExpenses(): List<RecurringExpense> {
        return recurringExpenseDao.getActiveRecurringExpenses().map { it.toDomain() }
    }

    override suspend fun getRecurringExpenseById(id: Long): RecurringExpense? {
        return recurringExpenseDao.getRecurringExpenseById(id)?.toDomain()
    }

    override suspend fun insertRecurringExpense(recurringExpense: RecurringExpense): Long {
        return recurringExpenseDao.insertRecurringExpense(RecurringExpenseEntity.fromDomain(recurringExpense))
    }

    override suspend fun updateRecurringExpense(recurringExpense: RecurringExpense) {
        recurringExpenseDao.updateRecurringExpense(RecurringExpenseEntity.fromDomain(recurringExpense))
    }

    override suspend fun deleteRecurringExpense(recurringExpense: RecurringExpense) {
        recurringExpenseDao.deleteRecurringExpense(RecurringExpenseEntity.fromDomain(recurringExpense))
    }

    override suspend fun deleteRecurringExpenseById(id: Long) {
        recurringExpenseDao.deleteRecurringExpenseById(id)
    }
}
