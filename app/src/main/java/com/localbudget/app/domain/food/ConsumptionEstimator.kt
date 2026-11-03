package com.localbudget.app.domain.food

import com.localbudget.app.domain.model.FridgeEvent
import com.localbudget.app.domain.model.FridgeEventSource
import com.localbudget.app.domain.model.FridgeEventType
import com.localbudget.app.domain.model.FridgeItem
import com.localbudget.app.domain.model.StorageLocation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Estimates whether fridge items are probably used up, from how long they have been kept,
 * whether the same food was bought again, and the household's own history.
 *
 * Model: an item's time in the fridge T (days) is Weibull with fixed shape [shape] and a
 * per-food scale λ. The prior puts λ at the shelf-life table value (ShelfLife), worth
 * [priorWeight] observations. Every item the user marked consumed or discarded is an
 * observed lifetime. Any other item is right-censored at the last time it was known to be
 * there (added, confirmed in a stock check, or restored) — not at its current age, since an
 * item nobody has marked yet may well be gone, and counting it would stretch every estimate. With the
 * shape fixed, θ = λ^k has a conjugate inverse-gamma posterior:
 *   α = α₀ + (observed count),  β = β₀ + Σ tᵢ^k over observed and censored items,
 * and λ̂ = (β / (α − 1))^(1/k). So the table value dominates at first and the household's
 * own pace takes over as events accumulate.
 *
 * Suggestions are conditional on the last time the user said the item was there (a stock
 * check, an undone end or a move): P(T ≤ age | T > age at that check). So answering "まだある"
 * quiets an item until enough further time has passed, instead of it being asked about again at once.
 *
 * Time is counted per storage: moving an item (say, into the freezer) ends its stay in the old
 * storage as a censored lifetime there and starts a new one, and its age restarts from the move.
 * Frozen food is used at irregular times, so it is only asked about once past its expiry.
 *
 * Every suggestion carries the numbers behind it, so the UI can say why it is asking.
 */
