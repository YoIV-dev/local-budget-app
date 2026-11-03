package com.localbudget.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.localbudget.app.data.local.dao.BudgetDao
import com.localbudget.app.data.local.dao.CategoryDao
import com.localbudget.app.data.local.dao.ExpenseDao
import com.localbudget.app.data.local.dao.FridgeDao
import com.localbudget.app.data.local.dao.RecurringExpenseDao
import com.localbudget.app.data.local.entity.BudgetEntity
import com.localbudget.app.data.local.entity.CategoryEntity
import com.localbudget.app.data.local.entity.ExpenseEntity
import com.localbudget.app.data.local.entity.FridgeEventEntity
import com.localbudget.app.data.local.entity.FridgeItemEntity
import com.localbudget.app.data.local.entity.ReceiptItemEntity
import com.localbudget.app.data.local.entity.RecurringExpenseEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        BudgetEntity::class,
        RecurringExpenseEntity::class,
        FridgeItemEntity::class,
        FridgeEventEntity::class,
        ReceiptItemEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun fridgeDao(): FridgeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_budget.db"
                )
                    // v1/v2 were pre-release builds without exported schemas; from v3 on, add a Migration per version bump
                    .fallbackToDestructiveMigrationFrom(1, 2)
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class AppDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialCategories(database.categoryDao())
                    }
                }
            }

            private suspend fun populateInitialCategories(categoryDao: CategoryDao) {
                if (categoryDao.getCategoryCount() == 0) {
                    categoryDao.insertCategories(DefaultCategories.list)
                }
            }
        }
    }
}
