package com.localbudget.app.domain.ocr

/**
 * One recognized text line with its axis-aligned bounding box in image pixels
 * (mirrors ML Kit's Text.Line without depending on Android types, so it is JVM-testable).
 * [angle] is the line's rotation in degrees, clockwise positive, as ML Kit reports it;
 * [confidence] is ML Kit's line confidence (0–1) when known.
 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val angle: Float = 0f,
    val confidence: Float? = null
) {
    val height: Int get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}
