package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorgiHomeTurnMotionTest {
    @Test fun extendedTurnBrakesBeforeThePoseClockAndCommitsAtTheEnd() {
        for (fps: Int in listOf(30, 60, 120)) {
            for (left in listOf(false, true)) {
                val motion = CompanionMotion(turnDurationSeconds = .56f, turnCommitSeconds = .56f)
                startTurn(motion, left)
                val heading = if (left) -1f else 1f
                var plantedX: Float? = null
                var plantedY: Float? = null
                var plantedDistance: Float? = null
                var ticks = 0
                while (motion.isTurning && ticks++ < fps * 3) {
                    val previousSpeed = motion.speed
                    val previousX = motion.x
                    val previousDistance = motion.distanceTravelled
                    motion.advance(1f / fps, if (left) 860f else 140f, 650f, 1f, 0,
                        toyFacesLeft = left)
                    if (previousSpeed > 0f) {
                        assertEquals("Braking must not advance the turn artwork clock", 0f, motion.turnElapsed, .0001f)
                        assertTrue("Turn braking must not accelerate", motion.speed <= previousSpeed + .001f)
                        assertTrue("Braking keeps the original travel direction", (motion.x - previousX) * heading >= -.001f)
                        assertTrue("Braking keeps distance monotonic", motion.distanceTravelled >= previousDistance - .001f)
                    } else {
                        if (plantedX == null) {
                            plantedX = motion.x
                            plantedY = motion.y
                            plantedDistance = motion.distanceTravelled
                        }
                        assertEquals("Planted turn must hold X", plantedX!!, motion.x, .001f)
                        assertEquals("Planted turn must hold Y", plantedY!!, motion.y, .001f)
                        assertEquals("Planted turn must hold travel", plantedDistance!!, motion.distanceTravelled, .001f)
                    }
                    if (motion.isTurning) assertEquals("Extended turn keeps its initial facing", left, motion.isFacingLeft)
                }
                assertTrue("Turn must reach its endpoint", ticks < fps * 3)
                assertTrue("Turn must include a planted phase", plantedX != null)
                assertTrue("Turn ends at its configured duration", motion.turnElapsed >= .56f && motion.turnElapsed <= .56f + 1f / fps + .001f)
                assertEquals("Facing commits only at the endpoint", !left, motion.isFacingLeft)
            }
        }
    }

    @Test fun defaultTurnKeepsLegacyDurationAndMidpointCommit() {
        val motion = CompanionMotion()
        assertEquals(CompanionMotion.TURN_SECONDS, motion.turnDurationSeconds, 0f)
        startTurn(motion, true)
        var ticks = 0
        while (motion.speed > 0f && ticks++ < 120) motion.advance(1f / 60f, 860f, 650f, 1f, 0, toyFacesLeft = true)
        assertEquals(0f, motion.turnElapsed, .0001f)
        repeat(10) {
            motion.advance(1f / 60f, 860f, 650f, 1f, 0, toyFacesLeft = true)
            assertTrue("Legacy turn keeps its original facing before midpoint", motion.isFacingLeft)
        }
        assertTrue(motion.turnElapsed < CompanionMotion.TURN_SECONDS / 2f)
        motion.advance(1f / 60f, 860f, 650f, 1f, 0, toyFacesLeft = true)
        assertTrue("Legacy turn commits at its midpoint", !motion.isFacingLeft)
        while (motion.isTurning && ticks++ < 120) motion.advance(1f / 60f, 860f, 650f, 1f, 0, toyFacesLeft = true)
        assertTrue("Legacy turn completes", ticks < 120)
    }

    private fun startTurn(motion: CompanionMotion, left: Boolean) {
        motion.context = CompanionIntentContext(hasToy = true)
        motion.finishExcursion(left)
        motion.beginIntent(CompanionIntent.PLAY)
        val initialTarget = if (left) 140f else 860f
        repeat(12) {
            motion.advance(.05f, initialTarget, 650f, 1f, 0, toyFacesLeft = left)
        }
        assertTrue("Approach must have built speed before turning", motion.speed > 0f)
        motion.advance(.01f, if (left) 860f else 140f, 650f, 1f, 0, toyFacesLeft = !left)
        assertTrue("Opposite target must start a turn", motion.isTurning)
    }
}
