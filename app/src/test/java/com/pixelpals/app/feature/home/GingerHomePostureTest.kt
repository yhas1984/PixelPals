package com.pixelpals.app.feature.home

import com.pixelpals.app.core.motion.GingerPostureMotion
import org.junit.Assert.*
import org.junit.Test

class GingerHomePostureTest {
    @Test fun observeFromGreetingRemainsSeatedAndBothRoutesPlantBeforeMovingAndPlaying() {
        for (fps in listOf(30, 60, 120)) for (target in listOf(240f, 760f)) {
            val motion = CompanionMotion(gingerPostures = true)
            motion.context = CompanionIntentContext(hasToy = true)
            motion.beginIntent(CompanionIntent.OBSERVE)
            assertTrue(motion.gingerIsSeated)
            motion.beginIntent(CompanionIntent.PLAY)
            assertEquals(GingerPostureMotion.Transition.STAND_UP, motion.gingerPostureTransition)
            var ticks = 0
            while (motion.gingerPostureTransition != null && ticks++ < fps) {
                motion.advance(1f / fps, target, 500f, 1f, 80)
                assertEquals(500f, motion.x, 0f)
                assertEquals(650f, motion.y, 0f)
                assertEquals(0f, motion.speed, 0f)
                assertEquals(0f, motion.distanceTravelled, 0f)
            }
            assertNull(motion.gingerPostureTransition)
            assertEquals(18, motion.gingerPostureEndpoint)
            assertFalse(motion.gingerIsSeated)
            ticks = 0
            while (motion.activity != CompanionActivity.PLAY && ticks++ < fps * 12)
                motion.advance(1f / fps, target, 500f, 1f, 80)
            assertEquals(CompanionActivity.PLAY, motion.activity)
            assertEquals(target, motion.x, .01f)
            assertEquals(target < 500f, motion.isFacingLeft)
            assertEquals(GingerPostureMotion.Transition.SIT_DOWN, motion.gingerPostureTransition)
            val distance = motion.distanceTravelled
            ticks = 0
            while (motion.gingerPostureTransition != null && ticks++ < fps) {
                motion.advance(1f / fps, target, 500f, 1f, 80)
                assertEquals(target, motion.x, .01f)
                assertEquals(650f, motion.y, 0f)
                assertEquals(0f, motion.speed, 0f)
                assertEquals(distance, motion.distanceTravelled, 0f)
            }
            assertNull(motion.gingerPostureTransition)
            assertEquals(0, motion.gingerPostureEndpoint)
            assertTrue(motion.gingerIsSeated)
        }
    }

    @Test fun scheduledWakeFinishesUprightAndNextApproachDoesNotRepeatRise() {
        for (fps in listOf(30, 60, 120)) {
            val motion = CompanionMotion(gingerPostures = true)
            motion.advanceScheduledRest(true, 0f)
            assertEquals(CompanionActivity.REST, motion.activity)
            motion.advanceScheduledRest(false, 0f)
            assertEquals(CompanionActivity.WAKE, motion.activity)
            var ticks = 0
            while (motion.activity == CompanionActivity.WAKE && ticks++ < fps * 2) {
                motion.advanceScheduledWake(1f / fps)
                assertEquals(500f, motion.x, 0f)
                assertEquals(650f, motion.y, 0f)
            }
            assertEquals(CompanionActivity.OBSERVE, motion.activity)
            assertFalse(motion.gingerIsSeated)
            motion.beginIntent(CompanionIntent.PLAY)
            assertNull(motion.gingerPostureTransition)
        }
    }

    @Test fun reducedMotionClearsPendingPostureAndPreservesPosition() {
        val motion = CompanionMotion(gingerPostures = true)
        motion.beginIntent(CompanionIntent.PLAY)
        motion.advance(.1f, 760f, 500f, 1f, 80)
        motion.settleWithoutMovement(false, reduced = true)
        assertNull(motion.gingerPostureTransition)
        assertTrue(motion.gingerIsSeated)
        assertEquals(500f, motion.x, 0f)
        motion.settleWithoutMovement(true, reduced = true)
        assertEquals(CompanionActivity.REST, motion.activity)
        assertNull(motion.gingerPostureTransition)
        assertEquals(500f, motion.x, 0f)
    }
}
