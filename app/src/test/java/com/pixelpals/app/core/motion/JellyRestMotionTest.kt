package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.SpeciesCarePose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellyRestMotionTest {
    @Test fun careClampsToAwakeEndpointsAndKeepsSleepFramesInRange(): Unit {
        val start: JellyRestPose = JellyRestMotion.sampleCare(-1f, false)
        val end: JellyRestPose = JellyRestMotion.sampleCare(2f, false)
        assertEquals(JellyRestPose(SpeciesCarePose(), 20, 0f), start)
        assertEquals(JellyRestPose(SpeciesCarePose(), 20, 0f), end)
        for (step: Int in 0..100) {
            val pose: JellyRestPose = JellyRestMotion.sampleCare(step / 100f, false)
            assertTrue(pose.frame == 20 || pose.frame in 24..29)
            assertTrue(pose.sleepAmount in 0f..1f)
        }
    }

    @Test fun careSleepsThenWakesWithoutTruncatingTheFinalOnePointTwoSeconds(): Unit {
        assertEquals(20, JellyRestMotion.sampleCare(0f, false).frame)
        assertTrue(JellyRestMotion.sampleCare(.15f, false).frame in 24..29)
        assertEquals(29, JellyRestMotion.sampleCare(.50f, false).frame)
        assertEquals(29, JellyRestMotion.sampleCare(.70f, false).frame)
        assertTrue(JellyRestMotion.sampleCare(.85f, false).frame in 24..29)
        val wake: JellyRestPose = JellyRestMotion.sampleScheduled(1.2f, true, false)
        assertEquals(JellyRestPose(SpeciesCarePose(), 20, 0f), wake)
        assertEquals(29, JellyRestMotion.sampleScheduled(1.2f, false, false).frame)
    }

    @Test fun careFramesUseTheRequestedTwoHundredFiftyMillisecondSlots(): Unit {
        val expected: List<Int> = listOf(20, 24, 25, 26, 27, 28, 29, 29, 29, 29, 29, 29, 29, 29, 29, 28, 27, 26, 25, 24, 20)
        val actual: List<Int> = (0..20).map { index: Int ->
            JellyRestMotion.sampleCare(index * 250f / 5_000f, false).frame
        }
        assertEquals(expected, actual)
    }

    @Test fun scheduledFramesFollowTheSameEntryAndExitFigure(): Unit {
        val expectedEntry: List<Int> = listOf(20, 24, 25, 26, 27, 28, 29)
        val expectedExit: List<Int> = listOf(29, 28, 27, 26, 25, 24, 20)
        val actualEntry: List<Int> = (0..6).map { index: Int ->
            JellyRestMotion.sampleScheduled(index * 1.2f / 6f, false, false).frame
        }
        val actualExit: List<Int> = (0..6).map { index: Int ->
            JellyRestMotion.sampleScheduled(index * 1.2f / 6f, true, false).frame
        }
        assertEquals(expectedEntry, actualEntry)
        assertEquals(expectedExit, actualExit)
    }

    @Test fun bodyAreaIsConstantAndBodyHasNoTravelOrRotation(): Unit {
        for (step: Int in 0..200) {
            val pose: JellyRestPose = JellyRestMotion.sampleCare(step / 200f, false)
            assertEquals(1f, pose.body.scaleX * pose.body.scaleY, .0001f)
            assertEquals(0f, pose.body.x, 0f)
            assertEquals(0f, pose.body.y, 0f)
            assertEquals(0f, pose.body.rotation, 0f)
        }
    }

    @Test fun bodyCurveIsContinuousAtSleepBoundaries(): Unit {
        val boundaries: List<Float> = listOf(.30f, .70f)
        val epsilon: Float = .0001f
        for (boundary: Float in boundaries) {
            val before: JellyRestPose = JellyRestMotion.sampleCare(boundary - epsilon, false)
            val after: JellyRestPose = JellyRestMotion.sampleCare(boundary + epsilon, false)
            assertTrue(abs(before.body.scaleY - after.body.scaleY) < .002f)
            assertTrue(abs(before.sleepAmount - after.sleepAmount) < .002f)
        }
    }

    @Test fun reducedMotionIsStillAndScheduledEndpointsAreImmediate(): Unit {
        for (step: Int in 0..99) {
            val pose: JellyRestPose = JellyRestMotion.sampleCare(step / 100f, true)
            assertEquals(JellyRestPose(SpeciesCarePose(scaleX = 1f / .86f, scaleY = .86f), 29, 1f), pose)
        }
        assertEquals(JellyRestPose(SpeciesCarePose(), 20, 0f), JellyRestMotion.sampleCare(1f, true))
        assertEquals(JellyRestPose(SpeciesCarePose(), 20, 0f), JellyRestMotion.sampleScheduled(0f, true, true))
        assertEquals(29, JellyRestMotion.sampleScheduled(0f, false, true).frame)
    }
}
