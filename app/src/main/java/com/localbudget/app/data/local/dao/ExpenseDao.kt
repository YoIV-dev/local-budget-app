package com.localbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.localbudget.app.data.local.entity.ExpenseEntity
import com.localbudget.app.data.local.entity.ExpenseWithCategory
import com.localbudget.app.data.local.entity.ReceiptItemEntity
import com.localbudget.app.domain.model.SourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Transaction
    @Query("SELECT * FROM expenses ORDER BY epoch_day DESC, created_at DESC")
    fun getAllExpenses(): Flow<List<ExpenseWithCategory>>

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY epoch_day DESC, created_at DESC LIMIT :limit")
    fun getRecentExpenses(limit: Int): Flow<List<ExpenseWithCategory>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE epoch_day >= :startEpochDay AND epoch_day <= :endEpochDay ORDER BY epoch_day DESC, created_at DESC")
    fun getExpensesByDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<ExpenseWithCategory>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE year_month = :yearMonth ORDER BY epoch_day DESC, created_at DESC")
    fun getExpensesByYearMonth(yearMonth: String): Flow<List<ExpenseWithCategory>>

    @Transaction
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getExpenseById(id: Long): ExpenseWithCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Insert
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Insert
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Transaction
    suspend fun insertExpenseWithItems(expense: ExpenseEntity, items: List<ReceiptItemEntity>): Long {
        val id = insertExpense(expense)
        insertReceiptItems(items.map { it.copy(expenseId = id) })
        return id
    }

    /**
     * Saves a receipt. A card-statement row of the same day and amount is the same purchase, and the
     * receipt also has its items and the store as printed, so the receipt takes that row's place.
     */
    @Transaction
    suspend fun insertReceiptReplacingCardImport(expense: ExpenseEntity, items: List<ReceiptItemEntity>): Long {
        findExpenseId(SourceType.CARD_CSV.name, expense.epochDay, expense.amount)?.let { deleteExpenseById(it) }
        return insertExpenseWithItems(expense, items)
    }

    @Query("SELECT id FROM expenses WHERE source_type = :sourceType AND epoch_day = :epochDay AND amount = :amount ORDER BY created_at LIMIT 1")
    suspend fun findExpenseId(sourceType: String, epochDay: Long, amount: Long): Long?

    @Query("DELETE FROM receipt_items WHERE expense_id = :expenseId")
    suspend fun deleteReceiptItems(expenseId: Long)

    @Transaction
    suspend fun updateExpenseWithItems(expense: ExpenseEntity, items: List<ReceiptItemEntity>) {
        updateExpense(expense)
        deleteReceiptItems(expense.id)
        insertReceiptItems(items.map { it.copy(expenseId = expense.id) })
    }

    @Query("SELECT * FROM receipt_items WHERE expense_id = :expenseId ORDER BY position")
    suspend fun getReceiptItems(expenseId: Long): List<ReceiptItemEntity>

    @Query("SELECT * FROM expenses WHERE source_type = :sourceType")
    suspend fun getExpensesBySource(sourceType: String): List<ExpenseEntity>

    @Query("UPDATE expenses SET store_name = :storeName, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStoreName(id: Long, storeName: String, updatedAt: Long)

    /** Replaces an expense's receipt lines, numbering them again from 0. */
    @Transaction
    suspend fun replaceReceiptItems(expenseId: Long, items: List<ReceiptItemEntity>) {
        deleteReceiptItems(expenseId)
        insertReceiptItems(items.mapIndexed { i, item -> item.copy(id = 0, position = i) })
    }

    @Query("SELECT * FROM receipt_items ORDER BY expense_id, position")
    suspend fun getAllReceiptItems(): List<ReceiptItemEntity>
}