class ConsumptionEstimator(
    private val shape: Double = 2.0,
    private val priorWeight: Double = 2.0
) {

    enum class Kind {
        /** Probably used up: ask "使い切った？" */
        LIKELY_CONSUMED,
        /** Past its estimated expiry but not clearly used: ask "捨てた？" */
        PAST_EXPIRY,
        /** In between: list in the weekly stock check */
        UNSURE
    }

    enum class Reason { REPURCHASED, ELAPSED, PAST_EXPIRY }

    /** Learned lifetime for one food in one storage. [scaleDays] is λ. */
    data class Lifetime(
        val foodKey: String,
        val storage: StorageLocation,
        val priorDays: Int,
        val scaleDays: Double,
        val observed: Int,
        val censored: Int
    ) {
        /** Days by which half of such items are gone. */
        fun medianDays(shape: Double): Double = scaleDays * ln(2.0).pow(1 / shape)
        val isLearned: Boolean get() = observed > 0
    }

    data class Suggestion(
        val item: FridgeItem,
        val kind: Kind,
        val reason: Reason,
        /** Probability the item is no longer in stock. */
        val probability: Double,
        val ageDays: Long,
        val lifetime: Lifetime,
        /** Estimated expiry: the item's own, or purchase date + table value. */
        val expiry: LocalDate,
        /** Last day the user said the item was still there, if ever. */
        val confirmedOn: LocalDate? = null,
        /** The newer purchase of the same food, for REPURCHASED. */
        val newerPurchase: FridgeItem? = null
    )

    /** An item to ask about in the stock check; [probability] is set when the estimator is unsure. */
    data class StockCheckEntry(
        val item: FridgeItem,
        val lastSeenOn: LocalDate,
        val probability: Double?
    )

    /**
     * Suggestions for the active items in [items], most confident first.
     * [items] should include ended items too, so repurchases and lifetimes can be found.
     */
    fun suggest(
        items: List<FridgeItem>,
        events: List<FridgeEvent>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Suggestion> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val lifetimes = learn(items, events, nowMillis)
        val confirmed = lastPresence(events, CONFIRMATIONS)
        val active = items.filter { it.isActive }
        val suggestions = active.mapNotNull { item ->
            val key = keyOf(item)
            val lifetime = lifetimes[key to item.storage] ?: priorOnly(key, item)
            val age = ChronoUnit.DAYS.between(item.storedSince, today).coerceAtLeast(0)
            val confirmedAt = confirmed[item.id]
            val confirmedOn = confirmedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            val seenAge = confirmedOn?.let { ChronoUnit.DAYS.between(item.storedSince, it).coerceAtLeast(0) } ?: 0
            val p = probabilityGoneSince(age.toDouble(), seenAge.toDouble(), lifetime.scaleDays)

            // A repurchase only counts if it came after the user last said this one was still there
            val newer = items
                .filter { it.id != item.id && keyOf(it) == key && it.purchaseDate > item.purchaseDate }
                .filter { confirmedAt == null || it.createdAt > confirmedAt }
                .minByOrNull { it.purchaseDate }
            val expiry = item.estimatedExpiry ?: item.storedSince.plusDays(lifetime.priorDays.toLong())
            val recheckExpiry = confirmedOn == null || ChronoUnit.DAYS.between(confirmedOn, today) >= RECHECK_DAYS
            val pastExpiry = today.isAfter(expiry) && recheckExpiry

            fun suggestion(kind: Kind, reason: Reason, probability: Double = p) =
                Suggestion(item, kind, reason, probability, age, lifetime, expiry, confirmedOn, newer)
            when {
                item.storage == StorageLocation.FREEZER ->
                    if (pastExpiry) suggestion(Kind.PAST_EXPIRY, Reason.PAST_EXPIRY) else null
                newer != null -> suggestion(Kind.LIKELY_CONSUMED, Reason.REPURCHASED, maxOf(p, REPURCHASE_PROBABILITY))
                p >= LIKELY_THRESHOLD -> suggestion(Kind.LIKELY_CONSUMED, Reason.ELAPSED)
                pastExpiry -> suggestion(Kind.PAST_EXPIRY, Reason.PAST_EXPIRY)
                p >= UNSURE_THRESHOLD -> suggestion(Kind.UNSURE, Reason.ELAPSED)
                else -> null
            }
        }
        return suggestions.sortedByDescending { it.probability }
    }

    /**
     * Items for the stock check: those the estimator is [Kind.UNSURE] about, then active items
     * nobody has looked at for [staleDays] days, oldest first. Items already shown as a
     * LIKELY_CONSUMED / PAST_EXPIRY prompt are left to that prompt.
     */
    fun stockCheck(
        items: List<FridgeItem>,
        events: List<FridgeEvent>,
        suggestions: List<Suggestion>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<StockCheckEntry> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val seen = lastPresence(events, PRESENCE)
        fun lastSeenOn(item: FridgeItem) =
            seen[item.id]?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: item.purchaseDate

        val prompted = suggestions.filter { it.kind != Kind.UNSURE }.map { it.item.id }.toSet()
        val unsure = suggestions.filter { it.kind == Kind.UNSURE }
            .map { StockCheckEntry(it.item, lastSeenOn(it.item), it.probability) }
        val listed = prompted + unsure.map { it.item.id }
        val stale = items
            .filter { it.isActive && it.id !in listed }
            .map { StockCheckEntry(it, lastSeenOn(it), null) }
            .filter { ChronoUnit.DAYS.between(it.lastSeenOn, today) >= staleDays(it.item.storage) }
            .sortedBy { it.lastSeenOn }
        return unsure + stale
    }

    /** P(T ≤ age) for a Weibull(shape, λ) lifetime. */
    fun probabilityGone(ageDays: Double, scaleDays: Double): Double =
        if (ageDays <= 0) 0.0 else 1 - exp(-(ageDays / scaleDays).pow(shape))

    /** P(T ≤ age | T > seenAge): gone by now, given it was still there at [seenAgeDays]. */
    fun probabilityGoneSince(ageDays: Double, seenAgeDays: Double, scaleDays: Double): Double =
        if (ageDays <= seenAgeDays) 0.0
        else 1 - exp(-((ageDays / scaleDays).pow(shape) - (seenAgeDays.coerceAtLeast(0.0) / scaleDays).pow(shape)))

    /**
     * Posterior lifetimes per (food key, storage) from the event log.
     * Only user/receipt events count: lifetimes the app itself ended (ESTIMATED) would feed
     * the model its own guesses.
     */
    fun learn(items: List<FridgeItem>, events: List<FridgeEvent>, nowMillis: Long): Map<Pair<String, StorageLocation>, Lifetime> {
        data class Sample(val key: String, val storage: StorageLocation, val days: Double, val observed: Boolean, val prior: Int)

        val samples = events.groupBy { it.itemId }.flatMap { (_, history) ->
            val ordered = history.sortedBy { it.occurredAt }
            val added = ordered.firstOrNull { it.type == FridgeEventType.ADDED } ?: return@flatMap emptyList()
            val last = ordered.last()
            // One stay per storage; a move ends the stay before it without the item being used up
            val stays = listOf(added) + ordered.filter { it.type == FridgeEventType.MOVED && it.occurredAt >= added.occurredAt }
            stays.mapIndexed { index, start ->
                val prior = ShelfLife.days(start.foodKey, start.category, start.storage)
                fun days(until: Long) = ((until - start.occurredAt).toDouble() / MILLIS_PER_DAY).coerceAtLeast(MIN_DAYS)
                val next = stays.getOrNull(index + 1)
                val ended = last.type == FridgeEventType.CONSUMED || last.type == FridgeEventType.DISCARDED
                when {
                    next != null -> Sample(start.foodKey, start.storage, days(next.occurredAt), false, prior)
                    ended && last.source != FridgeEventSource.ESTIMATED ->
                        Sample(start.foodKey, start.storage, days(last.occurredAt), true, prior)
                    else -> {
                        val lastSeen = ordered.last { it.type in PRESENCE && it.source != FridgeEventSource.ESTIMATED }
                        Sample(start.foodKey, start.storage, days(minOf(lastSeen.occurredAt, nowMillis)), false, prior)
                    }
                }
            }
        }

        return samples.groupBy { it.key to it.storage }.mapValues { (group, list) ->
            val prior = list.first().prior
            val observed = list.count { it.observed }
            val alpha = priorWeight + observed
            val beta = prior.toDouble().pow(shape) * (priorWeight - 1) + list.sumOf { it.days.pow(shape) }
            Lifetime(
                foodKey = group.first,
                storage = group.second,
                priorDays = prior,
                scaleDays = (beta / (alpha - 1)).pow(1 / shape),
                observed = observed,
                censored = list.size - observed
            )
        }
    }

    private fun priorOnly(key: String, item: FridgeItem): Lifetime {
        val prior = ShelfLife.days(key, item.category, item.storage)
        return Lifetime(key, item.storage, prior, prior.toDouble(), observed = 0, censored = 0)
    }

    /** Time of each item's latest user/receipt event of the given types. */
    private fun lastPresence(events: List<FridgeEvent>, types: Set<FridgeEventType>): Map<Long, Long> =
        events.filter { it.type in types && it.source != FridgeEventSource.ESTIMATED }
            .groupBy { it.itemId }
            .mapValues { (_, list) -> list.maxOf { it.occurredAt } }

    private fun keyOf(item: FridgeItem): String = item.normalizedName.ifBlank { FridgeItemEnricher.foodKey(item.name) }

    companion object {
        const val LIKELY_THRESHOLD = 0.8
        const val UNSURE_THRESHOLD = 0.4
        const val REPURCHASE_PROBABILITY = 0.9
        /** After "まだある" to a past-expiry item, ask again only this many days later. */
        const val RECHECK_DAYS = 3L
        /** Active items not looked at for this long go into the stock check. */
        const val STALE_DAYS = 7L
        /** Frozen food sits for weeks by design; a weekly check would only nag. */
        const val FREEZER_STALE_DAYS = 30L
        fun staleDays(storage: StorageLocation): Long =
            if (storage == StorageLocation.FREEZER) FREEZER_STALE_DAYS else STALE_DAYS
        private val PRESENCE = setOf(FridgeEventType.ADDED, FridgeEventType.CONFIRMED, FridgeEventType.REVERTED, FridgeEventType.MOVED)
        /** The user saying the item is there; ADDED is not a check, a receipt may be scanned days later. */
        private val CONFIRMATIONS = setOf(FridgeEventType.CONFIRMED, FridgeEventType.REVERTED, FridgeEventType.MOVED)
        private const val MILLIS_PER_DAY = 86_400_000.0
        /** Same-day consumption still counts as a short lifetime rather than zero. */
        private const val MIN_DAYS = 0.5
    }
}
