package com.pixelpals.app.feature.home

import org.junit.Assert.*
import org.junit.Test

class GingerTreeVisitTest {
    @Test fun bothJumpsPrepareOnTheirSupportBeforeShowingTheAirPose() {
        for (fps: Int in listOf(30, 60, 120)) {
            val visit: GingerTreeVisit = GingerTreeVisit(500f, 650f)
            val preparationTimes: MutableMap<String, Float> = mutableMapOf()
            repeat(fps * 25) {
                val phase: String = visit.phase.name
                if (phase == "COIL" || phase == "COIL_BRANCH") {
                    val expectedX: Float = if (phase == "COIL") 525f else GingerTreeVisit.BRANCH_X
                    val expectedY: Float = if (phase == "COIL") 650f else GingerTreeVisit.BRANCH_Y
                    assertEquals(expectedX, visit.x, .001f)
                    assertEquals(expectedY, visit.y, .001f)
                    assertEquals("pounce", visit.clip)
                    assertEquals(0f, visit.clipSeconds, .001f)
                    preparationTimes[phase] = (preparationTimes[phase] ?: 0f) + 1f / fps
                }
                if (visit.isAirborne) {
                    assertEquals("pounce", visit.clip)
                    assertTrue(visit.clipSeconds >= .12f)
                }
                visit.advance(1f / fps)
            }
            assertEquals(setOf("COIL", "COIL_BRANCH"), preparationTimes.keys)
            preparationTimes.values.forEach { assertTrue(it in .34f..(.35f + 1f / fps + .001f)) }
            assertEquals(GingerTreeVisit.Phase.DONE, visit.phase)
        }
    }
    @Test fun visitsTheBranchAndReturnsWithoutChangingItsOrigin() {
        for (start in listOf(180f to 445f, 500f to 650f, 830f to 651f)) {
            val visit = GingerTreeVisit(start.first, start.second)
            val phases = mutableSetOf<GingerTreeVisit.Phase>()
            repeat(5000) {
                visit.advance(1f / 60f)
                phases.add(visit.phase)
                assertTrue(visit.x in 140f..860f)
                assertTrue(visit.y in 290f..680f)
                if (visit.phase == GingerTreeVisit.Phase.PERCH) {
                    assertEquals(GingerTreeVisit.BRANCH_X, visit.x, .001f)
                    assertEquals(GingerTreeVisit.BRANCH_Y, visit.y, .001f)
                }
            }
            assertEquals(GingerTreeVisit.Phase.entries.toSet(), phases)
            assertEquals(start.first, visit.x, .001f)
            assertEquals(start.second, visit.y, .001f)
            assertEquals(start.first < 525f, visit.facingLeft)
            val motion: CompanionMotion = CompanionMotion()
            motion.finishExcursion(visit.facingLeft)
            assertEquals(visit.facingLeft, motion.isFacingLeft)
            assertEquals(visit.facingLeft, motion.turnFromFacingLeft)
            assertFalse(motion.isTurning)
            assertEquals(CompanionActivity.OBSERVE, motion.activity)
            assertEquals(0f, motion.speed, .001f)
        }
    }
}
