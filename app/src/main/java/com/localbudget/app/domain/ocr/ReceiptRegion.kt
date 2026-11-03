package com.localbudget.app.domain.ocr

import kotlin.math.max
import kotlin.math.roundToInt

/** Axis-aligned rectangle in pixels of the upright image, the space ML Kit reports line boxes in. */
data class ImageRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun contains(x: Double, y: Double): Boolean = x >= left && x <= right && y >= top && y <= bottom
}

/**
 * Where the receipt is in a scanned image, so text around it (the table, other papers, packaging)
 * never reaches the parsers.
 */
object ReceiptRegion {

    // The viewfinder guide frame as fractions of the preview, which fills the screen
    const val GUIDE_WIDTH = 0.85f
    const val GUIDE_HEIGHT = 0.65f
    /** Fraction of the free vertical space above the frame (a bit above center, clear of the shutter bar). */
    const val GUIDE_TOP_SHARE = 0.4f

    /** The guide frame in a viewfinder of the given size, as left/top/right/bottom. */
    fun guideFrame(viewWidth: Float, viewHeight: Float): FloatArray {
        val width = viewWidth * GUIDE_WIDTH
        val height = viewHeight * GUIDE_HEIGHT
        val left = (viewWidth - width) / 2f
        val top = (viewHeight - height) * GUIDE_TOP_SHARE
        return floatArrayOf(left, top, left + width, top + height)
    }

    /**
     * The guide frame mapped into the captured image. The preview shows the camera image scaled to
     * cover the view and centered (PreviewView's FILL_CENTER), with the capture having the same field
     * of view as the preview; [imageWidth]/[imageHeight] are the upright image's size (after rotation).
     */
    fun guideFrameInImage(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): ImageRect {
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val offsetX = (viewWidth - imageWidth * scale) / 2f
        val offsetY = (viewHeight - imageHeight * scale) / 2f
        val (left, top, right, bottom) = guideFrame(viewWidth.toFloat(), viewHeight.toFloat())
        fun toImage(v: Float, offset: Float, limit: Int) = ((v - offset) / scale).roundToInt().coerceIn(0, limit)
        return ImageRect(
            toImage(left, offsetX, imageWidth),
            toImage(top, offsetY, imageHeight),
            toImage(right, offsetX, imageWidth),
            toImage(bottom, offsetY, imageHeight)
        )
    }

    /**
     * The lines inside the receipt: of the candidate [regions], the one holding the most line centers
     * wins, and only the lines centered in it are kept (a line cut by the edge stays if it is mostly in).
     * With no candidates, or none holding any text, all lines are kept rather than returning nothing.
     */
    fun linesInside(lines: List<OcrLine>, regions: List<ImageRect>): List<OcrLine> {
        val best = regions
            .map { region -> lines.filter { region.contains(it.centerX, it.centerY) } }
            .maxByOrNull { it.size }
        return best?.ifEmpty { null } ?: lines
    }
}
