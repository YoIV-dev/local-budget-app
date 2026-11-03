package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.OcrLine
import com.localbudget.app.domain.ocr.ReceiptRowBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ReceiptRowBuilderTest {

    @Test
    fun namesAndPricesInSeparateBlocksAreJoinedPerRow() {
        // ML Kit block order: all names, then all prices
        val lines = listOf(
            line("ｷｬﾍﾞﾂ", left = 40, width = 120, y = 100),
            line("ﾓﾔｼ", left = 40, width = 80, y = 150),
            line("¥198", left = 620, width = 80, y = 100),
            line("¥38", left = 640, width = 60, y = 150)
        )

        assertEquals(listOf("ｷｬﾍﾞﾂ ¥198", "ﾓﾔｼ ¥38"), ReceiptRowBuilder.buildRows(lines))
    }

    @Test
    fun rowsAreReadTopToBottomAndLeftToRight() {
        val lines = listOf(
            line("合計", left = 40, width = 60, y = 300),
            line("¥1,199", left = 600, width = 100, y = 302),
            line("ﾏﾙｴﾂ", left = 300, width = 100, y = 20)
        )

        assertEquals(listOf("ﾏﾙｴﾂ", "合計 ¥1,199"), ReceiptRowBuilder.buildRows(lines))
    }

    @Test
    fun tiltedPhotoKeepsRowsTogether() {
        // At 3° a price 600px to the right sits ~31px lower than its name: more than half a line
        // height, so plain y-grouping would split every row
        val angle = 3.0
        val lines = listOf(
            line("ｷｬﾍﾞﾂ", left = 40, width = 120, y = 100, angle = angle),
            line("¥198", left = 620, width = 80, y = 100, angle = angle),
            line("ﾓﾔｼ", left = 40, width = 80, y = 150, angle = angle),
            line("¥38", left = 640, width = 60, y = 150, angle = angle)
        )

        assertEquals(listOf("ｷｬﾍﾞﾂ ¥198", "ﾓﾔｼ ¥38"), ReceiptRowBuilder.buildRows(lines))
    }

    @Test
    fun emptyInputGivesNoRows() {
        assertEquals(emptyList<String>(), ReceiptRowBuilder.buildRows(emptyList()))
    }

    /** A 30px-high line whose unrotated top-left is (left, y), rotated clockwise by [angle] about the origin. */
    private fun line(text: String, left: Int, width: Int, y: Int, angle: Double = 0.0): OcrLine {
        val t = Math.toRadians(angle)
        val corners = listOf(left to y, left + width to y, left to y + 30, left + width to y + 30)
            .map { (x, yy) -> (x * cos(t) - yy * sin(t)) to (x * sin(t) + yy * cos(t)) }
        return OcrLine(
            text = text,
            left = corners.minOf { it.first }.roundToInt(),
            top = corners.minOf { it.second }.roundToInt(),
            right = corners.maxOf { it.first }.roundToInt(),
            bottom = corners.maxOf { it.second }.roundToInt(),
            angle = angle.toFloat()
        )
    }
}
