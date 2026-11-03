package com.localbudget.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.localbudget.app.data.local.entity.RecurringExpenseEntity
import com.localbudget.app.data.local.entity.RecurringExpenseWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {
    @Transaction
    @Query("SELECT * FROM recurring_expenses ORDER BY day_of_month ASC, id ASC")
    fun getAllRecurringExpenses(): Flow<List<RecurringExpenseWithCategory>>

    @Transaction
    @Query("SELECT * FROM recurring_expenses WHERE is_active = 1")
    suspend fun getActiveRecurringExpenses(): List<RecurringExpenseWithCategory>

    @Transaction
    @Query("SELECT * FROM recurring_expenses WHERE id = :id LIMIT 1")
    suspend fun getRecurringExpenseById(id: Long): RecurringExpenseWithCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpense(item: RecurringExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurringExpenses(items: List<RecurringExpenseEntity>)

    @Update
    suspend fun updateRecurringExpense(item: RecurringExpenseEntity)

    @Delete
    suspend fun deleteRecurringExpense(item: RecurringExpenseEntity)

    @Query("DELETE FROM recurring_expenses WHERE id = :id")
    suspend fun deleteRecurringExpenseById(id: Long)
}
