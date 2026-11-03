package com.localbudget.app.eval

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Writes report.md (for reading) and summary.json (for diffing runs) into [outDir]. */
object ScanReportWriter {

    fun write(
        outDir: File,
        results: List<SampleResult>,
        missingOcr: List<String>
    ) {
        outDir.mkdirs()
        File(outDir, "report.md").writeText(markdown(results, missingOcr))
        File(outDir, "summary.json").writeText(summaryJson(results).toString(2))
    }

    fun markdown(results: List<SampleResult>, missingOcr: List<String>): String = buildString {
        val overall = ScanEvaluator.summarize(results)
        appendLine("# スキャン精度レポート")
        appendLine()
        appendLine("対象 ${overall.receipts} 枚" + if (missingOcr.isNotEmpty()) "（ocr.txt 未取得 ${missingOcr.size} 枚: ${missingOcr.joinToString()}）" else "")
        appendLine()
        appendLine("| 区分 | 枚数 | 店名 | 日付 | 合計 | 食材 適合率 | 食材 再現率 | 食材 F1 | カテゴリ | 価格 |")
        appendLine("|---|---|---|---|---|---|---|---|---|---|")
        appendLine(row("全体", overall))
        for ((condition, summary) in ScanEvaluator.summarizeByCondition(results)) {
            appendLine(row(condition, summary))
        }

        val failures = results.filter {
            !it.storeOk || !it.dateOk || !it.totalOk ||
                it.missed.isNotEmpty() || it.spurious.isNotEmpty() || it.miscategorized.isNotEmpty() ||
                it.wrongPrices.isNotEmpty()
        }
        if (failures.isNotEmpty()) {
            appendLine()
            appendLine("## 誤りのあったレシート")
            for (r in failures) {
                appendLine()
                appendLine("### ${r.id}")
                if (!r.storeOk) appendLine("- 店名: 「${r.predictedStore}」")
                if (!r.dateOk) appendLine("- 日付: 不一致")
                if (!r.totalOk) appendLine("- 合計: ${r.predictedTotal}")
                if (r.missed.isNotEmpty()) appendLine("- 見逃し: ${r.missed.joinToString("、")}")
                if (r.spurious.isNotEmpty()) appendLine("- 誤検出: ${r.spurious.joinToString("、")}")
                if (r.miscategorized.isNotEmpty()) appendLine("- カテゴリ誤り: ${r.miscategorized.joinToString("、")}")
                if (r.wrongPrices.isNotEmpty()) appendLine("- 価格誤り: ${r.wrongPrices.joinToString("、")}")
            }
        }
    }

    fun summaryJson(results: List<SampleResult>): JSONObject {
        val byCondition = JSONObject()
        ScanEvaluator.summarizeByCondition(results).forEach { (k, v) -> byCondition.put(k, toJson(v)) }
        return JSONObject()
            .put("overall", toJson(ScanEvaluator.summarize(results)))
            .put("byCondition", byCondition)
            .put("receipts", JSONArray(results.map { r ->
                JSONObject()
                    .put("id", r.id)
                    .put("storeOk", r.storeOk)
                    .put("dateOk", r.dateOk)
                    .put("totalOk", r.totalOk)
                    .put("missed", JSONArray(r.missed))
                    .put("spurious", JSONArray(r.spurious))
                    .put("miscategorized", JSONArray(r.miscategorized))
                    .put("wrongPrices", JSONArray(r.wrongPrices))
            }))
    }

    private fun toJson(s: ScanSummary): JSONObject = JSONObject()
        .put("receipts", s.receipts)
        .put("storeAccuracy", jsonNumber(s.storeAccuracy))
        .put("dateAccuracy", jsonNumber(s.dateAccuracy))
        .put("totalAccuracy", jsonNumber(s.totalAccuracy))
        .put("foodPrecision", jsonNumber(s.foodPrecision))
        .put("foodRecall", jsonNumber(s.foodRecall))
        .put("foodF1", jsonNumber(s.foodF1))
        .put("categoryAccuracy", jsonNumber(s.categoryAccuracy))
        .put("priceAccuracy", jsonNumber(s.priceAccuracy))

    // JSON has no NaN; an undefined ratio is written as null
    private fun jsonNumber(v: Double): Any = if (v.isNaN()) JSONObject.NULL else v

    private fun row(label: String, s: ScanSummary): String =
        "| $label | ${s.receipts} | ${pct(s.storeAccuracy)} | ${pct(s.dateAccuracy)} | ${pct(s.totalAccuracy)} | " +
            "${pct(s.foodPrecision)} | ${pct(s.foodRecall)} | ${pct(s.foodF1)} | ${pct(s.categoryAccuracy)} | ${pct(s.priceAccuracy)} |"

    private fun pct(v: Double): String = if (v.isNaN()) "—" else String.format(Locale.ROOT, "%.1f%%", v * 100)
}
