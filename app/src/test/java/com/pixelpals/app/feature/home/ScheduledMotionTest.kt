package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMotionTest {
    @Test fun scheduleKeepsAnExistingSleepAtItsCurrentBed() {
        val motion = CompanionMotion()
        motion.context = CompanionIntentContext(energy = 10, hasBed = true)
        motion.beginIntent(CompanionIntent.REST)
        repeat(500) { motion.advance(.05f, 500f, 312.5f, 1f, 0, bedY = 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        val elapsed: Float = motion.elapsed
        assertTrue(elapsed > 2f)
        motion.advanceScheduledRest(true, .05f, 312.5f, 548f)
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(elapsed + .05f, motion.elapsed, .001f)
        assertEquals(312.5f, motion.x, .001f)
        assertEquals(548f, motion.y, .001f)
    }

    @Test fun scheduleWakesAnAlreadySleepingPetBeforeApproachingAnotherBed() {
        val motion = CompanionMotion()
        motion.settleWithoutMovement(true)
        motion.advanceScheduledRest(true, .05f, 312.5f, 548f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
        repeat(20) { motion.advanceScheduledRest(true, .05f, 312.5f, 548f) }
        assertEquals(CompanionActivity.WAKE, motion.activity)
        assertEquals(500f, motion.x, .001f)
        assertEquals(650f, motion.y, .001f)
        repeat(1000) { motion.advanceScheduledRest(true, .05f, 312.5f, 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(312.5f, motion.x, .001f)
    }

    @Test fun reactivatingSchedulePreservesTheWakeAlreadyInProgress() {
        val motion = CompanionMotion()
        motion.advanceScheduledRest(true, .1f)
        motion.advanceScheduledRest(false, 0f)
        assertEquals(.1f, motion.restElapsedBeforeWake, .001f)
        motion.advanceScheduledWake(.1f)
        motion.advanceScheduledRest(true, .1f, 300f, 550f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
        assertEquals(.2f, motion.elapsed, .001f)
        assertEquals(500f, motion.x, .001f)
        repeat(1000) { motion.advanceScheduledRest(true, .05f, 300f, 550f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(300f, motion.x, .001f)
    }

    @Test fun movingABedWakesBeforeWalkingAndSettlesAtTheNewLocation() {
        val motion = CompanionMotion()
        repeat(1000) { motion.advanceScheduledRest(true, .05f, 312.5f, 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        motion.advanceScheduledRest(true, .05f, 662.5f, 651f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
        repeat(20) {
            motion.advanceScheduledRest(true, .05f, 662.5f, 651f)
            assertEquals(CompanionActivity.WAKE, motion.activity)
            assertEquals(312.5f, motion.x, .001f)
            assertEquals(548f, motion.y, .001f)
        }
        repeat(1000) { motion.advanceScheduledRest(true, .05f, 662.5f, 651f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(662.5f, motion.x, .001f)
        assertEquals(651f, motion.y, .001f)
    }

    @Test fun ordinaryLowEnergyRestAlsoFollowsAMovedBedWithoutSlidingAsleep() {
        val motion = CompanionMotion()
        motion.context = CompanionIntentContext(energy = 10, hasBed = true)
        motion.settleWithoutMovement(true)
        motion.advance(.05f, 500f, 312.5f, 1f, 0, bedY = 548f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
        assertEquals(500f, motion.x, .001f)
        repeat(1000) { motion.advance(.05f, 500f, 312.5f, 1f, 0, bedY = 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(312.5f, motion.x, .001f)
        assertEquals(548f, motion.y, .001f)
    }

    @Test fun endingTheScheduleDuringRelocationDoesNotCutTheWakePoseShort() {
        val motion = CompanionMotion()
        motion.advanceScheduledRest(true, .05f)
        motion.advanceScheduledRest(true, .05f, 312.5f, 548f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
        motion.advanceScheduledRest(true, .1f, 312.5f, 548f)
        assertFalse(motion.advanceScheduledRest(false, .05f))
        assertEquals(CompanionActivity.WAKE, motion.activity)
        assertEquals(.1f, motion.elapsed, .001f)
        repeat(30) { motion.advanceScheduledWake(.05f) }
        assertEquals(CompanionActivity.OBSERVE, motion.activity)
        assertEquals(500f, motion.x, .001f)
    }

    @Test fun scheduledRestWalksToBedAndStaysThere() {
        val motion = CompanionMotion()
        assertTrue(motion.advanceScheduledRest(true, 1f / 60f, 312.5f, 548f))
        assertEquals(CompanionActivity.APPROACH_BED, motion.activity)
        assertEquals(500f, motion.x, .001f)
        repeat(3000) { motion.advanceScheduledRest(true, 1f / 60f, 312.5f, 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(312.5f, motion.x, .001f)
        assertEquals(548f, motion.y, .001f)
        repeat(1000) { motion.advanceScheduledRest(true, .1f, 312.5f, 548f) }
        assertEquals(CompanionActivity.REST, motion.activity)
    }

    @Test fun removedBedAndCancelledScheduleDoNotTrapApproach() {
        val motion = CompanionMotion()
        motion.advanceScheduledRest(true, .1f, 300f, 445f)
        motion.advanceScheduledRest(false, .1f)
        assertEquals(CompanionActivity.OBSERVE, motion.activity)
        motion.advanceScheduledRest(true, .1f, 300f, 445f)
        motion.advanceScheduledRest(true, .1f)
        assertEquals(CompanionActivity.REST, motion.activity)
    }

    @Test fun wakeCompletesWithoutResumingLocomotionEarly() {
        val motion = CompanionMotion()
        motion.advanceScheduledRest(true, .1f)
        motion.advanceScheduledRest(false, 0f)
        val x = motion.x
        val y = motion.y
        repeat(10) {
            assertTrue(motion.advanceScheduledWake(.1f))
            assertEquals(CompanionActivity.WAKE, motion.activity)
            assertEquals(x, motion.x, .001f)
            assertEquals(y, motion.y, .001f)
            assertEquals(.1f, motion.restElapsedBeforeWake, .001f)
        }
        repeat(3) { motion.advanceScheduledWake(.1f) }
        assertEquals(CompanionActivity.OBSERVE, motion.activity)
        assertFalse(motion.advanceScheduledWake(.1f))
        motion.advanceScheduledRest(true, .1f)
        assertEquals(CompanionActivity.REST, motion.activity)
    }

    @Test fun sleepStaysStillAndWakesWhenScheduleEnds() {
        val motion = CompanionMotion()
        val x = motion.x
        val y = motion.y
        repeat(1000) { assertTrue(motion.advanceScheduledRest(true, .1f)) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(x, motion.x, .001f)
        assertEquals(y, motion.y, .001f)
        assertTrue(motion.elapsed > 1.4f)
        assertFalse(motion.advanceScheduledRest(false, .1f))
        assertEquals(CompanionActivity.WAKE, motion.activity)
        assertEquals(0f, motion.elapsed, .001f)
        motion.advanceScheduledRest(false, .1f)
        assertEquals(CompanionActivity.WAKE, motion.activity)
    }
}
