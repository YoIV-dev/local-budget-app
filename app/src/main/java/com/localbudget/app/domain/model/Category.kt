package com.localbudget.app.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val budgetLimit: Long? = null,
    val isDefault: Boolean = false
)
