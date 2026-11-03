package com.localbudget.app.domain.ocr

import java.nio.ByteBuffer

object ReceiptDetector {

    /**
     * Analyzes the Y (luminance) plane of an image buffer to detect receipt rectangle and blur.
     *
     * @param yBuffer Grayscale buffer (e.g. from ImageProxy.planes[0].buffer)
     * @param width Image width in pixels
     * @param height Image height in pixels
     * @param rowStride Row stride of the Y plane
     */
    fun analyzeLuminance(
        yBuffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int
    ): DetectionResult {
        // Center region of interest (ROI)
        val roiStartX = (width * 0.15f).toInt()
        val roiEndX = (width * 0.85f).toInt()
        val roiStartY = (height * 0.20f).toInt()
        val roiEndY = (height * 0.80f).toInt()

        val step = 4 // Downsample step for real-time speed (< 5ms per frame)

        var totalBrightness = 0L
        var totalSamples = 0
        var edgePixels = 0

        // For Laplacian variance (blur score)
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapCount = 0

        val rowOffsets = IntArray(height)
        for (y in 0 until height) {
            rowOffsets[y] = y * rowStride
        }

        // Safe bounds for 3x3 kernel with step offset
        val yLimit = (roiEndY - step).coerceAtMost(height - 1 - step)
        val xLimit = (roiEndX - step).coerceAtMost(width - 1 - step)

        var y = (roiStartY + step).coerceAtLeast(step)
        while (y < yLimit) {
            val row = rowOffsets[y]
            val rowPrev = rowOffsets[y - step]
            val rowNext = rowOffsets[y + step]

            var x = (roiStartX + step).coerceAtLeast(step)
            while (x < xLimit) {
                val center = yBuffer.get(row + x).toInt() and 0xFF
                val left = yBuffer.get(row + (x - step)).toInt() and 0xFF
                val right = yBuffer.get(row + (x + step)).toInt() and 0xFF
                val top = yBuffer.get(rowPrev + x).toInt() and 0xFF
                val bottom = yBuffer.get(rowNext + x).toInt() and 0xFF

                totalBrightness += center
                totalSamples++

                // Gradient magnitude
                val gradX = kotlin.math.abs(right - left)
                val gradY = kotlin.math.abs(bottom - top)
                if (gradX + gradY > 32) {
                    edgePixels++
                }

                // Laplacian: top + bottom + left + right - 4 * center
                val lap = (top + bottom + left + right - (center shl 2)).toDouble()
                lapSum += lap
                lapSqSum += (lap * lap)
                lapCount++

                x += step
            }
            y += step
        }

        if (totalSamples == 0 || lapCount == 0) {
            return DetectionResult()
        }

        val avgBrightness = (totalBrightness.toFloat() / totalSamples.toFloat())
        val edgeDensity = (edgePixels.toFloat() / totalSamples.toFloat())

        // Calculate variance of Laplacian
        val lapMean = lapSum / lapCount
        val blurVariance = (lapSqSum / lapCount) - (lapMean * lapMean)

        // Receipts typically have high brightness (white paper) and high edge density (printed text)
        val isReceiptDetected = avgBrightness >= 95f && edgeDensity >= 0.06f
        val isSharp = blurVariance >= 180.0

        val message = when {
            !isReceiptDetected -> "レシートを枠に合わせてください"
            !isSharp -> "手ぶれ・ピンボケ（端末を静止させてください）"
            else -> "レシート検出中（静止中...）"
        }

        return DetectionResult(
            isReceiptDetected = isReceiptDetected,
            isSharp = isSharp,
            brightnessScore = avgBrightness,
            edgeDensity = edgeDensity,
            blurScore = blurVariance.toFloat(),
            statusMessage = message
        )
    }

