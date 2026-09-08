package com.pixelpals.app.feature.home

import org.junit.Assert.*
import org.junit.Test

class GingerTreeVisitTest {
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
        }
    }
}
