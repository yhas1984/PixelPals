package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class CarePlayChoreographyTest {
    @Test fun variantsCycleIndependentlyForEachPetWithoutImmediateRepeats(): Unit {
        val variations: CarePlayVariations = CarePlayVariations()
        val expected: List<CarePlayVariation> = CarePlayVariation.entries
        assertEquals(expected + expected, List(6) { variations.nextFor(PetType.MOKI) })
        assertEquals(CarePlayVariation.DIRECT, variations.nextFor(PetType.NUBE_MICHI))
        assertEquals(CarePlayVariation.DIRECT, variations.nextFor(PetType.MOKI))
        assertEquals(CarePlayVariation.FEINT, variations.nextFor(PetType.NUBE_MICHI))
    }

    @Test fun allThreePathsAreDistinctContinuousBoundedAndSettle(): Unit {
        val paths: List<List<CarePlayBeat>> = CarePlayVariation.entries.map { variation ->
            val path: List<CarePlayBeat> = (0..1000).map { CarePlayChoreography.sample(it / 1000f, variation) }
            assertEquals(CarePlayBeat(-.8f, 0f, 0f), path.first())
            assertEquals(CarePlayBeat(0f, 0f, 1f), path.last())
            assertEquals(path.first(), CarePlayChoreography.sample(-1f, variation))
            assertEquals(path.last(), CarePlayChoreography.sample(2f, variation))
            path.forEach { assertTrue(it.travel in -1f..1f && it.lift in 0f..1f && it.poseProgress in 0f..1f) }
            path.zipWithNext().forEach { (before, after) ->
                assertTrue(abs(after.travel - before.travel) < .02f)
                assertTrue(abs(after.lift - before.lift) < .02f)
                assertTrue(abs(after.poseProgress - before.poseProgress) < .02f)
            }
            path
        }
        assertEquals(3, paths.toSet().size)
    }

    @Test fun variationNeverChangesCompletionOrManualAcceptance(): Unit {
        for (variation: CarePlayVariation in CarePlayVariation.entries) for (mode: CareSceneMode in CareSceneMode.entries) {
            val scene: CareSceneController = CareSceneController(CareSceneAction.PLAY, mode,
                CareSceneTiming(3000L, 2400L), variation)
            if (mode == CareSceneMode.MANUAL) {
                assertFalse(scene.advance(1000L))
                assertEquals(0L, scene.animationMs)
                scene.movePointer(CarePoint(.5f, .5f), CarePoint(.5f, .5f), false)
            }
            assertEquals(1, (1..400).count { scene.advance(10L) })
            assertTrue(scene.isComplete)
        }
    }

    @Test fun reducedMotionIsStillForEveryVariantAndSpecies(): Unit {
        for (pet: PetType in PetType.entries) for (variation: CarePlayVariation in CarePlayVariation.entries) {
            for (step: Int in 0..100) {
                assertEquals(SpeciesCarePose(), SpeciesCareMotion.sample(PetCareProfile.forPet(pet),
                    CareSceneAction.PLAY, step / 100f, true, variation))
            }
        }
    }
}
