package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class ImpCareMotionTest {
    @Test fun reducedMotionStillShowsAnOpenMouthForFireAndClosedEyesForSleep(): Unit {
        assertEquals(3, ImpCareMotion.getReducedFrame(CareSceneAction.FEED, 1500L))
        assertEquals(6, ImpCareMotion.getReducedFrame(CareSceneAction.FEED, 2400L))
        assertEquals(3, ImpCareMotion.getReducedFrame(CareSceneAction.FEED, 3300L))
        assertEquals(9, ImpCareMotion.getReducedFrame(CareSceneAction.REST, 0L))
        assertNull(ImpCareMotion.getReducedFrame(CareSceneAction.PLAY, 2400L))
    }
    @Test fun pettingUsesTwoSoftStrokesAndSettlesAtBothEnds(): Unit {
        val frames: List<ImpPettingPose> = (0..1000).map { ImpCareMotion.samplePetting(it / 1000f, false) }
        assertEquals(ImpPettingPose(), frames.first())
        assertEquals(0f, frames.last().handOffset, .00001f)
        frames.forEach { assertTrue(abs(it.handOffset) <= .045f && abs(it.leanDegrees) <= 2.2f) }
        val signs: List<Boolean> = frames.filter { abs(it.handOffset) > .0001f }.map { it.handOffset > 0f }
        assertEquals(3, signs.zipWithNext().count { (before, after) -> before != after })
        assertEquals(ImpPettingPose(), ImpCareMotion.samplePetting(.4f, true))
        val profile: PetCareProfile = PetCareProfile.forPet(PetType.DIABLILLO)
        assertEquals(ImpCareMotion.samplePetting(.2f, false).leanDegrees,
            SpeciesCareMotion.sample(profile, CareSceneAction.PET, .2f, false).rotation, 0f)
    }

    @Test fun wingsCloseOnceAndStayWrappedThroughoutTheNap(): Unit {
        val folds: List<Float> = (-10..110).map { ImpCareMotion.getWingFold(it / 100f, false) }
        assertEquals(0f, folds.first(), 0f)
        assertEquals(1f, folds.last(), 0f)
        assertTrue(folds.zipWithNext().all { (before, after) -> after >= before })
        assertTrue(ImpCareMotion.getWingFold(.25f, false) in .1f.. .9f)
        assertEquals(1f, ImpCareMotion.getWingFold(.41f, false), 0f)
        assertEquals(1f, ImpCareMotion.getWingFold(0f, true), 0f)
        assertEquals(CareBed.WING_WRAP, PetCareProfile.forPet(PetType.DIABLILLO).bed)
        assertEquals(CareBed.WEB, PetCareProfile.forPet(PetType.TELA).bed)
    }

    @Test fun fireOnlyAppearsAfterFoodIsGoneAndFadesBeforeTheSmile(): Unit {
        for (elapsed: Long in -100L..3600L step 10L) {
            val food: Float = ImpCareMotion.getFoodAmount(elapsed)
            val fire: ImpFirePose = ImpCareMotion.sampleFire(elapsed, false)
            assertTrue(food in 0f..1f && fire.strength in 0f..1f && fire.reach in 0f.. .40001f)
            if (fire.strength > 0f) {
                assertEquals(0f, food, 0f)
                assertTrue(elapsed in 2161L..2909L)
            }
        }
        assertEquals(1f, ImpCareMotion.getFoodAmount(0L), 0f)
        assertEquals(1f, ImpCareMotion.sampleFire(2400L, false).strength, 0f)
        assertEquals(ImpFirePose(), ImpCareMotion.sampleFire(3000L, false))
        assertEquals(ImpCareMotion.sampleFire(2300L, true), ImpCareMotion.sampleFire(2800L, true))
        assertTrue(ImpCareMotion.sampleFire(2400L, true).reach < ImpCareMotion.sampleFire(2400L, false).reach)
    }

    @Test fun appendedFireCannotAwardFeedingTwiceAndManualWaitHasNoFire(): Unit {
        for (mode: CareSceneMode in CareSceneMode.entries) {
            val scene: CareSceneController = CareSceneController(CareSceneAction.FEED, mode, CareSceneTiming(3300L, 2100L))
            if (mode == CareSceneMode.MANUAL) {
                scene.advance(4000L)
                assertEquals(ImpFirePose(), ImpCareMotion.sampleFire(scene.animationMs, false))
                scene.movePointer(CarePoint(.5f, .5f), CarePoint(.5f, .5f), true)
            }
            assertEquals(1, (1..400).count { scene.advance(10L) })
            assertTrue(scene.isComplete)
        }
    }
}
