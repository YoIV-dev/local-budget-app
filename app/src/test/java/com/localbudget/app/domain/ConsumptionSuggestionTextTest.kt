package com.localbudget.app.domain

import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionEstimator.Lifetime
import com.localbudget.app.domain.food.ConsumptionEstimator.Reason
import com.localbudget.app.domain.food.ConsumptionEstimator.Suggestion
import com.localbudget.app.domain.food.ConsumptionSuggestionText
import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.StorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ConsumptionSuggestionTextTest {

    private val milk = FridgeItem(name = "牛乳", category = FoodCategory.DAIRY, purchaseDate = LocalDate.of(2026, 10, 1))
    private val prior = Lifetime("ぎゅうにゅう", StorageLocation.FRIDGE, priorDays = 7, scaleDays = 7.0, observed = 0, censored = 0)

    @Test
    fun elapsedUsesTableValueUntilLearned() {
        val s = suggestion(Reason.ELAPSED, prior)
        assertEquals("購入から6日。目安は7日ほどです", ConsumptionSuggestionText.reason(s))
    }

    @Test
    fun elapsedUsesHouseholdMedianOnceLearned() {
        // median = λ·(ln 2)^(1/2) = 4.0 · 0.8326 ≈ 3.3 → 3
        val learned = prior.copy(scaleDays = 4.0, observed = 5)
        val s = suggestion(Reason.ELAPSED, learned)
        assertEquals("購入から6日。ふだんは3日ほどで使い切っています（5回の記録）", ConsumptionSuggestionText.reason(s))
    }

    @Test
    fun repurchaseNamesTheNewerPurchaseDate() {
        val newer = milk.copy(id = 2, purchaseDate = LocalDate.of(2026, 10, 6))
        val s = suggestion(Reason.REPURCHASED, prior, newer = newer)
        assertEquals("10/6に同じ食材を買っています", ConsumptionSuggestionText.reason(s))
    }

    @Test
    fun pastExpiryShowsExpiryAndConfirmation() {
        val s = suggestion(Reason.PAST_EXPIRY, prior, kind = Kind.PAST_EXPIRY, confirmedOn = LocalDate.of(2026, 10, 4))
        assertEquals("目安の期限（10/8）を過ぎています・10/4に確認済み", ConsumptionSuggestionText.reason(s))
        assertEquals("捨てた？", ConsumptionSuggestionText.question(Kind.PAST_EXPIRY))
    }

    @Test
    fun probabilityIsRoundedPercent() {
        assertEquals("85%", ConsumptionSuggestionText.probability(0.846))
    }

    private fun suggestion(
        reason: Reason,
        lifetime: Lifetime,
        kind: Kind = Kind.LIKELY_CONSUMED,
        newer: FridgeItem? = null,
        confirmedOn: LocalDate? = null
    ) = Suggestion(
        item = milk,
        kind = kind,
        reason = reason,
        probability = 0.85,
        ageDays = 6,
        lifetime = lifetime,
        expiry = LocalDate.of(2026, 10, 8),
        confirmedOn = confirmedOn,
        newerPurchase = newer
    )
}
