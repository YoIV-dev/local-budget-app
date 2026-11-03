package com.localbudget.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.localbudget.app.domain.model.ReceiptLineItem

/** One line read from the receipt of an expense, in printed order. Deleted with its expense. */
@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["expense_id"])]
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "expense_id")
    val expenseId: Long,
    val position: Int,
    val name: String,
    val price: Long?,
    val quantity: Int,
    @ColumnInfo(name = "unit_price")
    val unitPrice: Long?,
    val discount: Long
) {
    fun toDomain(): ReceiptLineItem = ReceiptLineItem(
        name = name,
        price = price,
        quantity = quantity,
        unitPrice = unitPrice,
        discount = discount
    )

    companion object {
        fun fromDomain(expenseId: Long, position: Int, item: ReceiptLineItem): ReceiptItemEntity = ReceiptItemEntity(
            expenseId = expenseId,
            position = position,
            name = item.name,
            price = item.price,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            discount = item.discount
        )
    }
}
