package com.pixelpals.app.core.motion

import org.junit.Assert.assertEquals
import org.junit.Test

class GingerPostureMotionTest {
    @Test
    fun standUpAndSitDownUseMirroredThreePoseTimeline() {
        assertEquals(GingerPostureMotion.Pose(0, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.STAND_UP, 0f))
        assertEquals(GingerPostureMotion.Pose(16, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.STAND_UP, .10f))
        assertEquals(GingerPostureMotion.Pose(17, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.STAND_UP, .28f))
        assertEquals(GingerPostureMotion.Pose(18, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.STAND_UP, .48f))
        assertEquals(GingerPostureMotion.Pose(18, true), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.STAND_UP, .66f))
        assertEquals(GingerPostureMotion.Pose(18, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.SIT_DOWN, 0f))
        assertEquals(GingerPostureMotion.Pose(17, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.SIT_DOWN, .10f))
        assertEquals(GingerPostureMotion.Pose(16, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.SIT_DOWN, .28f))
        assertEquals(GingerPostureMotion.Pose(0, false), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.SIT_DOWN, .48f))
        assertEquals(GingerPostureMotion.Pose(0, true), GingerPostureMotion.poseAt(GingerPostureMotion.Transition.SIT_DOWN, .66f))
    }
}
