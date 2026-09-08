package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMotionTest {
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
