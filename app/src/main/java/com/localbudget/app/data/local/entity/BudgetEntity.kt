package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.Budget

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "year_month")
    val yearMonth: String, // "YYYY-MM"
    @ColumnInfo(name = "total_budget")
    val totalBudget: Long
) {
    fun toDomain(): Budget = Budget(
        yearMonth = yearMonth,
        totalBudget = totalBudget
    )

    companion object {
        fun fromDomain(budget: Budget): BudgetEntity = BudgetEntity(
            yearMonth = budget.yearMonth,
            totalBudget = budget.totalBudget
        )
    }
}