    /**
     * Bright paper areas in a photo without a guide frame (gallery images), largest first, in the
     * luminance grid's pixels. [luma] is row-major 0–255, a downscaled copy of the image is enough.
     *
     * Empty when no paper stands out from its surroundings: low contrast, or the bright part is the
     * background itself (spans most of the image or touches three of its edges, e.g. a white table or
     * a screenshot), in which case the whole image should be read.
     */
    fun findPaperRegions(luma: IntArray, width: Int, height: Int): List<ImageRect> {
        val total = width * height
        if (total == 0 || luma.size < total) return emptyList()

        val threshold = otsuThreshold(luma, total)
        var brightSum = 0L
        var brightCount = 0
        var darkSum = 0L
        for (i in 0 until total) {
            if (luma[i] > threshold) { brightSum += luma[i]; brightCount++ } else darkSum += luma[i]
        }
        if (brightCount == 0 || brightCount == total) return emptyList()
        val contrast = brightSum / brightCount - darkSum / (total - brightCount)
        if (contrast < PAPER_MIN_CONTRAST) return emptyList()

        // 4-connected components of bright pixels, each reduced to its bounding box and size
        val seen = BooleanArray(total)
        val stack = IntArray(total)
        val regions = mutableListOf<Pair<ImageRect, Int>>()
        for (start in 0 until total) {
            if (seen[start] || luma[start] <= threshold) continue
            var left = width; var top = height; var right = -1; var bottom = -1
            var size = 0
            var sp = 0
            stack[sp++] = start
            seen[start] = true
            while (sp > 0) {
                val p = stack[--sp]
                val x = p % width
                val y = p / width
                size++
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
                fun visit(q: Int) {
                    if (!seen[q] && luma[q] > threshold) { seen[q] = true; stack[sp++] = q }
                }
                if (x > 0) visit(p - 1)
                if (x < width - 1) visit(p + 1)
                if (y > 0) visit(p - width)
                if (y < height - 1) visit(p + width)
            }
            if (size >= total * PAPER_MIN_AREA) {
                regions.add(ImageRect(left, top, right + 1, bottom + 1) to size)
            }
        }

        val isBackground = regions.any { (rect, _) ->
            val edges = listOf(rect.left == 0, rect.top == 0, rect.right == width, rect.bottom == height).count { it }
            rect.width.toLong() * rect.height >= total * PAPER_MAX_COVER || edges >= 3
        }
        if (isBackground) return emptyList()

        // A little slack around the paper for text printed right at its edge
        val padX = (width * PAPER_PADDING).toInt()
        val padY = (height * PAPER_PADDING).toInt()
        return regions.sortedByDescending { it.second }.map { (rect, _) ->
            ImageRect(
                (rect.left - padX).coerceAtLeast(0),
                (rect.top - padY).coerceAtLeast(0),
                (rect.right + padX).coerceAtMost(width),
                (rect.bottom + padY).coerceAtMost(height)
            )
        }
    }

    private const val PAPER_MIN_CONTRAST = 40
    private const val PAPER_MIN_AREA = 0.03
    private const val PAPER_MAX_COVER = 0.9
    private const val PAPER_PADDING = 0.02

    /** Otsu's threshold: the luminance that best splits the histogram into two classes. */
    private fun otsuThreshold(luma: IntArray, total: Int): Int {
        val histogram = IntArray(256)
        for (i in 0 until total) histogram[luma[i].coerceIn(0, 255)]++
        val sumAll = (0..255).sumOf { it.toLong() * histogram[it] }
        var sumBelow = 0L
        var countBelow = 0L
        var best = 0
        var bestVariance = -1.0
        for (t in 0..255) {
            countBelow += histogram[t]
            sumBelow += t.toLong() * histogram[t]
            val countAbove = total - countBelow
            if (countBelow == 0L || countAbove == 0L) continue
            val meanBelow = sumBelow.toDouble() / countBelow
            val meanAbove = (sumAll - sumBelow).toDouble() / countAbove
            val variance = countBelow.toDouble() * countAbove * (meanAbove - meanBelow) * (meanAbove - meanBelow)
            if (variance > bestVariance) { bestVariance = variance; best = t }
        }
        return best
    }
}
