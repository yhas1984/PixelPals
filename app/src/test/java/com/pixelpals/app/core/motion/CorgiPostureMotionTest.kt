package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorgiPostureMotionTest {
    @Test
    fun sitDownUsesPlantedSequenceAndHoldsFinalSeat() {
        assertEquals(0, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, 0f).frame)
        assertEquals(15, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, .10f).frame)
        assertEquals(16, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, .25f).frame)
        assertEquals(17, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, .42f).frame)
        val done: CorgiPostureMotion.Pose = CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, .52f)
        assertEquals(17, done.frame)
        assertTrue(done.finished)
    }

    @Test
    fun standUpReversesSequenceAndEndsOnStandingFrame() {
        assertEquals(17, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.STAND_UP, 0f).frame)
        assertEquals(16, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.STAND_UP, .10f).frame)
        assertEquals(15, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.STAND_UP, .25f).frame)
        assertEquals(0, CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.STAND_UP, .42f).frame)
        val done: CorgiPostureMotion.Pose = CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.STAND_UP, .52f)
        assertEquals(0, done.frame)
        assertTrue(done.finished)
    }

    @Test
    fun restBlinkIsBriefAndReturnsToSeatedBody() {
        assertEquals(17, CorgiPostureMotion.restingFrame(1f))
        assertEquals(18, CorgiPostureMotion.restingFrame(2.21f))
        assertEquals(19, CorgiPostureMotion.restingFrame(2.30f))
        assertEquals(18, CorgiPostureMotion.restingFrame(2.39f))
        assertEquals(17, CorgiPostureMotion.restingFrame(2.50f))
        assertFalse(CorgiPostureMotion.poseAt(CorgiPostureMotion.Transition.SIT_DOWN, .42f).finished)
    }
}
