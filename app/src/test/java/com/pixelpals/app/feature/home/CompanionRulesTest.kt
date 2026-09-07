package com.pixelpals.app.feature.home

import org.junit.Assert.*
import org.junit.Test

class CompanionRulesTest {
    @Test fun gaitDoesNotAdvanceWhilePreparingOrResting(): Unit {
        val motion: CompanionMotion = CompanionMotion()
        while (motion.activity == CompanionActivity.GREET) motion.advance(.05f, 800f, 200f, 1f, 80)
        val distance: Float = motion.distanceTravelled
        repeat(3) { motion.advance(.05f, 800f, 200f, 1f, 80) }
        assertEquals(distance, motion.distanceTravelled, .001f)
        repeat(2400) {
            motion.advance(.05f, 800f, 200f, 1f, 80)
            if (motion.activity == CompanionActivity.REST) {
                val arrived: Float = motion.distanceTravelled
                repeat(20) { motion.advance(.05f, 800f, 200f, 1f, 80) }
                assertEquals(arrived, motion.distanceTravelled, .001f)
                return
            }
        }
        fail("Never reached rest")
    }
    @Test fun reversingAPlacedTargetBrakesBeforeTurning(): Unit {
        val motion: CompanionMotion = CompanionMotion()
        while (motion.speed < 60f) motion.advance(.02f, 850f, 200f, 1f, 80)
        val previousSpeed: Float = motion.speed
        motion.advance(.02f, 150f, 200f, 1f, 80)
        assertTrue(motion.isTurning)
        assertFalse(motion.isFacingLeft)
        assertTrue(motion.speed in (previousSpeed - 4f)..previousSpeed)
        repeat(100) { motion.advance(.02f, 150f, 200f, 1f, 80) }
        assertTrue(motion.isFacingLeft)
        assertTrue(motion.distanceTravelled > 0f)
    }

    @Test fun wallClockChangesCannotCompleteTravelWithinOneBoot(): Unit {
        assertEquals(110L, ExpeditionClock.advance(100, 1000, Long.MAX_VALUE, 10, true))
        assertEquals(110L, ExpeditionClock.advance(100, 1000, -100000, 10, true))
    }
    @Test fun rebootUsesNonNegativeBoundedOfflineTime(): Unit {
        assertEquals(100L, ExpeditionClock.advance(100, 1000, -900, -50, false))
        assertEquals(1100L, ExpeditionClock.advance(100, 1000, Long.MAX_VALUE, -50, false))
    }
    @Test fun catalogueHasExactlyTwentyFourUniqueObjectsAndThreeRewardRoutes(): Unit {
        assertEquals(24, DecorationCatalog.all.size)
        assertEquals(24, DecorationCatalog.all.map { it.id }.toSet().size)
        assertEquals(ExpeditionDestination.entries.map { it.id }.toSet(), DecorationCatalog.all.mapNotNull { it.expedition }.toSet())
        assertTrue(DecorationCatalog.starters.any { it.kind == DecorationKind.BED })
        assertTrue(DecorationCatalog.starters.any { it.kind == DecorationKind.TOY })
        assertTrue(DecorationCatalog.starters.any { it.kind == DecorationKind.BOWL })
    }
    @Test fun placementRejectsOutOfBoundsAndAcceptsCorners(): Unit {
        assertFalse(HomeGrid.isValid(-1, 0)); assertFalse(HomeGrid.isValid(5, 2))
        assertFalse(HomeGrid.isValid(0, 3)); assertTrue(HomeGrid.isValid(4, 2))
    }
    @Test fun targetChangesAndLongPausesNeverTeleportTheCompanion(): Unit {
        val motion: CompanionMotion = CompanionMotion()
        var previous: Float = motion.x
        repeat(4000) { index ->
            motion.advance(if (index == 200) 60f else .05f, if (index < 300) 200f else 800f, 220f, 1.2f, 80)
            assertTrue(kotlin.math.abs(motion.x - previous) <= 10.3f)
            assertTrue(motion.x in 180f..820f)
            previous = motion.x
        }
    }
    @Test fun reachesRearRowBeforeResting(): Unit {
        val motion = CompanionMotion()
        repeat(3000) {
            motion.advance(.05f, 500f, 200f, 1f, 80, 440f, 440f)
            if (motion.activity == CompanionActivity.REST) {
                assertEquals(440f, motion.y, 1f)
                assertEquals(200f, motion.x, 1f)
                return
            }
        }
        fail("The pet never reached its rear-row bed")
    }
}
