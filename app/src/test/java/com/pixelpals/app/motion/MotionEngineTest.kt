package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionEngineTest {

    private val fixedStep = 1f / 60f

    @Test
    fun clampsHugeDeltasToCatchUpLimit() {
        val engine = MotionEngine()
        assertEquals(15 * fixedStep, engine.sanitizeDeltaSeconds(10f), 0.0001f)
        assertEquals(0f, engine.sanitizeDeltaSeconds(Float.NaN), 0.0001f)
        assertEquals(0f, engine.sanitizeDeltaSeconds(-1f), 0.0001f)
    }

    @Test
    fun oneSixtiethProducesOneFixedStep() {
        val engine = MotionEngine()
        val result = engine.splitDelta(fixedStep)
        assertEquals(1, result.steps)
        assertEquals(fixedStep, result.stepDt, 0.0001f)
    }

    @Test
    fun hugeFrameIsLimitedToMaxCatchUp() {
        val engine = MotionEngine(maxStepsPerFrame = 15)
        val result = engine.splitDelta(1f)
        assertEquals(15, result.steps)
        assertEquals(fixedStep, result.stepDt, 0.0001f)
    }

    @Test
    fun remainderIsKeptForTheNextFrame() {
        val engine = MotionEngine()
        // 30 Hz: medio paso queda acumulado y se completa en el siguiente frame.
        val first = engine.splitDelta(fixedStep * 2f)
        assertEquals(2, first.steps)
        val second = engine.splitDelta(fixedStep)
        assertEquals(1, second.steps)
    }

    @Test
    fun equivalentStepsAcrossRefreshRates() {
        // 60 Hz vs 30 Hz vs 120 Hz: la misma duración simula los mismos pasos.
        fun totalSteps(delta: Float, frames: Int): Int {
            val engine = MotionEngine()
            var steps = 0
            repeat(frames) { steps += engine.splitDelta(delta).steps }
            return steps
        }
        val at60Hz = totalSteps(fixedStep, 120)
        val at30Hz = totalSteps(fixedStep * 2f, 60)
        val at120Hz = totalSteps(fixedStep / 2f, 240)
        assertEquals(120, at60Hz)
        assertEquals(120, at30Hz)
        assertEquals(120, at120Hz)
    }

    @Test
    fun resetDiscardsAccumulatedTime() {
        val engine = MotionEngine()
        engine.splitDelta(0.5f)
        engine.resetAccumulator()
        val result = engine.splitDelta(fixedStep)
        assertEquals(1, result.steps)
        assertTrue(result.stepDt > 0f)
    }
}
