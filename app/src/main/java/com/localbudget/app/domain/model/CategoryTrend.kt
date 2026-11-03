package com.localbudget.app.domain.model

/** Spending split by category for consecutive months, oldest first, for the stacked trend chart. */
data class CategoryTrend(
    /** Every category with spending in any of the months, the largest over the whole span first. */
    val categories: List<Category>,
    val months: List<MonthCategoryAmounts>
) {
    companion object {
        val EMPTY = CategoryTrend(emptyList(), emptyList())

        /** [labels] and [expensesPerMonth] are parallel, one entry per month; expenses in unknown categories are skipped. */
        fun from(labels: List<String>, expensesPerMonth: List<List<Expense>>, allCategories: List<Category>): CategoryTrend {
            val byId = allCategories.associateBy { it.id }
            val perMonth = expensesPerMonth.map { expenses ->
                expenses.groupBy { it.categoryId }.mapValues { (_, catExpenses) -> catExpenses.sumOf { it.amount } }
            }
            // One order for every month, so a category stays at the same height in each stack
            val categories = perMonth.flatMap { it.entries }
                .groupBy({ it.key }, { it.value })
                .mapNotNull { (catId, amounts) -> byId[catId]?.let { it to amounts.sum() } }
                .sortedWith(compareByDescending<Pair<Category, Long>> { it.second }.thenBy { it.first.id })
                .map { it.first }
            val months = labels.zip(perMonth) { label, totals ->
                MonthCategoryAmounts(label, categories.mapNotNull { category -> totals[category.id]?.let { category to it } })
            }
            return CategoryTrend(categories, months)
        }
    }
}

/** One month of a [CategoryTrend]: amounts in the trend's category order, categories without spending left out. */
data class MonthCategoryAmounts(
    val label: String,
    val amounts: List<Pair<Category, Long>>
) {
    val total: Long get() = amounts.sumOf { it.second }
}
