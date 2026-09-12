package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class CorgiTurnMotionTest {
    @Test
    fun negativeTimeClampsToFirstPose() {
        assertEquals(CorgiTurnMotion.Pose(0, false, false), CorgiTurnMotion.poseAt(-1f))
    }

    @Test
    fun eachSegmentHasTheDeclaredBoundaryAndOrientation() {
        val expected: List<CorgiTurnMotion.Pose> = listOf(
            CorgiTurnMotion.Pose(0, false, false),
            CorgiTurnMotion.Pose(20, false, false),
            CorgiTurnMotion.Pose(21, false, false),
            CorgiTurnMotion.Pose(22, false, false),
            CorgiTurnMotion.Pose(21, true, false),
            CorgiTurnMotion.Pose(20, true, false),
            CorgiTurnMotion.Pose(0, true, false),
        )
        expected.forEachIndexed { index: Int, pose: CorgiTurnMotion.Pose ->
            assertEquals(pose, CorgiTurnMotion.poseAt(index * CorgiTurnMotion.SEGMENT_SECONDS))
        }
        assertEquals(CorgiTurnMotion.Pose(0, true, false), CorgiTurnMotion.poseAt(.559f))
    }

    @Test
    fun endpointIsFinishedEvenWithLargeDelta() {
        assertEquals(CorgiTurnMotion.Pose(0, true, true), CorgiTurnMotion.poseAt(.56f))
        assertEquals(CorgiTurnMotion.Pose(0, true, true), CorgiTurnMotion.poseAt(4f))
    }

    @Test
    fun sampledAt30To120FpsVisitsTheSevenPosesInOrder() {
        val expected: List<CorgiTurnMotion.Pose> = listOf(
            CorgiTurnMotion.Pose(0, false, false),
            CorgiTurnMotion.Pose(20, false, false),
            CorgiTurnMotion.Pose(21, false, false),
            CorgiTurnMotion.Pose(22, false, false),
            CorgiTurnMotion.Pose(21, true, false),
            CorgiTurnMotion.Pose(20, true, false),
            CorgiTurnMotion.Pose(0, true, false),
        )
        for (fps: Int in listOf(30, 60, 120)) {
            val seen: MutableList<CorgiTurnMotion.Pose> = mutableListOf()
            var elapsed: Float = 0f
            while (elapsed < CorgiTurnMotion.DURATION_SECONDS) {
                val pose: CorgiTurnMotion.Pose = CorgiTurnMotion.poseAt(elapsed)
                if (seen.lastOrNull() != pose) seen += pose
                elapsed += 1f / fps
            }
            assertEquals("fps=$fps must visit the seven poses in order", expected, seen.map { it.copy(finished = false) })
            assertEquals(CorgiTurnMotion.Pose(0, true, true), CorgiTurnMotion.poseAt(elapsed))
        }
    }
}
