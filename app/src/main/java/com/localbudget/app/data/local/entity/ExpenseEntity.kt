package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.SourceType
import java.time.LocalDate

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["epoch_day"]),
        Index(value = ["year_month"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "epoch_day")
    val epochDay: Long,
    @ColumnInfo(name = "year_month")
    val yearMonth: String, // "YYYY-MM"
    val amount: Long,
    @ColumnInfo(name = "store_name")
    val storeName: String,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val memo: String = "",
    @ColumnInfo(name = "source_type")
    val sourceType: String = SourceType.MANUAL.name,
    @ColumnInfo(name = "has_receipt_image")
    val hasReceiptImage: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(category: Category? = null): Expense = Expense(
        id = id,
        date = LocalDate.ofEpochDay(epochDay),
        amount = amount,
        storeName = storeName,
        categoryId = categoryId,
        category = category,
        memo = memo,
        sourceType = try {
            SourceType.valueOf(sourceType)
        } catch (_: Exception) {
            SourceType.MANUAL
        },
        hasReceiptImage = hasReceiptImage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(expense: Expense): ExpenseEntity {
            val ym = String.format("%04d-%02d", expense.date.year, expense.date.monthValue)
            return ExpenseEntity(
                id = expense.id,
                epochDay = expense.date.toEpochDay(),
                yearMonth = ym,
                amount = expense.amount,
                storeName = expense.storeName,
                categoryId = expense.categoryId,
                memo = expense.memo,
                sourceType = expense.sourceType.name,
                hasReceiptImage = expense.hasReceiptImage,
                createdAt = expense.createdAt,
                updatedAt = expense.updatedAt
            )
        }
    }
}
