package com.pixelpals.app.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMotionTest {
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
