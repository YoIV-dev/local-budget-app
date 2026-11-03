package com.localbudget.app.domain.model

import java.time.LocalDate

data class Expense(
    val id: Long = 0,
    val date: LocalDate,
    val amount: Long,
    val storeName: String,
    val categoryId: Long,
    val category: Category? = null,
    val memo: String = "",
    val sourceType: SourceType = SourceType.MANUAL,
    val hasReceiptImage: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
