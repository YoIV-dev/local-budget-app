package com.localbudget.app.eval

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Runs the bundled ML Kit Japanese recognizer (same options as ReceiptOcrEngine) over
 * receipt images pushed by eval/run_device_ocr.sh, writing next to each image:
 *   ocr.txt         – Text.text, the exact string ReceiptParser sees in the app
 *   ocr_lines.json  – every line with its block index and bounding box, for row reconstruction
 */
@RunWith(AndroidJUnit4::class)
class OcrDumpTest {

    @Test
    fun dumpOcrForEvalImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.getExternalFilesDir(null), "eval")
        val receiptDirs = root.listFiles { f -> f.isDirectory }?.sortedBy { it.name }.orEmpty()
        assumeTrue("No receipt images pushed to $root", receiptDirs.isNotEmpty())

        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        try {
            for (dir in receiptDirs) {
                val image = dir.listFiles { f -> f.extension.lowercase() in IMAGE_EXTENSIONS }
                    ?.minByOrNull { it.name }
                    ?: continue
                // fromFilePath applies EXIF orientation, like the gallery import path in the app
                val input = InputImage.fromFilePath(context, Uri.fromFile(image))
                val result = Tasks.await(recognizer.process(input), 60, TimeUnit.SECONDS)

                File(dir, "ocr.txt").writeText(result.text)
                File(dir, "ocr_lines.json").writeText(linesJson(result, input).toString(2))
            }
        } finally {
            recognizer.close()
        }
    }

    private fun linesJson(result: Text, input: InputImage): JSONObject {
        val lines = JSONArray()
        result.textBlocks.forEachIndexed { blockIndex, block ->
            for (line in block.lines) {
                val box = line.boundingBox
                lines.put(
                    JSONObject()
                        .put("text", line.text)
                        .put("block", blockIndex)
                        .put("left", box?.left)
                        .put("top", box?.top)
                        .put("right", box?.right)
                        .put("bottom", box?.bottom)
                        .put("angle", line.angle.toDouble())
                        .put("confidence", line.confidence.toDouble())
                )
            }
        }
        return JSONObject()
            .put("imageWidth", input.width)
            .put("imageHeight", input.height)
            .put("lines", lines)
    }

    private companion object {
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
