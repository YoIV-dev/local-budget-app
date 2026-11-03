package com.localbudget.app.data

import com.localbudget.app.data.scanlog.ScanLogStore
import com.localbudget.app.domain.food.ReceiptFoodExtractor
import com.localbudget.app.domain.ocr.DetectionResult
import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptParser
import com.localbudget.app.domain.ocr.ReceiptRowBuilder
import com.localbudget.app.domain.scanlog.ConfirmedReceipt
import com.localbudget.app.domain.scanlog.ScanLogEntry
import com.localbudget.app.domain.scanlog.ScanSource
import com.localbudget.app.eval.ScanDataset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ScanLogStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val lines = listOf(
        OcrLine("ﾏﾙｴﾂ 新宿店", 250, 20, 450, 50, confidence = 0.9f),
        OcrLine("2026/10/01", 250, 60, 450, 90),
        OcrLine("ｷｬﾍﾞﾂ", 40, 100, 160, 130),
        OcrLine("ﾎﾟﾃﾄﾁｯﾌﾟｽ", 40, 150, 220, 180),
        OcrLine("¥198", 620, 100, 700, 130),
        OcrLine("¥148", 620, 150, 700, 180),
        OcrLine("合計 ¥346", 40, 200, 700, 230)
    )

    private fun entry(confirmed: ConfirmedReceipt?): ScanLogEntry {
        val text = ReceiptRowBuilder.buildText(lines)
        val parsed = ReceiptParser.parse(text).copy(ocrLines = lines)
        return ScanLogEntry(
            capturedAtMillis = 1_790_000_000_000,
            source = ScanSource.AUTO_CAPTURE,
            detection = DetectionResult(isReceiptDetected = true, isSharp = true, brightnessScore = 120f),
            rotationDegrees = 90,
            parsed = parsed,
            predictedFood = ReceiptFoodExtractor.extractLines(text),
            confirmed = confirmed
        )
    }

    @Test
    fun savedScanIsLaidOutLikeAnEvalEntry() {
        val store = ScanLogStore(tmp.root)
        val e = entry(
            ConfirmedReceipt("マルエツ 新宿三丁目店", LocalDate.of(2026, 10, 1), 346, 1L, keptFoodNames = listOf("ｷｬﾍﾞﾂ"))
        )
        store.save(e, image = byteArrayOf(1, 2, 3))

        val dir = tmp.root.listFiles()!!.single()
        assertEquals(
            setOf("image.jpg", "ocr.txt", "ocr_lines.json", "log.json", "truth.draft.json"),
            dir.list()!!.toSet()
        )

        val log = JSONObject(dir.resolve("log.json").readText())
        assertEquals("AUTO_CAPTURE", log.getString("source"))
        // Store name was fixed by the user; the rest was read correctly
        assertEquals(listOf("STORE"), log.getJSONArray("corrections").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertEquals(198, log.getJSONObject("predicted").getJSONArray("food").getJSONObject(0).getInt("price"))

        // The eval loader reads the draft and the line boxes as-is
        val truth = ScanDataset.parseTruth(dir.name, dir.resolve("truth.draft.json").readText())
        assertEquals(346L, truth.total)
        assertEquals(listOf("ｷｬﾍﾞﾂ"), truth.items.filter { it.isFood }.map { it.name })
        assertEquals(lines, ScanDataset.parseOcrLines(dir.resolve("ocr_lines.json").readText()))
    }

    @Test
    fun discardedScanHasNoTruthDraft() {
        val store = ScanLogStore(tmp.root)
        store.save(entry(confirmed = null), image = null)

        val dir = tmp.root.listFiles()!!.single()
        assertFalse(dir.resolve("truth.draft.json").exists())
        assertFalse(dir.resolve("image.jpg").exists())
        assertTrue(JSONObject(dir.resolve("log.json").readText()).isNull("confirmed"))
    }

    @Test
    fun exportZipsScansWithCorrectionSummary() {
        val store = ScanLogStore(tmp.root)
        val parsedStore = entry(null).parsed.storeName
        store.save(entry(ConfirmedReceipt(parsedStore, LocalDate.of(2026, 10, 1), 346, 1L, listOf("ｷｬﾍﾞﾂ"))), null)
        store.save(entry(ConfirmedReceipt(parsedStore, LocalDate.of(2026, 10, 1), 999, 1L, listOf("ｷｬﾍﾞﾂ"))), null)
        store.save(entry(null), null)
        assertEquals(3, store.count())

        val out = ByteArrayOutputStream()
        store.exportZip(out)
        val names = mutableListOf<String>()
        var summary: JSONObject? = null
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                names.add(e.name)
                if (e.name == "scan_log/summary.json") summary = JSONObject(zip.readBytes().decodeToString())
            }
        }

        assertEquals(4 + 4 + 3 + 1, names.size) // no images: 4 files per saved scan, 3 when discarded, + summary
        assertEquals(2, summary!!.getInt("saved"))
        assertEquals(1, summary!!.getInt("discarded"))
        assertEquals(0.5, summary!!.getJSONObject("correctionRate").getDouble("AMOUNT"), 1e-9)
        assertEquals(0.0, summary!!.getJSONObject("correctionRate").getDouble("STORE"), 1e-9)

        store.clear()
        assertEquals(0, store.count())
    }
}
