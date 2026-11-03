package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.RecurringExpense

@Entity(
    tableName = "recurring_expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["category_id"])
    ]
)
data class RecurringExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val amount: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "day_of_month")
    val dayOfMonth: Int,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    val memo: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(category: Category? = null): RecurringExpense = RecurringExpense(
        id = id,
        name = name,
        amount = amount,
        categoryId = categoryId,
        category = category,
        dayOfMonth = dayOfMonth,
        isActive = isActive,
        memo = memo,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(model: RecurringExpense): RecurringExpenseEntity = RecurringExpenseEntity(
            id = model.id,
            name = model.name,
            amount = model.amount,
            categoryId = model.categoryId,
            dayOfMonth = model.dayOfMonth,
            isActive = model.isActive,
            memo = model.memo,
            createdAt = model.createdAt
        )
    }
}

data class RecurringExpenseWithCategory(
    @Embedded
    val recurringExpense: RecurringExpenseEntity,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryEntity?
) {
    fun toDomain(): RecurringExpense {
        return recurringExpense.toDomain(category?.toDomain())
    }
}
