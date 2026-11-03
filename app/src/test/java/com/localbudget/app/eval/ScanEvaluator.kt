package com.localbudget.app.eval

import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.ocr.ReceiptParser
import com.localbudget.app.domain.text.JapaneseTextNormalizer

/** Per-receipt outcome of running ReceiptParser + ReceiptFoodExtractor on its OCR output. */
data class SampleResult(
    val id: String,
    val conditions: Map<String, String>,
    val storeOk: Boolean,
    val dateOk: Boolean,
    val totalOk: Boolean,
    val predictedStore: String,
    val predictedTotal: Long,
    val truthFoodCount: Int,
    val predictedFoodCount: Int,
    val matchedFoodCount: Int,
    val categoryCorrectCount: Int,
    /** Matched food items whose truth has a price, and how many of those got exactly that price. */
    val pricedCount: Int,
    val priceCorrectCount: Int,
    val missed: List<String>,
    val spurious: List<String>,
    val miscategorized: List<String>,
    val wrongPrices: List<String>
)

/** Aggregate over a set of receipts. Ratios are NaN when their denominator is 0. */
data class ScanSummary(
    val receipts: Int,
    val storeAccuracy: Double,
    val dateAccuracy: Double,
    val totalAccuracy: Double,
    val foodPrecision: Double,
    val foodRecall: Double,
    val foodF1: Double,
    val categoryAccuracy: Double,
    val priceAccuracy: Double
)

object ScanEvaluator {

    /** Minimum normalized similarity for a predicted item name to count as a truth item. */
    const val ITEM_MATCH_THRESHOLD = 0.6

    fun evaluate(sample: ScanSample): SampleResult {
        val truth = sample.truth
        val parsed = ReceiptParser.parse(sample.parserInput)
        val predictedItems = ReceiptFoodExtractor.extractLines(sample.parserInput)
        val truthFood = truth.items.filter { it.isFood }

        // Greedy one-to-one matching, most similar pairs first
        val pairs = mutableListOf<Triple<Int, Int, Double>>()
        for ((pi, p) in predictedItems.withIndex()) {
            for ((ti, t) in truthFood.withIndex()) {
                val sim = similarity(p.item.name, t.name)
                if (sim >= ITEM_MATCH_THRESHOLD) pairs.add(Triple(pi, ti, sim))
            }
        }
        val usedPredicted = mutableSetOf<Int>()
        val usedTruth = mutableSetOf<Int>()
        val miscategorized = mutableListOf<String>()
        val wrongPrices = mutableListOf<String>()
        var categoryCorrect = 0
        var priced = 0
        var priceCorrect = 0
        for ((pi, ti, _) in pairs.sortedByDescending { it.third }) {
            if (pi in usedPredicted || ti in usedTruth) continue
            usedPredicted.add(pi)
            usedTruth.add(ti)
            val expected = truthFood[ti].category
            val actual = predictedItems[pi].category
            if (expected == null || expected == actual) {
                categoryCorrect++
            } else {
                miscategorized.add("${truthFood[ti].name}: ${expected.name}→${actual.name}")
            }
            val expectedPrice = truthFood[ti].price ?: continue
            val actualPrice = predictedItems[pi].item.price
            priced++
            if (actualPrice == expectedPrice) {
                priceCorrect++
            } else {
                wrongPrices.add("${truthFood[ti].name}: $expectedPrice→${actualPrice ?: "なし"}")
            }
        }

        return SampleResult(
            id = truth.id,
            conditions = truth.conditions,
            storeOk = storeMatches(parsed.storeName, truth.store),
            dateOk = parsed.date == truth.date,
            totalOk = parsed.amount == truth.total,
            predictedStore = parsed.storeName,
            predictedTotal = parsed.amount,
            truthFoodCount = truthFood.size,
            predictedFoodCount = predictedItems.size,
            matchedFoodCount = usedTruth.size,
            categoryCorrectCount = categoryCorrect,
            pricedCount = priced,
            priceCorrectCount = priceCorrect,
            missed = truthFood.indices.filter { it !in usedTruth }.map { truthFood[it].name },
            spurious = predictedItems.indices.filter { it !in usedPredicted }.map { predictedItems[it].item.name },
            miscategorized = miscategorized,
            wrongPrices = wrongPrices
        )
    }

    fun summarize(results: List<SampleResult>): ScanSummary {
        val n = results.size
        val predicted = results.sumOf { it.predictedFoodCount }
        val truth = results.sumOf { it.truthFoodCount }
        val matched = results.sumOf { it.matchedFoodCount }
        val precision = ratio(matched, predicted)
        val recall = ratio(matched, truth)
        val f1 = if (precision.isNaN() || recall.isNaN() || precision + recall == 0.0) {
            Double.NaN
        } else {
            2 * precision * recall / (precision + recall)
        }
        return ScanSummary(
            receipts = n,
            storeAccuracy = ratio(results.count { it.storeOk }, n),
            dateAccuracy = ratio(results.count { it.dateOk }, n),
            totalAccuracy = ratio(results.count { it.totalOk }, n),
            foodPrecision = precision,
            foodRecall = recall,
            foodF1 = f1,
            categoryAccuracy = ratio(results.sumOf { it.categoryCorrectCount }, matched),
            priceAccuracy = ratio(results.sumOf { it.priceCorrectCount }, results.sumOf { it.pricedCount })
        )
    }

    /** Summaries keyed by "condition=value" (e.g. "lighting=dim"), for every condition seen. */
    fun summarizeByCondition(results: List<SampleResult>): Map<String, ScanSummary> =
        results
            .flatMap { r -> r.conditions.map { (k, v) -> "$k=$v" to r } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .mapValues { (_, group) -> summarize(group) }

    /** Store names often gain/lose a branch suffix or punctuation, so containment either way counts. */
    fun storeMatches(predicted: String, truth: String): Boolean {
        val p = JapaneseTextNormalizer.matchKey(predicted)
        val t = JapaneseTextNormalizer.matchKey(truth)
        return p.isNotEmpty() && t.isNotEmpty() && (p.contains(t) || t.contains(p))
    }

    /** 1 − normalized Levenshtein distance over match keys (width/kana/whitespace-insensitive). */
    fun similarity(a: String, b: String): Double {
        val x = JapaneseTextNormalizer.matchKey(a)
        val y = JapaneseTextNormalizer.matchKey(b)
        val maxLen = maxOf(x.length, y.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(x, y).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private fun ratio(num: Int, den: Int): Double = if (den == 0) Double.NaN else num.toDouble() / den
}
