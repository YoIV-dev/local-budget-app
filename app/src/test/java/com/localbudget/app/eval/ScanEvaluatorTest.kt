package com.localbudget.app.eval

import com.localbudget.app.domain.model.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScanEvaluatorTest {

    private val date = LocalDate.of(2026, 10, 1)

    private val ocrText = """
        ﾏﾙｴﾂ 新宿店
        2026/10/01 18:20
        ｷｬﾍﾞﾂ              ¥198
        国産豚ﾛｰｽ           ¥498
        ｻﾗﾀﾞﾁｷﾝ            ¥238
        ﾎﾟﾃﾄﾁｯﾌﾟｽ          ¥148
        合計                ¥1,082
    """.trimIndent()

    private val truth = ReceiptTruth(
        id = "r1",
        store = "マルエツ",
        date = date,
        total = 1082,
        conditions = mapOf("lighting" to "dim"),
        items = listOf(
            TruthItem("ｷｬﾍﾞﾂ", 198, isFood = true, category = FoodCategory.VEGETABLE),
            // Annotated as OTHER on purpose: the extractor says MEAT, so this counts as a category error
            TruthItem("国産豚ﾛｰｽ", 498, isFood = true, category = FoodCategory.OTHER),
            TruthItem("ｻﾗﾀﾞﾁｷﾝ", 238, isFood = true, category = FoodCategory.MEAT),
            TruthItem("ﾎﾟﾃﾄﾁｯﾌﾟｽ", 148, isFood = false),
            // Missing from the OCR text entirely → recall miss
            TruthItem("牛乳", 0, isFood = true, category = FoodCategory.DAIRY)
        )
    )

    @Test
    fun testEvaluateCountsMatchesMissesAndCategoryErrors() {
        val r = ScanEvaluator.evaluate(ScanSample(truth, ocrText))

        assertTrue(r.storeOk)  // "マルエツ 新宿店" contains "マルエツ" after NFKC
        assertTrue(r.dateOk)
        assertTrue(r.totalOk)
        assertEquals(4, r.truthFoodCount)
        assertEquals(3, r.predictedFoodCount)
        assertEquals(3, r.matchedFoodCount)
        assertEquals(2, r.categoryCorrectCount)
        assertEquals(listOf("牛乳"), r.missed)
        assertEquals(emptyList<String>(), r.spurious)
        assertEquals(listOf("国産豚ﾛｰｽ: OTHER→MEAT"), r.miscategorized)
    }

    @Test
    fun testSummarizeIsMicroAveraged() {
        val r = ScanEvaluator.evaluate(ScanSample(truth, ocrText))
        val wrongTotal = ScanEvaluator.evaluate(ScanSample(truth.copy(id = "r2", total = 999), ocrText))
        val s = ScanEvaluator.summarize(listOf(r, wrongTotal))

        assertEquals(2, s.receipts)
        assertEquals(0.5, s.totalAccuracy, 1e-9)
        assertEquals(1.0, s.foodPrecision, 1e-9)   // 6 matched / 6 predicted
        assertEquals(0.75, s.foodRecall, 1e-9)     // 6 matched / 8 truth
        assertEquals(2 * 0.75 / 1.75, s.foodF1, 1e-9)
        assertEquals(4.0 / 6, s.categoryAccuracy, 1e-9)
    }

    @Test
    fun testEmptyDenominatorsAreNaN() {
        val s = ScanEvaluator.summarize(emptyList())
        assertTrue(s.totalAccuracy.isNaN())
        assertTrue(s.foodF1.isNaN())
    }

    @Test
    fun testSummarizeByCondition() {
        val dim = ScanEvaluator.evaluate(ScanSample(truth, ocrText))
        val bright = ScanEvaluator.evaluate(
            ScanSample(truth.copy(id = "r2", total = 1, conditions = mapOf("lighting" to "normal")), ocrText)
        )
        val byCondition = ScanEvaluator.summarizeByCondition(listOf(dim, bright))

        assertEquals(setOf("lighting=dim", "lighting=normal"), byCondition.keys)
        assertEquals(1.0, byCondition.getValue("lighting=dim").totalAccuracy, 1e-9)
        assertEquals(0.0, byCondition.getValue("lighting=normal").totalAccuracy, 1e-9)
    }

    @Test
    fun testSimilarityAndStoreMatching() {
        assertEquals(1.0, ScanEvaluator.similarity("ｷｬﾍﾞﾂ", "キャベツ"), 1e-9)
        assertTrue(ScanEvaluator.similarity("国産豚ロース", "国産豚ロ一ス") >= ScanEvaluator.ITEM_MATCH_THRESHOLD) // OCR: ー→一
        assertFalse(ScanEvaluator.similarity("キャベツ", "牛乳") >= ScanEvaluator.ITEM_MATCH_THRESHOLD)
        assertTrue(ScanEvaluator.storeMatches("イオン幕張新都心店", "イオン"))
        assertFalse(ScanEvaluator.storeMatches("", "イオン"))
    }

    @Test
    fun testParseTruthJson() {
        val t = ScanDataset.parseTruth(
            "fallback-id",
            """
            {
              "store": "イオン",
              "date": "2026-09-20",
              "total": 2010,
              "conditions": {"lighting": "normal", "wrinkled": true},
              "items": [
                {"name": "国産牛こま切れ", "price": 980, "isFood": true, "category": "MEAT"},
                {"name": "ゴミ袋 45L", "price": 298, "isFood": false}
              ]
            }
            """.trimIndent()
        )
        assertEquals("fallback-id", t.id)
        assertEquals(LocalDate.of(2026, 9, 20), t.date)
        assertEquals(mapOf("lighting" to "normal", "wrinkled" to "true"), t.conditions)
        assertEquals(FoodCategory.MEAT, t.items[0].category)
        assertEquals(null, t.items[1].category)
    }
}
