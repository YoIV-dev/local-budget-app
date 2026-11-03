package com.localbudget.app.domain.ocr

data class DetectionResult(
    val isReceiptDetected: Boolean = false,
    val isSharp: Boolean = false,
    val brightnessScore: Float = 0f,
    val edgeDensity: Float = 0f,
    val blurScore: Float = 0f,
    val statusMessage: String = "レシートを枠に合わせてください"
) {
    val isReadyToCapture: Boolean
        get() = isReceiptDetected && isSharp
}
