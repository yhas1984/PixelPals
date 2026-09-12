package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesCareRigidityTest {
    @Test fun taroShellKeepsItsProportionsThroughoutEveryCare() {
        val profile: PetCareProfile = PetCareProfile.forPet(PetType.TARO)
        for (action: CareSceneAction in CareSceneAction.entries) {
            for (reduced: Boolean in listOf(false, true)) for (step: Int in 0..200) {
                val pose: SpeciesCarePose = SpeciesCareMotion.sample(profile, action, step / 200f, reduced)
                assertEquals("$action shell width at $step", 1f, pose.scaleX, 0f)
                assertEquals("$action shell height at $step", 1f, pose.scaleY, 0f)
            }
        }
    }

    @Test fun shellRigidityPreservesToyFollowingAndSoftSpeciesReactions() {
        val taro: PetCareProfile = PetCareProfile.forPet(PetType.TARO)
        val jelly: PetCareProfile = PetCareProfile.forPet(PetType.JELLY)
        assertTrue((0..100).any {
            SpeciesCareMotion.sample(taro, CareSceneAction.PLAY, it / 100f, false).x > 0f
        })
        assertTrue((0..100).any {
            SpeciesCareMotion.sample(jelly, CareSceneAction.PET, it / 100f, false).scaleX > 1f
        })
    }
}
