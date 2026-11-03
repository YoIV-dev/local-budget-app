package com.localbudget.app.domain

import com.localbudget.app.domain.ocr.AutoCaptureGate
import com.localbudget.app.domain.ocr.DetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCaptureGateTest {

    private val ready = DetectionResult(isReceiptDetected = true, isSharp = true, brightnessScore = 200f, edgeDensity = 0.12f)
    private val nothing = DetectionResult()

    /** Feeds [frame] every 33ms from [from] until [until]; returns the times the gate fired. */
    private fun AutoCaptureGate.feed(frame: (Long) -> DetectionResult, from: Long, until: Long): List<Long> =
        (from until until step 33).filter { onFrame(frame(it), it) }

    @Test
    fun aFewReadyFramesDoNotFire() {
        val gate = AutoCaptureGate(holdMillis = 900)
        assertEquals(emptyList<Long>(), gate.feed({ ready }, 0, 300))
    }

    @Test
    fun firesOnceAfterHoldingSteady() {
        val gate = AutoCaptureGate(holdMillis = 900)
        val fired = gate.feed({ ready }, 0, 5000)
        assertEquals(1, fired.size)
        assertTrue(fired.single() >= 900)
    }

    @Test
    fun sameReceiptIsNotCapturedAgainUntilItLeavesTheFrame() {
        val gate = AutoCaptureGate(holdMillis = 900)
        assertEquals(1, gate.feed({ ready }, 0, 3000).size)
        assertTrue(gate.isWaitingForNewReceipt())

        assertFalse(gate.onFrame(nothing, 3000))
        assertFalse(gate.isWaitingForNewReceipt())
        assertEquals(1, gate.feed({ ready }, 3033, 6000).size)
    }

    @Test
    fun movingCameraKeepsRestartingTheHold() {
        val gate = AutoCaptureGate(holdMillis = 900)
        // Brightness swings by 20 every frame: the phone is still moving over the paper
        val shaky = { t: Long -> ready.copy(brightnessScore = if ((t / 33) % 2 == 0L) 180f else 200f) }
        assertEquals(emptyList<Long>(), gate.feed(shaky, 0, 5000))
    }

    @Test
    fun blurFrameResetsTheHold() {
        val gate = AutoCaptureGate(holdMillis = 900)
        gate.feed({ ready }, 0, 600)
        assertFalse(gate.onFrame(ready.copy(isSharp = false), 600))
        // Needs a full hold again from here
        assertEquals(emptyList<Long>(), gate.feed({ ready }, 633, 1400))
    }

    @Test
    fun resetAllowsRetakingTheSameReceipt() {
        val gate = AutoCaptureGate(holdMillis = 900)
        gate.feed({ ready }, 0, 3000)
        gate.reset()
        assertEquals(1, gate.feed({ ready }, 3000, 6000).size)
    }
}
