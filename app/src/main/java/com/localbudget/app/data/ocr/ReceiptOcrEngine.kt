package com.localbudget.app.data.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.localbudget.app.domain.model.ParsedReceipt
import com.localbudget.app.domain.ocr.ImageRect
import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptParser
import com.localbudget.app.domain.ocr.ReceiptRegion
import com.localbudget.app.domain.ocr.ReceiptRowBuilder
import com.localbudget.app.domain.ocr.TotalCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ReceiptOcrEngine {

    // Using JapaneseTextRecognizerOptions with bundled model: 100% on-device, offline, zero network
    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    suspend fun processBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        receiptRegions: List<ImageRect> = emptyList()
    ): ParsedReceipt = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        processInputImage(image, receiptRegions)
    }

    /**
     * [receiptRegions]: where the receipt may be, in pixels of the upright image; text outside the
     * best-matching one is dropped before parsing (see [ReceiptRegion.linesInside]). Empty = read everything.
     */
    suspend fun processInputImage(
        image: InputImage,
        receiptRegions: List<ImageRect> = emptyList()
    ): ParsedReceipt = withContext(Dispatchers.Default) {
        try {
            val result = recognizer.process(image).await()
            val lines = ocrLines(result)?.let { ReceiptRegion.linesInside(it, receiptRegions) }
            // Rows rebuilt from line positions keep an item and its right-aligned price together;
            // without boxes, fall back to ML Kit's block order
            val text = lines?.let { ReceiptRowBuilder.buildText(it) }?.ifEmpty { null } ?: result.text
            val parsed = ReceiptParser.parse(text)
            val check = parsed.totalCheck
            val confidence = lines?.let { TotalCheck.confidenceOf(it, check.asRead ?: parsed.amount) }
            parsed.copy(ocrLines = lines.orEmpty(), totalCheck = check.copy(confidence = confidence))
        } catch (e: Exception) {
            ParsedReceipt(
                storeName = "",
                date = java.time.LocalDate.now(),
                amount = 0L,
                rawText = "OCR Error: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    /** Lines in ML Kit's block order, or null if any line has no bounding box. */
    private fun ocrLines(result: Text): List<OcrLine>? =
        result.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox ?: return null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom, line.angle, line.confidence)
        }

    fun close() {
        try {
            recognizer.close()
        } catch (_: Exception) {}
    }
}
