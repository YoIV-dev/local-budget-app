package com.localbudget.app.ui.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class ExpenseResults(val month: YearMonth, val query: String, val categoryId: Long?, val grouped: Map<LocalDate, List<Expense>>)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    val currentMonth: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now())
) : ViewModel() {
    val searchQuery = MutableStateFlow("")
    val selectedCategoryId = MutableStateFlow<Long?>(null)
    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val results: StateFlow<ExpenseResults?> = combine(currentMonth, searchQuery, selectedCategoryId) { month, query, category ->
        Triple(month, query, category)
    }.flatMapLatest { (ym, query, catId) ->
        val source = if (query.isNotBlank()) expenseRepository.getAllExpenses()
            else expenseRepository.getExpensesByMonth(ym.year, ym.monthValue)
        source.map { expenses ->
            val grouped = expenses.filter { expense ->
                val matchesQuery = query.isBlank() ||
                        expense.storeName.contains(query, ignoreCase = true) ||
                        expense.memo.contains(query, ignoreCase = true) ||
                        (expense.category?.name?.contains(query, ignoreCase = true) == true)

                val matchesCategory = catId == null || expense.categoryId == catId

                matchesQuery && matchesCategory
            }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
                .groupBy { it.date }
            ExpenseResults(ym, query, catId, grouped)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
    }

    fun setMonth(month: YearMonth) { currentMonth.value = month }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(categoryId: Long?) {
        selectedCategoryId.value = if (selectedCategoryId.value == categoryId) null else categoryId
    }

    /** [editedItems]: the saved receipt lines with prices the user corrected, null to keep them as they are. */
    fun saveExpense(expense: Expense, editedItems: List<ReceiptLineItem>? = null) {
        viewModelScope.launch {
            if (expense.id == 0L) {
                expenseRepository.insertExpense(expense)
            } else if (editedItems != null) {
                expenseRepository.updateExpenseWithItems(expense, editedItems)
            } else {
                expenseRepository.updateExpense(expense)
            }
        }
    }

    suspend fun getReceiptItems(expenseId: Long): List<ReceiptLineItem> = expenseRepository.getReceiptItems(expenseId)

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            expenseRepository.deleteExpenseById(id)
        }
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
        private val currentMonth: MutableStateFlow<YearMonth>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExpenseViewModel(expenseRepository, categoryRepository, currentMonth) as T
        }
    }
}
