package com.pixelpals.app.motion

import com.pixelpals.app.core.domain.PetType
import com.pixelpals.app.core.motion.PetFallDeformation
import org.junit.Assert.*
import org.junit.Test

class PetFallDeformationTest {
    private val soft: Set<PetType> = setOf(PetType.JELLY, PetType.BLOOP, PetType.NUBE_MICHI)
    @Test fun rigidSpeciesNeverStretchAndReducedMotionKeepsEveryBodyRigid() {
        for (pet: PetType in PetType.entries) for (speed: Float in listOf(-5000f, 0f, 5000f)) {
            assertEquals(1f, PetFallDeformation.heightScale(pet, speed, 80, 1.15f, .016f, true), 0f)
            if (pet !in soft) assertEquals(1f, PetFallDeformation.heightScale(pet, speed, 80, 1.15f, .016f, false), 0f)
        }
    }
    @Test fun softBodiesApproachTheirBoundWithoutAnInstantSizeJump() {
        for (pet: PetType in soft) {
            val limit: Float = if (pet == PetType.JELLY) 1.15f else 1.04f
            var height: Float = 1f
            repeat(60) {
                val next: Float = PetFallDeformation.heightScale(pet, 1000f, 80, height, 1f / 60, false)
                assertTrue(next >= height && next <= limit)
                assertTrue("No initial 15% snap", next - height < .03f)
                assertEquals("Reciprocal width preserves apparent area", 1f, next * (1f / next), .00001f)
                height = next
            }
            assertEquals(limit, height, .0001f)
            repeat(120) { height = PetFallDeformation.heightScale(pet, 0f, 80, height, 1f / 60, false) }
            assertEquals(1f, height, .0001f)
        }
    }
    @Test fun responseDependsOnElapsedTimeRatherThanFrameCount() {
        fun simulate(fps: Int): Float {
            var height: Float = 1f
            repeat(fps) { height = PetFallDeformation.heightScale(PetType.JELLY, 200f, 80, height, 1f / fps, false) }
            return height
        }
        assertEquals(simulate(30), simulate(120), .00001f)
        assertEquals(1f, PetFallDeformation.heightScale(PetType.JELLY, Float.NaN, 0, Float.NaN, Float.NaN, false), 0f)
    }
}
