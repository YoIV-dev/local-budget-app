package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.Category

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "icon_name")
    val iconName: String,
    @ColumnInfo(name = "color_hex")
    val colorHex: String,
    @ColumnInfo(name = "budget_limit")
    val budgetLimit: Long? = null,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false
) {
    fun toDomain(): Category = Category(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        budgetLimit = budgetLimit,
        isDefault = isDefault
    )

    companion object {
        fun fromDomain(category: Category): CategoryEntity = CategoryEntity(
            id = category.id,
            name = category.name,
            iconName = category.iconName,
            colorHex = category.colorHex,
            budgetLimit = category.budgetLimit,
            isDefault = category.isDefault
        )
    }
}
