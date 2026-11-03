package com.localbudget.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.localbudget.app.domain.model.Expense

data class ExpenseWithCategory(
    @Embedded
    val expense: ExpenseEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity?
) {
    fun toDomain(): Expense {
        return expense.toDomain(category?.toDomain())
    }
}
