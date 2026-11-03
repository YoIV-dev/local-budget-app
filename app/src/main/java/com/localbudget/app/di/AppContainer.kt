package com.localbudget.app.di

import android.content.Context
import com.localbudget.app.data.backup.CsvExportManager
import com.localbudget.app.data.backup.JsonBackupManager
import com.localbudget.app.data.local.AppDatabase
import com.localbudget.app.data.ocr.ReceiptOcrEngine
import com.localbudget.app.data.ocr.SavedScanTidier
import com.localbudget.app.data.reminder.FridgeReminderScheduler
import com.localbudget.app.data.reminder.FridgeReminderSettings
import com.localbudget.app.data.scanlog.ScanLogSettings
import com.localbudget.app.data.scanlog.ScanLogStore
import com.localbudget.app.data.statement.CardStatementImporter
import com.localbudget.app.data.statement.SubscriptionSelections
import com.localbudget.app.data.repository.BudgetRepositoryImpl
import com.localbudget.app.data.repository.CategoryRepositoryImpl
import com.localbudget.app.data.repository.ExpenseRepositoryImpl
import com.localbudget.app.data.repository.FridgeRepositoryImpl
import com.localbudget.app.data.repository.RecurringExpenseRepositoryImpl
import com.localbudget.app.domain.manager.RecurringExpenseManager
import com.localbudget.app.domain.repository.BudgetRepository
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import com.localbudget.app.domain.repository.FridgeRepository
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import java.io.File

interface AppContainer {
    val categoryRepository: CategoryRepository
    val expenseRepository: ExpenseRepository
    val budgetRepository: BudgetRepository
    val recurringExpenseRepository: RecurringExpenseRepository
    val fridgeRepository: FridgeRepository
    val receiptOcrEngine: ReceiptOcrEngine
    val recurringExpenseManager: RecurringExpenseManager
    val jsonBackupManager: JsonBackupManager
    val csvExportManager: CsvExportManager
    val cardStatementImporter: CardStatementImporter
    val subscriptionSelections: SubscriptionSelections
    val scanLogSettings: ScanLogSettings
    val scanLogStore: ScanLogStore
    val fridgeReminderSettings: FridgeReminderSettings
    val fridgeReminderScheduler: FridgeReminderScheduler
    val savedScanTidier: SavedScanTidier
}

class AppDataContainer(private val context: Context) : AppContainer {
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepositoryImpl(database.expenseDao())
    }

    override val budgetRepository: BudgetRepository by lazy {
        BudgetRepositoryImpl(database.budgetDao())
    }

    override val recurringExpenseRepository: RecurringExpenseRepository by lazy {
        RecurringExpenseRepositoryImpl(database.recurringExpenseDao())
    }

    override val fridgeRepository: FridgeRepository by lazy {
        FridgeRepositoryImpl(database.fridgeDao())
    }

    override val receiptOcrEngine: ReceiptOcrEngine by lazy {
        ReceiptOcrEngine()
    }

    override val recurringExpenseManager: RecurringExpenseManager by lazy {
        RecurringExpenseManager(recurringExpenseRepository, expenseRepository)
    }

    override val jsonBackupManager: JsonBackupManager by lazy {
        JsonBackupManager(categoryRepository, expenseRepository, recurringExpenseRepository, budgetRepository, fridgeRepository)
    }

    override val csvExportManager: CsvExportManager by lazy {
        CsvExportManager(expenseRepository, categoryRepository)
    }

    override val cardStatementImporter: CardStatementImporter by lazy {
        CardStatementImporter(context.contentResolver, expenseRepository, categoryRepository)
    }

    override val subscriptionSelections: SubscriptionSelections by lazy { SubscriptionSelections(context) }

    override val scanLogSettings: ScanLogSettings by lazy {
        ScanLogSettings(context)
    }

    // Kept out of cloud backup (res/xml/backup_rules.xml, data_extraction_rules.xml)
    override val scanLogStore: ScanLogStore by lazy {
        ScanLogStore(File(context.filesDir, "scan_log"))
    }

    override val fridgeReminderSettings: FridgeReminderSettings by lazy {
        FridgeReminderSettings(context)
    }

    override val fridgeReminderScheduler: FridgeReminderScheduler by lazy {
        FridgeReminderScheduler(context, fridgeReminderSettings)
    }

    override val savedScanTidier: SavedScanTidier by lazy {
        SavedScanTidier(context, database.expenseDao(), database.fridgeDao())
    }
}
