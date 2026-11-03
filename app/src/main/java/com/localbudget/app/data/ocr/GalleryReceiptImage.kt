package com.localbudget.app.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.localbudget.app.domain.ocr.ImageRect
import com.localbudget.app.domain.ocr.ReceiptDetector
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A receipt photo picked from the gallery: the decoded image, the rotation its EXIF data asks for,
 * and where the receipt paper is (in pixels of the upright image), since there is no guide frame.
 */
class GalleryReceiptImage(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
    val receiptRegions: List<ImageRect>
) {
    companion object {
        /** Longest side kept for OCR; larger photos are decoded at a power-of-two reduction. */
        private const val MAX_SIDE = 4096
        /** Longest side of the copy the paper is looked for in. */
        private const val DETECT_SIDE = 240

        fun load(context: Context, uri: Uri): GalleryReceiptImage {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("画像を読み込めませんでした")

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("画像を読み込めませんでした")

            val rotation = runCatching {
                resolver.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) }
            }.getOrNull() ?: 0

            return GalleryReceiptImage(bitmap, rotation, paperRegions(bitmap, rotation))
        }

        private fun exifRotation(exif: ExifInterface): Int =
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

        /** Paper regions found on a small upright copy, scaled up to the upright full-size image. */
        private fun paperRegions(bitmap: Bitmap, rotation: Int): List<ImageRect> {
            val factor = DETECT_SIDE.toFloat() / max(bitmap.width, bitmap.height)
            val scaledW = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
            val scaledH = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            val small = if (rotation == 0) scaled else {
                Bitmap.createBitmap(scaled, 0, 0, scaledW, scaledH, Matrix().apply { postRotate(rotation.toFloat()) }, false)
            }
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            val luma = IntArray(pixels.size) { i ->
                val c = pixels[i]
                (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
            }
            val regions = ReceiptDetector.findPaperRegions(luma, small.width, small.height)

            val swapped = rotation == 90 || rotation == 270
            val uprightW = if (swapped) bitmap.height else bitmap.width
            val uprightH = if (swapped) bitmap.width else bitmap.height
            val sx = uprightW.toFloat() / small.width
            val sy = uprightH.toFloat() / small.height
            if (small !== scaled) small.recycle()
            if (scaled !== bitmap) scaled.recycle()
            return regions.map {
                ImageRect(
                    (it.left * sx).toInt(),
                    (it.top * sy).toInt(),
                    (it.right * sx).roundToInt().coerceAtMost(uprightW),
                    (it.bottom * sy).roundToInt().coerceAtMost(uprightH)
                )
            }
        }
    }
}
