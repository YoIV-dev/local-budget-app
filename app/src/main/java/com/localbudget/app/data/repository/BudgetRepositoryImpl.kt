package com.localbudget.app.data.repository

import com.localbudget.app.data.local.dao.BudgetDao
import com.localbudget.app.data.local.entity.BudgetEntity
import com.localbudget.app.domain.model.Budget
import com.localbudget.app.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BudgetRepositoryImpl(
    private val budgetDao: BudgetDao
) : BudgetRepository {

    override fun getBudgetForMonth(yearMonth: String): Flow<Budget?> {
        return budgetDao.getBudgetForMonth(yearMonth).map { it?.toDomain() }
    }

    override suspend fun setBudget(budget: Budget) {
        budgetDao.setBudget(BudgetEntity.fromDomain(budget))
    }

    override suspend fun deleteBudget(yearMonth: String) {
        budgetDao.deleteBudget(yearMonth)
    }
}
