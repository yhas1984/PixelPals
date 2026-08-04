package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionEngineTest {
    @Test fun clampsHugeDeltas() {
        val engine = MotionEngine()
        assertEquals(0.12f, engine.sanitizeDeltaSeconds(10f), 0.0001f)
        assertEquals(0f, engine.sanitizeDeltaSeconds(Float.NaN), 0.0001f)
    }

    @Test fun splitsIntoSubsteps() {
        val engine = MotionEngine(maxDeltaSeconds = 0.16f, maxSubSteps = 4)
        val result = engine.splitDelta(0.16f)
        assertEquals(4, result.steps)
        assertTrue(result.stepDt > 0f)
    }
}
