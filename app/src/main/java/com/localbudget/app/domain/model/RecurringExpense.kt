package com.localbudget.app.domain.model

data class RecurringExpense(
    val id: Long = 0,
    val name: String,
    val amount: Long,
    val categoryId: Long,
    val category: Category? = null,
    val dayOfMonth: Int, // 1 to 31
    val isActive: Boolean = true,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
