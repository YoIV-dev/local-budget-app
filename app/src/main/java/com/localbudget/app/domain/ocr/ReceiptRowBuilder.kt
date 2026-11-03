package com.localbudget.app.domain.ocr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rebuilds printed receipt rows from ML Kit lines.
 *
 * ML Kit groups text into blocks, so an item name and its price on the far right often land in
 * different blocks and `Text.text` lists all names first and all prices after them. Here lines are
 * grouped by their vertical position instead (after undoing the page tilt), then read left to right.
 */
object ReceiptRowBuilder {

    /** Lines whose deskewed centers are within this fraction of the median line height share a row. */
    private const val ROW_TOLERANCE = 0.5

    fun buildRows(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() && it.height > 0 }
        if (usable.isEmpty()) return emptyList()

        // Rotate centers back by the median tilt so a sloped row lines up horizontally again.
        // y grows downward and the angle is clockwise, so a row tilted by θ has y ≈ y0 + x·tanθ.
        val theta = Math.toRadians(median(usable.map { it.angle.toDouble() }))
        val (s, c) = sin(theta) to cos(theta)
        val placed = usable.map { line ->
            Placed(
                line = line,
                x = line.centerX * c + line.centerY * s,
                y = line.centerY * c - line.centerX * s
            )
        }

        // A tilted line's axis-aligned box is taller than its glyphs (H = w·sinθ + h·cosθ);
        // H·cosθ − W·sinθ recovers the glyph height closely enough at receipt-photo tilts
        val glyphHeights = usable.map { (it.height * c - (it.right - it.left) * abs(s)).coerceAtLeast(1.0) }
        val tolerance = ROW_TOLERANCE * median(glyphHeights)

        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed.sortedBy { it.y }) {
            val row = rows.lastOrNull()
            if (row != null && abs(p.y - row.sumOf { it.y } / row.size) <= tolerance) {
                row.add(p)
            } else {
                rows.add(mutableListOf(p))
            }
        }

        return rows.map { row -> row.sortedBy { it.x }.joinToString(" ") { it.line.text.trim() } }
    }

    fun buildText(lines: List<OcrLine>): String = buildRows(lines).joinToString("\n")

    private class Placed(val line: OcrLine, val x: Double, val y: Double)

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
