package com.localbudget.app.domain.food

import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionEstimator.StockCheckEntry
import com.localbudget.app.domain.food.ConsumptionEstimator.Suggestion
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Decides what the daily fridge reminder says, if anything.
 *
 * A notification is only worth sending when something changed: a prompt the user has not been
 * notified about yet, or a stock check that has not been brought up for [STOCK_CHECK_INTERVAL_DAYS].
 * Prompts already notified are not repeated every day while they sit unanswered. One that drops
 * out (answered, or quieted by "まだある") and comes back later counts as new again.
 */
object FridgeReminderPlanner {

    /** What has been notified so far; kept between runs. */
    data class State(
        /** [keyOf] for every prompt that was current at the last run. */
        val notifiedKeys: Set<String> = emptySet(),
        val lastStockCheckOn: LocalDate? = null
    )

    data class Reminder(val title: String, val text: String)

    data class Plan(val reminder: Reminder?, val state: State)

    fun plan(
        suggestions: List<Suggestion>,
        stockCheck: List<StockCheckEntry>,
        state: State,
        today: LocalDate
    ): Plan {
        val prompts = suggestions.filter { it.kind != Kind.UNSURE }
        val keys = prompts.map(::keyOf).toSet()
        val hasNewPrompt = (keys - state.notifiedKeys).isNotEmpty()
        val stockCheckDue = stockCheck.isNotEmpty() && state.lastStockCheckOn.let {
            it == null || ChronoUnit.DAYS.between(it, today) >= STOCK_CHECK_INTERVAL_DAYS
        }

        // Unanswered prompts stay in the text once anything triggers a reminder
        val reminder = when {
            hasNewPrompt -> Reminder(
                title = "冷蔵庫に確認したい食材が${prompts.size}件あります",
                text = (promptLines(prompts) + listOfNotNull(stockCheckLine(stockCheck).takeIf { stockCheckDue }))
                    .joinToString("\n")
            )
            stockCheckDue -> Reminder(
                title = "冷蔵庫の在庫チェック",
                text = (listOfNotNull(stockCheckLine(stockCheck)) + promptLines(prompts)).joinToString("\n")
            )
            else -> null
        }
        val newState = State(
            notifiedKeys = keys,
            lastStockCheckOn = if (reminder != null && stockCheckDue) today else state.lastStockCheckOn
        )
        return Plan(reminder, newState)
    }

    /** Time from [now] until the next [at] (today if still ahead, else tomorrow). */
    fun delayUntilNext(now: ZonedDateTime, at: LocalTime = REMINDER_TIME): Duration {
        var next = now.with(at).truncatedTo(ChronoUnit.MINUTES)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    /** e.g. "牛乳、もやし、卵ほか2件を使い切った？". */
    private fun promptLines(prompts: List<Suggestion>): List<String> =
        listOf(Kind.LIKELY_CONSUMED, Kind.PAST_EXPIRY).mapNotNull { kind ->
            val names = prompts.filter { it.kind == kind }.map { it.item.name }.distinct()
            if (names.isEmpty()) return@mapNotNull null
            val shown = names.take(MAX_NAMES).joinToString("、")
            val more = if (names.size > MAX_NAMES) "ほか${names.size - MAX_NAMES}件" else ""
            "$shown${more}を${ConsumptionSuggestionText.question(kind)}"
        }

    private fun stockCheckLine(stockCheck: List<StockCheckEntry>): String? =
        if (stockCheck.isEmpty()) null else "在庫チェック ${stockCheck.size}件（あるかどうか答えるだけ）"

    private fun keyOf(s: Suggestion): String = "${s.item.id}:${s.kind.name}"

    /** Before dinner and the evening shopping trip. */
    val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)
    const val STOCK_CHECK_INTERVAL_DAYS = 7L
    private const val MAX_NAMES = 3
}
