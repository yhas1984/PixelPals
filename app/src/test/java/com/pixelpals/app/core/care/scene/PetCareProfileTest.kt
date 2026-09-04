package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PetCareProfileTest {
    @Test fun cloudCatAndRequestedPropsMatchTheirIdentity(): Unit {
        val cloud: PetCareProfile = PetCareProfile.forPet(PetType.NUBE_MICHI)
        val cat: PetCareProfile = PetCareProfile.forPet(PetType.GINGER)
        assertEquals(CareFood.DEWDROPS, cloud.food)
        assertEquals(CareToy.RAINBOW, cloud.toy)
        assertEquals(CarePlayStyle.CLOUD_DRIFT, cloud.play)
        assertEquals(CareTouchStyle.CLOUD_PUFF, cloud.touch)
        assertNotEquals(cat.feeding, cloud.feeding)
        assertNotEquals(cat.play, cloud.play)
        assertNotEquals(cat.touch, cloud.touch)
        val moki: PetCareProfile = PetCareProfile.forPet(PetType.MOKI)
        assertEquals(CareFood.FLY, moki.food)
        assertEquals(CareToy.DANCING_LEAF, moki.toy)
        assertEquals(CarePlayStyle.PEEK, moki.play)
        val lumi: PetCareProfile = PetCareProfile.forPet(PetType.LUMI)
        assertEquals(CareFood.BERRIES, lumi.food)
        assertEquals(CareToy.MAGIC_ORB, lumi.toy)
        assertEquals(CarePlayStyle.MAGIC_CHASE, lumi.play)
        assertEquals(CareFood.CRICKET, PetCareProfile.forPet(PetType.TELA).food)
        assertEquals(CareFood.CHILI, PetCareProfile.forPet(PetType.DIABLILLO).food)
    }
    @Test fun everySpeciesHasItsOwnFoodToyAndBed(): Unit {
        val profiles: List<PetCareProfile> = PetType.entries.map(PetCareProfile::forPet)
        assertEquals(15, profiles.size)
        assertEquals(15, profiles.map { it.food }.toSet().size)
        assertEquals(15, profiles.map { it.toy }.toSet().size)
        assertEquals(15, profiles.map { it.bed }.toSet().size)
        assertEquals(listOf(PetType.CORGI), PetType.entries.filter { PetCareProfile.forPet(it).play == CarePlayStyle.FETCH })
    }

    @Test fun anatomyAndTemperamentDriveTheInteraction(): Unit {
        val imp: PetCareProfile = PetCareProfile.forPet(PetType.DIABLILLO)
        assertEquals(CareFeedingStyle.HANDS, imp.feeding)
        assertEquals(CareToy.BALLOONS, imp.toy)
        assertEquals(CarePlayStyle.BALLOON_POP, imp.play)
        assertEquals(CareBed.WING_WRAP, imp.bed)
        assertEquals(CareFeedingStyle.TONGUE, PetCareProfile.forPet(PetType.MOKI).feeding)
        assertEquals(CareFeedingStyle.PECK, PetCareProfile.forPet(PetType.PATITO).feeding)
        assertEquals(CarePlayStyle.SLIDE, PetCareProfile.forPet(PetType.PIRU).play)
        assertEquals(CareTouchStyle.COIL, PetCareProfile.forPet(PetType.MENTA).touch)
        assertEquals(CareTouchStyle.SHELL, PetCareProfile.forPet(PetType.TARO).touch)
        assertTrue(imp.tempo > PetCareProfile.forPet(PetType.TARO).tempo)
        assertTrue(PetCareProfile.forPet(PetType.JELLY).tempo > PetCareProfile.forPet(PetType.BLOOP).tempo)
    }

    @Test fun allActionsAreBoundedAndReducedMotionIsStationary(): Unit {
        for (pet: PetType in PetType.entries) for (action: CareSceneAction in CareSceneAction.entries) {
            val profile: PetCareProfile = PetCareProfile.forPet(pet)
            for (step: Int in -10..110) {
                val pose: SpeciesCarePose = SpeciesCareMotion.sample(profile, action, step / 100f, false)
                assertTrue("$pet $action $step", abs(pose.x) <= .12f && abs(pose.y) <= .1f)
                assertTrue(pose.scaleX in .9f..1.1f && pose.scaleY in .9f..1.1f)
                assertTrue(pose.rotation in -10f..10f && pose.alpha in .7f..1f)
                assertEquals(SpeciesCarePose(), SpeciesCareMotion.sample(profile, action, step / 100f, true))
            }
        }
    }

    @Test fun speciesDoNotShareTheSameSixActionMotionSignature(): Unit {
        val signatures: List<List<SpeciesCarePose>> = PetType.entries.map { pet ->
            CareSceneAction.entries.flatMap { action ->
                listOf(.17f, .37f, .63f, .87f).map { progress ->
                    SpeciesCareMotion.sample(PetCareProfile.forPet(pet), action, progress, false)
                }
            }
        }
        assertEquals(PetType.entries.size, signatures.toSet().size)
    }
}
