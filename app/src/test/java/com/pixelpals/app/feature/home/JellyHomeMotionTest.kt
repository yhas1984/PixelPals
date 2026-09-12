package com.pixelpals.app.feature.home

import com.pixelpals.app.core.motion.JellyElasticMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyHomeMotionTest {
    @Test fun locomotionUsesContinuousElasticPoseAndLandingRecoversExactly() {
        val motion = JellyHomeMotion()
        repeat(36) { step ->
            val distance = step * 3f
            motion.advance(.016f, distance, CompanionActivity.APPROACH_TOY, 50f, false)
            assertTrue(motion.pose.scaleY.isFinite())
            assertTrue(motion.pose.scaleY in .81f..1.13f)
        }
        motion.advance(.016f, 150f, CompanionActivity.APPROACH_TOY, 50f, false)
        motion.advance(.016f, 150f, CompanionActivity.OBSERVE, 0f, false)
        val landingStart = motion.pose
        repeat(5) { motion.advance(.016f, JellyElasticMotion.STRIDE, CompanionActivity.OBSERVE, 0f, false) }
        assertTrue(kotlin.math.abs(motion.pose.lift) < kotlin.math.abs(landingStart.lift))
        repeat(40) { motion.advance(.016f, JellyElasticMotion.STRIDE, CompanionActivity.OBSERVE, 0f, false) }
        assertEquals(1f, motion.pose.scaleY, .0001f)
        assertEquals(0f, motion.pose.lift, .0001f)
    }

    @Test fun zeroDeltaPreservesAirbornePoseAndStoppingDescendsFromThatPose() {
        val motion = JellyHomeMotion()
        motion.advance(.016f, 0f, CompanionActivity.APPROACH_TOY, 50f, false)
        repeat(15) { step -> motion.advance(.016f, (step + 1) * 3f, CompanionActivity.APPROACH_TOY, 50f, false) }
        val airborne = motion.pose
        assertTrue(kotlin.math.abs(airborne.lift) > .01f)
        motion.advance(0f, 500f, CompanionActivity.OBSERVE, 0f, false)
        assertEquals(airborne, motion.pose)
        motion.advance(.08f, 500f, CompanionActivity.OBSERVE, 0f, false)
        assertTrue(kotlin.math.abs(motion.pose.lift) < kotlin.math.abs(airborne.lift))
        assertEquals(airborne.scaleY, motion.pose.scaleY, .0001f)
    }

    @Test fun touchRecoveryFinishesAtNeutralAndReducedMotionIsStationary() {
        val motion = JellyHomeMotion()
        motion.touch()
        repeat(60) { motion.advance(.016f, 0f, CompanionActivity.PLAY, 0f, false) }
        assertEquals(1f, motion.pose.scaleY, .0001f)
        motion.advance(.016f, 500f, CompanionActivity.APPROACH_TOY, 50f, false)
        motion.advance(.5f, 700f, CompanionActivity.APPROACH_TOY, 50f, true)
        assertEquals(JellyHomeMotion.Pose(), motion.pose)
        motion.advance(.5f, 800f, CompanionActivity.APPROACH_TOY, 50f, true)
        assertEquals(JellyHomeMotion.Pose(), motion.pose)
    }

    @Test fun playWaitsForContactAndToyMovesOnlyAfterCompression() {
        val motion = JellyHomeMotion()
        motion.advance(.016f, 0f, CompanionActivity.APPROACH_TOY, 50f, false)
        repeat(15) { motion.advance(.016f, (it + 1) * 3f, CompanionActivity.APPROACH_TOY, 50f, false) }
        val airborne = motion.pose
        assertTrue(airborne.lift < -10f)
        motion.advance(.016f, 45f, CompanionActivity.PLAY, 0f, false)
        assertTrue("Entering play teleported to the floor", motion.pose.lift < -10f)
        assertEquals(airborne.scaleY, motion.pose.scaleY, .001f)
        assertEquals(0f, motion.toyResponse, 0f)
        var toyMoved = false
        repeat(150) {
            motion.advance(.016f, 45f, CompanionActivity.PLAY, 0f, false)
            if (motion.toyResponse > .01f) {
                toyMoved = true
                assertEquals("Spring reacted before contact", 0f, motion.pose.lift, 0f)
            }
        }
        assertTrue(toyMoved)
        assertEquals(JellyHomeMotion.Pose(), motion.pose)
        assertEquals(0f, motion.toyResponse, 0f)
    }

    @Test fun interruptingARecoveryWithMovementStartsAtTheVisiblePose() {
        val motion = JellyHomeMotion()
        motion.touch()
        motion.advance(.18f, 200f, CompanionActivity.OBSERVE, 0f, false)
        val compressed = motion.pose
        motion.advance(.01f, 250f, CompanionActivity.APPROACH_TOY, 50f, false)
        assertTrue(kotlin.math.abs(motion.pose.scaleY - compressed.scaleY) < .02f)
        repeat(120) { motion.advance(.016f, 250f + it, CompanionActivity.APPROACH_TOY, 50f, false) }
        assertTrue(motion.pose.scaleY.isFinite())
    }
}
