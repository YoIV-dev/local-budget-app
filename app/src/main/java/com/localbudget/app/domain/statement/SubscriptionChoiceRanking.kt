package com.localbudget.app.domain.statement

import com.localbudget.app.domain.model.Expense
import com.localbudget.app.domain.text.JapaneseTextNormalizer
import java.time.YearMonth

/** Ordering hints only: every entry remains available for the user to select. */
object SubscriptionChoiceRanking {
    data class Choice(val expense: Expense, val recurringMonths: Int, val knownService: Boolean) {
        val score: Int get() = (if (recurringMonths >= 2) 4 + minOf(recurringMonths, 6) else 0) +
            (if (knownService) 4 else 0) + (if (expense.amount in 1L..3000L) 1 else 0)
    }

    fun rank(expenses: List<Expense>): List<Choice> = expenses
        .groupBy { it.amount to JapaneseTextNormalizer.matchKey(it.storeName) }
        .values.map { entries ->
            val latest = entries.maxBy { it.date }
            val months = entries.map { YearMonth.from(it.date) }.toSet()
            var consecutive = 1
            var previous = YearMonth.from(latest.date).minusMonths(1)
            while (previous in months) {
                consecutive++
                previous = previous.minusMonths(1)
            }
            val name = JapaneseTextNormalizer.matchKey(latest.storeName)
            Choice(latest, consecutive, SERVICE_HINTS.any { name.contains(it) })
        }.sortedWith(compareByDescending<Choice> { it.score }
            .thenByDescending { it.expense.date }
            .thenBy { JapaneseTextNormalizer.matchKey(it.expense.storeName) }
            .thenBy { it.expense.amount })

    private val SERVICE_HINTS = listOf(
        "google", "グーグル", "amazon", "アマゾン", "apple", "アップル", "netflix", "ネットフリックス",
        "spotify", "スポティファイ", "youtube", "ユーチューブ", "disney", "ディズニー", "hulu", "フールー",
        "microsoft", "マイクロソフト", "adobe", "アドビ", "icloud", "prime", "プライム", "サブスク"
    ).map(JapaneseTextNormalizer::matchKey)
}
