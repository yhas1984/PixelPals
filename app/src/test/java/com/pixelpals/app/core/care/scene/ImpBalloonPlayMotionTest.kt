package com.pixelpals.app.core.care.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpBalloonPlayMotionTest {
    @Test
    fun threeBalloonsPopInOrderAndStayGone(): Unit {
        val beforeFirst: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(.27f, false)
        val afterFirst: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(.36f, false)
        val afterSecond: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(.63f, false)
        val afterThird: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(.88f, false)
        assertTrue(beforeFirst.balloons.all { it.alpha > 0f })
        assertEquals(0f, afterFirst.balloons[0].alpha, 0f)
        assertTrue(afterFirst.balloons.drop(1).all { it.alpha > 0f })
        assertTrue(afterSecond.balloons.take(2).all { it.alpha == 0f })
        assertTrue(afterSecond.balloons[2].alpha > 0f)
        assertTrue(afterThird.balloons.all { it.alpha == 0f })
        assertNull(afterThird.activeBalloon)
        assertTrue(afterThird.celebration > 0f)
    }

    @Test
    fun tridentReachesEachBalloonAtItsPop(): Unit {
        for ((index: Int, progress: Float) in listOf(.28f, .54f, .79f).withIndex()) {
            val pose: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(progress, false)
            assertEquals(index, pose.activeBalloon)
            assertEquals(1f, pose.thrust, .0001f)
        }
    }

    @Test
    fun reducedMotionUsesReadableStepsWithoutBurstAnimation(): Unit {
        for (step: Int in 0..100) {
            val pose: ImpBalloonPlayPose = ImpBalloonPlayMotion.sample(step / 100f, true)
            assertTrue(pose.balloons.all { it.burst == 0f && it.scale == 1f })
            assertTrue(pose.thrust == 0f || pose.thrust == .72f)
        }
        assertEquals(3, ImpBalloonPlayMotion.sample(0f, true).balloons.count { it.alpha > 0f })
        assertEquals(2, ImpBalloonPlayMotion.sample(.3f, true).balloons.count { it.alpha > 0f })
        assertEquals(1, ImpBalloonPlayMotion.sample(.6f, true).balloons.count { it.alpha > 0f })
        assertEquals(0, ImpBalloonPlayMotion.sample(.9f, true).balloons.count { it.alpha > 0f })
    }

    @Test
    fun timingCommitsOnceAfterTheThirdPop(): Unit {
        val scene: CareSceneController = CareSceneController(
            CareSceneAction.PLAY,
            CareSceneMode.AUTOMATIC,
            CareSceneTiming(ImpBalloonPlayMotion.DURATION_MS, ImpBalloonPlayMotion.COMPLETION_MS),
        )
        assertEquals(1, (1..500).count { scene.advance(10L) })
        assertTrue(scene.isComplete)
    }
}
