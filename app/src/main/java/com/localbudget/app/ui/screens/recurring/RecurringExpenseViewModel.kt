package com.localbudget.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.domain.manager.RecurringExpenseManager
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.RecurringExpense
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.RecurringExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class RecurringExpenseViewModel(
    private val recurringRepository: RecurringExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val recurringManager: RecurringExpenseManager
) : ViewModel() {

    val recurringExpenses: StateFlow<List<RecurringExpense>> = recurringRepository.getAllRecurringExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val message = MutableStateFlow<String?>(null)

    init {
        // Automatically check and apply subscriptions on launch
        viewModelScope.launch {
            val count = recurringManager.applyRecurringExpensesForMonth(YearMonth.now())
            if (count > 0) {
                message.value = "今月の固定支出を${count}件自動計上しました"
            }
        }
    }

    fun saveRecurringExpense(item: RecurringExpense) {
        viewModelScope.launch {
            if (item.id == 0L) {
                recurringRepository.insertRecurringExpense(item)
            } else {
                recurringRepository.updateRecurringExpense(item)
            }
            recurringManager.applyRecurringExpensesForMonth(YearMonth.now())
        }
    }

    fun toggleActive(item: RecurringExpense) {
        viewModelScope.launch {
            recurringRepository.updateRecurringExpense(item.copy(isActive = !item.isActive))
        }
    }

    fun deleteRecurringExpense(id: Long) {
        viewModelScope.launch {
            recurringRepository.deleteRecurringExpenseById(id)
        }
    }

    fun clearMessage() {
        message.value = null
    }

    class Factory(
        private val recurringRepository: RecurringExpenseRepository,
        private val categoryRepository: CategoryRepository,
        private val recurringManager: RecurringExpenseManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecurringExpenseViewModel(recurringRepository, categoryRepository, recurringManager) as T
        }
    }
}
