package com.localbudget.app.data.scanlog

import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.scanlog.ScanLogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Verification-mode scan records, kept in app-private storage under [root] (excluded from
 * cloud backup) and leaving the device only when the user exports them.
 *
 * Each scan is a directory laid out like an eval/data entry (eval/README.md):
 *   image.jpg          – the captured photo, when available
 *   ocr.txt            – ML Kit's lines in block order (Text.text)
 *   ocr_lines.json     – the same lines with positions and confidences
 *   log.json           – capture scores, what the app read, what the user saved, which fields changed
 *   truth.draft.json   – truth.json prefilled from the user's corrections; review before use
 */
class ScanLogStore(private val root: File) {

    fun save(entry: ScanLogEntry, image: ByteArray?) {
        val dir = uniqueDir(DIR_NAME.format(Instant.ofEpochMilli(entry.capturedAtMillis).atZone(ZoneId.systemDefault())))
        dir.mkdirs()
        image?.let { File(dir, "image.jpg").writeBytes(it) }
        File(dir, "ocr.txt").writeText(entry.parsed.ocrLines.joinToString("\n") { it.text })
        File(dir, "ocr_lines.json").writeText(ocrLinesJson(entry.parsed.ocrLines).toString(2))
        File(dir, "log.json").writeText(logJson(entry).toString(2))
        truthDraftJson(entry, dir.name)?.let { File(dir, "truth.draft.json").writeText(it.toString(2)) }
    }

    fun count(): Int = scanDirs().size

    fun clear() {
        root.deleteRecursively()
    }

    /** Zips every scan plus summary.json (correction rates) into [out]. */
    fun exportZip(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            for (dir in scanDirs()) {
                for (file in dir.listFiles().orEmpty().sortedBy { it.name }) {
                    zip.putNextEntry(ZipEntry("scan_log/${dir.name}/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("scan_log/summary.json"))
            zip.write(summaryJson().toString(2).toByteArray())
            zip.closeEntry()
        }
    }

    /**
     * How often the user had to fix each field: the field's correction count over saved scans.
     * This is the app's accuracy under real use.
     */
    fun summaryJson(): JSONObject {
        val logs = scanDirs().mapNotNull { dir ->
            File(dir, "log.json").takeIf { it.isFile }?.let { JSONObject(it.readText()) }
        }
        val saved = logs.filter { !it.isNull("confirmed") }
        val rates = JSONObject()
        for (field in ScanLogEntry.Field.entries) {
            val corrected = saved.count { log ->
                val c = log.getJSONArray("corrections")
                (0 until c.length()).any { c.getString(it) == field.name }
            }
            rates.put(field.name, if (saved.isEmpty()) JSONObject.NULL else corrected.toDouble() / saved.size)
        }
        return JSONObject()
            .put("scans", logs.size)
            .put("saved", saved.size)
            .put("discarded", logs.size - saved.size)
            .put("correctionRate", rates)
    }

    internal fun logJson(entry: ScanLogEntry): JSONObject {
        val parsed = entry.parsed
        val log = JSONObject()
            .put("capturedAt", Instant.ofEpochMilli(entry.capturedAtMillis).toString())
            .put("source", entry.source.name)
            .put("rotationDegrees", entry.rotationDegrees)
            .put("predicted", JSONObject()
                .put("store", parsed.storeName)
                .put("date", parsed.date.toString())
                .put("amount", parsed.amount)
                .put("categoryId", parsed.suggestedCategoryId ?: JSONObject.NULL)
                .put("food", JSONArray(entry.predictedFood.map { line ->
                    JSONObject()
                        .put("name", line.item.name)
                        .put("category", line.category.name)
                        .put("price", line.item.price ?: JSONObject.NULL)
                        .put("quantity", line.item.quantity)
                        .put("discount", line.item.discount)
                })))
            .put("confirmed", entry.confirmed?.let { c ->
                JSONObject()
                    .put("store", c.storeName)
                    .put("date", c.date.toString())
                    .put("amount", c.amount)
                    .put("categoryId", c.categoryId)
                    .put("keptFood", JSONArray(c.keptFoodNames))
            } ?: JSONObject.NULL)
            .put("corrections", JSONArray(entry.corrections.map { it.name }.sorted()))
        entry.detection?.let { d ->
            log.put("detection", JSONObject()
                .put("receiptDetected", d.isReceiptDetected)
                .put("sharp", d.isSharp)
                .put("brightness", d.brightnessScore.toDouble())
                .put("edgeDensity", d.edgeDensity.toDouble())
                .put("blur", d.blurScore.toDouble()))
        }
        return log
    }

    /**
     * truth.json prefilled from a saved scan. Items cover only the food the app found (kept ones as
     * food, unchecked ones as not food); missed and non-food lines must be added by hand.
     */
    internal fun truthDraftJson(entry: ScanLogEntry, id: String): JSONObject? {
        val c = entry.confirmed ?: return null
        val kept = c.keptFoodNames.toSet()
        return JSONObject()
            .put("id", id)
            .put("store", c.storeName)
            .put("date", c.date.toString())
            .put("total", c.amount)
            .put("conditions", JSONObject().put("source", entry.source.name.lowercase()))
            .put("items", JSONArray(entry.predictedFood.map { line ->
                val item = JSONObject()
                    .put("name", line.item.name)
                    .put("isFood", line.item.name in kept)
                    .put("quantity", line.item.quantity)
                line.item.price?.let { item.put("price", it) }
                if (line.item.name in kept) item.put("category", line.category.name)
                item
            }))
    }

    private fun ocrLinesJson(lines: List<OcrLine>): JSONObject = JSONObject()
        .put("lines", JSONArray(lines.map { line ->
            JSONObject()
                .put("text", line.text)
                .put("left", line.left)
                .put("top", line.top)
                .put("right", line.right)
                .put("bottom", line.bottom)
                .put("angle", line.angle.toDouble())
                .put("confidence", line.confidence?.toDouble() ?: JSONObject.NULL)
        }))

    private fun scanDirs(): List<File> =
        root.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()

    private fun uniqueDir(name: String): File {
        var dir = File(root, name)
        var n = 2
        while (dir.exists()) dir = File(root, "$name-${n++}")
        return dir
    }

    private companion object {
        val DIR_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}
