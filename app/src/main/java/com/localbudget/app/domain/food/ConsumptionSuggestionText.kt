package com.localbudget.app.domain.food

import com.localbudget.app.domain.food.ConsumptionEstimator.Kind
import com.localbudget.app.domain.food.ConsumptionEstimator.Reason
import com.localbudget.app.domain.food.ConsumptionEstimator.Suggestion
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Wording for consumption prompts: the question, and the numbers that led to it. */
object ConsumptionSuggestionText {

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    fun question(kind: Kind): String = when (kind) {
        Kind.LIKELY_CONSUMED -> "使い切った？"
        Kind.PAST_EXPIRY -> "捨てた？"
        Kind.UNSURE -> "まだある？"
    }

    /** e.g. "購入から5日。目安は2日ほどです" or "10/6に同じ食材を買っています". */
    fun reason(s: Suggestion, shape: Double = 2.0): String {
        val main = when (s.reason) {
            Reason.REPURCHASED ->
                "${s.newerPurchase?.purchaseDate?.format(dateFormatter) ?: "最近"}に同じ食材を買っています"
            Reason.PAST_EXPIRY ->
                "目安の期限（${s.expiry.format(dateFormatter)}）を過ぎています"
            Reason.ELAPSED -> {
                val median = s.lifetime.medianDays(shape).roundToInt().coerceAtLeast(1)
                val since = if (s.item.storedOn != null) "${s.item.storage.placeName}に移してから" else "購入から"
                if (s.lifetime.isLearned) {
                    "$since${s.ageDays}日。ふだんは${median}日ほどで使い切っています（${s.lifetime.observed}回の記録）"
                } else {
                    "$since${s.ageDays}日。目安は${s.lifetime.priorDays}日ほどです"
                }
            }
        }
        val confirmed = s.confirmedOn?.let { "・${it.format(dateFormatter)}に確認済み" } ?: ""
        return main + confirmed
    }

    fun probability(p: Double): String = "${(p * 100).roundToInt()}%"
}
