package com.pixelpals.app.core.care.scene

import org.junit.Assert.*
import org.junit.Test

class CorgiAdditionalCareMotionTest {
    @Test fun cleaningScrubsThenRemovesTheSpongeBeforeTheFinalReaction(): Unit {
        val beginning: CorgiAdditionalCarePose = pose(CareSceneAction.CLEAN, 0L)
        val scrub: CorgiAdditionalCarePose = pose(CareSceneAction.CLEAN, 1_400L)
        val done: CorgiAdditionalCarePose = pose(CareSceneAction.CLEAN, 4_000L)
        assertEquals(12, beginning.frame)
        assertEquals(0f, beginning.propAlpha, 0f)
        assertEquals(13, scrub.frame)
        assertEquals(1f, scrub.propAlpha, 0f)
        assertTrue(scrub.foamAmount > 0f)
        assertEquals(15, done.frame)
        assertEquals(0f, done.propAlpha, 0f)
        assertEquals(0f, done.foamAmount, 0f)
        assertEquals(0f, done.rotation, 0f)
    }

    @Test fun restSettlesOnTheCushionAndBreathesWithoutWandering(): Unit {
        assertEquals(16, pose(CareSceneAction.REST, 0L).frame)
        assertEquals(17, pose(CareSceneAction.REST, 1_000L).frame)
        assertEquals(18, pose(CareSceneAction.REST, 2_000L).frame)
        val sleeping: CorgiAdditionalCarePose = pose(CareSceneAction.REST, 3_000L)
        assertEquals(19, sleeping.frame)
        assertTrue(sleeping.breathScale > 1f)
        assertEquals(0f, sleeping.propOffsetX, 0f)
        assertEquals(0f, sleeping.propOffsetY, 0f)
        assertEquals(1f, sleeping.propAlpha, 0f)
    }

    @Test fun medicineReachesOpenMouthThenEmptiesAndWithdraws(): Unit {
        val contact: CorgiAdditionalCarePose = pose(CareSceneAction.MEDICINE, 1_100L)
        val swallowed: CorgiAdditionalCarePose = pose(CareSceneAction.MEDICINE, 2_150L)
        val done: CorgiAdditionalCarePose = pose(CareSceneAction.MEDICINE, 4_000L)
        assertEquals(21, contact.frame)
        assertEquals(0f, contact.propOffsetX, 0f)
        assertEquals(0f, contact.propOffsetY, 0f)
        assertEquals(1f, contact.contentAmount, 0f)
        assertEquals(0f, swallowed.contentAmount, 0f)
        assertEquals(23, done.frame)
        assertEquals(0f, done.propAlpha, 0f)
    }

    @Test fun allNewActionsCommitOnceAndOnlyAfterTheirVisibleContact(): Unit {
        for (action: CareSceneAction in CorgiAdditionalCareMotion.actions) {
            val timing: CareSceneTiming = CorgiAdditionalCareMotion.getTiming(action)
            val scene: CareSceneController = CareSceneController(action, CareSceneMode.AUTOMATIC, timing)
            assertFalse(scene.advance(timing.completionMs - 1L))
            assertTrue(scene.advance(1L))
            repeat(500) { assertFalse(scene.advance(16L)) }
            assertTrue(scene.isComplete)
        }
    }

    @Test fun cancellingAnyNewActionBeforeCompletionNeverCommits(): Unit {
        for (action: CareSceneAction in CorgiAdditionalCareMotion.actions) {
            val scene: CareSceneController = CareSceneController(action, CareSceneMode.AUTOMATIC,
                CorgiAdditionalCareMotion.getTiming(action))
            assertFalse(scene.advance(500L))
            scene.cancel()
            assertFalse(scene.advance(10_000L))
        }
    }

    @Test fun posesStayBoundedAndUseOnlyTheirOwnFrames(): Unit {
        for (action: CareSceneAction in CorgiAdditionalCareMotion.actions) {
            val frames: IntRange = when (action) {
                CareSceneAction.CLEAN -> 12..15
                CareSceneAction.REST -> 16..19
                else -> 20..23
            }
            for (elapsed: Long in -100L..8_000L step 16L) {
                val pose: CorgiAdditionalCarePose = pose(action, elapsed)
                assertTrue(pose.frame in frames)
                assertTrue(pose.propAlpha in 0f..1f)
                assertTrue(pose.contentAmount in 0f..1f)
                assertTrue(pose.foamAmount in 0f..1f)
                assertTrue(pose.propOffsetX in -.12f.. .49f)
                assertTrue(pose.rotation in -3f..3f)
                assertTrue(pose.breathScale in .988f..1.012f)
            }
        }
    }

    @Test fun reducedMotionDisablesMovementButStillShowsTheCareTool(): Unit {
        for (action: CareSceneAction in CorgiAdditionalCareMotion.actions) {
            val expectedFrame: Int = CorgiAdditionalCareMotion.getPose(action, 0L, true).frame
            for (elapsed: Long in 0L..7_000L step 100L) {
                val pose: CorgiAdditionalCarePose = CorgiAdditionalCareMotion.getPose(action, elapsed, true)
                assertEquals(expectedFrame, pose.frame)
                assertEquals(0f, pose.propOffsetX, 0f)
                assertEquals(0f, pose.propOffsetY, 0f)
                assertEquals(0f, pose.rotation, 0f)
                assertEquals(1f, pose.breathScale, 0f)
                assertEquals(1f, pose.propAlpha, 0f)
            }
        }
    }

    private fun pose(action: CareSceneAction, elapsed: Long): CorgiAdditionalCarePose =
        CorgiAdditionalCareMotion.getPose(action, elapsed, false)
}
