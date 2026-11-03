package com.localbudget.app.domain.model

data class CategoryExpenseSummary(
    val category: Category,
    val totalAmount: Long,
    val count: Int,
    val percentage: Float // 0.0f to 1.0f
)
