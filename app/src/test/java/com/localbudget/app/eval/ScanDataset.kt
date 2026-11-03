package com.localbudget.app.eval

import com.localbudget.app.domain.model.FoodCategory
import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptRowBuilder
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One line item as printed on the receipt (ground truth). */
data class TruthItem(
    val name: String,
    val price: Long? = null,
    val quantity: Int = 1,
    val isFood: Boolean,
    val category: FoodCategory? = null
)

/** Hand-annotated ground truth for one receipt (`truth.json`). */
data class ReceiptTruth(
    val id: String,
    val store: String,
    val date: LocalDate,
    val total: Long,
    val conditions: Map<String, String> = emptyMap(),
    val items: List<TruthItem> = emptyList()
)

/**
 * Ground truth paired with the on-device OCR output: `ocr.txt` (ML Kit's Text.text) and,
 * when present, `ocr_lines.json` (per-line boxes).
 */
data class ScanSample(
    val truth: ReceiptTruth,
    val ocrText: String,
    val ocrLines: List<OcrLine>? = null
) {
    /** What the app's parsers receive: rows rebuilt from line boxes, as ReceiptOcrEngine does. */
    val parserInput: String
        get() = ocrLines?.let { ReceiptRowBuilder.buildText(it) }?.ifEmpty { null } ?: ocrText
}

/**
 * Loads `<root>/<id>/truth.json` + `<root>/<id>/ocr.txt` (+ optional `ocr_lines.json`), see eval/README.md.
 * Receipts without ocr.txt yet are listed in [LoadResult.missingOcr] rather than failing.
 */
object ScanDataset {

    data class LoadResult(
        val samples: List<ScanSample>,
        val missingOcr: List<String>
    )

    fun load(root: File): LoadResult {
        val dirs = root.listFiles { f -> f.isDirectory && File(f, "truth.json").isFile }
            ?.sortedBy { it.name }
            .orEmpty()

        val samples = mutableListOf<ScanSample>()
        val missingOcr = mutableListOf<String>()
        for (dir in dirs) {
            val truth = parseTruth(dir.name, File(dir, "truth.json").readText())
            val ocrFile = File(dir, "ocr.txt")
            if (ocrFile.isFile) {
                val linesFile = File(dir, "ocr_lines.json")
                val lines = if (linesFile.isFile) parseOcrLines(linesFile.readText()) else null
                samples.add(ScanSample(truth, ocrFile.readText(), lines))
            } else {
                missingOcr.add(dir.name)
            }
        }
        return LoadResult(samples, missingOcr)
    }

    fun parseTruth(defaultId: String, json: String): ReceiptTruth {
        val obj = JSONObject(json)
        val conditions = obj.optJSONObject("conditions")?.let { c ->
            c.keys().asSequence().associateWith { c.get(it).toString() }
        }.orEmpty()
        val itemsArray = obj.optJSONArray("items")
        val items = (0 until (itemsArray?.length() ?: 0)).map { i ->
            val item = itemsArray!!.getJSONObject(i)
            TruthItem(
                name = item.getString("name"),
                price = if (item.has("price")) item.getLong("price") else null,
                quantity = item.optInt("quantity", 1),
                isFood = item.getBoolean("isFood"),
                category = item.optString("category").takeIf { it.isNotEmpty() }?.let { FoodCategory.valueOf(it) }
            )
        }
        return ReceiptTruth(
            id = obj.optString("id", defaultId),
            store = obj.getString("store"),
            date = LocalDate.parse(obj.getString("date")),
            total = obj.getLong("total"),
            conditions = conditions,
            items = items
        )
    }

    /** Lines from `ocr_lines.json` (written by OcrDumpTest); null if any line lacks a box. */
    fun parseOcrLines(json: String): List<OcrLine>? {
        val array = JSONObject(json).getJSONArray("lines")
        return (0 until array.length()).map { i ->
            val line = array.getJSONObject(i)
            if (BOX_KEYS.any { line.isNull(it) }) return null
            OcrLine(
                text = line.getString("text"),
                left = line.getInt("left"),
                top = line.getInt("top"),
                right = line.getInt("right"),
                bottom = line.getInt("bottom"),
                angle = line.optDouble("angle", 0.0).toFloat(),
                confidence = if (line.isNull("confidence")) null else line.getDouble("confidence").toFloat()
            )
        }
    }

    private val BOX_KEYS = listOf("left", "top", "right", "bottom")
}
