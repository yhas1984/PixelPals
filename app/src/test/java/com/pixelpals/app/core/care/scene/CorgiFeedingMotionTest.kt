package com.pixelpals.app.core.care.scene

import org.junit.Assert.*
import org.junit.Test

class CorgiFeedingMotionTest {
    @Test fun onlyTheFourCorgiFeedingPosesAreUsed(): Unit {
        val frames: Set<Int> = (0L..6_000L step 100L).map(CorgiFeedingMotion::frameAt).toSet()
        assertEquals(setOf(0, 1, 2, 3), frames)
        assertEquals(2, CorgiFeedingMotion.frameAt(0L))
        assertEquals(0, CorgiFeedingMotion.frameAt(400L))
        assertEquals(1, CorgiFeedingMotion.frameAt(700L))
        assertEquals(3, CorgiFeedingMotion.frameAt(5_600L))
    }

    @Test fun foodDecreasesOnlyDuringBitesAndNeverRefills(): Unit {
        val amounts: List<Float> = (0L..6_000L step 50L).map(CorgiFeedingMotion::foodAt)
        assertTrue(amounts.all { it in 0f..1f })
        assertTrue(amounts.zipWithNext().all { (before, after) -> after <= before })
        assertEquals(1f, CorgiFeedingMotion.foodAt(400L), 0f)
        assertEquals(0f, CorgiFeedingMotion.foodAt(4_000L), 0f)
    }

    @Test fun desktopFeedCommitsOnceAfterTheBowlIsEmpty(): Unit {
        val scene: CareSceneController = playback()
        var completions: Int = 0
        repeat(100) {
            if (scene.advance(100L)) {
                completions++
                assertEquals(0f, CorgiFeedingMotion.foodAt(scene.animationMs), 0f)
                assertEquals(4_600L, scene.animationMs)
            }
        }
        assertEquals(1, completions)
        assertTrue(scene.isComplete)
    }

    @Test fun cancellingBeforeCompletionNeverConsumesCare(): Unit {
        val scene: CareSceneController = playback()
        assertFalse(scene.advance(3_000L))
        scene.cancel()
        assertFalse(scene.advance(10_000L))
        assertTrue(scene.isCancelled)
    }

    private fun playback(): CareSceneController = CareSceneController(
        CareSceneAction.FEED, CareSceneMode.AUTOMATIC, CorgiFeedingMotion.timing)
}
