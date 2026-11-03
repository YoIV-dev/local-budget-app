package com.localbudget.app.domain.ocr

import kotlin.math.abs

/**
 * Decides when live detection should fire the shutter.
 *
 * A single frame passing [DetectionResult.isReadyToCapture] is not enough: the frame must stay ready
 * and steady (brightness / edge density barely changing, i.e. the phone is held still over the same
 * paper) for [holdMillis]. After firing, the gate stays closed until the receipt leaves the frame,
 * so the same receipt is not captured again while it is still under the camera.
 */
class AutoCaptureGate(
    private val holdMillis: Long = 900L,
    private val maxBrightnessDelta: Float = 8f,
    private val maxEdgeDensityDelta: Float = 0.02f
) {
    private var streakStartMillis: Long? = null
    private var previous: DetectionResult? = null
    private var waitingForNewReceipt = false

    /** Feeds one analyzed frame; true means take the picture now. */
    fun onFrame(result: DetectionResult, nowMillis: Long): Boolean {
        val prev = previous
        previous = result

        if (!result.isReceiptDetected) {
            waitingForNewReceipt = false
        }
        if (waitingForNewReceipt || !result.isReadyToCapture) {
            streakStartMillis = null
            return false
        }

        val steady = prev != null && prev.isReadyToCapture &&
            abs(result.brightnessScore - prev.brightnessScore) <= maxBrightnessDelta &&
            abs(result.edgeDensity - prev.edgeDensity) <= maxEdgeDensityDelta
        val start = streakStartMillis
        if (!steady || start == null) {
            streakStartMillis = nowMillis
            return false
        }
        if (nowMillis - start < holdMillis) return false

        streakStartMillis = null
        waitingForNewReceipt = true
        return true
    }

    /** 0..1 progress of the current steady streak, for the on-screen hint. */
    fun progress(nowMillis: Long): Float {
        val start = streakStartMillis ?: return 0f
        return ((nowMillis - start).toFloat() / holdMillis).coerceIn(0f, 1f)
    }

    fun isWaitingForNewReceipt(): Boolean = waitingForNewReceipt

    fun reset() {
        streakStartMillis = null
        previous = null
        waitingForNewReceipt = false
    }
}
