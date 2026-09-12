package com.pixelpals.app.feature.home

import kotlin.math.hypot
import org.junit.Assert.*
import org.junit.Test

class TelaHomeWebTest {
    private fun onThread(x: Float, y: Float): Boolean = TelaHomeWeb.NODES.indices.any { a ->
        TelaHomeWeb.neighbors(a).any { b ->
            val p = TelaHomeWeb.NODES[a]; val q = TelaHomeWeb.NODES[b]
            val length = hypot(q.x-p.x,q.y-p.y)
            hypot(x-p.x,y-p.y)+hypot(q.x-x,q.y-y) <= length + .02f
        }
    }
    @Test fun patrolAndInterruptedHuntsStayOnThreadsWithoutJumpsAtAllFrameRates() {
        for (fps in listOf(30,60,120)) {
            val web = TelaHomeWeb()
            repeat(fps*80) { frame ->
                if (frame == fps*4) assertTrue(web.hunt(2))
                if (frame == fps*5) web.cancelHunt()
                if (frame == fps*6) assertTrue(web.hunt(1))
                val x = web.x; val y = web.y
                web.advance(1f/fps)
                assertTrue("movement is continuous", hypot(web.x-x,web.y-y) <= 125f/fps + .05f)
                assertTrue("actor follows painted edges", onThread(web.x, web.y))
            }
        }
    }
    @Test fun chosenFlyIsReachedAndMealSignalsOnceWithoutAutomaticFeeding() {
        for (fly in 0..2) {
            val web = TelaHomeWeb()
            repeat(260) { assertFalse(web.advance(.016f)) }
            assertTrue(web.hunt(fly))
            var signals = 0
            repeat(4000) { if (web.advance(.016f)) signals++ }
            assertEquals(1,signals)
            assertEquals(web.flies[fly].x,web.x,.01f)
            assertEquals(web.flies[fly].y,web.y,.01f)
            assertTrue(web.isFlyVisible(fly))
            web.finishMeal(true)
            assertFalse(web.isFlyVisible(fly))
            repeat(3000) { assertFalse(web.advance(.016f)) }
            assertTrue(web.isFlyVisible(fly))
        }
    }
    @Test fun failedMealRestAndReducedMotionDoNotGrantFood() {
        val web = TelaHomeWeb()
        repeat(1000) { assertFalse(web.advance(.016f, reduced=true)) }
        assertEquals(TelaHomeWeb.CENTER,WebPoint(web.x,web.y))
        assertTrue(web.hunt(0))
        val before=WebPoint(web.x,web.y)
        repeat(100) { assertFalse(web.advance(.016f,resting=true)) }
        assertEquals(before,WebPoint(web.x,web.y))
        repeat(3000) { web.advance(.016f,reduced=true) }
        assertTrue(web.isEating)
        web.finishMeal(false)
        assertTrue(web.isFlyVisible(0)); assertFalse(web.isEating)
        assertTrue(web.hunt(0))
    }
}
