package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YukiHomeHeatTest {
    @Test fun homeAdoptsTheSharedHysteresisOnEntry(): Unit {
        val heat = YukiHomeHeat()
        heat.updateTemperature(39f, sharedLatched = true)
        assertTrue(heat.active)
        heat.advance(.016f, reducedMotion = true)
        heat.updateTemperature(39f, sharedLatched = false)
        heat.advance(.016f, reducedMotion = true)
        assertFalse(heat.active)
    }
    @Test fun temperatureUsesHysteresisAndIgnoresMissingSamples() {
        val heat = YukiHomeHeat()
        heat.updateTemperature(Float.NaN)
        heat.updateTemperature(null)
        assertFalse(heat.active)
        heat.updateTemperature(40f)
        assertTrue(heat.isMelted)
        heat.updateTemperature(39f)
        assertTrue(heat.active)
        heat.updateTemperature(38f)
        assertFalse(heat.isMelted)
    }

    @Test fun meltAndRecoveryAreGradualAndClamped() {
        val heat = YukiHomeHeat()
        heat.updateTemperature(41f)
        repeat(4) { heat.advance(.1f) }
        assertEquals(.4f, heat.elapsedSeconds, .0001f)
        repeat(20) { heat.advance(.1f) }
        assertEquals(.96f, heat.elapsedSeconds, .0001f)
        heat.updateTemperature(37f)
        repeat(2) { heat.advance(.1f) }
        assertEquals(.76f, heat.elapsedSeconds, .0001f)
        repeat(20) { heat.advance(.1f) }
        assertEquals(0f, heat.elapsedSeconds, .0001f)
        assertFalse(heat.active)
    }

    @Test fun reducedMotionFreezesMeltPoseWithoutAnimation() {
        val heat = YukiHomeHeat()
        heat.updateTemperature(42f)
        heat.advance(.016f, reducedMotion = true)
        assertEquals(.96f, heat.elapsedSeconds, .0001f)
        heat.updateTemperature(37f)
        heat.advance(.016f, reducedMotion = true)
        assertEquals(0f, heat.elapsedSeconds, .0001f)
    }
}
