package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.ReceiptDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ReceiptDetectorTest {

    @Test
    fun testDarkUniformBackgroundNotDetected() {
        val width = 320
        val height = 240
        val buffer = ByteBuffer.allocate(width * height)

        // Fill with dark wood/desk background (e.g. 40)
        for (i in 0 until width * height) {
            buffer.put(40.toByte())
        }
        buffer.rewind()

        val result = ReceiptDetector.analyzeLuminance(buffer, width, height, width)

        assertFalse("Dark uniform background should not be detected as receipt", result.isReceiptDetected)
        assertFalse(result.isReadyToCapture)
    }

    @Test
    fun testWhiteReceiptWithSharpTextDetected() {
        val width = 320
        val height = 240
        val buffer = ByteBuffer.allocate(width * height)

        // Fill with white paper (230) and sharp black text lines (30)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isText = (y % 16 in 4..6) && (x % 8 in 1..4)
                val pixel = if (isText) 30 else 230
                buffer.put(pixel.toByte())
            }
        }
        buffer.rewind()

        val result = ReceiptDetector.analyzeLuminance(buffer, width, height, width)

        assertTrue("White paper with text edges should be detected as receipt", result.isReceiptDetected)
        assertTrue("High contrast sharp text should have high sharpness", result.isSharp)
        assertTrue("Ready to capture should be true", result.isReadyToCapture)
    }

    @Test
    fun testBlurryReceiptDetectedButNotSharp() {
        val width = 320
        val height = 240
        val buffer = ByteBuffer.allocate(width * height)

        // White paper (200) with very smooth/soft gradient (blurred)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val softVal = 180 + (kotlin.math.abs((x % 60) - 30) * 1.5).toInt()
                buffer.put(softVal.toByte())
            }
        }
        buffer.rewind()

        val result = ReceiptDetector.analyzeLuminance(buffer, width, height, width)

        // The blur variance on soft continuous gradient will be low (< 180)
        assertFalse("Smooth gradient without sharp edges should fail sharpness check", result.isSharp)
        assertFalse("Should not trigger auto capture when blurred", result.isReadyToCapture)
    }
}
