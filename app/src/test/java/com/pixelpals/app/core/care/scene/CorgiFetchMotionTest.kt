package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.motion.PetBounds
import org.junit.Assert.*
import org.junit.Test

class CorgiFetchMotionTest {
    private val bounds: PetBounds = PetBounds(0, 1_120, 100, 2_700)

    @Test fun runsAcrossTheDesktopInsteadOfReturningToTheStart(): Unit {
        val plan: CorgiFetchPlan = plan(100f)
        assertTrue(plan.endX - plan.startX > 640f)
        assertTrue(plan.timing.durationMs < 2_500L)
        val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, plan.timing.durationMs)
        assertEquals(plan.endX, pose.petX, 0f)
        assertTrue(pose.isCaught)
        assertEquals(2, pose.careFrame)
    }

    @Test fun usesAllFourRunningFramesAndThenLowersTheHead(): Unit {
        val plan: CorgiFetchPlan = plan(100f)
        val frames: Set<Int> = (160L until plan.catchMs - 220L step 16L)
            .mapNotNull { CorgiFetchMotion.getPose(plan, it).regularFrame }.toSet()
        assertEquals(setOf(10, 11, 12, 13), frames)
        assertNull(CorgiFetchMotion.getPose(plan, plan.catchMs - 200L).regularFrame)
        assertEquals(0, CorgiFetchMotion.getPose(plan, plan.catchMs - 200L).careFrame)
        assertEquals(1, CorgiFetchMotion.getPose(plan, plan.catchMs + 150L).careFrame)
    }

    @Test fun ballRollsAheadOfThePetInBothDirectionsUntilPickup(): Unit {
        for (left: Boolean in listOf(false, true)) {
            val plan: CorgiFetchPlan = plan(if (left) 1_000f else 100f, left)
            var previousBall: Float = CorgiFetchMotion.getPose(plan, 0L).ballX
            for (elapsed: Long in 0L until plan.catchMs step 16L) {
                val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, elapsed)
                assertTrue((pose.ballX - previousBall) * plan.direction >= 0f)
                assertTrue((pose.ballX - (pose.petX + 160f)) * plan.direction > 0f)
                assertFalse(pose.isCaught)
                previousBall = pose.ballX
            }
            assertTrue(CorgiFetchMotion.getPose(plan, plan.catchMs).isCaught)
        }
    }

    @Test fun choosesTheOpenSideAndStaysWithinBounds(): Unit {
        assertEquals(-1f, plan(1_110f).direction, 0f)
        assertEquals(1f, plan(10f, true).direction, 0f)
        for (x: Int in 0..1_120 step 40) for (left: Boolean in listOf(false, true)) {
            val plan: CorgiFetchPlan = plan(x.toFloat(), left)
            for (elapsed: Long in 0L..plan.timing.durationMs step 16L) {
                val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, elapsed)
                assertTrue(pose.petX in 0f..1_120f)
                assertTrue(pose.ballX in 0f..1_440f)
                assertEquals(2_700f, pose.petY, 0f)
            }
        }
    }

    @Test fun reducedMotionKeepsTheExistingPetInPlace(): Unit {
        val plan: CorgiFetchPlan = CorgiFetchMotion.createPlan(CarePoint(500f, 900f), bounds, 320, false, true)
        for (elapsed: Long in 0L..plan.timing.durationMs step 16L) {
            val pose: CorgiFetchPose = CorgiFetchMotion.getPose(plan, elapsed)
            assertEquals(500f, pose.petX, 0f)
            assertEquals(900f, pose.petY, 0f)
            assertNull(pose.regularFrame)
            assertEquals(2, pose.careFrame)
        }
    }

    @Test fun catchesAndCommitsExactlyOnce(): Unit {
        val plan: CorgiFetchPlan = plan(100f)
        val scene: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC, plan.timing)
        assertFalse(scene.advance(plan.catchMs - 1L))
        assertTrue(scene.advance(1L))
        repeat(200) { assertFalse(scene.advance(16L)) }
        assertTrue(scene.isComplete)
    }

    @Test fun cancellationBeforeTheCatchDoesNotApplyCare(): Unit {
        val scene: CareSceneController = CareSceneController(CareSceneAction.PLAY, CareSceneMode.AUTOMATIC, plan(100f).timing)
        assertFalse(scene.advance(300L))
        scene.cancel()
        assertFalse(scene.advance(10_000L))
    }

    @Test fun narrowScreensAndFeedingRemainSafe(): Unit {
        val plan: CorgiFetchPlan = CorgiFetchMotion.createPlan(CarePoint(0f, 100f), PetBounds(0, 0, 0, 100), 320, true, false)
        assertEquals(0f, CorgiFetchMotion.getPose(plan, 5_000L).petX, 0f)
        assertEquals(5_600L, CorgiFeedingMotion.timing.durationMs)
        assertEquals(4_600L, CorgiFeedingMotion.timing.completionMs)
    }

    private fun plan(x: Float, left: Boolean = false): CorgiFetchPlan =
        CorgiFetchMotion.createPlan(CarePoint(x, 2_700f), bounds, 320, left, false)
}
