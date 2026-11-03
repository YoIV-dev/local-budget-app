package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.ImageRect
import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptDetector
import com.localbudget.app.domain.ocr.ReceiptRegion
import com.localbudget.app.domain.ocr.ReceiptRowBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptRegionTest {

    @Test
    fun guideFrameMapsThroughTheCenterCropOfATallScreen() {
        // 3:4 capture shown on a 1080x2400 screen: scaled by 0.6 to 1800 wide, 360 px cut on each side
        val frame = ReceiptRegion.guideFrameInImage(1080, 2400, 3000, 4000)

        // View frame: x 81..999, y 336..1896
        assertEquals(ImageRect(735, 560, 2265, 3160), frame)
    }

    @Test
    fun guideFrameKeepsItsFractionsWhenAspectRatiosMatch() {
        val frame = ReceiptRegion.guideFrameInImage(300, 400, 3000, 4000)

        assertEquals(ImageRect(225, 560, 2775, 3160), frame)
    }

    @Test
    fun linesOutsideTheFrameNeverReachTheText() {
        val frame = ImageRect(700, 500, 2300, 3200)
        val lines = listOf(
            line("特売チラシ 全品10%OFF", left = 100, right = 900, y = 300), // above the frame
            line("イオン", left = 1200, right = 1600, y = 700),
            line("ｷｬﾍﾞﾂ", left = 800, right = 1100, y = 1200),
            line("¥198", left = 1900, right = 2200, y = 1200),
            line("合計 ¥198", left = 800, right = 2200, y = 2000),
            line("¥5,980", left = 2400, right = 2800, y = 1200), // price tag beside the receipt
            line("ﾎﾟｲﾝﾄｶｰﾄﾞ", left = 600, right = 1000, y = 2600) // cut by the edge, mostly inside
        )

        val kept = ReceiptRegion.linesInside(lines, listOf(frame))

        assertEquals(
            listOf("イオン", "ｷｬﾍﾞﾂ ¥198", "合計 ¥198", "ﾎﾟｲﾝﾄｶｰﾄﾞ"),
            ReceiptRowBuilder.buildRows(kept)
        )
    }

    @Test
    fun allLinesAreKeptWhenNoRegionIsGivenOrNoneHoldsText() {
        val lines = listOf(line("合計 ¥198", left = 100, right = 500, y = 100))

        assertEquals(lines, ReceiptRegion.linesInside(lines, emptyList()))
        assertEquals(lines, ReceiptRegion.linesInside(lines, listOf(ImageRect(1000, 1000, 2000, 2000))))
    }

    @Test
    fun theRegionHoldingTheMostTextWins() {
        val receipt = ImageRect(0, 0, 500, 1000)
        val sticker = ImageRect(600, 0, 1000, 300)
        val lines = listOf(
            line("ｾﾌﾞﾝｲﾚﾌﾞﾝ", left = 50, right = 400, y = 100),
            line("ｵﾆｷﾞﾘ ¥150", left = 50, right = 400, y = 300),
            line("合計 ¥150", left = 50, right = 400, y = 500),
            line("見切品", left = 650, right = 900, y = 100)
        )

        val kept = ReceiptRegion.linesInside(lines, listOf(sticker, receipt))

        assertEquals(lines.take(3), kept)
    }

    @Test
    fun whiteReceiptOnADarkTableIsFound() {
        val (luma, width, height) = image(200, 240, background = 50)
        paper(luma, width, x = 60..139, y = 30..209)

        val regions = ReceiptDetector.findPaperRegions(luma, width, height)

        assertEquals(1, regions.size)
        // The paper plus 2% slack (4 px across, 4 px down)
        assertEquals(ImageRect(56, 26, 144, 214), regions[0])
    }

    @Test
    fun largerPaperComesFirst() {
        val (luma, width, height) = image(200, 240, background = 50)
        paper(luma, width, x = 20..69, y = 20..79)
        paper(luma, width, x = 100..179, y = 20..219)

        val regions = ReceiptDetector.findPaperRegions(luma, width, height)

        assertEquals(2, regions.size)
        assertTrue(regions[0].left > 90)
    }

    @Test
    fun noRegionWhenTheBackgroundItselfIsBright() {
        // A screenshot or a receipt on a white table: the bright part runs to the image edges
        val (luma, width, height) = image(200, 240, background = 235)
        for (y in 100..110) for (x in 20..180) luma[y * width + x] = 30

        assertTrue(ReceiptDetector.findPaperRegions(luma, width, height).isEmpty())
    }

    @Test
    fun noRegionWhenNothingStandsOut() {
        val (luma, width, height) = image(200, 240, background = 120)
        for (y in 0 until height) for (x in 0 until width) luma[y * width + x] = 110 + (x + y) % 20

        assertTrue(ReceiptDetector.findPaperRegions(luma, width, height).isEmpty())
    }

    private fun image(width: Int, height: Int, background: Int) =
        Triple(IntArray(width * height) { background }, width, height)

    /** White paper with rows of dark print, like a receipt. */
    private fun paper(luma: IntArray, width: Int, x: IntRange, y: IntRange) {
        for (py in y) for (px in x) {
            val isText = py % 8 in 3..4 && px % 6 in 1..3 && px in (x.first + 4)..(x.last - 4)
            luma[py * width + px] = if (isText) 30 else 230
        }
    }

    private fun line(text: String, left: Int, right: Int, y: Int, height: Int = 60) =
        OcrLine(text, left, y - height / 2, right, y + height / 2)
}
