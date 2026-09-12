package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellyFlightMotionTest {
    private fun flight(vx: Float = 120f, vy: Float = -420f): JellyFlightMotion = JellyFlightMotion(
        startX = 100f, startY = 500f, velocityX = vx, velocityY = vy,
        gravity = 600f, minX = 0f, maxX = 400f, ceilingY = 0f, floorY = 500f,
    )

    @Test fun creationAndZeroDeltaPreservePosition() {
        val motion: JellyFlightMotion = flight()
        motion.advance(0f)
        assertEquals(100f, motion.x, 0f)
        assertEquals(500f, motion.y, 0f)
        assertTrue(!motion.landed)
    }

    @Test fun samplingAt30To120FpsReachesSameFloorAndHorizontalPosition() {
        val results: MutableList<Pair<Float, Float>> = mutableListOf()
        for (fps: Int in listOf(30, 60, 120)) {
            val motion: JellyFlightMotion = JellyFlightMotion(100f, 500f, 120f, -420f, 600f, 0f, 400f, 0f, 500f)
            repeat(240) { motion.advance(1f / fps) }
            assertTrue(motion.landed)
            results += motion.x to motion.y
        }
        assertEquals(results[0].first, results[1].first, .02f)
        assertEquals(results[1].first, results[2].first, .02f)
        results.forEach { assertEquals(500f, it.second, .02f) }
    }

    @Test fun stateAtHalfSecondIsFrameRateIndependent() {
        val states: MutableList<FloatArray> = mutableListOf()
        for (fps: Int in listOf(30, 60, 120)) {
            val motion: JellyFlightMotion = flight()
            repeat(fps / 2) { motion.advance(1f / fps) }
            states += floatArrayOf(motion.x, motion.y, motion.velocityX, motion.velocityY)
        }
        for (index: Int in 1 until states.size) {
            assertEquals(states[0][0], states[index][0], .02f)
            assertEquals(states[0][1], states[index][1], .02f)
            assertEquals(states[0][3], states[index][3], .02f)
        }
    }

    @Test fun ceilingAndWallStopVelocityWithoutCrossing() {
        val motion: JellyFlightMotion = JellyFlightMotion(390f, 100f, 100f, -500f, 600f, 0f, 400f, 0f, 500f)
        motion.advance(.232f)
        assertTrue("The upward flight has not hit the ceiling yet", motion.velocityY < 0f)
        motion.advance(.018f)
        assertTrue(motion.x <= 400f)
        assertTrue(motion.y >= 0f && motion.y < 1f)
        assertEquals(0f, motion.velocityX, .001f)
        assertTrue(motion.velocityY >= 0f)
    }

    @Test fun largeDeltaProcessesEventsAndLands() {
        val motion: JellyFlightMotion = flight(vx = 500f, vy = -300f)
        motion.advance(10f)
        assertTrue(motion.landed)
        assertEquals(500f, motion.y, .001f)
        assertEquals(0f, motion.velocityX, .001f)
        assertEquals(0f, motion.velocityY, .001f)
        assertTrue(abs(motion.x) <= 400f)
    }

    @Test fun exactFloorImpactIsClassifiedAsLanding() {
        val motion: JellyFlightMotion = JellyFlightMotion(100f, 500f, 0f, -300f, 600f, 0f, 400f, 0f, 500f)
        motion.advance(1f)
        assertTrue(motion.landed)
        assertEquals(500f, motion.y, .001f)
        assertEquals(0f, motion.velocityY, .001f)
    }

    @Test fun downwardReleaseFromFloorIsAlreadyLandedButUpwardReleaseCanJump() {
        val down: JellyFlightMotion = flight(vy = 40f)
        assertTrue(down.landed)
        down.advance(1f)
        assertEquals(500f, down.y, 0f)
        val up: JellyFlightMotion = flight(vy = -40f)
        assertTrue(!up.landed)
    }
}
