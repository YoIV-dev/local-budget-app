package com.localbudget.app.data

import com.localbudget.app.data.local.dao.ExpenseDao
import com.localbudget.app.data.local.entity.ExpenseEntity
import com.localbudget.app.data.local.entity.ExpenseWithCategory
import com.localbudget.app.data.local.entity.ReceiptItemEntity
import com.localbudget.app.data.repository.ExpenseRepositoryImpl
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.model.SourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpenseRepositoryTest {

    private val dao = InMemoryExpenseDao()
    private val repo = ExpenseRepositoryImpl(dao)

    private val expense = Expense(date = LocalDate.of(2026, 10, 8), amount = 674, storeName = "スーパー", categoryId = 1)
    private val milk = ReceiptLineItem(name = "牛乳", price = 476, quantity = 2, unitPrice = 238)
    private val cabbage = ReceiptLineItem(name = "ｷｬﾍﾞﾂ", price = 198, discount = 30)

    @Test
    fun itemsAreSavedUnderTheNewExpenseInPrintedOrder() = runTest {
        val id = repo.insertExpenseWithItems(expense, listOf(milk, cabbage))

        assertEquals(listOf(milk, cabbage), repo.getReceiptItems(id))
        assertEquals(listOf(0, 1), dao.items.map { it.position })
    }

    @Test
    fun allItemsAreGroupedByExpense() = runTest {
        val first = repo.insertExpenseWithItems(expense, listOf(milk))
        val second = repo.insertExpenseWithItems(expense, listOf(cabbage))
        repo.insertExpenseWithItems(expense, emptyList())

        assertEquals(mapOf(first to listOf(milk), second to listOf(cabbage)), repo.getAllReceiptItems())
    }

    @Test
    fun updatingAnExpenseKeepsItsItems() = runTest {
        val id = repo.insertExpenseWithItems(expense, listOf(milk))

        repo.updateExpense(expense.copy(id = id, amount = 700))

        assertEquals(listOf(milk), repo.getReceiptItems(id))
    }

    @Test
    fun correctedItemsReplaceTheSavedOnesOfThatExpenseOnly() = runTest {
        val id = repo.insertExpenseWithItems(expense, listOf(milk, cabbage))
        val other = repo.insertExpenseWithItems(expense, listOf(milk))
        val fixedCabbage = cabbage.withPrice(158)

        repo.updateExpenseWithItems(expense.copy(id = id, amount = 634), listOf(milk, fixedCabbage))

        assertEquals(listOf(milk, fixedCabbage), repo.getReceiptItems(id))
        assertEquals(listOf(0, 1), dao.items.filter { it.expenseId == id }.map { it.position })
        assertEquals(634L, repo.getExpenseById(id)?.amount)
        assertEquals(listOf(milk), repo.getReceiptItems(other))
    }

    @Test
    fun deletingAnExpenseDropsItsItems() = runTest {
        val id = repo.insertExpenseWithItems(expense, listOf(milk))

        repo.deleteExpenseById(id)

        assertTrue(repo.getReceiptItems(id).isEmpty())
    }

    @Test
    fun receiptTakesThePlaceOfTheCardStatementRowOfTheSamePurchase() = runTest {
        val cardRow = expense.copy(storeName = "ｲｵﾝﾘﾃｰﾙ", sourceType = SourceType.CARD_CSV)
        val cardId = repo.insertExpense(cardRow)
        val otherDayId = repo.insertExpense(cardRow.copy(date = expense.date.plusDays(1)))

        val receiptId = repo.insertReceiptExpense(expense.copy(sourceType = SourceType.RECEIPT_OCR), listOf(milk, cabbage))

        assertNull(repo.getExpenseById(cardId))
        assertEquals(setOf(otherDayId, receiptId), dao.expenses.map { it.id }.toSet())
        assertEquals(listOf(milk, cabbage), repo.getReceiptItems(receiptId))
    }

    @Test
    fun receiptLeavesHandEnteredExpensesAlone() = runTest {
        val manualId = repo.insertExpense(expense)

        repo.insertReceiptExpense(expense.copy(sourceType = SourceType.RECEIPT_OCR), listOf(milk))

        assertEquals(2, dao.expenses.size)
        assertEquals(674L, repo.getExpenseById(manualId)?.amount)
    }

    /** Mirrors the receipt_items foreign key: deleting an expense cascades to its lines. */
    private class InMemoryExpenseDao : ExpenseDao {
        val expenses = mutableListOf<ExpenseEntity>()
        val items = mutableListOf<ReceiptItemEntity>()

        override fun getAllExpenses(): Flow<List<ExpenseWithCategory>> = flowOf(expenses.map { ExpenseWithCategory(it, null) })
        override fun getRecentExpenses(limit: Int): Flow<List<ExpenseWithCategory>> =
            flowOf(expenses.take(limit).map { ExpenseWithCategory(it, null) })
        override fun getExpensesByDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<ExpenseWithCategory>> = getAllExpenses()
        override fun getExpensesByYearMonth(yearMonth: String): Flow<List<ExpenseWithCategory>> = getAllExpenses()
        override suspend fun getExpenseById(id: Long): ExpenseWithCategory? =
            expenses.find { it.id == id }?.let { ExpenseWithCategory(it, null) }

        override suspend fun insertExpense(expense: ExpenseEntity): Long {
            val id = (expenses.maxOfOrNull { it.id } ?: 0) + 1
            expenses.add(expense.copy(id = id))
            return id
        }

        override suspend fun insertExpenses(expenses: List<ExpenseEntity>) {
            expenses.forEach { insertExpense(it) }
        }

        override suspend fun findExpenseId(sourceType: String, epochDay: Long, amount: Long): Long? =
            this.expenses.filter { it.sourceType == sourceType && it.epochDay == epochDay && it.amount == amount }
                .minByOrNull { it.createdAt }?.id

        override suspend fun updateExpense(expense: ExpenseEntity) {
            expenses.replaceAll { if (it.id == expense.id) expense else it }
        }

        override suspend fun deleteExpense(expense: ExpenseEntity) = deleteExpenseById(expense.id)

        override suspend fun deleteExpenseById(id: Long) {
            expenses.removeAll { it.id == id }
            items.removeAll { it.expenseId == id }
        }

        override suspend fun insertReceiptItems(items: List<ReceiptItemEntity>) {
            this.items.addAll(items)
        }

        override suspend fun deleteReceiptItems(expenseId: Long) {
            items.removeAll { it.expenseId == expenseId }
        }

        override suspend fun getReceiptItems(expenseId: Long): List<ReceiptItemEntity> =
            items.filter { it.expenseId == expenseId }.sortedBy { it.position }

        override suspend fun getAllReceiptItems(): List<ReceiptItemEntity> =
            items.sortedWith(compareBy({ it.expenseId }, { it.position }))

        override suspend fun getExpensesBySource(sourceType: String): List<ExpenseEntity> =
            expenses.filter { it.sourceType == sourceType }

        override suspend fun updateStoreName(id: Long, storeName: String, updatedAt: Long) {
            val i = expenses.indexOfFirst { it.id == id }
            if (i >= 0) expenses[i] = expenses[i].copy(storeName = storeName, updatedAt = updatedAt)
        }
    }
}
