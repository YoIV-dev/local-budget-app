package com.localbudget.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localbudget.app.domain.model.Budget
import com.localbudget.app.domain.model.Category
import com.localbudget.app.domain.model.CategoryExpenseSummary
import com.localbudget.app.domain.model.CategoryTrend
import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.model.MonthlySummary
import com.localbudget.app.domain.model.ReceiptLineItem
import com.localbudget.app.data.statement.SubscriptionSelections
import com.localbudget.app.domain.model.SourceType
import com.localbudget.app.domain.repository.BudgetRepository
import com.localbudget.app.domain.repository.CategoryRepository
import com.localbudget.app.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    val currentMonth: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now()),
    private val subscriptionSelections: SubscriptionSelections
) : ViewModel() {

    // Home is preloaded at launch (see AppNavGraph), so everything here starts eagerly and is ready
    // by the time it is shown. Read once and shared by the list, the summary and the trend.
    private val categoriesFlow = categoryRepository.getAllCategories()
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val categories: StateFlow<List<Category>> = categoriesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private data class MonthView(val summary: MonthlySummary, val trend: CategoryTrend, val expenses: List<Expense>)

    // The trend's months include the selected and previous ones, so a single range read serves both
    // the summary and the trend; only the same month last year is read on its own.
    private val monthView: SharedFlow<MonthView> = currentMonth.flatMapLatest { ym ->
        val months = (TREND_MONTHS - 1 downTo 0).map { ym.minusMonths(it.toLong()) }
        val lastYear = ym.minusYears(1)
        combine(
            expenseRepository.getExpensesByDateRange(months.first().atDay(1), ym.atEndOfMonth()),
            expenseRepository.getExpensesByMonth(lastYear.year, lastYear.monthValue),
            budgetRepository.getBudgetForMonth(yearMonthString(ym)),
            categoriesFlow
        ) { rangeExpenses, lastYearExpenses, budget, categoriesList ->
            val byMonth = rangeExpenses.groupBy { YearMonth.from(it.date) }
            MonthView(
                summary = summaryOf(
                    ym,
                    expenses = byMonth[ym].orEmpty(),
                    budget = budget,
                    categoriesList = categoriesList,
                    previousExpenses = byMonth[ym.minusMonths(1)].orEmpty(),
                    lastYearExpenses = lastYearExpenses
                ),
                trend = CategoryTrend.from(months.map { "${it.monthValue}月" }, months.map { byMonth[it].orEmpty() }, categoriesList),
                expenses = byMonth[ym].orEmpty()
            )
        }
    }.flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val monthlySummary: StateFlow<MonthlySummary?> = monthView.map { it.summary }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val monthExpenses = monthView.map { it.expenses }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cardExpenses = expenseRepository.getAllExpenses().map { expenses ->
        expenses.filter { it.sourceType == SourceType.CARD_CSV && it.storeName.isNotBlank() && it.amount > 0 }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val subscriptionKeys = subscriptionSelections.keys
    fun setSubscriptions(keys: Set<String>) = subscriptionSelections.set(keys)

    /** Spending by category for the six months up to the selected one, oldest first, labelled like "10月". */
    val monthlyTrend: StateFlow<CategoryTrend> = monthView.map { it.trend }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CategoryTrend.EMPTY)

    private fun summaryOf(
        ym: YearMonth,
        expenses: List<Expense>,
        budget: Budget?,
        categoriesList: List<Category>,
        previousExpenses: List<Expense>,
        lastYearExpenses: List<Expense>
    ): MonthlySummary {
        val totalExpense = expenses.sumOf { it.amount }
        val budgetAmount = budget?.totalBudget

        val catMap = categoriesList.associateBy { it.id }
        val categoryTotals = expenses.groupBy { it.categoryId }.mapNotNull { (catId, catExpenses) ->
            val category = catMap[catId] ?: return@mapNotNull null
            val amount = catExpenses.sumOf { it.amount }
            CategoryExpenseSummary(
                category = category,
                totalAmount = amount,
                count = catExpenses.size,
                percentage = if (totalExpense > 0) amount.toFloat() / totalExpense.toFloat() else 0f
            )
        }.sortedByDescending { it.totalAmount }

        val remaining = budgetAmount?.let { it - totalExpense }
        val rate = budgetAmount?.let { if (it > 0) totalExpense.toFloat() / it.toFloat() else null }

        return MonthlySummary(
            yearMonth = yearMonthString(ym),
            totalExpense = totalExpense,
            budgetAmount = budgetAmount,
            remainingBudget = remaining,
            budgetUsageRate = rate,
            categorySummaries = categoryTotals,
            previousMonthTotal = previousExpenses.sumOf { it.amount },
            lastYearTotal = lastYearExpenses.sumOf { it.amount },
            remainingDays = MonthlySummary.remainingDaysOf(ym, LocalDate.now())
        )
    }

    private fun yearMonthString(ym: YearMonth) = String.format("%04d-%02d", ym.year, ym.monthValue)

    fun nextMonth() {
        currentMonth.value = currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        currentMonth.value = currentMonth.value.minusMonths(1)
    }

    fun setMonth(month: YearMonth) { currentMonth.value = month }

    fun setBudget(amount: Long) {
        viewModelScope.launch {
            budgetRepository.setBudget(Budget(yearMonthString(currentMonth.value), amount))
        }
    }

    /** [editedItems]: the saved receipt lines with prices the user corrected, null to keep them as they are. */
    fun saveExpense(expense: Expense, editedItems: List<ReceiptLineItem>? = null) {
        viewModelScope.launch {
            // insertExpense replaces the row, which would also delete the receipt lines saved with it
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

    private companion object {
        const val TREND_MONTHS = 6
    }

    class Factory(
        private val expenseRepository: ExpenseRepository,
        private val categoryRepository: CategoryRepository,
        private val budgetRepository: BudgetRepository,
        private val currentMonth: MutableStateFlow<YearMonth>,
        private val subscriptionSelections: SubscriptionSelections
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(expenseRepository, categoryRepository, budgetRepository, currentMonth, subscriptionSelections) as T
        }
    }
}
