package com.localbudget.app.domain

import com.localbudget.app.domain.food.ConsumptionEstimator
import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionEstimator.Reason
import com.localbudget.app.domain.food.FridgeItemEnricher
import com.localbudget.app.domain.food.ShelfLife
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeEvent
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.FridgeItemStatus
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ConsumptionEstimatorTest {

    private val estimator = ConsumptionEstimator()
    private val today = LocalDate.of(2026, 10, 7)
    private val now = millis(today) + 12 * HOUR

    @Test
    fun freshItemGetsNoSuggestion() {
        val milk = item(1, "牛乳 1L", FoodCategory.DAIRY, today.minusDays(1))

        assertTrue(estimator.suggest(listOf(milk), added(milk), now, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun oldItemIsLikelyConsumed() {
        // もやし keeps 2 days; after 5 days P(gone) = 1 − e^(−(5/2)²) ≈ 0.998
        val sprouts = item(1, "ﾓﾔｼ", FoodCategory.VEGETABLE, today.minusDays(5))

        val s = estimator.suggest(listOf(sprouts), added(sprouts), now, ZoneOffset.UTC).single()
        assertEquals(Kind.LIKELY_CONSUMED, s.kind)
        assertEquals(Reason.ELAPSED, s.reason)
        assertEquals(5L, s.ageDays)
        // Table value 2 days, plus the item itself censored at its add time (0.5-day floor)
        assertEquals(Math.sqrt(2.0 * 2.0 + 0.5 * 0.5), s.lifetime.scaleDays, 1e-9)
    }

    @Test
    fun repurchaseMarksOlderOneConsumed() {
        val oldMilk = item(1, "牛乳 1L", FoodCategory.DAIRY, today.minusDays(2))
        val newMilk = item(2, "成分無調整牛乳", FoodCategory.DAIRY, today)

        val suggestions = estimator.suggest(listOf(oldMilk, newMilk), added(oldMilk, newMilk), now, ZoneOffset.UTC)

        val s = suggestions.single()
        assertEquals(1L, s.item.id)
        assertEquals(Reason.REPURCHASED, s.reason)
        assertEquals(2L, s.newerPurchase?.id)
        assertTrue(s.probability >= ConsumptionEstimator.REPURCHASE_PROBABILITY)
    }

    @Test
    fun pastExpiryButNotClearlyUsedAsksAboutDiscarding() {
        // Expiry from the table is 3 days for meat, but the household's own history says meat lasts
        // ~12 days, so at day 5 it is past expiry without being likely consumed
        val meat = item(1, "豚ロース", FoodCategory.MEAT, today.minusDays(5))
        val history = (10L..14L).flatMap { n ->
            val past = item(100 + n, "豚ロース 切身", FoodCategory.MEAT, today.minusDays(60 + n), FridgeItemStatus.CONSUMED)
            listOf(addedEvent(past), endEvent(past, days = 12.0))
        }

        val s = estimator.suggest(listOf(meat), added(meat) + history, now, ZoneOffset.UTC).single()
        assertEquals(Kind.PAST_EXPIRY, s.kind)
        assertTrue(s.lifetime.isLearned)
        assertTrue(s.lifetime.scaleDays > 10)
    }

    @Test
    fun learningMovesFromTableValueTowardObservedLifetimes() {
        val events = (1L..20L).flatMap { n ->
            val milk = item(n, "牛乳", FoodCategory.DAIRY, today.minusDays(100 + n), FridgeItemStatus.CONSUMED)
            listOf(addedEvent(milk), endEvent(milk, days = 3.0))
        }
        val key = FridgeItemEnricher.foodKey("牛乳")

        val lifetime = estimator.learn(emptyList(), events, now).getValue(key to StorageLocation.FRIDGE)

        assertEquals(7, lifetime.priorDays)
        assertEquals(20, lifetime.observed)
        // Posterior: θ = (7²·1 + 20·3²) / (2 + 20 − 1) → λ = √(229/21) ≈ 3.30, near the observed 3 days
        assertEquals(Math.sqrt(229.0 / 21), lifetime.scaleDays, 1e-9)
    }

    @Test
    fun confirmedPresenceCountsAsCensoredAndLengthensTheEstimate() {
        // A milk the user confirmed still there after 20 days says milk can last longer than 7 days
        val milk = item(1, "牛乳", FoodCategory.DAIRY, today.minusDays(21))
        val confirmed = event(milk, FridgeEventType.CONFIRMED, FridgeEventSource.USER, milk.createdAt + 20 * DAY)
        val lifetime = estimator.learn(listOf(milk), added(milk) + confirmed, now)
            .getValue(FridgeItemEnricher.foodKey("牛乳") to StorageLocation.FRIDGE)

        assertEquals(0, lifetime.observed)
        assertEquals(1, lifetime.censored)
        assertTrue(lifetime.scaleDays > 7)
    }

    @Test
    fun unmarkedItemsDoNotStretchTheEstimate() {
        // Never marked for 20 days: unknown whether it is still there, so it is censored at day 0
        val milk = item(1, "牛乳", FoodCategory.DAIRY, today.minusDays(20))
        val lifetime = estimator.learn(listOf(milk), added(milk), now)
            .getValue(FridgeItemEnricher.foodKey("牛乳") to StorageLocation.FRIDGE)

        assertEquals(7.0, lifetime.scaleDays, 0.1)
    }

    @Test
    fun estimatedEndingsAreNotLearnedFrom() {
        val milk = item(1, "牛乳", FoodCategory.DAIRY, today.minusDays(30), FridgeItemStatus.CONSUMED)
        val events = listOf(addedEvent(milk), endEvent(milk, days = 1.0, source = FridgeEventSource.ESTIMATED))

        val lifetime = estimator.learn(listOf(milk), events, now).values.single()
        assertEquals(0, lifetime.observed)
        assertEquals(1, lifetime.censored)
    }

    @Test
    fun frozenItemsUseFreezerShelfLife() {
        val frozen = item(1, "冷凍 鶏もも肉", FoodCategory.MEAT, today.minusDays(10), storage = StorageLocation.FREEZER)

        assertTrue(estimator.suggest(listOf(frozen), added(frozen), now, ZoneOffset.UTC).isEmpty())
        assertEquals(StorageLocation.FREEZER, FridgeItemEnricher.guessStorage("冷凍 鶏もも肉"))
        assertEquals(30, ShelfLife.days(FridgeItemEnricher.foodKey("鶏もも肉"), FoodCategory.MEAT, StorageLocation.FREEZER))
    }

    @Test
    fun frozenItemsAreOnlyAskedAboutPastExpiry() {
        val frozen = item(1, "冷凍 鶏もも肉", FoodCategory.MEAT, today.minusDays(40), storage = StorageLocation.FREEZER)
        // A newer purchase would mark a refrigerated item used up, but not a frozen one
        val fresh = item(2, "鶏もも肉", FoodCategory.MEAT, today)

        val s = estimator.suggest(listOf(frozen, fresh), added(frozen, fresh), now, ZoneOffset.UTC).single()
        assertEquals(1L, s.item.id)
        assertEquals(Kind.PAST_EXPIRY, s.kind)
    }

    @Test
    fun ageCountsFromTheMoveIntoTheCurrentStorage() {
        // Potatoes keep 30 days; 40 days after purchase but moved to the pantry 2 days ago
        val bought = item(1, "じゃがいも", FoodCategory.VEGETABLE, today.minusDays(40), storage = StorageLocation.FRIDGE)
        val moved = FridgeItemEnricher.enrich(
            bought.copy(storage = StorageLocation.PANTRY, storedOn = today.minusDays(2), estimatedExpiry = null)
        )
        val events = listOf(
            addedEvent(bought),
            event(moved, FridgeEventType.MOVED, FridgeEventSource.USER, millis(today.minusDays(2)) + 9 * HOUR)
        )

        assertTrue(estimator.suggest(listOf(bought), listOf(addedEvent(bought)), now, ZoneOffset.UTC).isNotEmpty())
        assertTrue(estimator.suggest(listOf(moved), events, now, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun aMoveSplitsTheHistoryIntoOneStayPerStorage() {
        val chicken = item(1, "鶏もも肉", FoodCategory.MEAT, today.minusDays(30), FridgeItemStatus.CONSUMED, StorageLocation.FRIDGE)
        val frozen = chicken.copy(storage = StorageLocation.FREEZER)
        val events = listOf(
            addedEvent(chicken),
            event(frozen, FridgeEventType.MOVED, FridgeEventSource.USER, chicken.createdAt + 2 * DAY),
            endEvent(frozen, days = 20.0)
        )

        val lifetimes = estimator.learn(listOf(chicken), events, now)
        val key = chicken.normalizedName
        // Two days in the fridge without being used up, then used after 18 days in the freezer
        val fridge = lifetimes.getValue(key to StorageLocation.FRIDGE)
        assertEquals(0, fridge.observed)
        assertEquals(1, fridge.censored)
        val freezer = lifetimes.getValue(key to StorageLocation.FREEZER)
        assertEquals(1, freezer.observed)
        assertEquals(Math.sqrt((30.0 * 30.0 + 18.0 * 18.0) / 2), freezer.scaleDays, 1e-9)
    }

    @Test
    fun frozenItemsAreStaleOnlyAfterAMonth() {
        val frozen = item(1, "冷凍 鶏もも肉", FoodCategory.MEAT, today.minusDays(10), storage = StorageLocation.FREEZER)

        assertTrue(estimator.stockCheck(listOf(frozen), added(frozen), emptyList(), now, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun endedItemsAreNotSuggested() {
        val used = item(1, "ﾓﾔｼ", FoodCategory.VEGETABLE, today.minusDays(10), FridgeItemStatus.CONSUMED)
        assertTrue(estimator.suggest(listOf(used), added(used), now, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun stillThereQuietsAnItemUntilMoreTimePasses() {
        val sprouts = item(1, "ﾓﾔｼ", FoodCategory.VEGETABLE, today.minusDays(5))
        val confirmedToday = confirm(sprouts, today)

        assertTrue(estimator.suggest(listOf(sprouts), added(sprouts) + confirmedToday, now, ZoneOffset.UTC).isEmpty())

        // Confirmed on day 1: P(gone by 5 | there at 1) is still high for a 2-day food.
        // (A later confirmation also lengthens the learned lifetime, so it is not used here.)
        val s = estimator.suggest(listOf(sprouts), added(sprouts) + confirm(sprouts, today.minusDays(4)), now, ZoneOffset.UTC)
            .single()
        assertEquals(Kind.LIKELY_CONSUMED, s.kind)
        assertEquals(today.minusDays(4), s.confirmedOn)
        assertEquals(estimator.probabilityGoneSince(5.0, 1.0, s.lifetime.scaleDays), s.probability, 1e-12)
    }

    @Test
    fun repurchaseBeforeTheLastConfirmationIsNotEvidence() {
        val oldMilk = item(1, "牛乳 1L", FoodCategory.DAIRY, today.minusDays(2))
        val newMilk = item(2, "成分無調整牛乳", FoodCategory.DAIRY, today.minusDays(1))
        val events = added(oldMilk, newMilk) + confirm(oldMilk, today)

        assertTrue(estimator.suggest(listOf(oldMilk, newMilk), events, now, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun pastExpiryIsAskedAgainOnlyAfterRecheckDays() {
        val meat = item(1, "豚ロース", FoodCategory.MEAT, today.minusDays(5))
        val history = (10L..14L).flatMap { n ->
            val past = item(100 + n, "豚ロース 切身", FoodCategory.MEAT, today.minusDays(60 + n), FridgeItemStatus.CONSUMED)
            listOf(addedEvent(past), endEvent(past, days = 12.0))
        }
        val base = added(meat) + history

        assertTrue(estimator.suggest(listOf(meat), base + confirm(meat, today.minusDays(1)), now, ZoneOffset.UTC).isEmpty())
        val again = estimator.suggest(
            listOf(meat), base + confirm(meat, today.minusDays(ConsumptionEstimator.RECHECK_DAYS)), now, ZoneOffset.UTC
        ).single()
        assertEquals(Kind.PAST_EXPIRY, again.kind)
    }

    @Test
    fun stockCheckListsUnsureThenStaleButNotPromptedItems() {
        // 牛乳 at exactly its 7-day table value: P ≈ 0.63, not yet past expiry → UNSURE
        val milk = item(1, "牛乳", FoodCategory.DAIRY, today.minusDays(7))
        // Butter keeps 60 days; 10 days and nobody has looked since it was added → stale
        val butter = item(2, "バター", FoodCategory.DAIRY, today.minusDays(10))
        // Prompted as LIKELY_CONSUMED, so it stays out of the stock check
        val sprouts = item(3, "ﾓﾔｼ", FoodCategory.VEGETABLE, today.minusDays(5))
        // Fresh → neither
        val egg = item(4, "たまご 10個", FoodCategory.OTHER, today.minusDays(1))
        val items = listOf(milk, butter, sprouts, egg)
        val events = added(*items.toTypedArray())

        val suggestions = estimator.suggest(items, events, now, ZoneOffset.UTC)
        assertEquals(Kind.UNSURE, suggestions.single { it.item.id == 1L }.kind)

        val check = estimator.stockCheck(items, events, suggestions, now, ZoneOffset.UTC)
        assertEquals(listOf(1L, 2L), check.map { it.item.id })
        assertTrue(check[0].probability!! in 0.4..0.8)
        assertNull(check[1].probability)
        assertEquals(today.minusDays(10), check[1].lastSeenOn)

        // Saying "ある" to the butter takes it off the list
        val after = events + confirm(butter, today)
        assertEquals(listOf(1L), estimator.stockCheck(items, after, estimator.suggest(items, after, now, ZoneOffset.UTC), now, ZoneOffset.UTC).map { it.item.id })
    }

    @Test
    fun conditionalProbabilityMatchesSurvivalRatio() {
        val k = 2.0
        val lambda = 6.0
        fun survival(t: Double) = Math.exp(-Math.pow(t / lambda, k))
        assertEquals(1 - survival(8.0) / survival(3.0), estimator.probabilityGoneSince(8.0, 3.0, lambda), 1e-12)
        assertEquals(estimator.probabilityGone(8.0, lambda), estimator.probabilityGoneSince(8.0, 0.0, lambda), 1e-12)
        assertEquals(0.0, estimator.probabilityGoneSince(3.0, 3.0, lambda), 0.0)
    }

    @Test
    fun probabilityFollowsWeibullCdf() {
        assertEquals(0.0, estimator.probabilityGone(0.0, 5.0), 0.0)
        assertEquals(1 - Math.exp(-1.0), estimator.probabilityGone(5.0, 5.0), 1e-12)
    }

    @Test
    fun shelfLifeOverridesAreDictionaryKeywords() {
        for (key in ShelfLife.overrideKeys) {
            // Each override must be reachable: its key is what matchKeyword returns for that food
            assertEquals(key, com.localbudget.app.domain.food.FoodDictionary.matchKeyword(key))
        }
        assertNull(com.localbudget.app.domain.food.FoodDictionary.matchKeyword("ﾎﾟﾃﾄﾁｯﾌﾟｽ"))
    }

    private fun item(
        id: Long,
        name: String,
        category: FoodCategory,
        purchaseDate: LocalDate,
        status: FridgeItemStatus = FridgeItemStatus.ACTIVE,
        storage: StorageLocation = FridgeItemEnricher.guessStorage(name)
    ) = FridgeItemEnricher.enrich(
        FridgeItem(
            id = id,
            name = name,
            category = category,
            purchaseDate = purchaseDate,
            status = status,
            storage = storage,
            createdAt = millis(purchaseDate) + 18 * HOUR
        )
    )

    private fun added(vararg items: FridgeItem) = items.map { addedEvent(it) }

    private fun confirm(item: FridgeItem, on: LocalDate) =
        event(item, FridgeEventType.CONFIRMED, FridgeEventSource.USER, millis(on) + 9 * HOUR)

    private fun addedEvent(item: FridgeItem) = event(item, FridgeEventType.ADDED, FridgeEventSource.RECEIPT, item.createdAt)

    private fun endEvent(item: FridgeItem, days: Double, source: FridgeEventSource = FridgeEventSource.USER) =
        event(item, FridgeEventType.CONSUMED, source, item.createdAt + (days * DAY).toLong())

    private fun event(item: FridgeItem, type: FridgeEventType, source: FridgeEventSource, at: Long) =
        FridgeEvent(
            itemId = item.id,
            foodKey = item.normalizedName,
            category = item.category,
            storage = item.storage,
            type = type,
            source = source,
            occurredAt = at
        )

    private fun millis(date: LocalDate) = date.toEpochDay() * DAY

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
