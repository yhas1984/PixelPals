package com.pixelpals.app.feature.home

import org.junit.Assert.*
import org.junit.Test

class GingerRestMotionTest {
    @Test fun newRestUsesTheExistingPostureWithoutInsertingAnotherSitDown() {
        for (standing: Boolean in listOf(false, true)) {
            val motion = CompanionMotion(gingerPostures = true, gingerRestArtwork = true)
            motion.context = CompanionIntentContext(hasBed = false)
            if (standing) motion.finishExcursion(false)
            val x: Float = motion.x
            val y: Float = motion.y
            motion.beginIntent(CompanionIntent.REST)
            assertEquals(CompanionActivity.REST, motion.activity)
            assertEquals(!standing, motion.gingerRestStartsSeated)
            assertNull(motion.gingerPostureTransition)
            repeat(8) { motion.advanceScheduledRest(true, .1f) }
            assertEquals(x, motion.x, 0f)
            assertEquals(y, motion.y, 0f)
            assertNull(motion.gingerPostureTransition)
        }
    }

    @Test fun retainingAPoseNeverRewindsSleepOrRestartsAnInterruptedWake() {
        val motion = CompanionMotion(gingerPostures = true, gingerRestArtwork = true)
        motion.advanceScheduledRest(true, .1f)
        motion.retainScheduledRestPose(.54f)
        motion.retainScheduledRestPose(.18f)
        assertEquals(.54f, motion.elapsed, .001f)
        val x: Float = motion.x
        val y: Float = motion.y
        motion.advanceScheduledRest(false, 0f)
        assertEquals(.54f, motion.restElapsedBeforeWake, .001f)
        assertNull(motion.gingerPostureTransition)
        motion.retainScheduledRestPose(1f)
        assertEquals(0f, motion.elapsed, 0f)
        repeat(6) {
            assertTrue(motion.advanceScheduledWake(.1f))
            assertEquals(x, motion.x, 0f)
            assertEquals(y, motion.y, 0f)
        }
        repeat(7) { motion.advanceScheduledWake(.1f) }
        assertEquals(CompanionActivity.OBSERVE, motion.activity)
        assertFalse(motion.gingerIsSeated)
        assertNull(motion.gingerPostureTransition)
    }
}
