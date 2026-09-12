package com.pixelpals.app.core.motion

import com.pixelpals.app.core.care.scene.SpeciesCarePose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class JellyMedicineMotionTest {
    @Test fun endpointsClampToNeutralAndCompleteMedicine(): Unit {
        val start: JellyMedicinePose = JellyMedicineMotion.sample(-1f, false)
        val end: JellyMedicinePose = JellyMedicineMotion.sample(2f, false)
        assertEquals(JellyMedicinePose(SpeciesCarePose(), 0f, 1f, 1f), start)
        assertEquals(SpeciesCarePose(), end.body)
        assertEquals(0f, end.spoonReach, 0f)
        assertEquals(0f, end.medicineAmount, 0f)
        assertEquals(0f, end.spoonAlpha, 0f)
    }

    @Test fun contactEmptiesBeforeRetractionAndHidesBeforeSwallow(): Unit {
        val contact: JellyMedicinePose = JellyMedicineMotion.sample(.30f, false)
        val retreat: JellyMedicinePose = JellyMedicineMotion.sample(.47f, false)
        val swallow: JellyMedicinePose = JellyMedicineMotion.sample(.60f, false)
        assertTrue(contact.spoonReach > .99f)
        assertTrue(contact.medicineAmount in 0f..1f)
        assertTrue(retreat.spoonReach in 0f..1f)
        assertEquals(0f, retreat.medicineAmount, 0f)
        assertEquals(0f, JellyMedicineMotion.sample(.50f, false).spoonAlpha, 0f)
        assertTrue(abs(swallow.body.scaleY - 1f) > .001f)
    }

    @Test fun normalSamplingIsContinuousAtPhaseBoundaries(): Unit {
        val boundaries: List<Float> = listOf(
            JellyMedicineMotion.CONTACT_START,
            JellyMedicineMotion.CONTACT_END,
            JellyMedicineMotion.SPOON_RETURN_END,
            JellyMedicineMotion.SWALLOW_START,
            JellyMedicineMotion.SWALLOW_SQUASH_END,
            JellyMedicineMotion.SWALLOW_STRETCH_END,
            JellyMedicineMotion.SWALLOW_END,
        )
        val epsilon: Float = .0001f
        for (boundary: Float in boundaries) {
            val before: JellyMedicinePose = JellyMedicineMotion.sample(boundary - epsilon, false)
            val after: JellyMedicinePose = JellyMedicineMotion.sample(boundary + epsilon, false)
            assertTrue(abs(before.spoonReach - after.spoonReach) < .01f)
            assertTrue(abs(before.medicineAmount - after.medicineAmount) < .01f)
            assertTrue(abs(before.body.scaleY - after.body.scaleY) < .01f)
            assertTrue(abs(before.spoonAlpha - after.spoonAlpha) < .01f)
        }
    }

    @Test fun swallowPreservesBodyAreaAndReturnsToNeutral(): Unit {
        for (step: Int in 0..100) {
            val pose: JellyMedicinePose = JellyMedicineMotion.sample(step / 100f, false)
            assertEquals(1f, pose.body.scaleX * pose.body.scaleY, .0001f)
        }
        assertEquals(1f, JellyMedicineMotion.sample(1f, false).body.scaleY, 0f)
    }

    @Test fun reducedMotionKeepsSpoonAtMouthAndStillConsumesMedicine(): Unit {
        val early: JellyMedicinePose = JellyMedicineMotion.sample(.10f, true)
        val late: JellyMedicinePose = JellyMedicineMotion.sample(.75f, true)
        assertEquals(SpeciesCarePose(), early.body)
        assertEquals(1f, early.spoonReach, 0f)
        assertEquals(1f, early.medicineAmount, .0001f)
        assertEquals(1f, late.spoonReach, 0f)
        assertEquals(0f, late.medicineAmount, .0001f)
        assertEquals(0f, late.spoonAlpha, 0f)
    }
}
