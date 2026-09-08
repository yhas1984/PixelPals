package com.pixelpals.app.feature.home

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class CompanionIntentTest {
    @Test fun reducedMotionShowsRestWithoutMovingToTheBed(): Unit {
        val motion: CompanionMotion = CompanionMotion()
        motion.settleWithoutMovement(true)
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(500f, motion.x, 0f)
        assertEquals(0f, motion.distanceTravelled, 0f)
        motion.settleWithoutMovement(false)
        assertEquals(CompanionActivity.OBSERVE, motion.activity)
    }
    @Test fun missingToyNeverSelectsPlay(): Unit {
        val selector: CompanionIntentSelector = CompanionIntentSelector(Random(15))
        repeat(1000) { assertNotEquals(CompanionIntent.PLAY, selector.choose(CompanionIntentContext(hasToy = false))) }
    }
    @Test fun sickOrExhaustedCompanionsRestRegardlessOfPersonality(): Unit {
        val selector: CompanionIntentSelector = CompanionIntentSelector(Random(15))
        repeat(100) {
            assertEquals(CompanionIntent.REST, selector.choose(CompanionIntentContext(energy = 12, curiosity = 1f)))
            assertEquals(CompanionIntent.REST, selector.choose(CompanionIntentContext(isUnwell = true)))
        }
    }
    @Test fun preferencesChangeFrequencyWithoutLockingIntoOneAction(): Unit {
        fun playCount(preference: Float): Int {
            val selector: CompanionIntentSelector = CompanionIntentSelector(Random(42))
            return (0 until 2000).count { selector.choose(CompanionIntentContext(playPreference = preference)) == CompanionIntent.PLAY }
        }
        assertTrue(playCount(1f) > playCount(0f) + 150)
    }
    @Test fun exhaustedPetRestsInPlaceWithoutBedAndDoesNotWakeUntilEnergyRecovers(): Unit {
        val motion: CompanionMotion = CompanionMotion(CompanionIntentSelector(Random(42)))
        motion.context = CompanionIntentContext(energy = 10, hasBed = false, hasToy = false)
        repeat(2000) { motion.advance(.05f, 800f, 200f, 1f, 80) }
        assertEquals(CompanionActivity.REST, motion.activity)
        assertEquals(0f, motion.distanceTravelled, 0f)
        motion.context = motion.context.copy(energy = 90)
        var hasWoken: Boolean = false
        repeat(2000) {
            motion.advance(.05f, 800f, 200f, 1f, 80)
            if (motion.activity == CompanionActivity.WAKE) hasWoken = true
        }
        assertTrue(hasWoken)
    }
}
