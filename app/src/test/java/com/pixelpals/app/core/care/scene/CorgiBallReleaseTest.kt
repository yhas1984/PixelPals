package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.motion.PetBounds
import org.junit.Assert.*
import org.junit.Test

class CorgiBallReleaseTest {
    @Test fun fallsFromRestThenBouncesWithLessEnergyAndSettles(): Unit {
        val heights = (0..850).map { CorgiBallRelease.height(100f, 160f, it / 1_000f) }
        assertEquals(100f, heights.first(), 0f)
        assertEquals(160f, heights.last(), .001f)
        assertTrue(heights.all { it in 100f..160f })
        assertTrue(heights.take(341).zipWithNext().all { (a, b) -> b >= a })
        assertTrue(heights[440] < heights[340])
        assertTrue("Damped bounce", heights[440] > 150f)
        assertTrue("Continuous position at release and impact", heights.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < .5f })
    }

    @Test fun fadesOnlyAfterSettlingAndDoesNotMoveInReducedMotion(): Unit {
        for (reduced in listOf(false, true)) {
            val plan = CorgiFetchMotion.createPlan(CarePoint(400f, 900f), PetBounds(0, 1_120, 100, 900), 320, false, reduced)
            val startFade = CorgiFetchMotion.getPose(plan, plan.timing.durationMs - 250L)
            assertEquals(1f, startFade.ballAlpha, 0f)
            if (!reduced) assertTrue(startFade.releaseSeconds >= .60f)
            var previousAlpha = 1f
            for (elapsed in plan.catchMs..plan.timing.durationMs) {
                val pose = CorgiFetchMotion.getPose(plan, elapsed)
                assertTrue(pose.ballAlpha in 0f..previousAlpha)
                if (reduced) assertEquals(-1f, pose.releaseSeconds, 0f)
                previousAlpha = pose.ballAlpha
            }
            assertEquals(0f, previousAlpha, 0f)
            assertEquals(plan.catchMs, plan.timing.completionMs)
        }
    }
}
