package com.localbudget.app.domain.repository

import com.localbudget.app.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun getBudgetForMonth(yearMonth: String): Flow<Budget?>
    suspend fun setBudget(budget: Budget)
    suspend fun deleteBudget(yearMonth: String)
}
