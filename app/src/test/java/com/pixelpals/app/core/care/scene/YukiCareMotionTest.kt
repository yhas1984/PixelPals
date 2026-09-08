package com.pixelpals.app.core.care.scene

import com.pixelpals.app.core.domain.PetType
import org.junit.Assert.*
import org.junit.Test

class YukiCareMotionTest {
    @Test fun snowballLandsBeforeItsImpactFades(): Unit {
        assertEquals(100f, YukiCareMotion.ballHeight(100f, 160f, 100f, 0f), 0f)
        assertEquals(160f, YukiCareMotion.ballHeight(100f, 160f, 100f, 1f), 0f)
        for (start: Float in listOf(0f, .5f)) {
            assertEquals(0f, YukiCareMotion.impactAt(start + .4f), 0f)
            assertEquals(1f, YukiCareMotion.flightAt(start + .45f), .001f)
            assertTrue(YukiCareMotion.impactAt(start + .49f) > .8f)
        }
    }
    @Test fun bathMeltsPartiallyAndRecoversBeforeCompletion(): Unit {
        assertEquals(0f, YukiCareMotion.meltAt(0f), 0f)
        assertEquals(1f, YukiCareMotion.meltAt(.55f), .001f)
        assertEquals(0f, YukiCareMotion.meltAt(1f), 0f)
        val profile = PetCareProfile.forPet(PetType.YUKI)
        assertEquals(CareToy.SNOWBALL, profile.toy)
        assertEquals(CareWashStyle.SHOWER, profile.wash)
        assertEquals(SpeciesCarePose(), SpeciesCareMotion.sample(profile, CareSceneAction.CLEAN, .55f, true))
        assertEquals(1f, SpeciesCareMotion.sample(profile, CareSceneAction.CLEAN, 1f, false).scaleY, 0f)
    }
    @Test fun snowballIsHeldBeforeEachOfTwoThrows(): Unit {
        for (start: Float in listOf(0f, .5f)) {
            assertEquals(0f, YukiCareMotion.flightAt(start + .1f), 0f)
            assertEquals(1f, YukiCareMotion.flightAt(start + .45f), .001f)
            assertEquals(5, YukiCareMotion.playFrame(start + .1f))
            assertEquals(6, YukiCareMotion.playFrame(start + .3f))
        }
    }
}
