package com.pixelpals.app.feature.home

import org.junit.Assert.*
import org.junit.Test

class HomeDepthTest {
    @Test fun bedDoesNotCoverPetAtContact() {
        assertTrue(HomeDepth.isBehindPet(548f, 548f))
        assertTrue(HomeDepth.isBehindPet(548f, 547.7f))
        assertFalse(HomeDepth.isBehindPet(651f, 548f))
        assertTrue(HomeDepth.isBehindPet(445f, 548f))
    }

    @Test fun arrivalFromEitherDepthStopsExactlyOnBed() {
        for (bedY in listOf(445f, 548f, 651f)) {
            val motion = CompanionMotion()
            motion.context = CompanionIntentContext(hasBed = true)
            motion.beginIntent(CompanionIntent.REST)
            var frames = 0
            while (motion.activity != CompanionActivity.REST && frames++ < 4000) {
                motion.advance(1f / 60f, 500f, 312.5f, 1f, 50, bedY = bedY)
            }
            assertEquals(CompanionActivity.REST, motion.activity)
            assertEquals(312.5f, motion.x, .0001f)
            assertEquals(bedY, motion.y, .0001f)
            assertTrue(HomeDepth.isBehindPet(bedY, motion.y))
        }
    }
}
