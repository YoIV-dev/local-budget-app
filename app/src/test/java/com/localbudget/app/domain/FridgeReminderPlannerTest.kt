package com.localbudget.app.domain

import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionEstimator.Lifetime
import com.localbudget.app.domain.food.ConsumptionEstimator.Reason
import com.localbudget.app.domain.food.ConsumptionEstimator.StockCheckEntry
import com.localbudget.app.domain.food.ConsumptionEstimator.Suggestion
import com.localbudget.app.domain.food.FridgeReminderPlanner
import com.localbudget.app.domain.food.FridgeReminderPlanner.State
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class FridgeReminderPlannerTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val milk = item(1, "牛乳")
    private val sprouts = item(2, "もやし")
    private val cabbage = item(3, "キャベツ")

    @Test
    fun newPromptsAreNotifiedGroupedByQuestion() {
        val plan = FridgeReminderPlanner.plan(
            listOf(prompt(milk), prompt(sprouts), prompt(cabbage, Kind.PAST_EXPIRY)), emptyList(), State(), today
        )
        assertEquals("冷蔵庫に確認したい食材が3件あります", plan.reminder?.title)
        assertEquals("牛乳、もやしを使い切った？\nキャベツを捨てた？", plan.reminder?.text)
        assertEquals(setOf("1:LIKELY_CONSUMED", "2:LIKELY_CONSUMED", "3:PAST_EXPIRY"), plan.state.notifiedKeys)
    }

    @Test
    fun unansweredPromptsAreNotRepeatedDaily() {
        val first = FridgeReminderPlanner.plan(listOf(prompt(milk)), emptyList(), State(), today)
        val next = FridgeReminderPlanner.plan(listOf(prompt(milk)), emptyList(), first.state, today.plusDays(1))
        assertNull(next.reminder)
    }

    @Test
    fun aNewPromptBringsBackTheUnansweredOnes() {
        val state = State(notifiedKeys = setOf("1:LIKELY_CONSUMED"))
        val plan = FridgeReminderPlanner.plan(listOf(prompt(milk), prompt(sprouts)), emptyList(), state, today)
        assertEquals("牛乳、もやしを使い切った？", plan.reminder?.text)
    }

    @Test
    fun aPromptThatDroppedOutCountsAsNewWhenItReturns() {
        val answered = FridgeReminderPlanner.plan(emptyList(), emptyList(), State(setOf("1:LIKELY_CONSUMED")), today)
        assertEquals(emptySet<String>(), answered.state.notifiedKeys)
        val back = FridgeReminderPlanner.plan(listOf(prompt(milk)), emptyList(), answered.state, today.plusDays(3))
        assertEquals("冷蔵庫に確認したい食材が1件あります", back.reminder?.title)
    }

    @Test
    fun aChangedQuestionForTheSameItemIsNew() {
        val state = State(notifiedKeys = setOf("3:LIKELY_CONSUMED"))
        val plan = FridgeReminderPlanner.plan(listOf(prompt(cabbage, Kind.PAST_EXPIRY)), emptyList(), state, today)
        assertEquals("キャベツを捨てた？", plan.reminder?.text)
    }

    @Test
    fun longListsAreCut() {
        val items = (1L..5L).map { prompt(item(it, "品$it")) }
        val plan = FridgeReminderPlanner.plan(items, emptyList(), State(), today)
        assertEquals("品1、品2、品3ほか2件を使い切った？", plan.reminder?.text)
    }

    @Test
    fun stockCheckIsBroughtUpWeekly() {
        val check = listOf(StockCheckEntry(cabbage, today.minusDays(8), null))
        val first = FridgeReminderPlanner.plan(emptyList(), check, State(), today)
        assertEquals("冷蔵庫の在庫チェック", first.reminder?.title)
        assertEquals("在庫チェック 1件（あるかどうか答えるだけ）", first.reminder?.text)
        assertEquals(today, first.state.lastStockCheckOn)

        assertNull(FridgeReminderPlanner.plan(emptyList(), check, first.state, today.plusDays(6)).reminder)
        assertEquals(
            "冷蔵庫の在庫チェック",
            FridgeReminderPlanner.plan(emptyList(), check, first.state, today.plusDays(7)).reminder?.title
        )
    }

    @Test
    fun dueStockCheckRidesAlongWithNewPrompts() {
        val check = listOf(StockCheckEntry(cabbage, today.minusDays(8), 0.5))
        val plan = FridgeReminderPlanner.plan(listOf(prompt(milk)), check, State(), today)
        assertEquals("牛乳を使い切った？\n在庫チェック 1件（あるかどうか答えるだけ）", plan.reminder?.text)
        assertEquals(today, plan.state.lastStockCheckOn)
    }

    @Test
    fun stockCheckNotDueIsLeftOutAndKeepsItsDate() {
        val lastWeek = today.minusDays(2)
        val check = listOf(StockCheckEntry(cabbage, today.minusDays(8), null))
        val plan = FridgeReminderPlanner.plan(listOf(prompt(milk)), check, State(lastStockCheckOn = lastWeek), today)
        assertEquals("牛乳を使い切った？", plan.reminder?.text)
        assertEquals(lastWeek, plan.state.lastStockCheckOn)
    }

    @Test
    fun unsureSuggestionsAreLeftToTheStockCheck() {
        val plan = FridgeReminderPlanner.plan(listOf(prompt(milk, Kind.UNSURE)), emptyList(), State(), today)
        assertNull(plan.reminder)
    }

    @Test
    fun delayRunsToTodaysReminderTimeOrTomorrows() {
        val zone = ZoneId.of("Asia/Tokyo")
        val morning = LocalDateTime.of(2026, 10, 10, 9, 30).atZone(zone)
        assertEquals(Duration.ofMinutes(8 * 60 + 30), FridgeReminderPlanner.delayUntilNext(morning))
        val evening = LocalDateTime.of(2026, 10, 10, 18, 0).atZone(zone)
        assertEquals(Duration.ofHours(24), FridgeReminderPlanner.delayUntilNext(evening))
        assertEquals(Duration.ofMinutes(45), FridgeReminderPlanner.delayUntilNext(morning, LocalTime.of(10, 15)))
        assertEquals(Duration.ofHours(23).plusMinutes(15), FridgeReminderPlanner.delayUntilNext(morning, LocalTime.of(8, 45)))
    }

    private fun item(id: Long, name: String) =
        FridgeItem(id = id, name = name, category = FoodCategory.OTHER, purchaseDate = today.minusDays(10))

    private fun prompt(item: FridgeItem, kind: Kind = Kind.LIKELY_CONSUMED) = Suggestion(
        item = item,
        kind = kind,
        reason = if (kind == Kind.PAST_EXPIRY) Reason.PAST_EXPIRY else Reason.ELAPSED,
        probability = 0.9,
        ageDays = 10,
        lifetime = Lifetime("", StorageLocation.FRIDGE, priorDays = 5, scaleDays = 5.0, observed = 0, censored = 0),
        expiry = today.minusDays(5)
    )
}
