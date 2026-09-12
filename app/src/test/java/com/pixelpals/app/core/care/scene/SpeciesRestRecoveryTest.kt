package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Test

class SpeciesRestRecoveryTest {
    @Test fun wakingFinishesWithNeutralProportionsAndOpacity() {
        for (pet: PetType in listOf(PetType.JELLY, PetType.MENTA, PetType.BLOOP)) {
            val profile: PetCareProfile = PetCareProfile.forPet(pet)
            for (reduced: Boolean in listOf(false, true)) {
                val pose: SpeciesCarePose = SpeciesCareMotion.sample(profile, CareSceneAction.REST, 1f, reduced)
                assertEquals(pet.name, 1f, pose.scaleX, .00001f)
                assertEquals(pet.name, 1f, pose.scaleY, .00001f)
                assertEquals(pet.name, 1f, pose.alpha, .00001f)
                assertEquals(12, SpeciesRestRecovery.getFrame(profile.bed, 1f, reduced))
            }
        }
    }

    @Test fun wakingPreservesSleepBeforeRewardAndRecoversGradually() {
        for (step: Int in 0..79) assertEquals(step / 100f, SpeciesRestRecovery.getSleepAmount(step / 100f), 0f)
        var previous: Float = SpeciesRestRecovery.getSleepAmount(.8f)
        for (step: Int in 801..1000) {
            val amount: Float = SpeciesRestRecovery.getSleepAmount(step / 1000f)
            assertTrue("No last-frame correction", kotlin.math.abs(amount - previous) < .01f)
            previous = amount
        }
        assertNull(SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .79f, false))
        assertEquals(18, SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .8f, false))
        assertEquals(17, SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .85f, false))
        assertEquals(16, SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .9f, false))
        assertEquals(12, SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .95f, false))
        assertNull(SpeciesRestRecovery.getFrame(CareBed.PUDDLE, .95f, true))
        assertEquals(0f, SpeciesRestRecovery.getDreamAlpha(CareBed.PUDDLE, .85f), 0f)
        assertEquals(0f, SpeciesRestRecovery.getBedAlpha(CareBed.PUDDLE, 1f, false), 0f)
        assertNull(SpeciesRestRecovery.getFrame(CareBed.CUSHION, 1f, false))
    }

    @Test fun reviewedCatalogueWakeUsesOpenEyes() {
        for (pet: PetType in PetType.entries.filter { it != PetType.CORGI }) {
            val bed: CareBed = PetCareProfile.forPet(pet).bed
            val expected: Int = when (pet) { PetType.TELA, PetType.DIABLILLO -> 15; PetType.LUMI -> 1; else -> 12 }
            assertEquals(expected, SpeciesRestRecovery.getFrame(bed, 1f, false))
            assertEquals(expected, SpeciesRestRecovery.getFrame(bed, 1f, true))
        }
        assertEquals(10, SpeciesRestRecovery.getFrame(CareBed.STARLIGHT, .9f, false))
        assertEquals(11, SpeciesRestRecovery.getFrame(CareBed.WING_WRAP, .9f, false))
    }
}
